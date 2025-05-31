package com.example.hfecgplotter

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val TAG = "Main_Activity"

    // ─── USER‐CONFIGURABLE PARAMETERS ───────────────────────────────────────────
    private val samplingHz = 130f                  // e.g. 250f, 500f, 1000f, etc.
    private val uiRefreshIntervalMs = 20L           // ~50 Hz UI updates
    private val chartWindowSize = 200               // sliding‐window size in points

    // ─── Shared channel between producer & consumer ─────────────────────────────
    private val ecgChannel = Channel<Float>(
        capacity = 10_000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // ─── Next X‐value (increments by 1 per sample) ──────────────────────────────
    private var nextX = 0f

    // ─── Coroutine Jobs to cancel in onDestroy() ────────────────────────────────
    private var producerJob: Job? = null
    private var consumerJob: Job? = null

    // ─── The LineChart view from XML ────────────────────────────────────────────
    private lateinit var chart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Bind the LineChart
        chart = findViewById(R.id.ecgChart)

        // 2) Instantiate and configure via RealTimeChartSetup class:
        val chartSetup = ECGPlotter(chart, samplingHz)
        chartSetup.configureChart()

        // 3) Start producer + consumer coroutines (exactly as before)
        startProducer()
        startConsumer()
    }

    //───────────────────────────────────────────────────────────────────────────
    // PRODUCER: generates a 1 Hz sine wave at [samplingHz] (e.g. 500 Hz)
    //───────────────────────────────────────────────────────────────────────────
    private fun startProducer() {
        if (producerJob?.isActive == true) return

        producerJob = lifecycleScope.launch(Dispatchers.Default) {
            var t = 0.0
            val dt = 1.0 / samplingHz

            while (isActive) {
                val sample = sin(2 * PI * 1 * t).toFloat()
                ecgChannel.trySend(sample)
                t += dt
                val sleepMs = (dt * 1000).toLong().coerceAtLeast(1L)
                delay(sleepMs)
            }
        }
    }

    //───────────────────────────────────────────────────────────────────────────
    // CONSUMER: every [uiRefreshIntervalMs], drain the channel & update chart
    //───────────────────────────────────────────────────────────────────────────
    private fun startConsumer() {
        if (consumerJob?.isActive == true) return

        consumerJob = lifecycleScope.launch(Dispatchers.Main) {
            val data = chart.data ?: return@launch
            val dataSet = data.getDataSetByIndex(0) as? LineDataSet ?: return@launch

            while (isActive) {
                val batch = mutableListOf<Float>()
                while (true) {
                    val result = ecgChannel.tryReceive()
                    if (result.isSuccess) {
                        batch.add(result.getOrNull()!!)
                    } else {
                        break
                    }
                }

                if (batch.isNotEmpty()) {
                    var cnt = 0

                    val downSampleFactor = maxOf(1, batch.size / 50)
                    batch.forEachIndexed { idx, sample ->
                        if (idx % downSampleFactor == 0) {
                            data.addEntry(Entry(nextX, sample), 0)
                            nextX += 1f

                            cnt++
                        }
                    }

                    Log.d(TAG, "${batch.size}, $cnt")

                    data.notifyDataChanged()
                    chart.notifyDataSetChanged()
                    chart.setVisibleXRangeMaximum(chartWindowSize.toFloat())
                    chart.moveViewToX(nextX - chartWindowSize)
                    chart.invalidate()
                }

                delay(uiRefreshIntervalMs)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        producerJob?.cancel()
        consumerJob?.cancel()
    }
}