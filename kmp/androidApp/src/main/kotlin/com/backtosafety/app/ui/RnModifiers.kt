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
