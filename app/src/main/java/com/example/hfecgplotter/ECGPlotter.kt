package com.example.hfecgplotter

import android.graphics.Color
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.components.XAxis
import java.util.Locale

class ECGPlotter(
    private val chart: LineChart,
    private val samplingHz: Float
) {
    /** Applies all styling/formatting to the chart (axes, grid, dataset, etc.). */
    fun configureChart() {
        // 1) Create & style a single LineDataSet for "ECG (mV)"
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
            description.isEnabled = false  // No description text
            setTouchEnabled(false)         // Disable pinch/zoom/drag
            setScaleEnabled(false)         // No manual zooming
            setDrawGridBackground(false)
            setHardwareAccelerationEnabled(true)
        }

        // 4) X-Axis: one tick per second (samplingHz samples = 1 s)
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = samplingHz       // Draw a grid/label every "samplingHz" data points
            setDrawGridLines(true)

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    // Raw x = sample index. Divide by samplingHz → integer seconds.
                    val seconds = (value / samplingHz).toInt()
                    return seconds.toString()
                }
            }
        }

        // 5) Left Y-Axis: label each tick with "mV"
        chart.axisLeft.apply {
            setDrawGridLines(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    // Format as e.g. "0.0 mV", "-1.2 mV", etc.
                    return String.format(Locale.US, "%.1f mV", value)
                }
            }
        }

        // 6) Disable right Y-Axis and legend
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false

        // 7) IMPORTANT: reserve space on the left so our rotated TextView
        //    (“Amplitude (mV)”) won’t overlap with the chart’s Y-ticks
        chart.extraLeftOffset = 32f
    }
}
