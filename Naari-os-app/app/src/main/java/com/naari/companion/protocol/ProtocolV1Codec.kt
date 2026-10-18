package com.naari.companion.protocol

import java.nio.charset.StandardCharsets

/**
 * Authoritative codec and validator for Protocol v1.
 * Ref: PRD Section 5: Existing BLE v1 contract.
 *
 * Enforces:
 * - 4-byte little-endian request ID notification parser
 * - UTF-8 JSON size <= 480 bytes
 * - Message payload <= 256 bytes
 * - Guardian name <= 32 bytes without breaking multibyte characters
 * - Phone numbers: optional '+' with 3 to 20 digits (or empty to clear slot)
 * - Coordinate bounds: lat in [-90, 90], lon in [-180, 180]
 * - Explicit rejection of unknown operations and malformed payloads
 */
object ProtocolV1Codec {

    private val PHONE_REGEX = Regex("^\\+?[0-9]{3,20}$")

    /**
     * Parses the 4-byte little-endian request ID from a notification packet.
     * PRD: "A notification contains a 4-byte little-endian request ID, not JSON."
     */
    fun parseNotificationRequestId(bytes: ByteArray): Long {
        if (bytes.size != 4) {
            throw ProtocolException(
                "Invalid notification length: expected 4 bytes, got ${bytes.size}",
                ProtocolErrorCode.PARSE_ERROR
            )
        }
        val b0 = bytes[0].toLong() and 0xFFL
        val b1 = bytes[1].toLong() and 0xFFL
        val b2 = bytes[2].toLong() and 0xFFL
        val b3 = bytes[3].toLong() and 0xFFL
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    /**
     * Serializes an unsigned 32-bit integer request ID into 4 little-endian bytes.
     */
    fun serializeNotificationRequestId(id: Long): ByteArray {
        val bytes = ByteArray(4)
        bytes[0] = (id and 0xFFL).toByte()
        bytes[1] = ((id shr 8) and 0xFFL).toByte()
        bytes[2] = ((id shr 16) and 0xFFL).toByte()
        bytes[3] = ((id shr 24) and 0xFFL).toByte()
        return bytes
    }

    /**
     * Validates that a phone number meets PRD Section 5 constraints:
     * Optional leading '+', 3 to 20 digits.
     * An empty string is allowed to indicate clearing a guardian slot.
     */
    fun validatePhoneNumber(phone: String): Boolean {
        if (phone.isEmpty()) return true
        return phone.matches(PHONE_REGEX)
    }

    /**
     * Sanitizes a phone number from contact pickers or clipboard:
     * Retains optional leading '+', strips formatting spaces, hyphens, parentheses, and dots.
     */
    fun sanitizePhoneNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digitsOnly" else digitsOnly
    }

    /**
     * Truncates a UTF-8 string to at most [maxBytes] without cutting a multibyte sequence in half.
     */
    fun truncateNameUtf8(name: String, maxBytes: Int = ProtocolConstants.MAX_NAME_BYTES): String {
        val totalBytes = name.toByteArray(StandardCharsets.UTF_8)
        if (totalBytes.size <= maxBytes) {
            return name
        }

        var byteCount = 0
        val sb = StringBuilder()
        var i = 0
        while (i < name.length) {
            val codePoint = name.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            val charString = name.substring(i, i + charCount)
            val charBytes = charString.toByteArray(StandardCharsets.UTF_8).size

            if (byteCount + charBytes > maxBytes) {
                break
            }
            sb.append(charString)
            byteCount += charBytes
            i += charCount
        }
        return sb.toString()
    }

    /**
     * Builds the JSON payload for the phone hello handshake.
     * PRD: {"v":1,"op":"hello","sms":true,"calls":true,"location":true}
     */
    fun buildHelloJson(sms: Boolean, calls: Boolean, location: Boolean): String {
        val json = """{"v":1,"op":"hello","sms":$sms,"calls":$calls,"location":$location}"""
        validateMaxJsonBytes(json)
        return json
    }

