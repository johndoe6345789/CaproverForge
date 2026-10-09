package com.caproverforge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.caproverforge.data.Series
import com.caproverforge.ui.theme.LocalExtendedColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class ChartLine(val label: String, val series: Series, val color: Color)

/** Categorical slots 1–2 (blue, orange), validated for CVD separation on the card surfaces. */
@Composable
fun chartSeriesColors(): List<Color> {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) listOf(Color(0xFF3987E5), Color(0xFFD95926)) else listOf(Color(0xFF2A78D6), Color(0xFFEB6834))
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private val clock = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val clockShort = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

fun formatClock(epochSeconds: Long, seconds: Boolean = true): String =
    (if (seconds) clock else clockShort).format(Instant.ofEpochSecond(epochSeconds))

/** Rounds up to a 1/2/2.5/5 × 10ⁿ step so axis ticks are clean numbers. */
fun niceMax(value: Double): Double {
    if (value <= 0) return 1.0
    val exp = floor(log10(value))
    val base = 10.0.pow(exp)
    val f = value / base
    val nice = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { f <= it + 1e-9 }
    return nice * base
}

/**
 * Time-series line chart: 2dp lines, 10% area wash for a single series, hairline gridlines
 * at 0 / ½ / max, end dot, and touch scrubbing. [onScrub] reports the index under the finger.
 */
