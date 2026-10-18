package com.naari.companion.recordings

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.naari.companion.data.RecordingDao
import com.naari.companion.data.RecordingEntity
import com.naari.companion.data.RecordingSyncState
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Repository interface for managing local audio memos, transfers, and file exports.
 */
interface RecordingsRepository {
    val recordings: Flow<List<RecordingEntity>>

    fun getAvailableStorageBytes(): Long

    suspend fun addCapturedRecording(
        recordingId: String,
        deviceAddress: String,
        timestampMs: Long,
        durationMs: Long,
        totalBytes: Long,
        sha256Hex: String,
        title: String = "Voice Memo"
    ): Long

    suspend fun renameRecording(id: Long, newTitle: String): Result<Unit>
    suspend fun deleteRecording(id: Long): Result<Unit>
    fun getExportUri(id: Long, context: Context): Result<Uri>

    suspend fun executeTransfer(
        recordingId: String,
        chunkSource: suspend (startOffset: Long, onChunkReceived: suspend (AudioChunk) -> Unit) -> Unit
    ): Result<File>
}

/**
 * Production implementation of [RecordingsRepository].
 *
 * Enforces:
 * - App-owned storage without broad permissions.
 * - Local-only deletion (does not delete watch original).
 * - FileProvider export for sharing.
 * - Available-space check and atomic commit with SHA-256 integrity verification.
 */
class DefaultRecordingsRepository(
    private val recordingDao: RecordingDao,
    private val storageManager: RecordingsStorageManager,
    private val transferClient: RecordingTransferClient = RecordingTransferClient(storageManager)
) : RecordingsRepository {

    override val recordings: Flow<List<RecordingEntity>> = recordingDao.observeAll()

    override fun getAvailableStorageBytes(): Long {
        return storageManager.getAvailableStorageBytes()
    }

    override suspend fun addCapturedRecording(
        recordingId: String,
        deviceAddress: String,
        timestampMs: Long,
        durationMs: Long,
        totalBytes: Long,
        sha256Hex: String,
        title: String
    ): Long {
        val existing = recordingDao.getByRecordingId(recordingId)
        if (existing != null) {
            return existing.id
        }

        val entity = RecordingEntity(
            recordingId = recordingId,
            watchDeviceAddress = deviceAddress,
            timestampMs = timestampMs,
            durationMs = durationMs,
            totalBytes = totalBytes,
            sha256Hex = sha256Hex,
            title = title,
            syncState = RecordingSyncState.CapturedOnWatch
        )
        return recordingDao.insert(entity)
    }

    override suspend fun renameRecording(id: Long, newTitle: String): Result<Unit> {
        val count = recordingDao.updateTitle(id, newTitle.trim())
        return if (count > 0) Result.success(Unit) else Result.failure(IllegalArgumentException("Recording $id not found"))
    }

    override suspend fun deleteRecording(id: Long): Result<Unit> {
        val entity = recordingDao.getById(id)
            ?: return Result.failure(IllegalArgumentException("Recording $id not found"))

        // Delete phone local copy only (watch original is strictly preserved)
        entity.localFilePath?.let { path ->
            storageManager.deleteFile(File(path))
        }

        recordingDao.delete(id)
        return Result.success(Unit)
    }

    override fun getExportUri(id: Long, context: Context): Result<Uri> {
        val file = runCatching {
            val entity = kotlinx.coroutines.runBlocking { recordingDao.getById(id) }
                ?: throw IllegalArgumentException("Recording not found")
            val path = entity.localFilePath ?: throw IllegalStateException("Recording file not yet downloaded")
            File(path)
        }.getOrElse { return Result.failure(it) }

        if (!file.exists()) {
            return Result.failure(IllegalStateException("Local file ${file.name} does not exist on disk"))
        }

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        return Result.success(uri)
    }

    override suspend fun executeTransfer(
        recordingId: String,
        chunkSource: suspend (startOffset: Long, onChunkReceived: suspend (AudioChunk) -> Unit) -> Unit
    ): Result<File> {
        val entity = recordingDao.getByRecordingId(recordingId)
            ?: return Result.failure(IllegalArgumentException("Recording $recordingId not found in catalog"))

        var lastOffset = entity.transferredBytes
        var lastPersistedOffset = entity.transferredBytes
        try {
            val session = transferClient.startTransfer(
                recordingId = recordingId,
                totalBytes = entity.totalBytes,
                expectedSha256Hex = entity.sha256Hex,
                startOffset = entity.transferredBytes.coerceIn(0, entity.totalBytes)
            )

            recordingDao.updateSyncProgress(
                id = entity.id,
                transferredBytes = session.currentTransferredBytes,
                syncState = RecordingSyncState.Transferring,
                failureReason = null
            )

            // Stream chunks from source
            chunkSource(session.currentTransferredBytes) { chunk ->
                val newOffset = session.appendChunk(chunk)
                lastOffset = newOffset
                if (newOffset == entity.totalBytes || newOffset - lastPersistedOffset >= 16 * 1024) {
                    recordingDao.updateSyncProgress(
                        id = entity.id,
                        transferredBytes = newOffset,
                        syncState = RecordingSyncState.Transferring,
                        failureReason = null
                    )
                    lastPersistedOffset = newOffset
                }
            }

            // Verify and atomic commit
            val finalWav = session.completeAndCommit()

            recordingDao.markVerified(
                id = entity.id,
                localFilePath = finalWav.absolutePath,
                totalBytes = entity.totalBytes,
                syncState = RecordingSyncState.VerifiedOnPhone
            )

            return Result.success(finalWav)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            recordingDao.updateSyncProgress(
                id = entity.id,
                transferredBytes = lastOffset,
                syncState = RecordingSyncState.Failed,
                failureReason = e.message ?: "Transfer failed"
            )
            return Result.failure(e)
        }
    }
}