    /**
     * Parses the phone hello message from JSON.
     */
    fun parseHelloJson(jsonStr: String): PhoneHelloMessage {
        validateMaxJsonBytes(jsonStr)
        val map = parseSimpleJson(jsonStr)
        val v = (map["v"] as? Number)?.toInt()
            ?: throw ProtocolException("Missing version field", ProtocolErrorCode.INVALID_VERSION)
        if (v != 1) {
            throw ProtocolException("Unsupported version $v, expected 1", ProtocolErrorCode.INVALID_VERSION)
        }

        val op = map["op"] as? String
            ?: throw ProtocolException("Missing op field", ProtocolErrorCode.UNKNOWN_OPERATION)
        if (op != "hello") {
            throw UnknownOperationException(op)
        }

        val sms = map["sms"] as? Boolean ?: false
        val calls = map["calls"] as? Boolean ?: false
        val location = map["location"] as? Boolean ?: false

        return PhoneHelloMessage(version = v, op = op, sms = sms, calls = calls, location = location)
    }

    /**
     * Builds the guardian profile update JSON.
     * PRD: {"v":1,"op":"profile","name0":"...","phone0":"...","name1":"...","phone1":"..."}
     */
    fun buildProfileJson(
        name0: String,
        phone0: String,
        name1: String,
        phone1: String
    ): String {
        val cleanName0 = truncateNameUtf8(name0)
        val cleanName1 = truncateNameUtf8(name1)

        if (!validatePhoneNumber(phone0)) {
            throw InvalidPhoneNumberException(phone0)
        }
        if (!validatePhoneNumber(phone1)) {
            throw InvalidPhoneNumberException(phone1)
        }

        val escapedName0 = escapeJsonString(cleanName0)
        val escapedName1 = escapeJsonString(cleanName1)

        val json = """{"v":1,"op":"profile","name0":"$escapedName0","phone0":"$phone0","name1":"$escapedName1","phone1":"$phone1"}"""
        validateMaxJsonBytes(json)
        return json
    }

    fun buildProfileJson(profile: PhoneProfileMessage): String {
        return buildProfileJson(
            name0 = profile.name0,
            phone0 = profile.phone0,
            name1 = profile.name1,
            phone1 = profile.phone1
        )
    }

    /**
     * Parses the profile update JSON.
     */
    fun parseProfileJson(jsonStr: String): PhoneProfileMessage {
        validateMaxJsonBytes(jsonStr)
        val map = parseSimpleJson(jsonStr)
        val v = (map["v"] as? Number)?.toInt()
            ?: throw ProtocolException("Missing version field", ProtocolErrorCode.INVALID_VERSION)
        if (v != 1) {
            throw ProtocolException("Unsupported version $v, expected 1", ProtocolErrorCode.INVALID_VERSION)
        }

        val op = map["op"] as? String
            ?: throw ProtocolException("Missing op field", ProtocolErrorCode.UNKNOWN_OPERATION)
        if (op != "profile") {
            throw UnknownOperationException(op)
        }

        val name0 = map["name0"] as? String ?: ""
        val phone0 = map["phone0"] as? String ?: ""
        val name1 = map["name1"] as? String ?: ""
        val phone1 = map["phone1"] as? String ?: ""

        if (!validatePhoneNumber(phone0)) throw InvalidPhoneNumberException(phone0)
        if (!validatePhoneNumber(phone1)) throw InvalidPhoneNumberException(phone1)

        return PhoneProfileMessage(
            version = v,
            op = op,
            name0 = name0,
            phone0 = phone0,
            name1 = name1,
            phone1 = phone1
        )
    }

    /**
     * Parses an inbound watch request from characteristic e1.
     * PRD: "{}" means no pending request.
     */
    fun parseWatchRequest(jsonStr: String): WatchRequest? {
        validateMaxJsonBytes(jsonStr)
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty() || trimmed == "{}") {
            return null
        }

