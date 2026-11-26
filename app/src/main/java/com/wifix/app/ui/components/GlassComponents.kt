package com.wifix.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifix.app.ui.theme.WiFiXColors
import java.util.Locale

data class FloatingNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Detached floating glassmorphism capsule navigation bar matching the translucent pill dock style.
 */
@Composable
fun FloatingGlassNavBar(
    items: List<FloatingNavItem>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(50)
    val dockBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xD91A1F2E),
            Color(0xEB0C101A)
        )
    )
    val dockSpecularBorder = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.34f),
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = 0.22f)
        )
    )
    val activePillBg = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.24f),
            Color.White.copy(alpha = 0.13f)
        )
    )
    val activePillBorder = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.30f),
            Color.White.copy(alpha = 0.08f)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 20.dp,
                    shape = pillShape,
                    ambientColor = Color.Black.copy(alpha = 0.7f),
                    spotColor = Color.Black.copy(alpha = 0.8f)
                )
                .clip(pillShape)
                .background(dockBackground)
                .border(width = 1.dp, brush = dockSpecularBorder, shape = pillShape)
                .padding(horizontal = 5.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item.route == selectedRoute
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(pillShape)
                        .then(
                            if (isSelected) {
                                Modifier
                                    .background(activePillBg)
                                    .border(0.75.dp, activePillBorder, pillShape)
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onSelect(item.route) }
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (isSelected) Color.White else WiFiXColors.TextSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        color = if (isSelected) Color.White else WiFiXColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
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

@Composable
fun SpeedGaugeMeter(
    speedMbps: Double,
    phaseLabel: String,
    progressFraction: Float,
    modifier: Modifier = Modifier
) {
    val normalizedSweep = ((speedMbps / 150.0).coerceIn(0.0, 1.0)).toFloat()
    val visualFraction = if (progressFraction > 0f) {
        maxOf(normalizedSweep, progressFraction * 0.2f).coerceIn(0f, 1f)
    } else {
        normalizedSweep
    }

    Box(
        modifier = modifier.size(216.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 15.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)

            // Track arc
            drawArc(
                color = Color.White.copy(alpha = 0.10f),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Active gradient arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        WiFiXColors.AccentBlue,
                        WiFiXColors.AccentViolet,
                        WiFiXColors.AccentBlueSoft
                    )
                ),
                startAngle = 140f,
                sweepAngle = 260f * visualFraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = phaseLabel.uppercase(Locale.US),
                color = WiFiXColors.AccentBlueSoft,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format(Locale.US, "%.1f", speedMbps),
                color = WiFiXColors.TextPrimary,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Mbps  |  ${String.format(Locale.US, "%.2f", speedMbps / 8.0)} MB/s",
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
            .height(88.dp)
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
