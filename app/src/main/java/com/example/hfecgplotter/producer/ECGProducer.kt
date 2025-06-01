package com.example.hfecgplotter.producer

import com.example.hfecgplotter.writer.FileWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class ECGProducer(
    private val samplingHz: Float,
    private val ecgChannel: Channel<Float>,
    private val fileWriter: FileWriter,
    private val scope: CoroutineScope
) {
    // Internal Job reference so we can cancel this producer when needed
    private var producerJob: Job? = null

    /**
     * Starts the producer coroutine (if not already running). It will:
     * 1) Run on Dispatchers.Default
     * 2) Generate a 1 Hz sine‐wave sample (Float) every (1 / samplingHz) seconds
     * 3) Push each sample into ecgChannel.trySend(...) and storageWriter.enqueueSample(...)
     */
    fun start() {
        // If we’re already running, do nothing
        if (producerJob?.isActive == true) return

        producerJob = scope.launch(Dispatchers.Default) {
            var t = 0.0
            val dt = 1.0 / samplingHz  // seconds per sample

            while (isActive) {
                // a) Generate a 1 Hz sine sample in “mV”
                val sample = sin(2 * PI * 1 * t).toFloat()

                // b) Push into the plot‐channel
                ecgChannel.trySend(sample)

                // c) Enqueue for file writing
                fileWriter.enqueueSample(sample)

                // d) Advance time & delay approximately dt * 1000 ms
                t += dt
                val sleepMs = (dt * 1000).toLong().coerceAtLeast(1L)
                delay(sleepMs)
            }
        }
    }

    /**
     * Stops the producer coroutine so no new samples will be generated.
     */
    fun stop() {
        producerJob?.cancel()
    }
}