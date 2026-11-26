package com.wifix.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifix.app.ui.theme.WiFiXColors
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

data class FloatingNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Rebuilt floating glassmorphism capsule dock:
 * - Zero translucent elevation shadow glitches on MIUI/Android
 * - Smooth spring-sliding active pill capsule
 * - Touch barrier preventing taps from bleeding through to underlying lists
 */
@Composable
fun FloatingGlassNavBar(
    items: List<FloatingNavItem>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }
    val hasActiveDockItem = selectedIndex >= 0

    val pillShape = RoundedCornerShape(34.dp)
    val activeCapsuleShape = RoundedCornerShape(28.dp)

    val dockBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xEE181E2C),
            Color(0xF50B0F19)
        )
    )
    val dockSpecularBorder = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.28f),
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = 0.16f)
        )
    )
    val activePillBg = Brush.verticalGradient(
        colors = listOf(
            WiFiXColors.AccentBlue.copy(alpha = 0.34f),
            WiFiXColors.AccentViolet.copy(alpha = 0.22f)
        )
    )
    val activePillBorder = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.36f),
            WiFiXColors.AccentBlueSoft.copy(alpha = 0.18f)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(pillShape)
                .background(dockBackground)
                .border(width = 1.dp, brush = dockSpecularBorder, shape = pillShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(5.dp)
        ) {
            val tabWidth = maxWidth / items.size
            val clampedIndex = selectedIndex.coerceAtLeast(0)

            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * clampedIndex,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = 380f
                ),
                label = "dock_pill_offset"
            )
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (hasActiveDockItem) 1f else 0f,
                animationSpec = tween(durationMillis = 180),
                label = "dock_pill_alpha"
            )

            // Sliding frosted selection capsule
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .alpha(indicatorAlpha)
                    .clip(activeCapsuleShape)
                    .background(activePillBg)
                    .border(0.8.dp, activePillBorder, activeCapsuleShape)
            )

            // Fixed-geometry tab row (constant font weight avoids horizontal layout jitter)
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = item.route == selectedRoute
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else WiFiXColors.TextSecondary,
                        animationSpec = tween(durationMillis = 180),
                        label = "dock_item_color"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(activeCapsuleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab
                            ) {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(item.route)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = contentColor,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = item.label,
                            color = contentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WiFiXBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WiFiXColors.CanvasCharcoal)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        WiFiXColors.AccentBlue.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.08f),
                    radius = size.width * 0.75f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        WiFiXColors.AccentViolet.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.90f, size.height * 0.35f),
                    radius = size.width * 0.70f
                )
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.22f),
            WiFiXColors.AccentBlue.copy(alpha = 0.14f),
            WiFiXColors.AccentViolet.copy(alpha = 0.12f)
        )
    )
    val surfaceBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.085f),
            Color.White.copy(alpha = 0.04f)
        )
    )

    Column(
        modifier = modifier
            .clip(shape)
            .background(surfaceBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun StatusPill(
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(dotColor.copy(alpha = 0.14f))
            .border(1.dp, dotColor.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = WiFiXColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Overhauled smooth logarithmic speedometer with spring physics, active radar halo,
 * calibration tick marks, glowing leading orb, and 60fps numeric interpolation.
 */
@Composable
fun SpeedGaugeMeter(
    speedMbps: Double,
    phaseLabel: String,
    progressFraction: Float,
    isRunning: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Logarithmic mapping so 1..500 Mbps all have crisp, legible needle movement
    val targetFraction = if (speedMbps <= 0.0) {
        if (isRunning) (progressFraction * 0.14f).coerceIn(0.03f, 0.14f) else 0f
    } else {
        (ln(1.0 + speedMbps) / ln(1.0 + 500.0)).toFloat().coerceIn(0f, 1f)
    }

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "gauge_sweep_fraction"
    )

    val animatedSpeed by animateFloatAsState(
        targetValue = speedMbps.toFloat().coerceAtLeast(0f),
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "gauge_speed_number"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "gauge_pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gauge_pulse_phase"
    )

    val activeSweepAngle = 250f * animatedFraction
    val startAngle = 145f

    Box(
        modifier = modifier.size(236.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerStroke = 14.dp.toPx()
            val ringRadius = (size.minDimension / 2f) - outerStroke - 8.dp.toPx()
            val arcTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
            val arcSize = Size(ringRadius * 2f, ringRadius * 2f)

            // Active pulsing ambient core glow while measuring
            if (isRunning) {
                val glowRadius = ringRadius * (0.78f + (pulsePhase * 0.16f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            WiFiXColors.AccentBlue.copy(alpha = 0.18f * (1f - pulsePhase * 0.4f)),
                            WiFiXColors.AccentViolet.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = center
                )
            }

            // Perimeter calibration ticks
            val tickCount = 30
            for (i in 0..tickCount) {
                val tickFraction = i.toFloat() / tickCount
                val deg = startAngle + (250f * tickFraction)
                val rad = deg * (PI / 180.0)
                val isMajor = i % 5 == 0
                val innerR = ringRadius - (if (isMajor) 19.dp.toPx() else 14.dp.toPx())
                val outerR = ringRadius - 9.dp.toPx()
                val lit = tickFraction <= animatedFraction && animatedFraction > 0.01f

                drawLine(
                    color = if (lit) {
                        WiFiXColors.AccentBlueSoft.copy(alpha = if (isMajor) 0.85f else 0.45f)
                    } else {
                        Color.White.copy(alpha = if (isMajor) 0.18f else 0.08f)
                    },
                    start = Offset(
                        x = center.x + (innerR * cos(rad)).toFloat(),
                        y = center.y + (innerR * sin(rad)).toFloat()
                    ),
                    end = Offset(
                        x = center.x + (outerR * cos(rad)).toFloat(),
                        y = center.y + (outerR * sin(rad)).toFloat()
                    ),
                    strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Recessed background track arc
            drawArc(
                color = Color.White.copy(alpha = 0.09f),
                startAngle = startAngle,
                sweepAngle = 250f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = outerStroke, cap = StrokeCap.Round)
            )

            // Soft outer halo behind active sweep
            if (activeSweepAngle > 1f) {
                drawArc(
                    color = WiFiXColors.AccentBlue.copy(alpha = 0.24f),
                    startAngle = startAngle,
                    sweepAngle = activeSweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = outerStroke + 8.dp.toPx(), cap = StrokeCap.Round)
                )

                // Main high-contrast gradient arc
                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            WiFiXColors.AccentBlue,
                            WiFiXColors.AccentViolet,
                            WiFiXColors.AccentBlueSoft
                        )
                    ),
                    startAngle = startAngle,
                    sweepAngle = activeSweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = outerStroke, cap = StrokeCap.Round)
                )

                // Leading-edge luminous tip orb
                val tipRad = (startAngle + activeSweepAngle) * (PI / 180.0)
                val tipCenter = Offset(
                    x = center.x + (ringRadius * cos(tipRad)).toFloat(),
                    y = center.y + (ringRadius * sin(tipRad)).toFloat()
                )
                drawCircle(
                    color = WiFiXColors.AccentBlueSoft.copy(alpha = 0.35f),
                    radius = 12.dp.toPx(),
                    center = tipCenter
                )
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = tipCenter
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = phaseLabel.uppercase(Locale.US),
                color = WiFiXColors.AccentBlueSoft,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.9.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format(Locale.US, "%.1f", animatedSpeed),
                color = WiFiXColors.TextPrimary,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Mbps  |  ${String.format(Locale.US, "%.2f", animatedSpeed / 8.0)} MB/s",
                color = WiFiXColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun LiveSparklineGraph(
    downloadHistoryMbps: List<Double>,
    uploadHistoryMbps: List<Double>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
    ) {
        val allValues = downloadHistoryMbps + uploadHistoryMbps
        val maxVal = (allValues.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val count = maxOf(downloadHistoryMbps.size, uploadHistoryMbps.size, 2)

        for (i in 1..2) {
            val y = size.height * (i / 3f)
            drawLine(
                color = Color.White.copy(alpha = 0.07f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        fun drawSeries(values: List<Double>, lineColor: Color, fillTop: Color) {
            if (values.size < 2) return
            val stepX = size.width / (count - 1).coerceAtLeast(1)
            val linePath = Path()
            val fillPath = Path()

            values.forEachIndexed { index, value ->
                val x = index * stepX
                val ratio = (value / maxVal).toFloat().coerceIn(0f, 1f)
                val y = size.height - (ratio * (size.height - 8f)) - 4f
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            fillPath.lineTo((values.size - 1) * stepX, size.height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(colors = listOf(fillTop, Color.Transparent))
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
        }

        drawSeries(
            values = uploadHistoryMbps,
            lineColor = WiFiXColors.AccentViolet,
            fillTop = WiFiXColors.AccentViolet.copy(alpha = 0.16f)
        )
        drawSeries(
            values = downloadHistoryMbps,
            lineColor = WiFiXColors.AccentBlue,
            fillTop = WiFiXColors.AccentBlue.copy(alpha = 0.24f)
        )
    }
}
