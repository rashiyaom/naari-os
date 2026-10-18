package com.naari.companion.protocol

import java.util.UUID

/**
 * Authoritative BLE and JSON Protocol constants for Naari OS Companion.
 * Ref: PRD Section 5 (BLE v1 Contract) & Section 6 (Additive Extensions).
 */
object ProtocolConstants {
    // Protocol v1 BLE Service & Characteristics
    val SERVICE_UUID_V1: UUID = UUID.fromString("d973f2e0-b19e-11ee-a506-0242ac120002")
    val REQUEST_CHAR_UUID_V1: UUID = UUID.fromString("d973f2e1-b19e-11ee-a506-0242ac120002")
    val REPLY_CHAR_UUID_V1: UUID = UUID.fromString("d973f2e2-b19e-11ee-a506-0242ac120002")
    val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // BLE MTU bounds
    const val DEFAULT_MTU = 23
    const val TARGET_MTU = 512

    // Advertised device name filter
    const val DEVICE_NAME_PREFIX = "Naari Watch"

    // Buffer and payload limits
    const val MAX_JSON_BYTES = 480
    const val MAX_MESSAGE_BYTES = 256
    const val MAX_NAME_BYTES = 32

    // Phone number string constraints
    const val MIN_PHONE_LENGTH = 3
    const val MAX_PHONE_LENGTH = 20

    // Coordinate validation bounds
    const val MIN_LATITUDE = -90.0
    const val MAX_LATITUDE = 90.0
    const val MIN_LONGITUDE = -180.0
    const val MAX_LONGITUDE = 180.0

    // Protocol v2 BLE Service & Characteristics (Control, Telemetry & Data Transfer)
    val SERVICE_UUID_V2: UUID = UUID.fromString("d973f2f0-b19e-11ee-a506-0242ac120002")
    val CONTROL_CMD_CHAR_UUID_V2: UUID = UUID.fromString("d973f2f1-b19e-11ee-a506-0242ac120002")
    val EVENTS_CHAR_UUID_V2: UUID = UUID.fromString("d973f2f2-b19e-11ee-a506-0242ac120002")
    val DATA_TRANSFER_CHAR_UUID_V2: UUID = UUID.fromString("d973f2f3-b19e-11ee-a506-0242ac120002")
    const val MAX_JSON_BYTES_V2 = 512

    // Timing constants
    const val HELLO_INTERVAL_MS = 15_000L
    const val READINESS_EXPIRATION_MS = 45_000L
    const val CALL_TIMEOUT_MS = 30_000L
    const val ACTION_TIMEOUT_MS = 90_000L
    const val LOCATION_CACHE_EXPIRATION_MS = 120_000L
    const val CONTROL_COMMAND_TIMEOUT_MS = 5_000L
}
