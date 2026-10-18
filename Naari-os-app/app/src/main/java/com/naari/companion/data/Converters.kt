package com.naari.companion.data

import androidx.room.TypeConverter
import com.naari.companion.ledger.RequestLedgerStatus
import com.naari.companion.protocol.WatchRequestType

/**
 * Room TypeConverters for custom enum and value mappings.
 */
class Converters {

    @TypeConverter
    fun fromLedgerStatus(status: RequestLedgerStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toLedgerStatus(value: String?): RequestLedgerStatus? {
        if (value == null) return null
        return try {
            RequestLedgerStatus.valueOf(value)
        } catch (_: IllegalArgumentException) {
            RequestLedgerStatus.UnknownOutcome
        }
    }

    @TypeConverter
    fun fromWatchRequestType(type: WatchRequestType?): String? {
        return type?.wireName
    }

    @TypeConverter
    fun toWatchRequestType(value: String?): WatchRequestType? {
        if (value == null) return null
        return WatchRequestType.fromWire(value) ?: WatchRequestType.SMS
    }
}
