package com.backtosafety.app.home

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.backtosafety.app.ui.Icon
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.rnBorder
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.app.ui.style
import com.backtosafety.app.ui.udp
import com.backtosafety.core.ActiveEmergency
import com.backtosafety.core.Analytics
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.AppClock
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.Profile
import com.backtosafety.core.SEARCH_WINDOW_SECONDS
import com.backtosafety.core.Translate
import com.backtosafety.core.data.Store
import com.backtosafety.core.formatCountdown
import com.backtosafety.core.invoke
import com.backtosafety.core.secondsRemaining
import kotlinx.coroutines.delay
import kotlin.time.Instant

private val EMERGENCY_IDLE_BG = Color(0xFFEF4444)
private val EMERGENCY_ACTIVE_BG = Color(0xFFB91C1C)
private val EMERGENCY_SWEEP = Color(0xFFEF4444)

/** Port of app/index.tsx. */
@Composable
fun HomeScreen(t: Translate, emergencyNumber: String, store: Store, navigate: (String) -> Unit) {
    val colors = LocalAppColors.current
    val type = DesignTokens.Typography
    val space = DesignTokens.Spacing
    var profile by remember { mutableStateOf<Profile?>(null) }
    var contactCount by remember { mutableIntStateOf(0) }
    var emergency by remember { mutableStateOf<ActiveEmergency?>(null) }
    var secondsLeft by remember { mutableIntStateOf(0) }

    // Reloaded every time home is shown (useFocusEffect).
    LaunchedEffect(Unit) {
        Analytics.screen("home")
        profile = store.profile()
        contactCount = store.contacts().size
        emergency = store.activeEmergency()
    }
    // Re-derived from the start time every tick; counting ticks drifts in the background (F-16).
    LaunchedEffect(emergency) {
        val started = emergency?.let { Instant.parse(it.startedAt).toEpochMilliseconds() } ?: return@LaunchedEffect
        while (true) {
            secondsLeft = secondsRemaining(started, AppClock.nowMs())
            delay(1000)
        }
    }

    val p = profile
    val hasProfile = !p?.name.isNullOrEmpty()
    val active = emergency
    val expired = active != null && secondsLeft == 0

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .verticalScroll(rememberScrollState())
            .padding(start = space.lg.udp, end = space.lg.udp, top = space.lg.udp, bottom = space.xxl.udp),
    ) {
        // Header
        Row(Modifier.fillMaxWidth().padding(top = space.xs.udp, bottom = space.xl.udp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xxs.udp)) {
                if (hasProfile) {
                    Text(
                        t("caringFor").uppercase(),
                        style = type.caption.style(fontWeight = 600, letterSpacing = 0.8f),
                        color = colors.textSecondary,
                    )
                }
                Text(
                    if (hasProfile) p!!.name else t("appTitle"),
                    style = type.headline.style(), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.padding(start = space.md.udp).testTag("home-profile").clickable {
                Analytics.track(AnalyticsEvent.SCREEN_VIEWED, mapOf("screen" to "profile", "source" to "home"))
                navigate("profile")
            }) {
                val bitmap = remember(p?.photoUri) {
                    p?.photoUri?.let { BitmapFactory.decodeFile(Uri.parse(it).path)?.asImageBitmap() }
                }
                if (bitmap != null) {
                    Image(bitmap, null, Modifier.size(56.udp).clip(CircleShape).testTag("home-photo"), contentScale = ContentScale.Crop)
                } else {
                    Box(
                        Modifier.size(56.udp).clip(CircleShape).background(colors.primaryLight).rnBorder(1.udp, colors.border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon("person.fill", 28f, colors.textSecondary) }
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).size(20.udp).clip(CircleShape).background(colors.tint)
                        .rnBorder(2.udp, Color(DesignTokens.Light.textOnPrimary), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon("pencil", 10f, Color(DesignTokens.Light.textOnPrimary)) }
            }
        }

        // Emergency button
        val (title, subtitle) = when {
            active == null -> t("emergencyButton.startTitle") to t("emergencyButton.startSubtitle")
            expired -> t("emergencyButton.timerExpiredTitle", mapOf("emergencyNumber" to emergencyNumber)) to
                t("emergencyButton.timerExpiredSubtitle")
            else -> t("emergencyButton.activeTitle") to t(
                "emergencyButton.remaining",
                mapOf("time" to formatCountdown(secondsLeft), "checked" to active.checkedSteps.size, "total" to 11),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = space.lg.udp)
                .shadow(4.udp, RoundedCornerShape(DesignTokens.Radius.xl.dp))
                .clip(RoundedCornerShape(DesignTokens.Radius.xl.dp))
                .background(if (active != null) EMERGENCY_ACTIVE_BG else EMERGENCY_IDLE_BG)
                .testTag("home-start-emergency")
                // One accessible element, read as its texts, like RN's touchable (Maestro matches it).
                .clearAndSetSemantics { contentDescription = "$title, $subtitle"; role = Role.Button }
                .clickable { navigate("emergency") },
        ) {
            if (active != null && !expired) {
                val elapsed = (SEARCH_WINDOW_SECONDS - secondsLeft).toFloat() / SEARCH_WINDOW_SECONDS
                Box(Modifier.matchParentSize().drawBehind { drawRect(EMERGENCY_SWEEP, size = Size(size.width * elapsed, size.height)) })
            }
            Row(
                Modifier.padding(vertical = space.lg.udp, horizontal = space.xl.udp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(space.md.udp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.udp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon("exclamationmark.triangle.fill", 28f, Color(DesignTokens.Light.textOnPrimary))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = type.bodyBold.style(), color = Color(DesignTokens.Light.textOnPrimary), modifier = Modifier.padding(bottom = 2.udp))
                        Text(subtitle, style = type.caption.style(), color = Color.White.copy(alpha = 0.8f))
                    }
                }
                Icon("chevron.right", 20f, Color.White.copy(alpha = 0.7f))
            }
        }

        // Quick actions
        Row(Modifier.padding(bottom = space.lg.udp), horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
            QuickAction(
                "📞", t("quickActions.contacts"),
                if (contactCount == 0) t("quickActions.contactsNone") else t("quickActions.contactsSaved", mapOf("count" to contactCount)),
                "home-contacts", Modifier.weight(1f),
            ) {
                Analytics.track(AnalyticsEvent.SCREEN_VIEWED, mapOf("screen" to "contacts", "source" to "home"))
                navigate("contacts")
            }
            QuickAction("📍", t("quickActions.places"), t("quickActions.placesSubtitle"), "home-places", Modifier.weight(1f)) {
                Analytics.track(AnalyticsEvent.DESTINATION_ADD_TAPPED, mapOf("source" to "home"))
                navigate("destinations")
            }
        }

        // Emergency info
        if (hasProfile) {
            EmergencyInfoCard(t, p!!) {
                Analytics.track(AnalyticsEvent.SCREEN_VIEWED, mapOf("screen" to "readout", "source" to "home"))
                navigate("readout")
            }
        }

        // Settings link
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = space.sm.udp)
                .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
                .background(colors.card)
                .rnBorder(1.udp, colors.border, RoundedCornerShape(DesignTokens.Radius.lg.dp))
                // A Pressable inside the bordered card in RN: the ID covers the inside only.
                .testTag("home-settings")
                .clickable {
                    Analytics.track(AnalyticsEvent.SCREEN_VIEWED, mapOf("screen" to "settings", "source" to "home"))
                    navigate("settings")
                }
                .padding(space.lg.udp),
            horizontalArrangement = Arrangement.spacedBy(space.sm.udp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon("gearshape", 18f, colors.textSecondary)
            Text(t("settingsLink"), style = type.body.style(), color = colors.text, modifier = Modifier.weight(1f))
            Icon("chevron.right", 16f, colors.textSecondary)
        }
    }
}

