package com.naari.companion.protocol

import java.nio.charset.StandardCharsets

/**
 * Pure-Kotlin serializer and parser for Protocol v2 Additive Control & Events.
 * Ref: PRD Section 6 & docs/PROTOCOL_V2.md.
 * Does not depend on Android SDK's org.json.JSONObject, enabling standalone host unit testing.
 */
object ProtocolV2Codec {

    fun buildGetStatusCommand(cmdId: Int): String {
        return buildCommandJson(cmdId, "get_status", emptyMap())
    }

    fun buildGetProfileCommand(cmdId: Int): String {
        return buildCommandJson(cmdId, "get_profile", emptyMap())
    }

    fun buildSetWallpaperCommand(cmdId: Int, index: Int): String {
        require(index in 0..4) { "Wallpaper index must be between 0 and 4 inclusive, got $index" }
        return buildCommandJson(cmdId, "set_wallpaper", mapOf("index" to index))
    }

    fun buildSetBrightnessCommand(cmdId: Int, percent: Int): String {
        require(percent in 0..100) { "Brightness percent must be between 0 and 100 inclusive, got $percent" }
        return buildCommandJson(cmdId, "set_brightness", mapOf("percent" to percent))
    }

    fun buildSetVolumeCommand(cmdId: Int, percent: Int): String {
        require(percent in 0..100) { "Volume percent must be between 0 and 100 inclusive, got $percent" }
        return buildCommandJson(cmdId, "set_volume", mapOf("percent" to percent))
    }

    fun buildSetFallEnabledCommand(cmdId: Int, enabled: Boolean): String {
        return buildCommandJson(cmdId, "set_fall_enabled", mapOf("enabled" to enabled))
    }

    fun buildSetModeCommand(cmdId: Int, mode: WatchMode): String {
        return buildCommandJson(cmdId, "set_mode", mapOf("mode" to mode.name))
    }

    fun buildFindWatchCommand(cmdId: Int, durationSeconds: Int = 15): String {
        require(durationSeconds in 1..60) { "Find watch duration must be 1..60 seconds, got $durationSeconds" }
        return buildCommandJson(cmdId, "find_watch", mapOf("duration_s" to durationSeconds))
    }

    fun buildSyncTimeCommand(cmdId: Int, epochSeconds: Long, tzOffsetMinutes: Int): String {
        return buildCommandJson(
            cmdId,
            "sync_time",
            mapOf("epoch_s" to epochSeconds, "tz_offset_min" to tzOffsetMinutes)
        )
    }

    fun buildSafeCheckInCommand(cmdId: Int, pinHash: String): String {
        return buildCommandJson(cmdId, "safe_check_in", mapOf("pin_hash" to pinHash))
    }

    private fun buildCommandJson(cmdId: Int, operation: String, args: Map<String, Any?>): String {
        val sb = StringBuilder()
        sb.append("{\"v\":2,\"cmd\":").append(cmdId)
        sb.append(",\"op\":\"").append(escapeJson(operation)).append("\"")
        sb.append(",\"args\":{")

        var first = true
        for ((k, v) in args) {
            if (!first) sb.append(",")
            first = false
            sb.append("\"").append(escapeJson(k)).append("\":")
            sb.append(serializeJsonValue(v))
        }
        sb.append("}}")

        val jsonString = sb.toString()
        val byteLength = jsonString.toByteArray(StandardCharsets.UTF_8).size
        if (byteLength > ProtocolConstants.MAX_JSON_BYTES_V2) {
            throw ProtocolPayloadTooLargeException("Command JSON exceeds ${ProtocolConstants.MAX_JSON_BYTES_V2} bytes (was $byteLength bytes)")
        }
        return jsonString
    }

