package com.notiz.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.notiz.app.resources.*
import com.notiz.app.ui.theme.Palette
import org.jetbrains.compose.resources.stringResource

/** Which placeholder layout to show while a screen loads; each mimics the real content. */
enum class SkeletonKind {
    /** Rows with a round avatar: members, residents list. */
    List,

    /** Stacked cards with a title and two lines: notices, polls, help desk, gate. */
    Cards,

    /** The dues hero card, then payment rows. */
    Dues,

    /** Chat bubbles on both sides. */
    Chat,

    /** Home below the band: the overlapping card, a tile grid, a notice card. */
    Home,

    /** Settings-style form: a few labelled blocks. */
    Form,
}

// One animation drives every block on screen, so they shimmer together.
private val LocalShimmer = compositionLocalOf<State<Float>?> { null }

/** A soft light band sweeping left to right across the block (or a plain block if no animation). */
private fun Modifier.shimmer(state: State<Float>?): Modifier = drawBehind {
    drawRect(Palette.chip)
    // Read the animation here (draw phase only), so blocks redraw without recomposing
    val progress = state?.value
    if (progress != null) {
        val band = size.width * 0.6f
        val x = -band + (size.width + band * 2) * progress
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                start = Offset(x - band / 2, 0f),
                end = Offset(x + band / 2, size.height),
            ),
        )
    }
}

/** One grey placeholder block. */
@Composable
fun Bone(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val state = LocalShimmer.current
    Box(modifier.clip(shape).shimmer(state))
}

@Composable
private fun Line(width: Float, height: Dp = 12.dp) = Bone(Modifier.fillMaxWidth(width).height(height), RoundedCornerShape(6.dp))

@Composable
private fun SkeletonCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun AvatarRow(lineWidth: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Bone(Modifier.size(40.dp), CircleShape)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Line(lineWidth, 13.dp)
            Line(lineWidth * 0.55f, 10.dp)
        }
    }
}

/**
 * Placeholder shaped like the screen that is loading, shimmering gently. Announced as
 * "Loading" to screen readers.
 */
@Composable
fun Skeleton(kind: SkeletonKind, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
    )
    val loading = stringResource(Res.string.loading)
    CompositionLocalProvider(LocalShimmer provides progress) {
        Column(
            modifier.fillMaxSize().padding(16.dp).semantics { contentDescription = loading },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (kind) {
                SkeletonKind.List -> SkeletonCard {
                    listOf(0.7f, 0.5f, 0.62f, 0.45f, 0.66f, 0.52f).forEach { AvatarRow(it) }
                }
                SkeletonKind.Cards -> repeat(3) { i ->
                    SkeletonCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Bone(Modifier.size(44.dp), RoundedCornerShape(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Line(if (i == 1) 0.5f else 0.7f, 14.dp)
                                Line(0.35f, 10.dp)
                            }
                        }
                        Line(0.95f)
                        Line(if (i == 2) 0.5f else 0.75f)
                    }
                }
                SkeletonKind.Dues -> {
                    SkeletonCard {
                        Line(0.4f, 12.dp)
                        Line(0.55f, 30.dp)
                        Line(0.35f, 12.dp)
                        Bone(Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(14.dp))
                    }
                    SkeletonCard { repeat(4) { AvatarRow(0.6f) } }
                }
                SkeletonKind.Chat -> {
                    listOf(false to 0.6f, false to 0.45f, true to 0.55f, false to 0.7f, true to 0.4f, true to 0.6f).forEach { (mine, w) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            if (!mine) {
                                Bone(Modifier.size(32.dp), CircleShape)
                                Box(Modifier.width(8.dp))
                            }
                            Bone(Modifier.fillMaxWidth(w).height(44.dp), RoundedCornerShape(18.dp))
                        }
                    }
                }
                SkeletonKind.Home -> {
                    SkeletonCard {
                        Line(0.45f, 12.dp)
                        Line(0.6f, 26.dp)
                        Bone(Modifier.fillMaxWidth().height(44.dp), RoundedCornerShape(14.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(2) {
                            Column(
                                Modifier.weight(1f).background(Color.White, RoundedCornerShape(20.dp)).padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Bone(Modifier.size(36.dp), RoundedCornerShape(10.dp))
                                Line(0.8f)
                                Line(0.5f, 10.dp)
                            }
                        }
                    }
                    SkeletonCard {
                        Line(0.6f, 14.dp)
                        Line(0.9f)
                        Line(0.7f)
                    }
                }
                SkeletonKind.Form -> repeat(3) {
                    SkeletonCard {
                        Line(0.3f, 10.dp)
                        Bone(Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(14.dp))
                        Line(0.6f, 10.dp)
                    }
                }
            }
        }
    }
}

/** Small spinning arc for buttons and photo tiles (instead of the standard Material spinner). */
@Composable
fun NotizSpinner(modifier: Modifier = Modifier.size(22.dp), color: Color = Palette.teal, strokeWidth: Dp = 2.5.dp) {
    val transition = rememberInfiniteTransition()
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
    )
    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        drawArc(
            color = color.copy(alpha = 0.2f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke),
        )
        drawArc(
            color = color,
            startAngle = angle - 90f, sweepAngle = 100f, useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}
