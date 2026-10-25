package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CommandNavyDark

/**
 * "Liquid Glass" look shared by every card in the app (light + dark).
 *
 * PERFORMANCE (120 Hz): nothing here blurs, shadows or clips per frame. The soft shadow / coloured
 * halo is rendered ONCE into a bitmap (cached in [HaloCache], shared between equal-sized cards) and
 * then drawn as a plain image, so scrolling stays cheap on 90/120 Hz panels.
 */

/** Height the floating dock covers at the bottom; scroll screens add it to their bottom padding so the last item can scroll clear of the dock. */
val LocalDockInset = compositionLocalOf<Dp> { 0.dp }

val WebCardShape = RoundedCornerShape(24.dp)
/** One fixed height for every sensor card so neighbours always line up. */
val WebSensorCardHeight = 112.dp

// ---- colour tokens (names kept from the old theme so the rest of the app compiles unchanged) ----
val WebCyanDark = Color(0xFF5B9BFF)     // main accent, dark
val WebCyanLight = Color(0xFF2F6FD6)    // main accent, light
val WebGreen = Color(0xFF10B981)
val WebRed = Color(0xFFFF4D5E)
val WebAmber = Color(0xFFF59E0B)
val WebPurple = Color(0xFFA855F7)

fun webTextMain(isDark: Boolean) = if (isDark) Color(0xFFF5F5F8) else Color(0xFF111118)
fun webTextMuted(isDark: Boolean) = if (isDark) Color(0xFF9A9AAB) else Color(0xFF5B5B6B)
fun webBorder(isDark: Boolean) = if (isDark) Color(0x24FFFFFF) else Color(0x1F3A3354)
fun webBorderSubtle(isDark: Boolean) = if (isDark) Color(0x14FFFFFF) else Color(0x143A3354)

/** Glass edge: bright rim on top, fading to a soft outline at the bottom. */
fun webBorderBrush(isDark: Boolean): Brush = if (isDark) {
    Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF)))
} else {
    Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0x2E3A3354)))
}

/** Frosted glass fill. */
fun webCardBrush(isDark: Boolean): Brush = if (isDark) {
    Brush.linearGradient(listOf(Color(0xCC26262F), Color(0xB31B1B23)))
} else {
    Brush.linearGradient(listOf(Color(0xFAFFFFFF), Color(0xEDF8F6FC)))
}

/** Subtle shine overlay (kept for callers that want it). */
fun webShineBrush(isDark: Boolean): Brush = if (isDark) {
    Brush.linearGradient(0f to Color(0x14FFFFFF), 0.5f to Color(0x04FFFFFF), 1f to Color(0x00FFFFFF))
} else {
    Brush.linearGradient(listOf(Color(0x99FFFFFF), Color(0x33FFFFFF)))
}

/** Kept for callers that only want a brush for a card background. */
fun webGlassBrush(isDark: Boolean): Brush = webCardBrush(isDark)

// ---------------------------------------------------------------------------------------------
// Cached halo (soft drop shadow + optional coloured glow)
// ---------------------------------------------------------------------------------------------
private data class HaloKey(
    val w: Int, val h: Int, val r: Int, val pad: Int,
    val shadow: Int, val glow: Int, val shadowBlur: Int, val shadowDy: Int, val glowBlur: Int
)

private object HaloCache {
    private val cache = object : LruCache<HaloKey, androidx.compose.ui.graphics.ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: HaloKey, value: androidx.compose.ui.graphics.ImageBitmap) = value.width * value.height * 4
    }
    fun get(key: HaloKey, build: () -> androidx.compose.ui.graphics.ImageBitmap) =
        cache.get(key) ?: build().also { cache.put(key, it) }
}

/**
 * Soft drop shadow ([shadowColor]) and/or coloured glow ([glowColor]) around a rounded card.
 * Both are drawn outside the card outline only (the glass fill is translucent). The parent should
 * leave ~[extent] dp of room around the card or the halo is clipped at the list edge.
 */
