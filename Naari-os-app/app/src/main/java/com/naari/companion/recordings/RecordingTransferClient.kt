package com.naari.companion.recordings

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio memo chunk received over the dedicated binary characteristic.
 * Ref: PRD Section 6 (Recording protocol).
 */
data class AudioChunk(
    val sequence: Long,
    val byteOffset: Long,
    val length: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioChunk
        return sequence == other.sequence &&
                byteOffset == other.byteOffset &&
                length == other.length &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = sequence.hashCode()
        result = 31 * result + byteOffset.hashCode()
        result = 31 * result + length
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Client handling chunked, resumable transfer of watch audio memos.
 *
 * Enforces:
 * - Resumable transfers starting from arbitrary byte offsets.
 * - Duplicate chunk handling (duplicate sequences/offsets are deduplicated safely).
 * - Available storage verification before write.
 * - Length and SHA-256 integrity verification before atomic commit.
 */
class RecordingTransferClient(
    private val storageManager: RecordingsStorageManager
) {

    companion object {
        const val CHUNK_HEADER_SIZE = 10

        fun parseChunk(packet: ByteArray): AudioChunk {
            if (packet.size < CHUNK_HEADER_SIZE) {
                throw IllegalArgumentException("Packet size ${packet.size} too small for 10-byte chunk header")
            }
            val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
            val seq = buf.getInt().toLong() and 0xFFFFFFFFL
            val offset = buf.getInt().toLong() and 0xFFFFFFFFL
            val len = buf.getShort().toInt() and 0xFFFF

            if (packet.size < CHUNK_HEADER_SIZE + len) {
                throw IllegalArgumentException("Packet truncated: declared length $len, but packet has only ${packet.size - CHUNK_HEADER_SIZE} payload bytes")
            }

            val payload = ByteArray(len)
            buf.get(payload)

            return AudioChunk(
                sequence = seq,
                byteOffset = offset,
                length = len,
                payload = payload
            )
        }
    }

    /**
     * Session state for an ongoing resumable transfer.
     */
    class TransferSession(
        val recordingId: String,
        val totalBytes: Long,
        val expectedSha256Hex: String,
        private val tempFile: File,
        private val storageManager: RecordingsStorageManager
    ) {
        private val receivedSequences = mutableSetOf<Long>()
        private var highestOffset = 0L

        init {
            // If resuming from existing temp file, initialize offset
            if (tempFile.exists()) {
                highestOffset = tempFile.length()
            }
        }

        val currentTransferredBytes: Long
            get() = highestOffset

        /**
         * Appends an audio chunk to the temporary file.
         * Gracefully handles duplicate chunks and out-of-order retries.
         */
        @Synchronized
        fun appendChunk(chunk: AudioChunk): Long {
            require(chunk.length == chunk.payload.size && chunk.length > 0) { "Invalid chunk length" }
            require(chunk.byteOffset >= 0 && chunk.byteOffset + chunk.length <= totalBytes) { "Chunk outside memo bounds" }
            require(chunk.byteOffset <= highestOffset) { "Missing chunk before offset ${chunk.byteOffset}" }
            RandomAccessFile(tempFile, "rw").use { raf ->
                raf.seek(chunk.byteOffset)
                if(chunk.byteOffset < highestOffset) {
                    require(chunk.byteOffset + chunk.length <= highestOffset) { "Overlapping chunk" }
                    val existing = ByteArray(chunk.length)
                    raf.readFully(existing)
                    require(existing.contentEquals(chunk.payload)) { "Conflicting duplicate chunk" }
                } else {
                    raf.write(chunk.payload)
                    raf.fd.sync()
                    highestOffset += chunk.length
                }
            }

            return highestOffset
        }

        val isComplete: Boolean
            get() = highestOffset == totalBytes

        /**
         * Completes transfer, verifies SHA-256, and atomically commits final WAV.
         */
        @Synchronized
        fun completeAndCommit(): File {
            if (!isComplete) {
                throw IllegalStateException("Transfer incomplete: received $highestOffset of $totalBytes bytes")
            }
            return storageManager.commitTempFileToWav(
                tempFile = tempFile,
                recordingId = recordingId,
                expectedSha256Hex = expectedSha256Hex
            )
        }
    }

    fun startTransfer(
        recordingId: String,
        totalBytes: Long,
        expectedSha256Hex: String,
        startOffset: Long = 0L
    ): TransferSession {
        require(recordingId.matches(Regex("[A-Za-z0-9_-]{1,96}"))) { "Unsafe recording id" }
        require(totalBytes in 1..320000 && expectedSha256Hex.matches(Regex("[A-Fa-f0-9]{64}"))) { "Invalid memo metadata" }
        // 1. Available space check
        storageManager.checkAvailableSpace(totalBytes)

        val tempFile = storageManager.getTempFile(recordingId)
        if (startOffset == 0L && tempFile.exists()) {
            tempFile.delete()
        }

        require(startOffset >= 0 && startOffset <= totalBytes) { "Invalid resume offset" }
        if(tempFile.exists() && tempFile.length() > totalBytes) tempFile.delete()
        return TransferSession(
            recordingId = recordingId,
            totalBytes = totalBytes,
            expectedSha256Hex = expectedSha256Hex,
            tempFile = tempFile,
            storageManager = storageManager
        )
    }
}