        val map = parseSimpleJson(trimmed)
        val v = (map["v"] as? Number)?.toInt()
            ?: throw ProtocolException("Missing version field", ProtocolErrorCode.INVALID_VERSION)
        if (v != 1) {
            throw ProtocolException("Unsupported version $v, expected 1", ProtocolErrorCode.INVALID_VERSION)
        }

        val id = (map["id"] as? Number)?.toLong()
            ?: throw ProtocolException("Missing request id", ProtocolErrorCode.PARSE_ERROR)

        val rawType = map["type"] as? String
            ?: throw ProtocolException("Missing request type", ProtocolErrorCode.UNKNOWN_OPERATION)

        val type = WatchRequestType.fromWire(rawType)
            ?: throw UnknownOperationException(rawType)

        val phone = map["phone"] as? String
        if (phone != null && phone.isNotEmpty() && !validatePhoneNumber(phone)) {
            throw InvalidPhoneNumberException(phone)
        }

        val message = map["message"] as? String
        if (message != null) {
            val msgBytes = message.toByteArray(StandardCharsets.UTF_8).size
            if (msgBytes > ProtocolConstants.MAX_MESSAGE_BYTES) {
                throw OversizedPayloadException(msgBytes, ProtocolConstants.MAX_MESSAGE_BYTES)
            }
        }