fun Modifier.webHalo(
    shadowColor: Color?,
    glowColor: Color?,
    cornerRadius: Dp = 24.dp,
    shadowBlur: Dp = 12.dp,
    shadowDy: Dp = 4.dp,
    glowBlur: Dp = 8.dp
): Modifier = this.drawWithCache {
    val w = size.width.toInt()
    val h = size.height.toInt()
    if (w <= 0 || h <= 0 || (shadowColor == null && glowColor == null)) {
        return@drawWithCache onDrawBehind { }
    }
    val sb = shadowBlur.toPx(); val sdy = shadowDy.toPx(); val gb = glowBlur.toPx()
    val r = cornerRadius.toPx()
    val pad = (maxOf(if (shadowColor != null) sb + sdy else 0f, if (glowColor != null) gb else 0f) + 2f).toInt()
    val key = HaloKey(
        w, h, r.toInt(), pad,
        shadowColor?.toArgb() ?: 0, glowColor?.toArgb() ?: 0, sb.toInt(), sdy.toInt(), gb.toInt()
    )
    val image = HaloCache.get(key) {
        val bmp = Bitmap.createBitmap(w + pad * 2, h + pad * 2, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (shadowColor != null) {
            p.color = shadowColor.toArgb()
            p.maskFilter = BlurMaskFilter(sb.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            c.drawRoundRect(RectF(pad.toFloat(), pad + sdy, pad + w.toFloat(), pad + sdy + h), r, r, p)
        }
        if (glowColor != null) {
            p.color = glowColor.toArgb()
            p.maskFilter = BlurMaskFilter(gb.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            c.drawRoundRect(RectF(pad.toFloat(), pad.toFloat(), pad + w.toFloat(), pad + h.toFloat()), r, r, p)
        }
        // punch out the card area so the translucent glass is not tinted by its own shadow
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        c.drawRoundRect(RectF(pad.toFloat(), pad.toFloat(), pad + w.toFloat(), pad + h.toFloat()), r, r, clear)
        bmp.asImageBitmap()
    }
    onDrawBehind { drawImage(image, topLeft = Offset(-pad.toFloat(), -pad.toFloat())) }
}

/** Old name kept so nothing else breaks: a coloured glow halo (no drop shadow). */
fun Modifier.webNeonGlow(
    color: Color,
    isDark: Boolean,
    extent: Dp = 8.dp,
    cornerRadius: Dp = 24.dp
): Modifier = webHalo(
    shadowColor = null,
    glowColor = color.copy(alpha = if (isDark) 0.38f else 0.28f),
    cornerRadius = cornerRadius,
    glowBlur = extent
)

/**
 * Liquid-glass card: translucent frosted fill, glass rim border, soft shadow, optional coloured halo.
 * [accent] (e.g. red) switches the card to an alert look; [glow] is a gentle sensor-colour halo.
 */
fun Modifier.webGlass(
    isDark: Boolean,
    shape: Shape = WebCardShape,
    accent: Color? = null,
    topLine: Boolean = true,
    glow: Color? = null,
    cornerRadius: Dp = 24.dp
): Modifier {
    val neon = accent ?: glow
    val border: Brush = when {
        accent != null -> SolidColor(accent.copy(alpha = 0.65f))
        glow != null -> Brush.verticalGradient(listOf(glow.copy(alpha = 0.55f), glow.copy(alpha = 0.20f)))
        else -> webBorderBrush(isDark)
    }
    return this
        .webHalo(
            shadowColor = if (isDark) Color(0x66000000) else Color(0x2A3A3354),
            glowColor = neon?.copy(alpha = if (isDark) 0.34f else 0.24f),
            cornerRadius = cornerRadius
        )
        .clip(shape)
        .background(webCardBrush(isDark))
        .let { m -> if (accent != null) m.background(accent.copy(alpha = if (isDark) 0.12f else 0.08f)) else m }
        .border(1.dp, border, shape)
        .drawBehind {
            if (topLine) {
                val inset = 20.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, (accent ?: if (isDark) Color.White else Color.White).copy(alpha = if (isDark) 0.30f else 0.9f), Color.Transparent),
                        startX = inset, endX = size.width - inset
                    ),
                    topLeft = Offset(inset, 0.5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, 1.dp.toPx())
                )
            }
        }
}

/** Page background: soft off-white (light) / near-black (dark) with two gentle colour blooms the glass picks up. */
@Composable
fun WebPageBackground(isDark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(if (isDark) Color(0xFF0B0B10) else Color(0xFFF4F1F7))
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(if (isDark) Color(0x375B9BFF) else Color(0x302F6FD6), Color.Transparent),
                center = Offset(w * 0.12f, h * 0.02f),
                radius = maxOf(w, 1f) * 0.95f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(if (isDark) Color(0x2BA855F7) else Color(0x30B58CFF), Color.Transparent),
                center = Offset(w * 0.95f, h * 0.98f),
                radius = maxOf(w, h) * 0.7f
            )
        )
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
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = if (isDark) 0.16f else 0.12f))
            .border(1.dp, webBorderSubtle(isDark), RoundedCornerShape(12.dp)),
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
