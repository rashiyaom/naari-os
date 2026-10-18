package com.naari.companion

import com.naari.companion.protocol.InvalidCoordinatesException
import com.naari.companion.protocol.InvalidPhoneNumberException
import com.naari.companion.protocol.OversizedPayloadException
import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.PhoneReplyStatus
import com.naari.companion.protocol.ProtocolConstants
import com.naari.companion.protocol.ProtocolException
import com.naari.companion.protocol.ProtocolV1Codec
import com.naari.companion.protocol.UnknownOperationException
import com.naari.companion.protocol.WatchRequestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class ProtocolV1CodecTest {

    @Test
    fun notificationRequestId_littleEndianParsingAndSerialization() {
        // [0x7B, 0x00, 0x00, 0x00] == 123 in little-endian uint32
        val bytes123 = byteArrayOf(0x7B.toByte(), 0x00, 0x00, 0x00)
        assertEquals(123L, ProtocolV1Codec.parseNotificationRequestId(bytes123))

        // Large 32-bit unsigned: 0xFFFFFFFE == 4294967294L
        val bytesMax = byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val parsedMax = ProtocolV1Codec.parseNotificationRequestId(bytesMax)
        assertEquals(4294967294L, parsedMax)

        // Roundtrip serialization
        val serialized = ProtocolV1Codec.serializeNotificationRequestId(parsedMax)
        assertEquals(bytesMax.toList(), serialized.toList())

        // Rejects invalid byte lengths
        try {
            ProtocolV1Codec.parseNotificationRequestId(byteArrayOf(0x01, 0x02))
            org.junit.Assert.fail("Expected ProtocolException on short byte array")
        } catch (_: ProtocolException) {
            // Success
        }
    }

    @Test
    fun helloMessage_roundtripAndValidation() {
        val json = ProtocolV1Codec.buildHelloJson(sms = true, calls = true, location = false)
        val hello = ProtocolV1Codec.parseHelloJson(json)
        assertEquals(1, hello.version)
        assertEquals("hello", hello.op)
        assertTrue(hello.sms)
        assertTrue(hello.calls)
        assertFalse(hello.location)

        // Rejects invalid version
        val invalidVersion = """{"v":2,"op":"hello","sms":true,"calls":true,"location":true}"""
        try {
            ProtocolV1Codec.parseHelloJson(invalidVersion)
            org.junit.Assert.fail("Expected exception for version 2")
        } catch (_: ProtocolException) {
            // Success
        }
    }

    @Test
    fun profileMessage_validPhoneAndUnicodeNames() {
        val json = ProtocolV1Codec.buildProfileJson(
            name0 = "Guardian A",
            phone0 = "+15555550101",
            name1 = "Guardian B",
            phone1 = "+919876543210"
        )
        val profile = ProtocolV1Codec.parseProfileJson(json)
        assertEquals(1, profile.version)
        assertEquals("profile", profile.op)
        assertEquals("Guardian A", profile.name0)
        assertEquals("+15555550101", profile.phone0)
        assertEquals("Guardian B", profile.name1)
        assertEquals("+919876543210", profile.phone1)

        // Clearing a slot with empty string is explicitly valid per PRD
        val clearJson = ProtocolV1Codec.buildProfileJson(
            name0 = "Guardian A",
            phone0 = "+15555550101",
            name1 = "",
            phone1 = ""
        )
        val clearProfile = ProtocolV1Codec.parseProfileJson(clearJson)
        assertEquals("", clearProfile.phone1)
    }

    @Test(expected = InvalidPhoneNumberException::class)
    fun profileMessage_rejectsInvalidPhoneNumberWithLetters() {
        ProtocolV1Codec.buildProfileJson(
            name0 = "Guardian",
            phone0 = "1234abcd",
            name1 = "",
            phone1 = ""
        )
    }

    @Test(expected = InvalidPhoneNumberException::class)
    fun profileMessage_rejectsTooShortPhoneNumber() {
        ProtocolV1Codec.buildProfileJson(
            name0 = "Guardian",
            phone0 = "+1",
            name1 = "",
            phone1 = ""
        )
    }

    @Test(expected = InvalidPhoneNumberException::class)
    fun profileMessage_rejectsTooLongPhoneNumber() {
        // 25 digits (exceeds max 20)
        ProtocolV1Codec.buildProfileJson(
            name0 = "Guardian",
            phone0 = "+1234567890123456789012345",
            name1 = "",
            phone1 = ""
        )
    }

    @Test
    fun truncateNameUtf8_preservesMultibyteBoundaries() {
        // Devanagari 3-byte characters: 'ना' (न = 3 bytes, ा = 3 bytes)
        val hindiName = "नारी रक्षा साथी" // Multibyte characters
        val truncated = ProtocolV1Codec.truncateNameUtf8(hindiName, maxBytes = 15)

        val byteSize = truncated.toByteArray(StandardCharsets.UTF_8).size
        assertTrue("Truncated byte size must be <= 15, was $byteSize", byteSize <= 15)
        // Verify no broken characters by re-encoding and decoding
        val roundtrip = String(truncated.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
        assertEquals(truncated, roundtrip)
        assertFalse("String must not contain unicode replacement character", truncated.contains("\uFFFD"))
    }

    @Test
    fun watchRequest_parsingValidAndEmpty() {
        // Empty read / {} indicates no pending request
        assertNull(ProtocolV1Codec.parseWatchRequest("{}"))
        assertNull(ProtocolV1Codec.parseWatchRequest(""))
        assertNull(ProtocolV1Codec.parseWatchRequest("   "))

        // Location SMS request
        val json = """{"v":1,"id":123,"type":"location_sms","phone":"+15555550101","message":"Safety alert"}"""
        val req = ProtocolV1Codec.parseWatchRequest(json)
        assertNotNull(req)
        assertEquals(1, req!!.version)
        assertEquals(123L, req.id)
        assertEquals(WatchRequestType.LOCATION_SMS, req.type)
        assertEquals("+15555550101", req.phone)
        assertEquals("Safety alert", req.message)

        // Call request
        val callJson = """{"v":1,"id":124,"type":"call","phone":"+15555550101"}"""
        val callReq = ProtocolV1Codec.parseWatchRequest(callJson)
        assertNotNull(callReq)
        assertEquals(WatchRequestType.CALL, callReq!!.type)

        // Location only request
        val locJson = """{"v":1,"id":125,"type":"location"}"""
        val locReq = ProtocolV1Codec.parseWatchRequest(locJson)
        assertNotNull(locReq)
        assertEquals(WatchRequestType.LOCATION, locReq!!.type)
    }

    @Test(expected = UnknownOperationException::class)
    fun watchRequest_rejectsUnknownType() {
        val unknownJson = """{"v":1,"id":126,"type":"voice_memo"}"""
        ProtocolV1Codec.parseWatchRequest(unknownJson)
    }

    @Test(expected = OversizedPayloadException::class)
    fun watchRequest_rejectsMessageOver256Bytes() {
        val longMessage = "A".repeat(257)
        val json = """{"v":1,"id":127,"type":"sms","phone":"+15555550101","message":"$longMessage"}"""
        ProtocolV1Codec.parseWatchRequest(json)
    }

    @Test
    fun phoneReply_roundtripAndCoordinatesValidation() {
        val reply = PhoneReply(
            version = 1,
            id = 123L,
            status = PhoneReplyStatus.SENT,
            detail = "SMS sent",
            lat = 19.0760,
            lon = 72.8777
        )
        val json = ProtocolV1Codec.buildReplyJson(reply)
        val parsed = ProtocolV1Codec.parseReplyJson(json)

        assertEquals(reply.id, parsed.id)
        assertEquals(reply.status, parsed.status)
        assertEquals(reply.detail, parsed.detail)
        assertEquals(19.0760, parsed.lat!!, 0.0001)
        assertEquals(72.8777, parsed.lon!!, 0.0001)
    }

    @Test(expected = InvalidCoordinatesException::class)
    fun phoneReply_rejectsLatitudeOutOfRange() {
        val badReply = PhoneReply(
            id = 123L,
            status = PhoneReplyStatus.LOCATED,
            lat = 91.5,
            lon = 72.0
        )
        ProtocolV1Codec.buildReplyJson(badReply)
    }

    @Test(expected = InvalidCoordinatesException::class)
    fun phoneReply_rejectsLongitudeOutOfRange() {
        val badReply = PhoneReply(
            id = 123L,
            status = PhoneReplyStatus.LOCATED,
            lat = 19.0,
            lon = -185.0
        )
        ProtocolV1Codec.buildReplyJson(badReply)
    }

    @Test(expected = InvalidCoordinatesException::class)
    fun phoneReply_rejectsNaNCoordinates() {
        val badReply = PhoneReply(
            id = 123L,
            status = PhoneReplyStatus.LOCATED,
            lat = Double.NaN,
            lon = 72.0
        )
        ProtocolV1Codec.buildReplyJson(badReply)
    }

    @Test(expected = OversizedPayloadException::class)
    fun parseWatchRequest_rejectsPayloadExceeding480Bytes() {
        val massiveJson = "{\"v\":1,\"id\":1,\"type\":\"sms\",\"message\":\"" + "X".repeat(500) + "\"}"
        ProtocolV1Codec.parseWatchRequest(massiveJson)
    }

    @Test
    fun largestUtf8Payload_within480Bytes_succeeds() {
        // Construct a request that is exactly 480 UTF-8 bytes with maximum allowable message (256 bytes)
        val message256 = "A".repeat(ProtocolConstants.MAX_MESSAGE_BYTES)
        val base = """{"v":1,"id":999,"type":"sms","phone":"+15555550101","message":"$message256"}"""
        val currentBytes = base.toByteArray(StandardCharsets.UTF_8).size
        val padding = " ".repeat(ProtocolConstants.MAX_JSON_BYTES - currentBytes)
        val exact480Json = base.dropLast(1) + padding + "}"

        assertEquals(480, exact480Json.toByteArray(StandardCharsets.UTF_8).size)
        val parsed = ProtocolV1Codec.parseWatchRequest(exact480Json)
        assertNotNull(parsed)
        assertEquals(999L, parsed!!.id)
        assertEquals(256, parsed.message?.length)
    }
}
