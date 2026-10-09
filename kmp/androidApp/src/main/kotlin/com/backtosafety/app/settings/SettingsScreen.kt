package com.backtosafety.app.settings

import android.app.AlertDialog
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.Analytics
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.backtosafety.app.BuildConfig
import com.backtosafety.app.ui.Icon
import com.backtosafety.app.ui.udp
import com.backtosafety.app.ui.AppCard
import com.backtosafety.app.ui.ListItem
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.ScreenHeader
import com.backtosafety.app.ui.rnBorder
import com.backtosafety.app.ui.rnLineHeight
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.app.ui.rnTextStyleNatural
import com.backtosafety.app.ui.style
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.Translate
import com.backtosafety.core.data.Store
import com.backtosafety.core.invoke
import kotlinx.coroutines.launch

private const val TAPS_TO_UNLOCK = 7
private const val SCHEMA_VERSION = 2

/** Port of app/settings.tsx. */
@Composable
fun SettingsScreen(
    t: Translate,
    tCommon: Translate,
    store: Store,
    themePreference: String,
    onThemeChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val space = DesignTokens.Spacing
    var devMode by remember { mutableStateOf(BuildConfig.DEBUG) }
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var deviceId by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        Analytics.screen("settings")
        deviceId = runCatching { store.deviceId() }.getOrNull()
    }

    fun onVersionTap() {
        val now = System.currentTimeMillis()
        if (now - lastTap > 1000) {
            tapCount = 1
        } else {
            tapCount += 1
            if (tapCount >= TAPS_TO_UNLOCK && !devMode) {
                devMode = true
                Analytics.track(AnalyticsEvent.SETTINGS_DEV_MODE_UNLOCKED)
                AlertDialog.Builder(context).setTitle(t("devModeAlert.title")).setMessage(t("devModeAlert.message"))
                    .setPositiveButton("OK", null).show()
            }
        }
        lastTap = now
    }

    fun deleteAccount() {
        AlertDialog.Builder(context)
            .setTitle(t("deleteAccountModal.title"))
            .setMessage(t("deleteAccountModal.message"))
            .setNegativeButton(t("deleteAccountModal.cancel"), null)
            .setPositiveButton(t("deleteAccountModal.confirm")) { _, _ ->
                deleting = true
                scope.launch {
                    runCatching { store.clearAllData() }
                        .onSuccess {
                            Analytics.track(AnalyticsEvent.SETTINGS_ACCOUNT_DELETED)
                            onDeleted()
                        }
                        .onFailure {
                            AlertDialog.Builder(context).setTitle(tCommon("error")).setMessage(t("deleteAccountError"))
                                .setPositiveButton("OK", null).show()
                        }
                    deleting = false
                }
            }
            .show()
    }

    Column(
        Modifier.fillMaxSize().background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        ScreenHeader(t("screenTitle"), "settings", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(space.xl.udp),
            verticalArrangement = Arrangement.spacedBy(space.lg.udp),
        ) {
            AppCard {
                SectionHeader("paintbrush.fill", colors.text, t("sections.appearance.title"))
                Description(t("sections.appearance.description"))
                Row(horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
                    for ((value, icon) in listOf("system" to "📱", "light" to "☀️", "dark" to "🌙")) {
                        Option(t("themeOptions.$value"), "settings-theme-$value", themePreference == value, icon) {
                            onThemeChange(value)
                        }
                    }
                }
            }

            AppCard {
                SectionHeader("trash.fill", Color(DesignTokens.Semantic.error), t("sections.deleteAccount.title"))
                Description(t("sections.deleteAccount.description"))
                val shape = RoundedCornerShape(DesignTokens.Radius.md.dp)
                Row(
                    Modifier.fillMaxWidth().testTag("settings-delete-account").alpha(if (deleting) 0.6f else 1f).clip(shape)
                        .clickable(enabled = !deleting, onClick = ::deleteAccount)
                        .background(Color(DesignTokens.Semantic.error)).padding(vertical = 14.udp, horizontal = 20.udp),
                    horizontalArrangement = Arrangement.spacedBy(space.sm.udp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val white = Color(DesignTokens.Light.textOnPrimary)
                    Icon("trash.fill", 18f, white)
                    Text(
                        t(if (deleting) "sections.deleteAccount.deletingButton" else "sections.deleteAccount.button"),
                        style = DesignTokens.Typography.bodyBold.style(), color = white,
                    )
                }
            }

            if (devMode) {
                AppCard {
                    SectionHeader("wrench.fill", colors.text, t("sections.devTools.title"))
                    Description(t("sections.devTools.description"))
                    SectionHeader("globe", colors.text, t("languageSection.title"))
                    Description(t("languageSection.description"))
                    Row(horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
                        for ((lang, label) in listOf("en" to "English", "es" to "Español")) {
                            Option(label, "settings-language-$lang", language == lang) { onLanguageChange(lang) }
                        }
                    }
                }
            }

            AppCard {
                Text(t("sections.about.title"), style = rnTextStyleNatural(20f, 700), color = colors.text)
                ListItem(t("sections.about.app"), context.applicationInfo.loadLabel(context.packageManager).toString())
                ListItem(
                    t("sections.about.version"),
                    versionLabel() + if (devMode) t("sections.about.devSuffix") else "",
                    testID = "settings-version", onPress = ::onVersionTap,
                )
                ListItem(t("sections.about.platform"), "android")
                ListItem(t("sections.about.theme"), if (colors.isDark) "dark" else "light")
                if (devMode) ListItem(t("sections.about.dbSchema"), SCHEMA_VERSION.toString())
                ListItem(
                    t("sections.about.deviceId"), deviceId ?: "—", testID = "settings-device-id",
                    ruleColor = Color.Transparent,
                ) {
                    deviceId?.let {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(null, it))
                        AlertDialog.Builder(context).setTitle(tCommon("copied")).setMessage(t("deviceIdCopied"))
                            .setPositiveButton("OK", null).show()
                    }
                }
            }
        }
    }
}

