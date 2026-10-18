package com.naari.companion.recordings

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class LowStorageException(message: String) : Exception(message)
class Sha256MismatchException(message: String) : Exception(message)

/**
 * Manages app-owned private storage for voice memos, available space checks,
 * and atomic commitment with SHA-256 integrity verification.
 * Ref: PRD Section 3 & Master Prompt Task 17.
 */
class RecordingsStorageManager(
    private val storageDir: File
) {

    init {
        storageDir.mkdirs()
    }

    companion object {
        const val MINIMUM_SAFETY_STORAGE_BYTES = 5 * 1024 * 1024L // 5 MB safety margin
    }

    fun getAvailableStorageBytes(): Long {
        return storageDir.usableSpace
    }

    /**
     * Checks if sufficient space is available before beginning transfer.
     */
    fun checkAvailableSpace(requiredBytes: Long, safetyMarginBytes: Long = MINIMUM_SAFETY_STORAGE_BYTES) {
        val usable = storageDir.usableSpace
        val needed = requiredBytes + safetyMarginBytes
        if (usable < needed) {
            throw LowStorageException(
                "Insufficient storage: requires $needed bytes (with safety margin), but only $usable bytes available"
            )
        }
    }

    fun getTempFile(recordingId: String): File {
        return File(storageDir, "temp_${recordingId}.tmp")
    }

    fun getFinalWavFile(recordingId: String): File {
        return File(storageDir, "memo_${recordingId}.wav")
    }

    /**
     * Computes the SHA-256 hexadecimal checksum of a file.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies SHA-256 checksum and atomically commits temporary PCM file into final standard WAV.
     */
    fun commitTempFileToWav(
        tempFile: File,
        recordingId: String,
        expectedSha256Hex: String
    ): File {
        if (!tempFile.exists()) {
            throw IllegalStateException("Temporary transfer file ${tempFile.name} does not exist")
        }

        val actualSha256 = computeSha256(tempFile)
        if (!actualSha256.equals(expectedSha256Hex, ignoreCase = true)) {
            tempFile.delete() // Prune corrupted file
            throw Sha256MismatchException(
                "SHA-256 verification mismatch for memo $recordingId: calculated $actualSha256, expected $expectedSha256Hex"
            )
        }

        val rawPcm = tempFile.readBytes()
        val finalWavFile = getFinalWavFile(recordingId)

        val staging = File(finalWavFile.parentFile, finalWavFile.name + ".pending")
        WavWriter.writeWavFile(staging, rawPcm)
        java.io.RandomAccessFile(staging,"rw").use { it.fd.sync() }
        java.nio.file.Files.move(staging.toPath(), finalWavFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        tempFile.delete() // Clean up temp file

        return finalWavFile
    }

    fun deleteFile(file: File): Boolean {
        return if (file.exists()) file.delete() else true
    }
}
