package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiUiState
import kotlin.math.roundToInt

private val FabBlue = Color(0xFF2F6FD6)
private val FabCyan = Color(0xFF6FA8FF)
private val FabInk = Color(0xFFFFFFFF)

/**
 * The website's floating "Ask SafeGuard" pill (robot icon + label, blue→cyan gradient).
 *
 * Place it inside a full-screen Box with `Modifier.align(Alignment.BottomEnd)` (plus any
 * padding) as [modifier]. Tap = open chat. It can also be dragged, so if it ever covers
 * something (e.g. the map's bottom panel) the user can move it out of the way; the new
 * position is shared by all screens via [AiUiState].
 *
 * @param containerSize size (px) of the full-screen Box the button floats in - used to keep it on screen.
 */
@Composable
fun AskSafeGuardFab(
    containerSize: IntSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var fabSize by remember { mutableStateOf(IntSize.Zero) }

    val sideMarginPx = with(density) { 16.dp.toPx() }
    val bottomMarginPx = with(density) { 16.dp.toPx() }
    // keep clear of the status bar + the top pill bar
    val topReservedPx = with(density) { 150.dp.toPx() }

    val travelX = (containerSize.width - fabSize.width - 2f * sideMarginPx).coerceAtLeast(0f)
    val travelY = (containerSize.height - fabSize.height - bottomMarginPx - topReservedPx).coerceAtLeast(0f)
    val minX = -travelX
    val minY = -travelY

    val shape = RoundedCornerShape(50)

    Row(
        modifier = modifier
            .offset {
                IntOffset(
                    AiUiState.fabX.coerceIn(minX, 0f).roundToInt(),
                    AiUiState.fabY.coerceIn(minY, 0f).roundToInt()
                )
            }
            .onSizeChanged { fabSize = it }
            // cached glow bitmap instead of a per-frame shadow pass (smooth at 120 Hz)
            .webHalo(shadowColor = null, glowColor = FabBlue.copy(alpha = 0.55f), cornerRadius = 40.dp, glowBlur = 10.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(FabBlue, FabCyan)))
            .pointerInput(minX, minY) {
                detectDragGestures { change, drag ->
                    change.consume()
                    AiUiState.fabX = (AiUiState.fabX + drag.x).coerceIn(minX, 0f)
                    AiUiState.fabY = (AiUiState.fabY + drag.y).coerceIn(minY, 0f)
                }
            }
            .clickable(
                onClickLabel = "Open Ask SafeGuard chat",
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = null,
            tint = FabInk,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "Ask SafeGuard",
            color = FabInk,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}