@Composable
private fun QuickAction(emoji: String, title: String, subtitle: String, testTag: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val space = DesignTokens.Spacing
    Column(
        modifier
            .heightIn(min = 110.udp)
            .testTag(testTag)
            .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
            .clickable(onClick = onClick)
            .background(colors.card)
            .rnBorder(1.udp, colors.border, RoundedCornerShape(DesignTokens.Radius.lg.dp))
            .padding(space.lg.udp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.xs.udp, Alignment.CenterVertically),
    ) {
        Box(Modifier.padding(bottom = space.xs.udp).size(44.udp).clip(CircleShape).background(colors.primaryLight), contentAlignment = Alignment.Center) {
            Text(emoji, style = rnTextStyle(22f, 24f))
        }
        Text(title, style = DesignTokens.Typography.bodyBold.style(), color = colors.text)
        Text(subtitle, style = DesignTokens.Typography.caption.style(), color = colors.textSecondary)
    }
}

@Composable
private fun EmergencyInfoCard(t: Translate, profile: Profile, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val type = DesignTokens.Typography
    val space = DesignTokens.Spacing
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = space.lg.udp)
            .testTag("home-readout")
            .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
            .clickable(onClick = onClick)
            .background(colors.card)
            .rnBorder(1.udp, colors.border, RoundedCornerShape(DesignTokens.Radius.lg.dp))
            .padding(space.lg.udp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("emergencyInfo.title"), style = type.bodyBold.style(), color = colors.text, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(DesignTokens.Radius.md.dp)).background(colors.primaryLight)
                    .padding(horizontal = space.sm.udp, vertical = space.xs.udp),
                horizontalArrangement = Arrangement.spacedBy(space.xxs.udp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t("emergencyInfo.script911"), style = type.caption.style().copy(fontWeight = FontWeight.SemiBold), color = colors.tint)
                Icon("chevron.right", 14f, colors.tint)
            }
        }
        val items = listOfNotNull(
            profile.medicalConditions?.takeIf { it.isNotEmpty() }?.let { "emergencyInfo.healthNotes" to it },
            profile.medications?.takeIf { it.isNotEmpty() }?.let { "emergencyInfo.medications" to it },
            profile.cognitiveStatus?.takeIf { it.isNotEmpty() }?.let { "emergencyInfo.cognitiveStatus" to it },
            profile.deescalationTechniques?.takeIf { it.isNotEmpty() }?.let { "emergencyInfo.deescalation" to it },
        )
        if (items.isNotEmpty()) {
            Box(Modifier.padding(top = space.md.udp).fillMaxWidth().height(1.udp).background(colors.border))
            Column(Modifier.padding(top = space.md.udp), verticalArrangement = Arrangement.spacedBy(space.md.udp)) {
                for ((label, value) in items) {
                    Column {
                        Text(
                            t(label).uppercase(),
                            style = type.small.style(fontWeight = 700, letterSpacing = 0.6f),
                            color = colors.textSecondary, modifier = Modifier.padding(bottom = space.xxs.udp),
                        )
                        Text(value, style = type.body.style(), color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
