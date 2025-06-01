package com.example.hfecgplotter

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.hfecgplotter.plot.ECGPlotter
import com.example.hfecgplotter.producer.ECGProducer
import com.example.hfecgplotter.writer.FileWriter
import com.github.mikephil.charting.charts.LineChart
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_WRITE_STORAGE = 100
    }

    // ─── USER‐CONFIGURABLE PARAMETERS ───────────────────────────────────────────
    private val samplingHz = 500f                  // e.g. 250f, 500f, 1000f
    private val uiRefreshIntervalMs = 20L           // ~50 Hz UI updates
    private val chartWindowSize = 200               // sliding‐window points

    // ─── Channel for plotting (drained by RealTimeChartSetup) ────────────────
    private val ecgChannel = Channel<Float>(
        capacity = 10_000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // ─── Chart + its setup/consumer helper ────────────────────────────────────
    private lateinit var chartSetup: ECGPlotter

    // ─── Storage writer (writes CSV under Downloads/) ─────────────────────────
    private lateinit var fileWriter: FileWriter

    // ─── Now, a single ECGProducer instance ───────────────────────────────────
    private lateinit var producer: ECGProducer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Bind the LineChart view
        val chart: LineChart = findViewById(R.id.ecgChart)

        // 2) Instantiate & configure RealTimeChartSetup, passing in:
        //    • chart
        //    • samplingHz
        //    • ecgChannel
        //    • lifecycleScope
        //    • uiRefreshIntervalMs
        //    • chartWindowSize
        chartSetup = ECGPlotter(
            chart = chart,
            samplingHz = samplingHz,
            ecgChannel = ecgChannel,
            scope = lifecycleScope,
            uiRefreshIntervalMs = uiRefreshIntervalMs,
            chartWindowSize = chartWindowSize
        )

        // 3) Ask for WRITE_EXTERNAL_STORAGE permission if needed
        requestStoragePermissionIfNeeded()

        // 4) Instantiate & start the file‐writer
        fileWriter = FileWriter(scope = lifecycleScope)
        fileWriter.start()

        // 5) Instantiate & start the ECGProducer
        producer = ECGProducer(
            samplingHz = samplingHz,
            ecgChannel = ecgChannel,
            fileWriter = fileWriter,
            scope = lifecycleScope
        )
        producer.start()

        // 6) Start the chart consumer
        chartSetup.startConsumer()
    }

    override fun onDestroy() {
        super.onDestroy()

        // 1) Stop the producer first → no new ECG samples will be generated
        producer.stop()

        // 2) Signal storage writer to finish writing any buffered samples, then await
        fileWriter.stop()
        lifecycleScope.launch {
            fileWriter.awaitCompletion()
            // 3) Once file writing is done, stop the chart consumer
            chartSetup.stopConsumer()
        }
    }

    //───────────────────────────────────────────────────────────────────────────
    // Request WRITE_EXTERNAL_STORAGE (API 23–28). On Q+ (API 29+), you’d use
    // MediaStore/SAF instead. This example assumes legacy external‐storage.
    //───────────────────────────────────────────────────────────────────────────
    private fun requestStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_STORAGE
            )
        }
    }
}