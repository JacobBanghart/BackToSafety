package com.backtosafety.app.onboarding

import androidx.compose.foundation.Image
import com.backtosafety.app.ui.TrackStepViewed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.udp
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.app.ui.style
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.Translate
import com.backtosafety.core.invoke

/** First onboarding step. Port of app/onboarding/index.tsx. */
@Composable
fun WelcomeScreen(
    t: Translate,
    themePreference: String,
    onThemeChange: (String) -> Unit,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TrackStepViewed("welcome")
    val colors = LocalAppColors.current
    val featureBg = if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color(DesignTokens.Neutral.c100)
    val optionBg = if (colors.isDark) Color.White.copy(alpha = 0.1f) else Color(DesignTokens.Neutral.c100)
    val scroll = rememberScrollState()
    val type = DesignTokens.Typography
    val space = DesignTokens.Spacing

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(start = space.xl.udp, end = space.xl.udp, top = space.xxxl.udp, bottom = space.xl.udp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = space.xxl.udp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Logo(Modifier.padding(bottom = space.xl.udp).size(180.udp))
                Text(
                    t("welcome.title"),
                    style = rnTextStyle(36f, 44f, fontWeight = 700, letterSpacing = type.display.letterSpacing),
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = space.lg.udp),
                )
                Text(
                    t("welcome.description"),
                    style = type.bodyLarge.style(),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                Modifier.padding(bottom = space.xxl.udp),
                verticalArrangement = Arrangement.spacedBy(space.lg.udp),
            ) {
                for ((key, icon) in listOf("timer" to "⏱️", "readout" to "📋", "privacy" to "🔒")) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
                            .background(featureBg)
                            .padding(space.lg.udp),
                        horizontalArrangement = Arrangement.spacedBy(16.udp),
                    ) {
                        Box(
                            Modifier.size(44.udp).clip(CircleShape).background(colors.primaryLight),
                            contentAlignment = Alignment.Center,
                        ) { Text(icon, style = rnTextStyle(22f, 24f)) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xs.udp)) {
                            Text(t("welcome.features.$key.title"), style = type.bodyBold.style(), color = colors.text)
                            Text(t("welcome.features.$key.description"), style = type.body.style(), color = colors.textSecondary)
                        }
                    }
                }
            }

            Column(Modifier.padding(bottom = space.xl.udp)) {
                Text(
                    t("welcome.themeLabel").uppercase(),
                    style = type.caption.style(fontWeight = 600, letterSpacing = 1f),
                    color = colors.textDisabled,
                    modifier = Modifier.padding(bottom = space.md.udp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
                    for ((value, label, icon) in listOf(
                        Triple("light", "welcome.themeOptions.light", "☀️"),
                        Triple("dark", "welcome.themeOptions.dark", "🌙"),
                        Triple("system", "welcome.themeOptions.auto", "📱"),
                    )) {
                        val selected = themePreference == value
                        Row(
                            Modifier
                                .weight(1f)
                                .testTag("onboarding-welcome-theme-$value")
                                .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
                                .background(if (selected) colors.primaryLight else optionBg)
                                .border(
                                    2.udp,
                                    if (selected) colors.tint else Color.Transparent,
                                    RoundedCornerShape(DesignTokens.Radius.lg.dp),
                                )
                                .clickable { onThemeChange(value) }
                                .padding(space.md.udp),
                            horizontalArrangement = Arrangement.spacedBy(8.udp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(icon, style = rnTextStyle(20f, 24f))
                            Text(
                                t(label),
                                style = type.bodyLarge.style().copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                                color = if (selected) colors.tint else colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(space.lg.udp), verticalArrangement = Arrangement.spacedBy(space.lg.udp)) {
            if (scroll.value <= 10) {
                Text(
                    t("welcome.scrollHint").uppercase(),
                    style = type.caption.style(fontWeight = 600, letterSpacing = 1f),
                    color = colors.textDisabled,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.udp)
                    .testTag("onboarding-get-started")
                    .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
                    .background(colors.primary)
                    .clickable(onClick = onGetStarted)
                    .padding(vertical = space.md.udp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t("welcome.getStarted"),
                    style = type.bodyLarge.style().copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textOnPrimary,
                )
            }
            Text(
                t("welcome.privacy"),
                style = type.caption.style(),
                color = colors.textDisabled,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Logo(modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember {
        context.assets.open("images/logo-full.png").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }
    Image(bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}
