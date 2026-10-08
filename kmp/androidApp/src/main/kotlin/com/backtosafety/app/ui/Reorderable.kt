package com.backtosafety.app.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex

/**
 * utils/draggable-flatlist: long-press an item, drag it, and it swaps with each neighbour it
 * passes the middle of. [onReorder] gets the new order when the finger lifts. Lists here are
 * a handful of cards, so a plain Column (inside the screen's scroll) is enough.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onDragStart: () -> Unit = {},
    onReorder: (List<T>) -> Unit,
    itemContent: @Composable (item: T, index: Int, dragging: Boolean) -> Unit,
) {
    var order by remember(items) { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }

    Column {
        order.forEachIndexed { index, item ->
            val k = key(item)
            // key(): a swap must move this item's composition (and its in-flight gesture)
            // with it; positional slots would restart pointerInput and cancel the drag.
            key(k) {
            val dragging = k == draggingKey
            Box(
                Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) offset else 0f }
                    .onSizeChanged { heights[k] = it.height }
                    .pointerInput(k) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingKey = k
                                offset = 0f
                                onDragStart()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                offset += amount.y
                                val i = order.indexOfFirst { key(it) == k }
                                if (offset > 0 && i < order.lastIndex) {
                                    val next = heights[key(order[i + 1])] ?: return@detectDragGesturesAfterLongPress
                                    if (offset > next / 2f) {
                                        order = order.toMutableList().apply { add(i + 1, removeAt(i)) }
                                        offset -= next
                                    }
                                } else if (offset < 0 && i > 0) {
                                    val prev = heights[key(order[i - 1])] ?: return@detectDragGesturesAfterLongPress
                                    if (-offset > prev / 2f) {
                                        order = order.toMutableList().apply { add(i - 1, removeAt(i)) }
                                        offset += prev
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingKey = null
                                offset = 0f
                                if (order != items) onReorder(order)
                            },
                            onDragCancel = {
                                draggingKey = null
                                offset = 0f
                                order = items
                            },
                        )
                    },
            ) { itemContent(item, index, dragging) }
            }
        }
    }
}
