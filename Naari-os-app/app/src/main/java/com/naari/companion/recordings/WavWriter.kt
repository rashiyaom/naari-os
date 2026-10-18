package com.naari.companion.recordings

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Standard WAV (16 kHz, mono, PCM16) file generator.
 * Ref: PRD Section 3 (Recordings) & Master Prompt Task 17.
 */
object WavWriter {

    const val SAMPLE_RATE = 16000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val HEADER_SIZE = 44

    /**
     * Generates a 44-byte standard RIFF WAVE header for PCM16 mono 16 kHz audio.
     */
    fun createWavHeader(dataLength: Int): ByteArray {
        val totalLength = dataLength + 36
        val byteRate = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8) // 32,000 bytes/sec
        val blockAlign = (CHANNELS * (BITS_PER_SAMPLE / 8)).toShort() // 2 bytes

        val buffer = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalLength)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // "fmt " sub-chunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // Subchunk1Size for PCM
        buffer.putShort(1.toShort()) // AudioFormat 1 = PCM
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign)
        buffer.putShort(BITS_PER_SAMPLE.toShort())

        // "data" sub-chunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataLength)

        return buffer.array()
    }

    /**
     * Writes raw PCM16 samples into a complete, playable WAV file.
     */
    fun writeWavFile(destinationFile: File, rawPcm: ByteArray) {
        destinationFile.parentFile?.mkdirs()
        FileOutputStream(destinationFile).use { out ->
            val header = createWavHeader(rawPcm.size)
            out.write(header)
            out.write(rawPcm)
            out.flush()
        }
    }

    /**
     * Updates header sizes in an existing WAV file after appending.
     */
    fun updateWavHeaderSizes(wavFile: File, dataSize: Long) {
        if (!wavFile.exists() || wavFile.length() < HEADER_SIZE) return
        RandomAccessFile(wavFile, "rw").use { raf ->
            val totalLength = (dataSize + 36).toInt()

            // Update ChunkSize at offset 4
            raf.seek(4)
            val buf4 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalLength)
            raf.write(buf4.array())

            // Update Subchunk2Size at offset 40
            raf.seek(40)
            val buf40 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize.toInt())
            raf.write(buf40.array())
        }
    }
}
