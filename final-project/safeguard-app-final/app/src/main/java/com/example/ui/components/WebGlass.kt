package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import com.example.ui.theme.CommandNavyDark

/**
 * Website look ("glass-panel" / "sensor-card-v2" in style.css) shared by every card in the app,
 * and the website page background ([WebPageBackground]).
 */

val WebCardShape = RoundedCornerShape(18.dp)
/** One fixed height for every sensor card so neighbours always line up. */
val WebSensorCardHeight = 112.dp

// ---- website colour tokens (style.css :root / body.light-theme) ----
val WebCyanDark = Color(0xFF00F2FF)
val WebCyanLight = Color(0xFF0077FF)
val WebGreen = Color(0xFF10B981)
val WebRed = Color(0xFFFF3366)
val WebAmber = Color(0xFFF59E0B)
val WebPurple = Color(0xFFA855F7)

fun webTextMain(isDark: Boolean) = if (isDark) Color(0xFFF8FAFC) else Color(0xFF05080F)
fun webTextMuted(isDark: Boolean) = if (isDark) Color(0xFF8FA2B8) else Color(0xFF33415A)
fun webBorder(isDark: Boolean) = if (isDark) Color(0x2E78DCFF) else Color(0x590077FF)       // .18 / .35
fun webBorderSubtle(isDark: Boolean) = if (isDark) Color(0x13FFFFFF) else Color(0x1F0F172A)  // .075 / .12

/** sensor-card-v2 base fill (145deg gradient). */
fun webCardBrush(isDark: Boolean): Brush = if (isDark) {
    Brush.linearGradient(listOf(Color(0xB80E1B2F), Color(0x9E070E1A)))
} else {
    Brush.linearGradient(listOf(Color(0xE0DEE7F3), Color(0xB8CDD9E9)))
}

/** --glass-shine overlay. */
fun webShineBrush(isDark: Boolean): Brush = if (isDark) {
    Brush.linearGradient(
        0f to Color(0x15FFFFFF), 0.45f to Color(0x05FFFFFF), 1f to Color(0x0600F2FF)
    )
} else {
    Brush.linearGradient(listOf(Color(0xF2FFFFFF), Color(0x80FFFFFF)))
}

/** Kept for callers that only want a brush for a card background. */
fun webGlassBrush(isDark: Boolean): Brush = webCardBrush(isDark)

/**
 * Applies the website card: rounded 18, gradient fill + shine, thin cyan border, and the 2px
 * cyan highlight line along the top edge. [accent] (red etc.) switches the border to an alert look.
 */
fun Modifier.webGlass(
    isDark: Boolean,
    shape: Shape = WebCardShape,
    accent: Color? = null,
    topLine: Boolean = true,
    /** Neon glow colour around the card (website's hover glow). null = no glow. An [accent] (alert) wins. */
    glow: Color? = null
): Modifier {
    val neon = accent ?: glow
    val border = when {
        accent != null -> accent.copy(alpha = 0.55f)
        glow != null -> glow.copy(alpha = if (isDark) 0.85f else 0.75f)
        else -> webBorder(isDark)
    }
    val line = if (isDark) WebCyanDark else WebCyanLight
    return this
        .then(if (neon != null) Modifier.webNeonGlow(neon, isDark) else Modifier)
        .clip(shape)
        .background(webCardBrush(isDark))
        .background(webShineBrush(isDark))
        .let { m ->
            if (accent != null) m.background(accent.copy(alpha = if (isDark) 0.10f else 0.08f)) else m
        }
        .border(1.dp, border, shape)
        .drawBehind {
            if (topLine) {
                val inset = 13.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, (accent ?: line).copy(alpha = 0.30f), Color.Transparent),
                        startX = inset, endX = size.width - inset
                    ),
                    topLeft = Offset(inset, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, 2.dp.toPx())
                )
            }
        }
}

/**
 * Neon glow ring around a card, like the website's
 * `box-shadow: 0 0 0 1px ..., 0 0 22px 3px ..., 0 0 46px 8px ...` hover glow.
 *
 * Drawn only OUTSIDE the card outline (the card fill is translucent, so the glow must not
 * show through it) using stacked translucent rings, which works on every Android version.
 * It extends [extent] dp (default 5) past the card edge - the parent list needs at least that much
 * side padding inside it, or the glow gets clipped at the screen edge.
 */
