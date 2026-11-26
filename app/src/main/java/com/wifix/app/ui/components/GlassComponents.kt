package com.wifix.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifix.app.ui.theme.WiFiXColors

/**
 * Ambient charcoal backdrop with subtle blue/violet radial lighting for depth beneath glass surfaces.
 */
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
            fontWeight = FontWeight.Medium
        )
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
            .height(92.dp)
    ) {
        val allValues = downloadHistoryMbps + uploadHistoryMbps
        val maxVal = (allValues.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val count = downloadHistoryMbps.size.coerceAtLeast(2)

        // Subtle horizontal reference grid
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
                brush = Brush.verticalGradient(
                    colors = listOf(fillTop, Color.Transparent)
                )
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
