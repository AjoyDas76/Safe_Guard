package com.example.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.widget.TextView
import com.example.R
import com.example.model.EventSnapshot
import com.example.ui.screens.ChartMetric
import com.example.ui.screens.TelemetryPalette
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelemetryMarkerView(
    context: Context,
    private val baseTimeProvider: () -> Long,
    private val getSnapshotAtRelSec: (Float) -> EventSnapshot?,
    private val metricProvider: () -> ChartMetric,
    private val isDarkProvider: () -> Boolean
) : MarkerView(context, R.layout.layout_chart_marker) {

    private val tvTime: TextView = findViewById(R.id.tv_marker_time)
    private val tvValue: TextView = findViewById(R.id.tv_marker_value)
    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) return
        val baseTime = baseTimeProvider()
        val pointTimeMillis = baseTime + (e.x * 1000L).toLong()
        tvTime.text = sdf.format(Date(pointTimeMillis))

        val snapshot = getSnapshotAtRelSec(e.x)
        val metric = metricProvider()
        val isDark = isDarkProvider()

        val tempHex = if (isDark) TelemetryPalette.TempHexDark else TelemetryPalette.TempHexLight
        val humHex = if (isDark) TelemetryPalette.HumHexDark else TelemetryPalette.HumHexLight
        val presHex = if (isDark) TelemetryPalette.PresHexDark else TelemetryPalette.PresHexLight

        when (metric) {
            ChartMetric.TEMPERATURE -> {
                val temp = snapshot?.temperature ?: e.y.toDouble()
                tvValue.text = String.format(Locale.US, "Temp: %.1f°C", temp)
                tvValue.setTextColor(AndroidColor.parseColor(tempHex))
            }
            ChartMetric.HUMIDITY -> {
                val hum = snapshot?.humidity ?: e.y.toDouble()
                tvValue.text = String.format(Locale.US, "Humidity: %.1f%%", hum)
                tvValue.setTextColor(AndroidColor.parseColor(humHex))
            }
            ChartMetric.PRESSURE -> {
                val pres = snapshot?.pressure ?: e.y.toDouble()
                tvValue.text = String.format(Locale.US, "Pressure: %.0fhPa", pres)
                tvValue.setTextColor(AndroidColor.parseColor(presHex))
            }
            ChartMetric.ALL -> {
                if (snapshot != null) {
                    when (highlight?.dataSetIndex) {
                        0 -> {
                            tvValue.text = String.format(Locale.US, "Temp: %.1f°C", snapshot.temperature)
                            tvValue.setTextColor(AndroidColor.parseColor(tempHex))
                        }
                        1 -> {
                            tvValue.text = String.format(Locale.US, "Humidity: %.1f%%", snapshot.humidity)
                            tvValue.setTextColor(AndroidColor.parseColor(humHex))
                        }
                        2 -> {
                            tvValue.text = String.format(Locale.US, "Pressure: %.0fhPa", snapshot.pressure)
                            tvValue.setTextColor(AndroidColor.parseColor(presHex))
                        }
                        else -> {
                            tvValue.text = String.format(
                                Locale.US,
                                "T: %.1f°C  H: %.0f%%  P: %.0f",
                                snapshot.temperature,
                                snapshot.humidity,
                                snapshot.pressure
                            )
                            tvValue.setTextColor(AndroidColor.WHITE)
                        }
                    }
                } else {
                    tvValue.text = String.format(Locale.US, "Val: %.1f", e.y)
                    tvValue.setTextColor(AndroidColor.WHITE)
                }
            }
        }

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat() - 14f)
    }
}
