package com.backtosafety.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** An RN border: drawn like Compose's, but it also takes up space (box-sizing: border-box). */
fun Modifier.rnBorder(width: Dp, color: Color, shape: Shape): Modifier =
    if (width == 0.dp) this else border(width, color, shape).padding(width)

/** An RN negative marginTop: the element is pulled up and takes that much less height. */
fun Modifier.negativeTopMargin(margin: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val px = margin.roundToPx()
    layout(placeable.width, (placeable.height - px).coerceAtLeast(0)) { placeable.place(0, -px) }
}

/** StyleSheet.hairlineWidth: one physical pixel. */
val hairline: Dp
    @androidx.compose.runtime.Composable get() = with(androidx.compose.ui.platform.LocalDensity.current) { 1.toDp() }

/**
 * A layout size in dp that rounds to pixels half-to-even. Yoga rounds each node's absolute
 * position once, so RN's pixel errors never add up; Compose rounds every padding, gap and
 * size separately, half up. At 420dpi the spacing tokens land on exactly .5 px (4dp = 10.5px,
 * 12dp = 31.5px, 44dp = 115.5px), so every one rounded up and long screens drifted further
 * down with each element. Half-to-even rounds about as many down as up.
 */
val Float.udp: Dp
    @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = with(androidx.compose.ui.platform.LocalDensity.current) {
        (Math.rint((this@udp * density).toDouble()).toFloat() / density).dp
    }

val Int.udp: Dp
    @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = toFloat().udp

/** onboarding_step_viewed when a step's screen appears (each onboarding screen's mount effect). */
@androidx.compose.runtime.Composable
fun TrackStepViewed(step: String) = androidx.compose.runtime.LaunchedEffect(Unit) {
    com.backtosafety.core.Analytics.track(com.backtosafety.core.AnalyticsEvent.ONBOARDING_STEP_VIEWED, mapOf("step" to step))
}

/** onboarding_step_completed / _skipped. */
fun trackStep(completed: Boolean, step: String) = com.backtosafety.core.Analytics.track(
    if (completed) com.backtosafety.core.AnalyticsEvent.ONBOARDING_STEP_COMPLETED else com.backtosafety.core.AnalyticsEvent.ONBOARDING_STEP_SKIPPED,
    mapOf("step" to step),
)