@Composable
fun LineChart(
    lines: List<ChartLine>,
    formatY: (Double) -> String,
    modifier: Modifier = Modifier,
    yMax: Double? = null,
    height: androidx.compose.ui.unit.Dp = 132.dp,
    showAxis: Boolean = true,
    selected: Int? = null,
    onScrub: (Int?) -> Unit = {},
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axisText = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val crosshair = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val gutterPx = remember { floatArrayOf(0f) } // written while drawing, read by the scrub gesture
    val times = lines.firstOrNull()?.series?.times.orEmpty()
    val dataMax = lines.maxOfOrNull { l -> l.series.values.maxOrNull() ?: 0.0 } ?: 0.0
    val top = yMax ?: niceMax(dataMax * 1.1)
    val description = lines.joinToString { l -> "${l.label} ${l.series.last?.let(formatY) ?: "no data"}" }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(times.size) {
                if (times.size < 2) return@pointerInput
                fun indexAt(x: Float): Int {
                    val g = gutterPx[0]
                    val frac = ((x - g) / (size.width - g)).coerceIn(0f, 1f)
                    return (frac * (times.size - 1) + 0.5f).toInt().coerceIn(0, times.lastIndex)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onScrub(indexAt(down.position.x))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        if (change.positionChange() != Offset.Zero) {
                            change.consume()
                            onScrub(indexAt(change.position.x))
                        }
                    }
                    onScrub(null)
                }
            },
    ) {
        val ticks = listOf(0.0, top / 2, top)
        // Y labels live in their own gutter so the data never runs underneath them.
        val tickLayouts = if (showAxis) ticks.map { measurer.measure(formatY(it), axisText) } else emptyList()
        val gutter = if (showAxis) (tickLayouts.maxOf { it.size.width } + 8.dp.toPx()) else 0f
        gutterPx[0] = gutter
        val w = size.width - gutter
        val h = size.height
        val plotTop = if (showAxis) 8.dp.toPx() else 0f
        val plotH = h - plotTop - (if (showAxis) 8.dp.toPx() else 2.dp.toPx())
        fun y(v: Double) = plotTop + plotH * (1f - (v / top).toFloat().coerceIn(0f, 1f))
        fun x(i: Int, n: Int) = gutter + if (n <= 1) w else w * i / (n - 1)

        if (showAxis) {
            ticks.forEachIndexed { i, v ->
                val gy = y(v)
                drawLine(grid, Offset(gutter, gy), Offset(size.width, gy), strokeWidth = 1f)
                val layout = tickLayouts[i]
                drawText(layout, topLeft = Offset(0f, gy - layout.size.height / 2f))
            }
        }

        lines.forEach { line ->
            val v = line.series.values
            if (v.size < 2) return@forEach
            val path = Path().apply {
                v.forEachIndexed { i, value -> if (i == 0) moveTo(x(i, v.size), y(value)) else lineTo(x(i, v.size), y(value)) }
            }
            if (lines.size == 1) {
                val area = Path().apply {
                    addPath(path)
                    lineTo(size.width, y(0.0)); lineTo(gutter, y(0.0)); close()
                }
                drawPath(area, line.color.copy(alpha = 0.10f))
            }
            drawPath(path, line.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        val idx = selected?.takeIf { it in times.indices }
        if (idx != null) {
            val cx = x(idx, times.size)
            drawLine(crosshair, Offset(cx, plotTop), Offset(cx, plotTop + plotH), strokeWidth = 1f)
        }
        lines.forEach { line ->
            val v = line.series.values
            if (v.isEmpty()) return@forEach
            val i = idx ?: v.lastIndex
            val c = Offset(x(i, v.size), y(v[i.coerceAtMost(v.lastIndex)]))
            drawCircle(surface, radius = 6.dp.toPx(), center = c)
            drawCircle(line.color, radius = 4.dp.toPx(), center = c)
        }
    }
}

/**
 * A titled chart card: the headline shows the latest value, or the scrubbed value and its time.
 * Two-series charts get a legend whose entries carry the values as text.
 */
@Composable
fun MetricChartCard(
    title: String,
    lines: List<ChartLine>,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
    yMax: Double? = null,
    subtitle: String? = null,
    formatAxis: (Double) -> String = formatValue,
    headline: ((Int?) -> String)? = null,
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val times = lines.firstOrNull()?.series?.times.orEmpty()
    fun valueAt(line: ChartLine) = (selected?.let { line.series.values.getOrNull(it) } ?: line.series.last)

    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    selected?.let { times.getOrNull(it) }?.let { formatClock(it) } ?: (subtitle ?: "Now"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (lines.size == 1) {
                Text(
                    headline?.invoke(selected) ?: valueAt(lines.first())?.let(formatValue) ?: "–",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        if (lines.size > 1) {
            Row(Modifier.padding(top = 8.dp)) {
                lines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                        Box(Modifier.size(width = 14.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(line.color))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${line.label} ${valueAt(line)?.let(formatValue) ?: "–"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (times.size < 2) {
            Text("Waiting for data…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LineChart(lines, formatAxis, yMax = yMax, selected = selected, onScrub = { selected = it })
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(formatClock(times.first(), seconds = false), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("Now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Usage meter: the fill turns warning at 80% and error at 90%; the track is a light step of the same hue. */
@Composable
fun UsageMeter(fraction: Double, modifier: Modifier = Modifier) {
    val ext = LocalExtendedColors.current
    val fill = when {
        fraction >= 0.9 -> MaterialTheme.colorScheme.error
        fraction >= 0.8 -> ext.warning
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(fill.copy(alpha = 0.18f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(fill)
        )
    }
}

/** Status line under a meter once usage is high: icon + words, never colour alone. */
@Composable
fun UsageWarning(fraction: Double, what: String) {
    if (fraction < 0.8) return
    val color = if (fraction >= 0.9) MaterialTheme.colorScheme.error else LocalExtendedColors.current.warning
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Icon(Icons.Outlined.Warning, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (fraction >= 0.9) "$what is almost full" else "$what is getting full",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

fun formatPercent(v: Double) = if (v < 10) String.format(java.util.Locale.getDefault(), "%.1f%%", v) else "${v.toInt()}%"

fun formatKbps(kbps: Double): String = when {
    kbps >= 1_000_000 -> String.format(java.util.Locale.getDefault(), "%.1f Gb/s", kbps / 1_000_000)
    kbps >= 1_000 -> String.format(java.util.Locale.getDefault(), "%.1f Mb/s", kbps / 1_000)
    else -> "${ceil(kbps).toInt()} kb/s"
}

fun formatLoad(v: Double) = String.format(java.util.Locale.getDefault(), "%.2f", v)
