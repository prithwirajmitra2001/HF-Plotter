package com.example.hfecgplotter.writer

import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

class FileWriter(
    private val scope: CoroutineScope,
    private val flushEvery: Long = 100L // flush after every 100 samples
) {
    // Buffer for incoming samples; drop oldest if it overflows.
    private val fileChannel = Channel<Float>(
        capacity = 10_000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Will hold the writer coroutine job
    private var writerJob: Job? = null

    /**
     * Call to begin writing. Launches a coroutine on Dispatchers.IO that:
     * 1) Creates (or overwrites) Downloads/ecg_data.csv
     * 2) Writes a header row: "SampleNumber,ECG_Value"
     * 3) Loops over fileChannel, writing each sample as one CSV line.
     *    Flushes every [flushEvery] lines. Only when fileChannel is closed
     *    AND fully drained does the coroutine complete and the file close.
     */
    fun start() {
        if (writerJob?.isActive == true) return

        writerJob = scope.launch(Dispatchers.IO) {
            // 1) Check/create Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            if (downloadsDir == null || (!downloadsDir.exists() && !downloadsDir.mkdirs())) {
                // Cannot access or create Downloads → abort
                return@launch
            }

            // 2) Create or overwrite "ecg_data.csv"
            val csvFile = File(downloadsDir, "ecg_data.csv")
            if (csvFile.exists()) {
                csvFile.delete()
            }
            csvFile.createNewFile()

            // 3) Open a BufferedWriter; the 'use { }' block ensures it closes when done
            BufferedWriter(FileWriter(csvFile, /*append=*/ true)).use { writer ->
                // 3a) Write header
                writer.write("SampleNumber,ECG_Value\n")
                writer.flush()

                var sampleNumber = 0L

                // 3b) Loop: consume from fileChannel until it's closed AND empty
                for (value in fileChannel) {
                    // Write CSV line:
                    writer.write("$sampleNumber,$value\n")
                    sampleNumber += 1

                    // Flush every [flushEvery] lines
                    if (sampleNumber % flushEvery == 0L) {
                        writer.flush()
                    }
                }
                // Once fileChannel is closed *and* all items drained, this loop exits
                // The BufferedWriter is then closed automatically by 'use { … }'.
            }
        }
    }

    /**
     * Request to stop writing. This closes the channel so no new samples
     * can be enqueued. The writer coroutine is *not immediately cancelled*;
     * instead, it will finish draining any remaining samples. Only when the
     * channel is empty does the coroutine complete and the file close.
     */
    fun stop() {
        // 1) Close the channel to signal "no more samples".
        fileChannel.close()
        // 2) Do NOT cancel writerJob here, so it can finish draining.
        //    If someone wants to wait until everything is written, they can call awaitCompletion().
    }

    /**
     * Enqueue a new ECG sample (Float) to be written to disk.
     * If the internal buffer is full, the oldest sample is dropped.
     *
     * @param sample the raw ECG Float value (e.g., in mV).
     */
    fun enqueueSample(sample: Float) {
        // If channel is closed, trySend will simply fail silently.
        fileChannel.trySend(sample)
    }

    /**
     * Suspends until the writer coroutine has fully completed (i.e., the fileChannel
     * is closed and all queued samples have been written, and the file closed).
     *
     * Usage (from a CoroutineScope):
     *    storageWriter.stop()
     *    storageWriter.awaitCompletion()
     */
    suspend fun awaitCompletion() {
        writerJob?.join()
    }
}