fun Modifier.webNeonGlow(
    color: Color,
    isDark: Boolean,
    extent: androidx.compose.ui.unit.Dp = 5.dp,
    cornerRadius: androidx.compose.ui.unit.Dp = 18.dp
): Modifier = this.drawBehind {
    val ext = extent.toPx()
    val r = cornerRadius.toPx()
    val outline = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
    }
    val rings = 10
    val peak = if (isDark) 0.50f else 0.38f
    clipPath(outline, clipOp = ClipOp.Difference) {
        for (i in rings - 1 downTo 0) {
            val t = (i + 1f) / rings            // 1 = outermost ring, ~0 = touching the card
            val d = t * ext
            val a = peak * (1f - t) * (1f - t) + 0.015f
            drawRoundRect(
                color = color.copy(alpha = a.coerceIn(0f, 1f)),
                topLeft = Offset(-d, -d),
                size = androidx.compose.ui.geometry.Size(size.width + 2 * d, size.height + 2 * d),
                cornerRadius = CornerRadius(r + d, r + d),
                style = Stroke(width = ext / rings * 2.2f)
            )
        }
    }
}

/** Website page background: radial cyan glow top-centre + purple glow bottom-right + faint grid. */
@Composable
fun WebPageBackground(isDark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(if (isDark) Color(0xFF040812) else Color(0xFFDDE1E6))
        // radial-gradient(1200px 700px at 50% -10%, cyan .12 -> transparent 58%)
        val topR = maxOf(w, 1f) * 1.05f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (isDark) Color(0x1F00F2FF) else Color(0x240077FF),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, -h * 0.04f),
                radius = topR * 0.9f
            )
        )
        // radial-gradient(900px 600px at 100% 100%, purple .09 -> transparent 55%)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (isDark) Color(0x17A855F7) else Color(0x14A855F7),
                    Color.Transparent
                ),
                center = Offset(w, h),
                radius = maxOf(w, h) * 0.75f
            )
        )
        // 42px grid, fading downwards (body::before)
        val step = 42.dp.toPx()
        val gridColor = if (isDark) Color(0x0AFFFFFF) else Color(0x0A0F172A)
        var x = 0f
        while (x < w) { drawLine(gridColor, Offset(x, 0f), Offset(x, h * 0.85f), 1f); x += step }
        var y = 0f
        while (y < h * 0.85f) { drawLine(gridColor, Offset(0f, y), Offset(w, y), 1f); y += step }
    }
}

/** Sparkline (Chart.js line, tension .4, gradient fill) used at the bottom of sensor cards. */
@Composable
fun WebSparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val mn = values.min()
        val mx = values.max()
        val span = (mx - mn).let { if (it < 1e-6f) 1f else it }
        val padV = 4.dp.toPx()
        val h = size.height - padV * 2
        val dx = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(i * dx, padV + h - ((v - mn) / span) * h) }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]; val p1 = pts[i]
                val cx = (p0.x + p1.x) / 2f
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** sensor-icon-box: 36dp rounded-10 tile with subtle border. */
@Composable
fun WebIconBox(icon: ImageVector, tint: Color, isDark: Boolean, contentDescription: String? = null) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (isDark) Color(0x09FFFFFF) else Color(0x0F0F172A))
            .border(1.dp, webBorderSubtle(isDark), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * The website's Temperature / Humidity / Pressure card.
 * icon box | TITLE / value+unit / status / range, with an optional sparkline along the bottom.
 */
@Composable
fun WebSensorCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accent: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    status: String? = null,
    statusColor: Color? = null,
    range: String? = null,
    sparkValues: List<Float> = emptyList(),
    isWarning: Boolean = false,
    valueColor: Color? = null
) {
    val muted = webTextMuted(isDark)
    val st = statusColor ?: if (isWarning) WebRed else WebGreen
    Column(
        modifier = modifier
                        .webGlass(isDark, accent = if (isWarning) WebRed else null, glow = accent)
            .height(WebSensorCardHeight)
            .padding(start = 12.dp, end = 12.dp, top = 11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            WebIconBox(icon, accent, isDark, title)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title.uppercase(), fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        value, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp,
                        color = valueColor ?: webTextMain(isDark), maxLines = 1, softWrap = false
                    )
                    if (unit.isNotBlank() && value != "--") {
                        Text(
                            unit, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp,
                            color = if (isDark) WebCyanDark else WebCyanLight,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                if (status != null) {
                    Text(status, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = st, modifier = Modifier.padding(top = 1.dp), maxLines = 1)
                }
                if (range != null) {
                    Text(range, fontSize = 8.sp, color = muted.copy(alpha = 0.85f), modifier = Modifier.padding(top = 1.dp), maxLines = 1)
                }
            }
        }
        // Always reserve the same bottom area so every card has an identical height.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (sparkValues.size >= 2) {
                WebSparkline(values = sparkValues, color = accent, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Tracks the last [max] numeric readings for a sparkline. */
@Composable
fun rememberSparkHistory(latest: Float?, max: Int = 10): List<Float> {
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(latest) {
        if (latest != null) {
            history.add(latest)
            while (history.size > max) history.removeAt(0)
        }
    }
    return history
}
