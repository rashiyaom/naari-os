package com.naari.companion.protocol

/**
 * Protocol error categories for strict conformance testing and runtime debugging.
 */
enum class ProtocolErrorCode {
    OVERSIZED_PAYLOAD,
    INVALID_VERSION,
    UNKNOWN_OPERATION,
    INVALID_PHONE_NUMBER,
    INVALID_COORDINATES,
    TRUNCATED_MULTIBYTE,
    PARSE_ERROR
}

open class ProtocolException(
    message: String,
    val errorCode: ProtocolErrorCode
) : Exception(message)

class OversizedPayloadException(
    val byteCount: Int,
    val maxAllowed: Int
) : ProtocolException(
    "Payload size $byteCount bytes exceeds limit of $maxAllowed bytes",
    ProtocolErrorCode.OVERSIZED_PAYLOAD
)

class InvalidPhoneNumberException(
    val rawNumber: String
) : ProtocolException(
    "Invalid phone number format: '$rawNumber'. Expected optional '+' with 3-20 digits",
    ProtocolErrorCode.INVALID_PHONE_NUMBER
)

class InvalidCoordinatesException(
    val lat: Double?,
    val lon: Double?
) : ProtocolException(
    "Invalid coordinates: lat=$lat, lon=$lon. Latitude must be in [-90, 90] and longitude in [-180, 180]",
    ProtocolErrorCode.INVALID_COORDINATES
)

class UnknownOperationException(
    val operation: String
) : ProtocolException(
    "Unknown protocol operation or type: '$operation'",
    ProtocolErrorCode.UNKNOWN_OPERATION
)

class ProtocolParseException(
    message: String,
    cause: Throwable? = null
) : ProtocolException(
    message,
    ProtocolErrorCode.PARSE_ERROR
)

class ProtocolPayloadTooLargeException(
    message: String
) : ProtocolException(
    message,
    ProtocolErrorCode.OVERSIZED_PAYLOAD
)
