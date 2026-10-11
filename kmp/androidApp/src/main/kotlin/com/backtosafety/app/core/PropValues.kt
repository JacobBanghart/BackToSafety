package com.backtosafety.app.core

import com.backtosafety.core.ffi.PropValue

/** A Kotlin value as the core takes it in a property bag. A [PropValue] passes through. */
fun Any?.toPropValue(): PropValue = when (this) {
    null -> PropValue.Null
    is PropValue -> this
    is Boolean -> PropValue.Bool(this)
    is Int -> PropValue.Integer(this.toLong())
    is Long -> PropValue.Integer(this)
    is Short -> PropValue.Integer(this.toLong())
    is Float -> PropValue.Decimal(this.toDouble())
    is Double -> PropValue.Decimal(this)
    is String -> PropValue.Text(this)
    else -> PropValue.Text(toString())
}

/** A core property value as the Kotlin UI and the PostHog SDK take it. */
fun PropValue.toAny(): Any? = when (this) {
    PropValue.Null -> null
    is PropValue.Bool -> value
    is PropValue.Integer -> value
    is PropValue.Decimal -> value
    is PropValue.Text -> value
}

internal fun Map<String, Any?>.toPropValues(): Map<String, PropValue> = mapValues { it.value.toPropValue() }

internal fun Map<String, PropValue>.toAnys(): Map<String, Any?> = mapValues { it.value.toAny() }
