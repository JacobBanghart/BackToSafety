package com.backtosafety.app.settings

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.backtosafety.app.BuildConfig
import com.backtosafety.app.ui.AppCard
import com.backtosafety.app.ui.DetailRow
import com.backtosafety.app.ui.Icon
import com.backtosafety.app.ui.ListItem
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.ScreenHeader
import com.backtosafety.app.ui.reportSaveFailed
import com.backtosafety.app.ui.rnBorder
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.app.ui.rnTextStyleNatural
import com.backtosafety.app.ui.runCatchingCancellable
import com.backtosafety.app.ui.showAlert
import com.backtosafety.app.ui.style
import com.backtosafety.app.ui.udp
import com.backtosafety.core.Analytics
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.Translate
import com.backtosafety.core.abbreviateId
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.DATABASE_VERSION
import com.backtosafety.core.invoke
import kotlinx.coroutines.launch

private const val TAPS_TO_UNLOCK = 7

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
        deviceId = runCatchingCancellable { store.deviceId() }.getOrNull()
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
                showAlert(context, t("devModeAlert.title"), t("devModeAlert.message"), confirm = tCommon("ok") to {})
            }
        }
        lastTap = now
    }

    fun deleteAccount() {
        showAlert(
            context, t("deleteAccountModal.title"), t("deleteAccountModal.message"),
            cancel = t("deleteAccountModal.cancel"),
            confirm = t("deleteAccountModal.confirm") to {
                deleting = true
                scope.launch {
                    runCatchingCancellable { store.clearAllData() }
                        .onSuccess {
                            Analytics.track(AnalyticsEvent.SETTINGS_ACCOUNT_DELETED)
                            Analytics.reset() // the deleted device ID is no longer who this is
                            onDeleted()
                        }
                        .onFailure { e ->
                            reportSaveFailed("settings", "delete_account", e)
                            showAlert(context, tCommon("error"), t("deleteAccountError"), confirm = tCommon("ok") to {})
                        }
                    deleting = false
                }
            },
        )
    }

    Column(
        Modifier.fillMaxSize().background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        ScreenHeader(t("screenTitle"), "settings", onBack)
        Column(
            // The 16dp margin of every other screen, so the cards line up with the header's action.
            Modifier.weight(1f).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(space.lg.udp),
            verticalArrangement = Arrangement.spacedBy(space.lg.udp),
        ) {
            AppCard {
                SectionHeader("paintbrush.fill", colors.text, t("sections.appearance.title"))
                Description(t("sections.appearance.description"))
                Row(horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
                    for ((value, icon) in listOf("system" to "iphone", "light" to "sun.max.fill", "dark" to "moon.fill")) {
                        Option(t("themeOptions.$value"), "settings-theme-$value", themePreference == value, icon) {
                            onThemeChange(value)
                        }
                    }
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
                    testID = "settings-version", ruleColor = Color.Transparent, onPress = ::onVersionTap,
                )
            }

            // Platform, theme and the device ID are for support, not for the caregiver: under
            // About, outside any card, in small type with one line each, so they read as
            // secondary. The values are words from the strings ("Android", "Dark"), not raw keys.
            Column(Modifier.fillMaxWidth().padding(horizontal = (space.lg + 1).udp)) {
                Text(
                    t("sections.about.diagnostics").uppercase(),
                    style = DesignTokens.Typography.caption.style(fontWeight = 600, letterSpacing = 0.8f),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = space.xs.udp).semantics { heading() },
                )
                DetailRow(t("sections.about.platform"), t("sections.about.platformAndroid"))
                DetailRow(t("sections.about.theme"), t(if (colors.isDark) "themeOptions.dark" else "themeOptions.light"))
                if (devMode) DetailRow(t("sections.about.dbSchema"), DATABASE_VERSION.toString())
                DetailRow(
                    // Shortened to one line with a copy icon: the whole UUID wrapped mid-string at large
                    // text, and support needs it pasted, not read out. The copy is the full ID.
                    t("sections.about.deviceId"), deviceId?.let(::abbreviateId) ?: "—", testID = "settings-device-id",
                    action = "doc.on.clipboard.fill" to t("sections.about.copyDeviceId"),
                ) {
                    deviceId?.let {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(null, it))
                        showAlert(context, tCommon("copied"), t("deviceIdCopied"), confirm = tCommon("ok") to {})
                    }
                }
            }

            // The danger zone sits last, under About, and is outlined rather than a solid red
            // block: deleting everything is rare and final, so it shouldn't read as prominently
            // as a routine option. A rule with space either side sets it apart from the support
            // details, so it doesn't read as one of them. Its confirmation dialog is unchanged.
            // The card is washed and edged in red and the button has a 1.5 red outline, so the
            // warning is carried by more than a faint outline and icon.
            Box(Modifier.padding(vertical = space.sm.udp).fillMaxWidth().height(1.udp).background(colors.border))
            AppCard(danger = true) {
                SectionHeader("trash.fill", colors.error, t("sections.deleteAccount.title"))
                Description(t("sections.deleteAccount.description"))
                val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.udp).testTag("settings-delete-account").alpha(if (deleting) 0.6f else 1f)
                        .clip(shape).clickable(enabled = !deleting, role = Role.Button, onClick = ::deleteAccount)
                        .background(colors.card).rnBorder(1.5.dp, colors.error, shape).padding(vertical = space.md.udp, horizontal = 20.udp),
                    horizontalArrangement = Arrangement.spacedBy(space.sm.udp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon("trash.fill", 18f, colors.error)
                    Text(
                        t(if (deleting) "sections.deleteAccount.deletingButton" else "sections.deleteAccount.button"),
                        style = DesignTokens.Typography.bodyBold.style(), color = colors.error,
                    )
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

/** A theme or language choice: 2dp border, tint and primaryLight when selected, the label in primaryText (tint on primaryLight is under 4.5:1). */
@Composable
private fun RowScope.Option(label: String, testID: String, selected: Boolean, icon: String? = null, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Column(
        Modifier.weight(1f).testTag(testID).clip(shape).selectable(selected, role = Role.RadioButton, onClick = onClick)
            .background(if (selected) colors.primaryLight else Color.Transparent)
            .rnBorder(2.udp, if (selected) colors.tint else colors.border, shape)
            .padding(vertical = DesignTokens.Spacing.lg.udp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A 24dp symbol, tinted like the label.
        icon?.let {
            Icon(it, 24f, if (selected) colors.primaryText else colors.textSecondary, Modifier.padding(bottom = DesignTokens.Spacing.xs.udp))
        }
        Text(
            label, style = rnTextStyle(14f, 24f, if (selected) 600 else 400),
            color = if (selected) colors.primaryText else colors.text,
        )
    }
}
