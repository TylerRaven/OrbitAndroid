package com.mangostudios.orbit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val Ease = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * A flash of the page's color when a page finishes opening — the Android take on the Mac's
 * PageGlowPulse with `fadesDown`. The light hugs the TOP edge and melts away partway down the
 * sides; it never rings the whole page. Rises in 0.35s, eases back, then fades over ~2.2s.
 */
@Composable
fun PageGlow(trigger: Int, hue: Double, cornerRadius: Dp, modifier: Modifier = Modifier) {
    val v = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        v.snapTo(0f)
        v.animateTo(1f, tween(350, easing = Ease))
        v.animateTo(0.75f, tween(300, easing = Ease))
        v.animateTo(0f, tween(2200, easing = Ease))
    }
    val color = Hue.color(hue, saturation = 0.7)

    Canvas(
        modifier
            .fillMaxSize()
            // Offscreen so the fade-down mask below only cuts the glow, not the page.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val p = v.value
        if (p <= 0.001f) return@Canvas
        val w = size.width
        val h = size.height
        val r = cornerRadius.toPx()

        // The blooms stay inside the page's rounded corners; only the rim sits on the edge.
        val inside = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r))) }
        clipPath(inside) {
            // Soft bloom spilling down from the top edge.
            val bloom = (34.dp.toPx() + 46.dp.toPx() * p)
            drawRect(
                Brush.verticalGradient(
                    0f to color.copy(alpha = 0.55f * p),
                    0.35f to color.copy(alpha = 0.22f * p),
                    1f to Color.Transparent,
                    endY = bloom,
                ),
                size = Size(w, bloom),
            )

            // Light along each side, inward from the edge; the mask fades it out going down.
            val side = 14.dp.toPx() + 14.dp.toPx() * p
            drawRect(
                Brush.horizontalGradient(0f to color.copy(alpha = 0.45f * p), 1f to Color.Transparent, endX = side),
                size = Size(side, h),
            )
            drawRect(
                Brush.horizontalGradient(0f to Color.Transparent, 1f to color.copy(alpha = 0.45f * p), startX = w - side, endX = w),
                topLeft = Offset(w - side, 0f),
                size = Size(side, h),
            )
        }

        // A crisp rim: white-hot at the top, the page color lower down.
        val line = 1.5.dp.toPx()
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), color), endY = h * 0.4f),
            topLeft = Offset(line / 2, line / 2),
            size = Size(w - line, h - line),
            cornerRadius = CornerRadius(r),
            style = Stroke(line),
            alpha = 0.75f * p,
        )
        drawRoundRect(
            color.copy(alpha = 0.35f * p),
            topLeft = Offset(line * 2, line * 2),
            size = Size(w - line * 4, h - line * 4),
            cornerRadius = CornerRadius(r),
            style = Stroke(line * 3),
        )

        // Fade everything out down the sides: full at the top, half by 18%, gone by half-way.
        drawRect(
            Brush.verticalGradient(
                0f to Color.White,
                0.18f to Color.White.copy(alpha = 0.5f),
                0.5f to Color.Transparent,
                endY = h,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}
