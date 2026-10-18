package com.naari.companion.recordings

import com.naari.companion.data.RecordingDao
import com.naari.companion.data.RecordingEntity
import com.naari.companion.data.RecordingSyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class RecordingTransferTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeRecordingDao : RecordingDao {
        val records = mutableListOf<RecordingEntity>()
        val flow = MutableStateFlow<List<RecordingEntity>>(emptyList())
        private var idCounter = 1L

        override suspend fun insert(recording: RecordingEntity): Long {
            val id = if (recording.id == 0L) idCounter++ else recording.id
            val saved = recording.copy(id = id)
            records.add(saved)
            flow.value = records.toList()
            return id
        }

        override suspend fun update(recording: RecordingEntity): Int {
            val idx = records.indexOfFirst { it.id == recording.id }
            if (idx >= 0) {
                records[idx] = recording
                flow.value = records.toList()
                return 1
            }
            return 0
        }

        override fun observeAll(): Flow<List<RecordingEntity>> = flow

        override suspend fun getById(id: Long): RecordingEntity? = records.find { it.id == id }

        override suspend fun getByRecordingId(recordingId: String): RecordingEntity? =
            records.find { it.recordingId == recordingId }

        override suspend fun delete(id: Long): Int {
            val removed = records.removeAll { it.id == id }
            if (removed) flow.value = records.toList()
            return if (removed) 1 else 0
        }

        override suspend fun updateTitle(id: Long, title: String): Int {
            val idx = records.indexOfFirst { it.id == id }
            if (idx >= 0) {
                records[idx] = records[idx].copy(title = title)
                flow.value = records.toList()
                return 1
            }
            return 0
        }

        override suspend fun updateSyncProgress(
            id: Long,
            transferredBytes: Long,
            syncState: RecordingSyncState,
            failureReason: String?
        ): Int {
            val idx = records.indexOfFirst { it.id == id }
            if (idx >= 0) {
                records[idx] = records[idx].copy(
                    transferredBytes = transferredBytes,
                    syncState = syncState,
                    failureReason = failureReason
                )
                flow.value = records.toList()
                return 1
            }
            return 0
        }

        override suspend fun markVerified(
            id: Long,
            localFilePath: String,
            totalBytes: Long,
            syncState: RecordingSyncState
        ): Int {
            val idx = records.indexOfFirst { it.id == id }
            if (idx >= 0) {
                records[idx] = records[idx].copy(
                    localFilePath = localFilePath,
                    transferredBytes = totalBytes,
                    syncState = syncState,
                    failureReason = null
                )
                flow.value = records.toList()
                return 1
            }
            return 0
        }
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun buildChunkPacket(sequence: Long, byteOffset: Long, payload: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(10 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(sequence.toInt())
        buf.putInt(byteOffset.toInt())
        buf.putShort(payload.size.toShort())
        buf.put(payload)
        return buf.array()
    }

    @Test
    fun wavHeader_containsValid44ByteRiffPcm16Format() {
        val pcmDataLength = 32000 // 1 second of 16 kHz 16-bit mono PCM
        val header = WavWriter.createWavHeader(pcmDataLength)

        assertEquals(44, header.size)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF descriptor
        val riff = ByteArray(4).also { buf.get(it) }
        assertEquals("RIFF", String(riff, Charsets.US_ASCII))
        assertEquals(pcmDataLength + 36, buf.getInt())
        val wave = ByteArray(4).also { buf.get(it) }
        assertEquals("WAVE", String(wave, Charsets.US_ASCII))

        // "fmt " chunk
        val fmt = ByteArray(4).also { buf.get(it) }
        assertEquals("fmt ", String(fmt, Charsets.US_ASCII))
        assertEquals(16, buf.getInt()) // Subchunk1Size
        assertEquals(1, buf.getShort().toInt()) // AudioFormat = 1 (PCM)
        assertEquals(1, buf.getShort().toInt()) // Channels = 1 (mono)
        assertEquals(16000, buf.getInt()) // Sample rate
        assertEquals(32000, buf.getInt()) // Byte rate (16000 * 1 * 2)
        assertEquals(2, buf.getShort().toInt()) // Block align
        assertEquals(16, buf.getShort().toInt()) // Bits per sample

        // "data" chunk
        val data = ByteArray(4).also { buf.get(it) }
        assertEquals("data", String(data, Charsets.US_ASCII))
        assertEquals(pcmDataLength, buf.getInt())
    }

    @Test
    fun wavWriter_writesCompletePlayableWavFile() {
        val dir = tempFolder.newFolder("wav_test")
        val destFile = File(dir, "memo_001.wav")
        val rawPcm = ByteArray(1024) { (it % 128).toByte() }

        WavWriter.writeWavFile(destFile, rawPcm)

        assertTrue(destFile.exists())
        assertEquals(44 + rawPcm.size.toLong(), destFile.length())

        val readBytes = destFile.readBytes()
        val dataPayload = readBytes.copyOfRange(44, readBytes.size)
        assertArrayEquals(rawPcm, dataPayload)
    }

    @Test
    fun parseChunk_correctlyParsesValid10ByteHeaderAndPayload() {
        val payload = "AudioMemoChunkTestData".toByteArray(Charsets.UTF_8)
        val packet = buildChunkPacket(sequence = 42L, byteOffset = 1024L, payload = payload)

        val chunk = RecordingTransferClient.parseChunk(packet)

        assertEquals(42L, chunk.sequence)
        assertEquals(1024L, chunk.byteOffset)
        assertEquals(payload.size, chunk.length)
        assertArrayEquals(payload, chunk.payload)
    }

    @Test
    fun parseChunk_rejectsTruncatedPackets() {
        // Less than 10 bytes
        try {
            RecordingTransferClient.parseChunk(ByteArray(8))
            fail("Expected IllegalArgumentException for small packet")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("too small") == true)
        }

        // Header claims 20 bytes payload but only 5 provided
        val truncated = buildChunkPacket(sequence = 1L, byteOffset = 0L, payload = ByteArray(20))
            .copyOfRange(0, 15) // 10 header + 5 payload
        try {
            RecordingTransferClient.parseChunk(truncated)
            fail("Expected IllegalArgumentException for truncated payload")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("truncated") == true)
        }
    }

    @Test
    fun successfulTransfer_verifiesSha256_andCommitsToPlayableWav() {
        val dir = tempFolder.newFolder("recordings_success")
        val storageManager = RecordingsStorageManager(dir)
        val transferClient = RecordingTransferClient(storageManager)

        val chunk1 = ByteArray(100) { 1 }
        val chunk2 = ByteArray(100) { 2 }
        val fullData = chunk1 + chunk2
        val expectedSha = sha256Hex(fullData)

        val session = transferClient.startTransfer(
            recordingId = "rec_001",
            totalBytes = fullData.size.toLong(),
            expectedSha256Hex = expectedSha
        )

        assertFalse(session.isComplete)

        session.appendChunk(AudioChunk(0, 0, chunk1.size, chunk1))
        assertEquals(100L, session.currentTransferredBytes)
        assertFalse(session.isComplete)

        session.appendChunk(AudioChunk(1, 100, chunk2.size, chunk2))
        assertEquals(200L, session.currentTransferredBytes)
        assertTrue(session.isComplete)

        val finalWav = session.completeAndCommit()

        assertTrue(finalWav.exists())
        assertEquals(44 + 200L, finalWav.length())
        assertFalse(storageManager.getTempFile("rec_001").exists())
    }

    @Test
    fun duplicateChunks_areSafelyDeduplicatedWithoutCorruptingStream() {
        val dir = tempFolder.newFolder("recordings_dedup")
        val storageManager = RecordingsStorageManager(dir)
        val transferClient = RecordingTransferClient(storageManager)

        val chunk1 = ByteArray(50) { 10 }
        val chunk2 = ByteArray(50) { 20 }
        val fullData = chunk1 + chunk2
        val expectedSha = sha256Hex(fullData)

        val session = transferClient.startTransfer(
            recordingId = "rec_dedup",
            totalBytes = fullData.size.toLong(),
            expectedSha256Hex = expectedSha
        )

        // Append chunk 1
        session.appendChunk(AudioChunk(0, 0, chunk1.size, chunk1))
        // Append duplicate chunk 1 (same sequence)
        session.appendChunk(AudioChunk(0, 0, chunk1.size, chunk1))
        // Append chunk 2
        session.appendChunk(AudioChunk(1, 50, chunk2.size, chunk2))
        // Append duplicate chunk 2
        session.appendChunk(AudioChunk(1, 50, chunk2.size, chunk2))

        assertEquals(100L, session.currentTransferredBytes)
        val finalWav = session.completeAndCommit()

        assertTrue(finalWav.exists())
        assertEquals(44 + 100L, finalWav.length())
    }

    @Test
    fun sha256Mismatch_rejectsCorruptedFileAndCleansUpTemp() {
        val dir = tempFolder.newFolder("recordings_mismatch")
        val storageManager = RecordingsStorageManager(dir)
        val transferClient = RecordingTransferClient(storageManager)

        val actualData = ByteArray(100) { 7 }
        val corruptedExpectedSha = "0000000000000000000000000000000000000000000000000000000000000000"

        val session = transferClient.startTransfer(
            recordingId = "rec_corrupted",
            totalBytes = actualData.size.toLong(),
            expectedSha256Hex = corruptedExpectedSha
        )

        session.appendChunk(AudioChunk(0, 0, actualData.size, actualData))

        try {
            session.completeAndCommit()
            fail("Expected Sha256MismatchException")
        } catch (e: Sha256MismatchException) {
            assertTrue(e.message?.contains("SHA-256 verification mismatch") == true)
        }

        // Verify corrupted temp file was pruned
        assertFalse(storageManager.getTempFile("rec_corrupted").exists())
        assertFalse(storageManager.getFinalWavFile("rec_corrupted").exists())
    }

    @Test
    fun oversizedManifest_isRejectedBeforeAllocatingStorage() {
        val dir = tempFolder.newFolder("recordings_low_storage")
        val storageManager = RecordingsStorageManager(dir)
        val transferClient = RecordingTransferClient(storageManager)

        // Usable space + 10 MB will always exceed available storage
        val impossibleBytes = storageManager.getAvailableStorageBytes() + 10 * 1024 * 1024L

        try {
            transferClient.startTransfer(
                recordingId = "rec_huge",
                totalBytes = impossibleBytes,
                expectedSha256Hex = "abc"
            )
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("metadata") == true)
        }
    }

    @Test
    fun repository_executeTransfer_streamsChunksAndMarksVerified() = runTest {
        val dir = tempFolder.newFolder("repo_test")
        val dao = FakeRecordingDao()
        val storageManager = RecordingsStorageManager(dir)
        val repo = DefaultRecordingsRepository(dao, storageManager)

        val chunk1 = ByteArray(64) { 1 }
        val chunk2 = ByteArray(64) { 2 }
        val fullData = chunk1 + chunk2
        val expectedSha = sha256Hex(fullData)

        // Seed memo record
        val id = repo.addCapturedRecording(
            recordingId = "watch_memo_99",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            timestampMs = 1700000000000L,
            durationMs = 8000L,
            totalBytes = fullData.size.toLong(),
            sha256Hex = expectedSha,
            title = "Test Memo"
        )
        assertEquals(1L, id)

        // Execute transfer with fake chunk source
        val result = repo.executeTransfer("watch_memo_99") { startOffset, onChunkReceived ->
            assertEquals(0L, startOffset)
            onChunkReceived(AudioChunk(0, 0, chunk1.size, chunk1))
            onChunkReceived(AudioChunk(1, 64, chunk2.size, chunk2))
        }

        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertTrue(file.exists())
        assertEquals(44 + 128L, file.length())

        val record = dao.getById(id)
        assertNotNull(record)
        assertEquals(RecordingSyncState.VerifiedOnPhone, record!!.syncState)
        assertEquals(file.absolutePath, record.localFilePath)
    }

    @Test
    fun repository_executeTransfer_onHashMismatch_marksFailedInDao() = runTest {
        val dir = tempFolder.newFolder("repo_mismatch_test")
        val dao = FakeRecordingDao()
        val storageManager = RecordingsStorageManager(dir)
        val repo = DefaultRecordingsRepository(dao, storageManager)

        val payload = ByteArray(64) { 9 }
        val wrongSha = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

        val id = repo.addCapturedRecording(
            recordingId = "bad_memo",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            timestampMs = 1700000000000L,
            durationMs = 4000L,
            totalBytes = payload.size.toLong(),
            sha256Hex = wrongSha
        )

        val result = repo.executeTransfer("bad_memo") { _, onChunkReceived ->
            onChunkReceived(AudioChunk(0, 0, payload.size, payload))
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Sha256MismatchException)

        val record = dao.getById(id)
        assertNotNull(record)
        assertEquals(RecordingSyncState.Failed, record!!.syncState)
        assertTrue(record.failureReason?.contains("SHA-256 verification mismatch") == true)
    }

    @Test
    fun localOnlyDeletion_deletesLocalWavAndCatalog_preservesWatchOriginal() = runTest {
        val dir = tempFolder.newFolder("delete_test")
        val dao = FakeRecordingDao()
        val storageManager = RecordingsStorageManager(dir)
        val repo = DefaultRecordingsRepository(dao, storageManager)

        // Write a mock wav file
        val mockWav = File(dir, "memo_delete_me.wav")
        mockWav.writeBytes(ByteArray(100))

        val id = dao.insert(
            RecordingEntity(
                recordingId = "memo_to_delete",
                watchDeviceAddress = "AA:BB:CC:DD:EE:FF",
                timestampMs = 1700000000000L,
                durationMs = 5000L,
                totalBytes = 100L,
                transferredBytes = 100L,
                sha256Hex = "abc",
                localFilePath = mockWav.absolutePath,
                title = "To Delete",
                syncState = RecordingSyncState.VerifiedOnPhone
            )
        )

        assertTrue(mockWav.exists())

        val deleteResult = repo.deleteRecording(id)
        assertTrue(deleteResult.isSuccess)

        // Local file deleted
        assertFalse(mockWav.exists())
        // Record deleted from Room
        val record = dao.getById(id)
        assertEquals(null, record)
    }

    @Test
    fun renameRecording_updatesCatalogTitle() = runTest {
        val dir = tempFolder.newFolder("rename_test")
        val dao = FakeRecordingDao()
        val storageManager = RecordingsStorageManager(dir)
        val repo = DefaultRecordingsRepository(dao, storageManager)

        val id = repo.addCapturedRecording(
            recordingId = "memo_rename",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            timestampMs = 1700000000000L,
            durationMs = 5000L,
            totalBytes = 100L,
            sha256Hex = "abc",
            title = "Original Title"
        )

        val renameResult = repo.renameRecording(id, "Updated Custom Title")
        assertTrue(renameResult.isSuccess)

        val record = dao.getById(id)
        assertEquals("Updated Custom Title", record?.title)
    }
    @Test
    fun transferRejectsGapsOverflowAndConflictingDuplicates() {
        val data = ByteArray(100) { 7 }
        val session = RecordingTransferClient(RecordingsStorageManager(tempFolder.newFolder())).startTransfer(
            "bounded_memo", 100, sha256Hex(data))
        fun rejects(chunk: AudioChunk) {
            try { session.appendChunk(chunk); fail("Expected invalid chunk rejection") }
            catch (_: IllegalArgumentException) { }
        }
        rejects(AudioChunk(1, 10, 10, data.copyOf(10)))
        rejects(AudioChunk(1, 0, 101, ByteArray(101)))
        session.appendChunk(AudioChunk(0, 0, 50, data.copyOf(50)))
        rejects(AudioChunk(0, 0, 50, ByteArray(50) { 9 }))
        assertEquals(50L, session.currentTransferredBytes)
        session.appendChunk(AudioChunk(1, 50, 50, data.copyOf(50)))
        assertTrue(session.completeAndCommit().exists())
    }

    @Test
    fun transferResumesFromDurablePartialFile() {
        val storage = RecordingsStorageManager(tempFolder.newFolder())
        val data = ByteArray(100) { it.toByte() }
        val client = RecordingTransferClient(storage)
        client.startTransfer("resume_memo", 100, sha256Hex(data))
            .appendChunk(AudioChunk(0, 0, 50, data.copyOfRange(0, 50)))
        val resumed = client.startTransfer("resume_memo", 100, sha256Hex(data), 50)
        assertEquals(50L, resumed.currentTransferredBytes)
        resumed.appendChunk(AudioChunk(1, 50, 50, data.copyOfRange(50, 100)))
        assertArrayEquals(data, resumed.completeAndCommit().readBytes().drop(44).toByteArray())
    }

}
