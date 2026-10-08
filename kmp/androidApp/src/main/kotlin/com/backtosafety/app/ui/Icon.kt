package com.backtosafety.app.ui

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * IconSymbol on Android: the SF Symbol name maps to a Material Icons name (spec/icons.json),
 * drawn from the same MaterialIcons.ttf the RN app uses, so icons match glyph for glyph.
 */
@Composable
fun Icon(name: String, size: Float, color: Color, modifier: Modifier = Modifier) {
    val icons = rememberIcons()
    val glyph = icons.glyph(name) ?: return
    Text(
        glyph,
        modifier = modifier,
        color = color,
        // A vector-icons glyph is RN text: its size rounds up to whole pixels too.
        style = with(LocalDensity.current) { ceilPx(size.sp.toPx()).toSp() }.let {
            TextStyle(fontFamily = icons.font, fontSize = it, lineHeight = it, textMotion = TextMotion.Animated)
        },
    )
}

class IconSet(private val mapping: Map<String, String>, private val glyphs: Map<String, Int>, val font: FontFamily) {
    fun glyph(name: String): String? =
        mapping[name]?.let(glyphs::get)?.let { String(Character.toChars(it)) }
}

@Composable
private fun rememberIcons(): IconSet {
    val context = LocalContext.current
    return remember { iconSet ?: loadIcons(context).also { iconSet = it } }
}

private var iconSet: IconSet? = null

private fun loadIcons(context: Context): IconSet {
    fun json(path: String) = Json.parseToJsonElement(context.assets.open(path).bufferedReader().readText()).jsonObject
    val mapping = json("icons/icons.json")["mapping"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
    val glyphs = json("icons/MaterialIcons.json").mapValues { it.value.jsonPrimitive.int }
    return IconSet(mapping, glyphs, FontFamily(Font("icons/MaterialIcons.ttf", context.assets)))
}
