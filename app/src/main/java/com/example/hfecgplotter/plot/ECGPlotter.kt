package com.example.hfecgplotter.plot

import android.graphics.Color
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class ECGPlotter(
    private val chart: LineChart,
    private val samplingHz: Float,
    private val ecgChannel: Channel<Float>,
    private val scope: CoroutineScope,
    private val uiRefreshIntervalMs: Long = 20L,
    private val chartWindowSize: Int = 200
) {
    // Holds the consumer coroutine job so we can cancel it.
    private var consumerJob: Job? = null

    // Internal X‐counter (increments by 1 per plotted sample)
    private var nextX = 0f

    init {
        configureChart()
    }

    /** Configures axes, formatters, dataset, etc., for a clean real‐time ECG plot. */
    private fun configureChart() {
        // 1) Create & style the LineDataSet for "ECG (mV)"
        val dataSet = LineDataSet(null, "ECG (mV)").apply {
            setDrawCircles(false)
            setDrawValues(false)
            color = Color.RED
            lineWidth = 2f
            mode = LineDataSet.Mode.LINEAR
        }

        // 2) Attach the DataSet to the chart
        chart.data = LineData(dataSet)

        // 3) General chart settings
        chart.apply {
            description.isEnabled = false    // no description text
            setTouchEnabled(false)           // disable user interactions
            setScaleEnabled(false)           // no manual zooming
            setDrawGridBackground(false)
            setHardwareAccelerationEnabled(true)
        }

        // 4) X‐Axis: place one grid+label per second (samplingHz samples = 1 s)
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = samplingHz         // draw a tick every "samplingHz" points
            setDrawGridLines(true)

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    // raw X = number of samples since start; divide by samplingHz → integer seconds
                    val sec = (value / samplingHz).toInt()
                    return sec.toString()
                }
            }
        }

        // 5) Left Y‐Axis: label in "mV"
        chart.axisLeft.apply {
            setDrawGridLines(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    // Format e.g. "-1.2 mV", "0.0 mV", "1.2 mV"
                    return String.format(Locale.US, "%.1f mV", value)
                }
            }
        }

        // 6) Disable right Y‐axis and legend
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
    }

    /**
     * Launches a coroutine on [scope] (Main dispatcher) that:
     *  • Every [uiRefreshIntervalMs] ms, drains all pending floats from [ecgChannel].
     *  • Downsamples to ≤50 points, appends them to the chart's dataset,
     *    and then scrolls a sliding window of size [chartWindowSize].
     */
    fun startConsumer() {
        if (consumerJob?.isActive == true) return

        consumerJob = scope.launch(Dispatchers.Main) {
            val lineData = chart.data ?: return@launch
            val dataSet = lineData.getDataSetByIndex(0) as? LineDataSet ?: return@launch

            while (isActive) {
                // 1) Drain all pending samples into a local list
                val batch = mutableListOf<Float>()
                while (true) {
                    val result = ecgChannel.tryReceive()
                    if (result.isSuccess) {
                        batch.add(result.getOrNull()!!)
                    } else {
                        break
                    }
                }

                // 2) If we have samples, downsample & append
                if (batch.isNotEmpty()) {
                    val downsampleFactor = maxOf(1, batch.size / 50)
                    batch.forEachIndexed { idx, sample ->
                        if (idx % downsampleFactor == 0) {
                            lineData.addEntry(Entry(nextX, sample), 0)
                            nextX += 1f
                        }
                    }

                    // 3) Notify & scroll
                    lineData.notifyDataChanged()
                    chart.notifyDataSetChanged()
                    chart.setVisibleXRangeMaximum(chartWindowSize.toFloat())
                    chart.moveViewToX(nextX - chartWindowSize)
                    chart.invalidate()
                }

                // 4) Wait until next UI tick
                delay(uiRefreshIntervalMs)
            }
        }
    }

    /** Cancels the consumer coroutine and stops updating the chart. */
    fun stopConsumer() {
        consumerJob?.cancel()
    }
}