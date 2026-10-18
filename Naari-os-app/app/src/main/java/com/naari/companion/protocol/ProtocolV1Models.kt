package com.naari.companion.protocol

/**
 * Protocol v1 Hello message transmitted by phone to watch.
 * Ref: PRD Section 5: {"v":1,"op":"hello","sms":true,"calls":true,"location":true}
 */
data class PhoneHelloMessage(
    val version: Int = 1,
    val op: String = "hello",
    val sms: Boolean,
    val calls: Boolean,
    val location: Boolean
)

/**
 * Protocol v1 Guardian profile synchronization message.
 * Ref: PRD Section 5: {"v":1,"op":"profile","name0":"...","phone0":"...","name1":"...","phone1":"..."}
 */
data class PhoneProfileMessage(
    val version: Int = 1,
    val op: String = "profile",
    val name0: String = "",
    val phone0: String = "",
    val name1: String = "",
    val phone1: String = ""
)

/**
 * Valid request types dispatched by the watch.
 */
enum class WatchRequestType(val wireName: String) {
    SMS("sms"),
    LOCATION_SMS("location_sms"),
    CALL("call"),
    LOCATION("location");

    companion object {
        fun fromWire(value: String): WatchRequestType? {
            return entries.find { it.wireName.equals(value, ignoreCase = true) }
        }
    }
}

/**
 * Inbound watch request parsed from long-read of characteristic e1.
 * Ref: PRD Section 5: {"v":1,"id":123,"type":"location_sms","phone":"...","message":"..."}
 */
data class WatchRequest(
    val version: Int = 1,
    val id: Long,
    val type: WatchRequestType,
    val phone: String? = null,
    val message: String? = null
)

/**
 * Allowed reply status values sent on characteristic e2.
 * Ref: PRD Section 5.
 */
enum class PhoneReplyStatus(val wireName: String) {
    RECEIVED("received"),
    SENT("sent"),
    DELIVERED("delivered"),
    LOCATED("located"),
    CALLING("calling"),
    NEEDS_PHONE("needs_phone"),
    FAILED("failed");

    companion object {
        fun fromWire(value: String): PhoneReplyStatus? {
            return entries.find { it.wireName.equals(value, ignoreCase = true) }
        }
    }
}

/**
 * Outbound phone reply message.
 * Ref: PRD Section 5: {"v":1,"id":123,"status":"received","detail":"Accepted by phone"}
 */
data class PhoneReply(
    val version: Int = 1,
    val id: Long,
    val status: PhoneReplyStatus,
    val detail: String? = null,
    val lat: Double? = null,
    val lon: Double? = null
)