    private fun serializeJsonValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is Boolean -> value.toString()
            is Number -> value.toString()
            is String -> "\"" + escapeJson(value) + "\""
            is List<*> -> {
                val items = value.joinToString(",") { serializeJsonValue(it) }
                "[$items]"
            }
            is Map<*, *> -> {
                val entries = value.entries.joinToString(",") { (k, v) ->
                    "\"" + escapeJson(k.toString()) + "\":" + serializeJsonValue(v)
                }
                "{$entries}"
            }
            else -> "\"" + escapeJson(value.toString()) + "\""
        }
    }

    private fun escapeJson(s: String): String {
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

    fun parseControlResponseJson(jsonString: String): ControlResponse {
        val byteLength = jsonString.toByteArray(StandardCharsets.UTF_8).size
        if (byteLength > ProtocolConstants.MAX_JSON_BYTES_V2) {
            throw ProtocolPayloadTooLargeException("Response JSON exceeds ${ProtocolConstants.MAX_JSON_BYTES_V2} bytes (was $byteLength bytes)")
        }

        val parsed = try {
            JsonParser(jsonString).parse() as? Map<*, *>
                ?: throw ProtocolParseException("Expected JSON object at root")
        } catch (e: Exception) {
            if (e is ProtocolException) throw e
            throw ProtocolParseException("Malformed response JSON: ${e.message}", e)
        }

        val version = (parsed["v"] as? Number)?.toInt() ?: 2
        val cmdId = (parsed["cmd"] as? Number)?.toInt()
            ?: throw ProtocolParseException("Missing required field 'cmd' in response JSON")

        val statusStr = (parsed["status"] as? String)?.lowercase() ?: ""
        val status = when (statusStr) {
            "applied" -> ControlStatus.Applied
            "accepted" -> ControlStatus.Accepted
            "rejected" -> ControlStatus.Rejected
            else -> throw ProtocolParseException("Unknown status: '$statusStr'")
        }

        val err = parsed["err"] as? String

        @Suppress("UNCHECKED_CAST")
        val appliedMap = (parsed["applied"] as? Map<String, Any?>) ?: emptyMap()

        return ControlResponse(
            version = version,
            commandId = cmdId,
            status = status,
            error = err,
            appliedData = appliedMap
        )
    }

    fun parseStatusSnapshot(appliedMap: Map<String, Any?>): WatchStatusSnapshot {
        val bootId = (appliedMap["boot_id"] as? Number)?.toLong() ?: 0L
        val seq = (appliedMap["seq"] as? Number)?.toLong() ?: 0L
        val battery = (appliedMap["battery"] as? Number)?.toInt()
        val charging = (appliedMap["charging"] as? Boolean) ?: false
        val wallpaper = (appliedMap["wallpaper"] as? Number)?.toInt()
        val brightness = (appliedMap["brightness"] as? Number)?.toInt()
        val volume = (appliedMap["volume"] as? Number)?.toInt()
        val fallEnabled = appliedMap["fall_enabled"] as? Boolean
        val modeStr = appliedMap["mode"] as? String
        val mode = when (modeStr?.uppercase()) {
            "SAFE" -> WatchMode.SAFE
            "WATCH" -> WatchMode.WATCH
            "EMERGENCY" -> WatchMode.EMERGENCY
            else -> null
        }

        val capabilitiesList = when (val caps = appliedMap["capabilities"]) {
            is List<*> -> caps.filterIsInstance<String>()
            else -> emptyList()
        }
        val capabilities = if (capabilitiesList.isNotEmpty()) {
            WatchCapabilities.fromList(capabilitiesList)
        } else {
            WatchCapabilities.NONE
        }

        return WatchStatusSnapshot(
            bootId = bootId,
            sequence = seq,
            batteryPercent = battery,
            isCharging = charging,
            activeWallpaper = wallpaper,
            brightness = brightness,
            volume = volume,
            fallDetectionEnabled = fallEnabled,
            mode = mode,
            capabilities = capabilities,
            lastSyncTimestampMs = System.currentTimeMillis()
        )
    }

    /**
     * Pure Kotlin recursive JSON parser for objects, arrays, and primitives.
     */
    private class JsonParser(private val text: String) {
        private var pos = 0

        fun parse(): Any? {
            skipWhitespace()
            val result = parseValue()
            skipWhitespace()
            return result
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= text.length) throw ProtocolParseException("Unexpected end of input")

            return when (val c = text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> {
                    if (c == '-' || c in '0'..'9') {
                        parseNumber()
                    } else {
                        throw ProtocolParseException("Unexpected character '$c' at offset $pos")
                    }
                }
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = mutableMapOf<String, Any?>()
            pos++ // skip '{'
            skipWhitespace()

            if (pos < text.length && text[pos] == '}') {
                pos++
                return map
            }

            while (pos < text.length) {
                skipWhitespace()
                if (pos >= text.length || text[pos] != '"') {
                    throw ProtocolParseException("Expected string key in object at offset $pos")
                }
                val key = parseString()
                skipWhitespace()
                if (pos >= text.length || text[pos] != ':') {
                    throw ProtocolParseException("Expected ':' after key at offset $pos")
                }
                pos++ // skip ':'
                skipWhitespace()
                val value = parseValue()
                map[key] = value

                skipWhitespace()
                if (pos < text.length && text[pos] == ',') {
                    pos++
                } else if (pos < text.length && text[pos] == '}') {
                    pos++
                    return map
                } else {
                    throw ProtocolParseException("Expected ',' or '}' in object at offset $pos")
                }
            }
            throw ProtocolParseException("Unterminated object")
        }

        private fun parseArray(): List<Any?> {
            val list = mutableListOf<Any?>()
            pos++ // skip '['
            skipWhitespace()

            if (pos < text.length && text[pos] == ']') {
                pos++
                return list
            }

            while (pos < text.length) {
                skipWhitespace()
                val value = parseValue()
                list.add(value)

                skipWhitespace()
                if (pos < text.length && text[pos] == ',') {
                    pos++
                } else if (pos < text.length && text[pos] == ']') {
                    pos++
                    return list
                } else {
                    throw ProtocolParseException("Expected ',' or ']' in array at offset $pos")
                }
            }
            throw ProtocolParseException("Unterminated array")
        }

        private fun parseString(): String {
            pos++ // skip leading '"'
            val sb = StringBuilder()
            while (pos < text.length) {
                val c = text[pos]
                if (c == '\\') {
                    pos++
                    if (pos >= text.length) throw ProtocolParseException("Unfinished escape sequence")
                    when (val esc = text[pos]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 >= text.length) throw ProtocolParseException("Unfinished unicode escape")
                            val hex = text.substring(pos + 1, pos + 5)
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> sb.append(esc)
                    }
                } else if (c == '"') {
                    pos++ // skip trailing '"'
                    return sb.toString()
                } else {
                    sb.append(c)
                }
                pos++
            }
            throw ProtocolParseException("Unterminated string")
        }

        private fun parseBoolean(): Boolean {
            if (text.startsWith("true", pos)) {
                pos += 4
                return true
            }
            if (text.startsWith("false", pos)) {
                pos += 5
                return false
            }
            throw ProtocolParseException("Invalid boolean token at offset $pos")
        }

        private fun parseNull(): Any? {
            if (text.startsWith("null", pos)) {
                pos += 4
                return null
            }
            throw ProtocolParseException("Invalid null token at offset $pos")
        }

        private fun parseNumber(): Number {
            val start = pos
            while (pos < text.length) {
                val c = text[pos]
                if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || c in '0'..'9') {
                    pos++
                } else {
                    break
                }
            }
            val numStr = text.substring(start, pos)
            return if (numStr.contains('.') || numStr.contains('e') || numStr.contains('E')) {
                numStr.toDoubleOrNull() ?: throw ProtocolParseException("Invalid float: $numStr")
            } else {
                numStr.toLongOrNull() ?: throw ProtocolParseException("Invalid integer: $numStr")
            }
        }

        private fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) {
                pos++
            }
        }
    }
}
