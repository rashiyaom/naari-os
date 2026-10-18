package com.naari.companion.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolV2CodecTest {

    @Test
    fun buildGetStatusCommand_createsValidJson() {
        val json = ProtocolV2Codec.buildGetStatusCommand(cmdId = 101)
        assertTrue(json.contains("\"v\":2"))
        assertTrue(json.contains("\"cmd\":101"))
        assertTrue(json.contains("\"op\":\"get_status\""))
    }

    @Test
    fun buildSetWallpaperCommand_validRange_createsJson() {
        val json = ProtocolV2Codec.buildSetWallpaperCommand(cmdId = 102, index = 3)
        assertTrue(json.contains("\"op\":\"set_wallpaper\""))
        assertTrue(json.contains("\"index\":3"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildSetWallpaperCommand_outOfBounds_throwsException() {
        ProtocolV2Codec.buildSetWallpaperCommand(cmdId = 103, index = 5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildSetWallpaperCommand_negative_throwsException() {
        ProtocolV2Codec.buildSetWallpaperCommand(cmdId = 104, index = -1)
    }

    @Test
    fun buildSetBrightnessCommand_validRange_createsJson() {
        val json = ProtocolV2Codec.buildSetBrightnessCommand(cmdId = 105, percent = 85)
        assertTrue(json.contains("\"op\":\"set_brightness\""))
        assertTrue(json.contains("\"percent\":85"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildSetBrightnessCommand_invalidRange_throwsException() {
        ProtocolV2Codec.buildSetBrightnessCommand(cmdId = 106, percent = 101)
    }

    @Test
    fun buildSetVolumeCommand_validRange_createsJson() {
        val json = ProtocolV2Codec.buildSetVolumeCommand(cmdId = 107, percent = 70)
        assertTrue(json.contains("\"op\":\"set_volume\""))
        assertTrue(json.contains("\"percent\":70"))
    }

    @Test
    fun buildSetFallEnabledCommand_createsJson() {
        val jsonTrue = ProtocolV2Codec.buildSetFallEnabledCommand(cmdId = 108, enabled = true)
        assertTrue(jsonTrue.contains("\"op\":\"set_fall_enabled\""))
        assertTrue(jsonTrue.contains("\"enabled\":true"))

        val jsonFalse = ProtocolV2Codec.buildSetFallEnabledCommand(cmdId = 109, enabled = false)
        assertTrue(jsonFalse.contains("\"enabled\":false"))
    }

    @Test
    fun buildSetModeCommand_createsJson() {
        val jsonSafe = ProtocolV2Codec.buildSetModeCommand(cmdId = 110, mode = WatchMode.SAFE)
        assertTrue(jsonSafe.contains("\"mode\":\"SAFE\""))

        val jsonWatch = ProtocolV2Codec.buildSetModeCommand(cmdId = 111, mode = WatchMode.WATCH)
        assertTrue(jsonWatch.contains("\"mode\":\"WATCH\""))
    }

    @Test
    fun buildFindWatchCommand_createsJson() {
        val json = ProtocolV2Codec.buildFindWatchCommand(cmdId = 112, durationSeconds = 20)
        assertTrue(json.contains("\"op\":\"find_watch\""))
        assertTrue(json.contains("\"duration_s\":20"))
    }

    @Test
    fun buildSyncTimeCommand_createsJson() {
        val json = ProtocolV2Codec.buildSyncTimeCommand(cmdId = 113, epochSeconds = 1791885600L, tzOffsetMinutes = 330)
        assertTrue(json.contains("\"op\":\"sync_time\""))
        assertTrue(json.contains("\"epoch_s\":1791885600"))
        assertTrue(json.contains("\"tz_offset_min\":330"))
    }

    @Test
    fun parseControlResponse_applied_parsesCorrectly() {
        val json = """
            {
              "v": 2,
              "cmd": 102,
              "status": "applied",
              "err": null,
              "applied": {
                "wallpaper": 3
              }
            }
        """.trimIndent()

        val resp = ProtocolV2Codec.parseControlResponseJson(json)
        assertEquals(2, resp.version)
        assertEquals(102, resp.commandId)
        assertEquals(ControlStatus.Applied, resp.status)
        assertNull(resp.error)
        assertEquals(3, (resp.appliedData["wallpaper"] as Number).toInt())
    }

    @Test
    fun parseControlResponse_rejected_parsesCorrectly() {
        val json = """
            {
              "v": 2,
              "cmd": 103,
              "status": "rejected",
              "err": "INVALID_RANGE"
            }
        """.trimIndent()

        val resp = ProtocolV2Codec.parseControlResponseJson(json)
        assertEquals(ControlStatus.Rejected, resp.status)
        assertEquals("INVALID_RANGE", resp.error)
    }

    @Test
    fun parseStatusSnapshot_populatesAllFields() {
        val appliedMap = mapOf(
            "boot_id" to 4928120L,
            "seq" to 142L,
            "battery" to 88,
            "charging" to true,
            "wallpaper" to 2,
            "brightness" to 90,
            "volume" to 60,
            "fall_enabled" to true,
            "mode" to "SAFE",
            "capabilities" to listOf("wallpaper", "brightness", "fall_detection")
        )

        val snapshot = ProtocolV2Codec.parseStatusSnapshot(appliedMap)
        assertEquals(4928120L, snapshot.bootId)
        assertEquals(142L, snapshot.sequence)
        assertEquals(88, snapshot.batteryPercent)
        assertTrue(snapshot.isCharging)
        assertEquals(2, snapshot.activeWallpaper)
        assertEquals(90, snapshot.brightness)
        assertEquals(60, snapshot.volume)
        assertEquals(true, snapshot.fallDetectionEnabled)
        assertEquals(WatchMode.SAFE, snapshot.mode)

        assertTrue(snapshot.capabilities.wallpaper)
        assertTrue(snapshot.capabilities.brightness)
        assertTrue(snapshot.capabilities.fallDetection)
        assertFalse(snapshot.capabilities.volume) // Not in capabilities list
    }
}