/** utils/appInfo.ts: "version (build)", or just the version when they're the same. */
private fun versionLabel(): String {
    val version = BuildConfig.VERSION_NAME
    val build = BuildConfig.VERSION_CODE.toString()
    return if (version.startsWith("internal-") || build == version) version else "$version ($build)"
}

@Composable
private fun SectionHeader(icon: String, iconColor: Color, title: String) {
    Row(
        Modifier.padding(bottom = DesignTokens.Spacing.sm.udp),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.udp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 20f, iconColor)
        // ThemedText type="subtitle": 20 bold with no line height of its own.
        Text(title, style = rnTextStyleNatural(20f, 700), color = LocalAppColors.current.text)
    }
}

@Composable
private fun Description(text: String) {
    Text(
        text, style = rnTextStyle(16f, 24f), color = LocalAppColors.current.textSecondary,
        modifier = Modifier.padding(bottom = DesignTokens.Spacing.lg.udp),
    )
}

/** A theme or language choice: 2dp border, tint and primaryLight when selected. */
@Composable
private fun RowScope.Option(label: String, testID: String, selected: Boolean, icon: String? = null, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Column(
        Modifier.weight(1f).testTag(testID).clip(shape).clickable(onClick = onClick)
            .background(if (selected) colors.primaryLight else Color.Transparent)
            .rnBorder(2.udp, if (selected) colors.tint else colors.border, shape)
            .padding(vertical = DesignTokens.Spacing.lg.udp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // themeIcon: fontSize 24 over ThemedText's 24 line height.
        icon?.let {
            Text(it, style = rnTextStyle(24f, 24f), modifier = Modifier.padding(bottom = DesignTokens.Spacing.xs.udp).rnLineHeight(24f))
        }
        Text(
            label, style = rnTextStyle(14f, 24f, if (selected) 600 else 400),
            color = if (selected) colors.tint else colors.text,
        )
    }
}