        return WatchRequest(
            version = v,
            id = id,
            type = type,
            phone = phone,
            message = message
        )
    }

    /**
     * Serializes a phone reply to characteristic e2.
     * PRD: {"v":1,"id":123,"status":"sent","detail":"SMS sent","lat":19.1,"lon":72.9}
     */
    fun buildReplyJson(reply: PhoneReply): String {
        validateCoordinates(reply.lat, reply.lon)

        val sb = StringBuilder()
        sb.append("""{"v":1,"id":""").append(reply.id)
        sb.append(""","status":"""").append(reply.status.wireName).append('"')

        if (reply.detail != null) {
            sb.append(""","detail":"""").append(escapeJsonString(reply.detail)).append('"')
        }

        if (reply.lat != null) {
            sb.append(""","lat":""").append(reply.lat)
        }

        if (reply.lon != null) {
            sb.append(""","lon":""").append(reply.lon)
        }

        sb.append('}')
        val json = sb.toString()
        validateMaxJsonBytes(json)
        return json
    }

    /**
     * Parses an outbound or cached phone reply.
     */
    fun parseReplyJson(jsonStr: String): PhoneReply {
        validateMaxJsonBytes(jsonStr)
        val map = parseSimpleJson(jsonStr)
        val v = (map["v"] as? Number)?.toInt()
            ?: throw ProtocolException("Missing version field", ProtocolErrorCode.INVALID_VERSION)
        if (v != 1) {
            throw ProtocolException("Unsupported version $v, expected 1", ProtocolErrorCode.INVALID_VERSION)
        }

        val id = (map["id"] as? Number)?.toLong()
            ?: throw ProtocolException("Missing reply id", ProtocolErrorCode.PARSE_ERROR)

        val rawStatus = map["status"] as? String
            ?: throw ProtocolException("Missing status field", ProtocolErrorCode.PARSE_ERROR)

        val status = PhoneReplyStatus.fromWire(rawStatus)
            ?: throw UnknownOperationException(rawStatus)

        val detail = map["detail"] as? String
        val lat = (map["lat"] as? Number)?.toDouble()
        val lon = (map["lon"] as? Number)?.toDouble()

        validateCoordinates(lat, lon)

        return PhoneReply(
            version = v,
            id = id,
            status = status,
            detail = detail,
            lat = lat,
            lon = lon
        )
    }

    private fun validateCoordinates(lat: Double?, lon: Double?) {
        if (lat != null) {
            if (lat.isNaN() || lat.isInfinite() || lat < ProtocolConstants.MIN_LATITUDE || lat > ProtocolConstants.MAX_LATITUDE) {
                throw InvalidCoordinatesException(lat, lon)
            }
        }
        if (lon != null) {
            if (lon.isNaN() || lon.isInfinite() || lon < ProtocolConstants.MIN_LONGITUDE || lon > ProtocolConstants.MAX_LONGITUDE) {
                throw InvalidCoordinatesException(lat, lon)
            }
        }
    }

    private fun validateMaxJsonBytes(json: String) {
        val byteCount = json.toByteArray(StandardCharsets.UTF_8).size
        if (byteCount > ProtocolConstants.MAX_JSON_BYTES) {
            throw OversizedPayloadException(byteCount, ProtocolConstants.MAX_JSON_BYTES)
        }
    }

    private fun escapeJsonString(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Pure-Kotlin parser for flat JSON objects.
     * Robust against whitespace, escapes, ints, doubles, booleans, and strings.
     */
    private fun parseSimpleJson(text: String): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val trimmed = text.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw ProtocolException("Expected JSON object starting with '{' and ending with '}'", ProtocolErrorCode.PARSE_ERROR)
        }

        var i = 1
        val len = trimmed.length - 1

        while (i < len) {
            // Skip whitespace and commas
            while (i < len && (trimmed[i].isWhitespace() || trimmed[i] == ',')) {
                i++
            }
            if (i >= len) break

            // Parse key (must be a quoted string)
            if (trimmed[i] != '"') {
                throw ProtocolException("Expected quoted string key at index $i", ProtocolErrorCode.PARSE_ERROR)
            }
            val (key, nextIndex) = parseJsonStringToken(trimmed, i)
            i = nextIndex

            // Skip whitespace to colon
            while (i < len && trimmed[i].isWhitespace()) {
                i++
            }
            if (i >= len || trimmed[i] != ':') {
                throw ProtocolException("Expected ':' after key at index $i", ProtocolErrorCode.PARSE_ERROR)
            }
            i++ // skip ':'

            // Skip whitespace to value
            while (i < len && trimmed[i].isWhitespace()) {
                i++
            }
            if (i >= len) {
                throw ProtocolException("Expected value after ':' at index $i", ProtocolErrorCode.PARSE_ERROR)
            }

            // Parse value
            val (value, valEndIndex) = parseJsonValueToken(trimmed, i)
            map[key] = value
            i = valEndIndex
        }

        return map
    }

    private fun parseJsonStringToken(s: String, startIndex: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var i = startIndex + 1
        var escaped = false

        while (i < s.length) {
            val c = s[i]
            if (escaped) {
                when (c) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'u' -> {
                        if (i + 4 < s.length) {
                            val hex = s.substring(i + 1, i + 5)
                            sb.append(hex.toInt(16).toChar())
                            i += 4
                        }
                    }
                    else -> sb.append(c)
                }
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '"') {
                return Pair(sb.toString(), i + 1)
            } else {
                sb.append(c)
            }
            i++
        }
        throw ProtocolException("Unterminated string literal", ProtocolErrorCode.PARSE_ERROR)
    }

    private fun parseJsonValueToken(s: String, startIndex: Int): Pair<Any?, Int> {
        val c = s[startIndex]
        if (c == '"') {
            return parseJsonStringToken(s, startIndex)
        }

        // Parse boolean / null / number
        var i = startIndex
        while (i < s.length && s[i] != ',' && s[i] != '}' && !s[i].isWhitespace()) {
            i++
        }
        val token = s.substring(startIndex, i).trim()
        val parsed: Any? = when {
            token == "true" -> true
            token == "false" -> false
            token == "null" -> null
            token.contains(".") -> token.toDoubleOrNull()
                ?: throw ProtocolException("Invalid number: $token", ProtocolErrorCode.PARSE_ERROR)
            else -> token.toLongOrNull()
                ?: throw ProtocolException("Invalid numeric token: $token", ProtocolErrorCode.PARSE_ERROR)
        }
        return Pair(parsed, i)
    }
}
