package com.naari.companion.actions

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import java.util.ArrayList

/**
 * Transport contract for dividing messages and sending SMS with low-level callback hooks.
 */
interface SmsTransport {
    fun divideMessage(message: String): List<String>

    fun sendSmsWithCallbacks(
        requestId: Long,
        destinationAddress: String,
        parts: List<String>,
        onSentPart: (partIndex: Int, resultCode: Int) -> Unit,
        onDeliveredPart: (partIndex: Int, resultCode: Int) -> Unit
    ): AutoCloseable
}

/**
 * Concrete Android implementation resolving default SMS subscription ID on Dual-SIM devices,
 * creating immutable PendingIntents, registering dynamic BroadcastReceivers, and dispatching
 * via platform SmsManager.
 */
class AndroidSmsTransport(
    private val context: Context
) : SmsTransport {

    override fun divideMessage(message: String): List<String> {
        val manager = resolveSmsManager()
        return try {
            manager.divideMessage(message)
        } catch (_: Exception) {
            fallbackDivideMessage(message)
        }
    }

    @SuppressLint("MissingPermission")
    override fun sendSmsWithCallbacks(
        requestId: Long,
        destinationAddress: String,
        parts: List<String>,
        onSentPart: (partIndex: Int, resultCode: Int) -> Unit,
        onDeliveredPart: (partIndex: Int, resultCode: Int) -> Unit
    ): AutoCloseable {
        val totalParts = parts.size.coerceAtLeast(1)
        val sentActionPrefix = "com.naari.companion.SMS_SENT_${requestId}_"
        val deliveredActionPrefix = "com.naari.companion.SMS_DELIVERED_${requestId}_"

        val filter = IntentFilter()
        val sentIntents = mutableListOf<PendingIntent>()
        val deliveryIntents = mutableListOf<PendingIntent>()

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }

        for (i in 0 until totalParts) {
            val sentAction = "$sentActionPrefix$i"
            val deliveredAction = "$deliveredActionPrefix$i"

            filter.addAction(sentAction)
            filter.addAction(deliveredAction)

            val sentIntent = PendingIntent.getBroadcast(
                context,
                (requestId * 100 + i).toInt(),
                Intent(sentAction).setPackage(context.packageName),
                pendingIntentFlags
            )
            val deliveredIntent = PendingIntent.getBroadcast(
                context,
                (requestId * 100 + totalParts + i).toInt(),
                Intent(deliveredAction).setPackage(context.packageName),
                pendingIntentFlags
            )

            sentIntents.add(sentIntent)
            deliveryIntents.add(deliveredIntent)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                if (action.startsWith(sentActionPrefix)) {
                    val partIndex = action.removePrefix(sentActionPrefix).toIntOrNull() ?: 0
                    onSentPart(partIndex, resultCode)
                } else if (action.startsWith(deliveredActionPrefix)) {
                    val partIndex = action.removePrefix(deliveredActionPrefix).toIntOrNull() ?: 0
                    onDeliveredPart(partIndex, resultCode)
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        try {
            val manager = resolveSmsManager()
            manager.sendMultipartTextMessage(
                destinationAddress,
                null,
                ArrayList(parts),
                ArrayList(sentIntents),
                ArrayList(deliveryIntents)
            )
        } catch (e: Exception) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
            throw e
        }

        return AutoCloseable {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    private fun resolveSmsManager(): SmsManager {
        val subId = try {
            SubscriptionManager.getDefaultSmsSubscriptionId()
        } catch (_: Exception) {
            -1
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = context.getSystemService(SmsManager::class.java)
            if (subId > 0 && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                base.createForSubscriptionId(subId)
            } else {
                base
            }
        } else {
            @Suppress("DEPRECATION")
            if (subId > 0 && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                SmsManager.getSmsManagerForSubscriptionId(subId)
            } else {
                SmsManager.getDefault()
            }
        }
    }

    companion object {
        fun fallbackDivideMessage(message: String): List<String> {
            if (message.isEmpty()) return listOf("")
            val hasUnicode = message.any { it.code > 127 }
            val maxPartLength = if (hasUnicode) 67 else 153
            if (message.length <= (if (hasUnicode) 70 else 160)) {
                return listOf(message)
            }
            return message.chunked(maxPartLength)
        }
    }
}
