package com.naari.companion.actions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/**
 * Interface contract abstracting telephony dialer and direct call dispatching.
 */
interface CallLauncher {
    fun hasCallPermission(): Boolean
    fun placeDirectCall(phoneNumber: String): Boolean
    fun openDialer(phoneNumber: String): Boolean
}

/**
 * Concrete Android implementation using TelecomManager / ACTION_CALL and ACTION_DIAL.
 */
class AndroidCallLauncher(
    private val context: Context
) : CallLauncher {

    override fun hasCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override fun placeDirectCall(phoneNumber: String): Boolean {
        val uri = Uri.fromParts("tel", phoneNumber, null)
        return try {
            val telecomManager = context.getSystemService(TelecomManager::class.java)
            if (telecomManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val extras = Bundle()
                telecomManager.placeCall(uri, extras)
                true
            } else {
                val intent = Intent(Intent.ACTION_CALL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun openDialer(phoneNumber: String): Boolean {
        return try {
            val uri = Uri.fromParts("tel", phoneNumber, null)
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
