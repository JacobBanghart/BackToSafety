package com.backtosafety.app.home

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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.backtosafety.app.ui.Icon
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.rememberPhoto
import com.backtosafety.app.ui.rnBorder
import com.backtosafety.app.ui.runCatchingCancellable
import com.backtosafety.app.ui.style
import com.backtosafety.app.ui.udp
import com.backtosafety.core.ActiveEmergency
import com.backtosafety.core.Analytics
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.AppClock
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.EMERGENCY_STEP_COUNT
import com.backtosafety.core.Profile
import com.backtosafety.core.ReadinessCheck
import com.backtosafety.core.ReadinessItem
import com.backtosafety.core.SEARCH_WINDOW_SECONDS
import com.backtosafety.core.Translate
import com.backtosafety.core.data.Store
import com.backtosafety.core.formatCountdown
import com.backtosafety.core.invoke
import com.backtosafety.core.readinessChecklist
import com.backtosafety.core.secondsRemaining
import kotlinx.coroutines.delay

// Starting a search and a search under way are both the deep red of Call 911: an emergency in
// progress must never look calmer than the idle Start button (a purple running state did). The
// running state is told apart at a glance by its shape, not a subtitle: the live countdown is the
// headline and a brighter red sweep covers the time already used (an inset white ring was
// dropped: inside the red it read as a second outline, noise). White text passes 4.5:1 on both reds.
private val EMERGENCY_IDLE_BG = Color(DesignTokens.Semantic.errorDark)
private val EMERGENCY_ACTIVE_BG = Color(DesignTokens.Semantic.errorDark)
private val EMERGENCY_SWEEP = Color(DesignTokens.Semantic.error)

/** Port of app/index.tsx. */
@Composable
fun HomeScreen(t: Translate, emergencyNumber: String, store: Store, navigate: (String) -> Unit) {
    val colors = LocalAppColors.current
    val type = DesignTokens.Typography
    val space = DesignTokens.Spacing
    var profile by remember { mutableStateOf<Profile?>(null) }
    var contactCount by remember { mutableIntStateOf(0) }
    var emergency by remember { mutableStateOf<ActiveEmergency?>(null) }

    // Null until read, and stays null if the read fails: then the readiness card isn't shown,
    // rather than telling the user to add places they already have.
    var placeCount by remember { mutableStateOf<Int?>(null) }
    var secondsLeft by remember { mutableIntStateOf(0) }

    // Reloaded every time home is shown (useFocusEffect).
    LaunchedEffect(Unit) {
        Analytics.screen("home")
        // The active emergency first: nothing optional delays the Start/Resume button's state.
        emergency = store.activeEmergency()
        profile = store.profile()
        contactCount = store.contacts().size
        placeCount = runCatchingCancellable { store.destinations().size }.getOrNull()
    }
    // Re-derived from the start time every tick; counting ticks drifts in the background (F-16).
    LaunchedEffect(emergency) {
        val started = emergency?.startedAtMs ?: return@LaunchedEffect
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
            .navigationBarsPadding()
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
            Box(Modifier.padding(start = space.md.udp).testTag("home-profile").semantics { contentDescription = t("editProfile") }.clickable(role = Role.Button) {
                Analytics.track(AnalyticsEvent.SCREEN_VIEWED, mapOf("screen" to "profile", "source" to "home"))
                navigate("profile")
            }) {
                val bitmap = rememberPhoto(p?.photoUri, 56)
                if (bitmap != null) {
                    Image(bitmap, null, Modifier.size(56.udp).clip(CircleShape).testTag("home-photo"), contentScale = ContentScale.Crop)
                } else {
                    Box(
                        Modifier.size(56.udp).clip(CircleShape).background(colors.primaryLight).rnBorder(1.udp, colors.border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon("person.fill", 28f, colors.textSecondary) }
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).size(20.udp).clip(CircleShape).background(colors.primary)
                        .rnBorder(2.udp, colors.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon("pencil", 10f, colors.textOnPrimary) }
            }
        }

        // Emergency button
        val (title, subtitle) = when {
            active == null -> t("emergencyButton.startTitle") to t("emergencyButton.startSubtitle")
            expired -> t("emergencyButton.timerExpiredTitle", mapOf("emergencyNumber" to emergencyNumber)) to
                t("emergencyButton.timerExpiredSubtitle")
            else -> t("emergencyButton.activeTitle") to t(
                "emergencyButton.remaining",
                mapOf("time" to formatCountdown(secondsLeft), "checked" to active.checkedSteps.size, "total" to EMERGENCY_STEP_COUNT),
            )
        }
        val running = active != null && !expired
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = space.lg.udp)
                // The same radius as every other card on home.
                .shadow(4.udp, RoundedCornerShape(DesignTokens.Radius.lg.dp))
                .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
                .background(if (active != null) EMERGENCY_ACTIVE_BG else EMERGENCY_IDLE_BG)
                .testTag("home-start-emergency")
                // One accessible element, read as its texts, like RN's touchable (Maestro matches it).
                .clearAndSetSemantics { contentDescription = "$title, $subtitle"; role = Role.Button }
                .clickable { navigate("emergency") },
        ) {
            if (running) {
                val elapsed = (SEARCH_WINDOW_SECONDS - secondsLeft).toFloat() / SEARCH_WINDOW_SECONDS
                Box(Modifier.matchParentSize().drawBehind { drawRect(EMERGENCY_SWEEP, size = Size(size.width * elapsed, size.height)) })
            }
            if (running) {
                RunningEmergency(t, secondsLeft, active?.checkedSteps?.size ?: 0)
            } else Row(
                Modifier.padding(vertical = space.lg.udp, horizontal = space.xl.udp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(space.md.udp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.udp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon("exclamationmark.triangle.fill", 28f, Color(DesignTokens.Light.textOnPrimary))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = type.bodyBold.style(), color = Color(DesignTokens.Light.textOnPrimary), modifier = Modifier.padding(bottom = 2.udp))
                        // 14sp at least: older eyes read it in sun, white on red.
                        Text(subtitle, style = type.label.style(fontWeight = 500, letterSpacing = 0f), color = Color(DesignTokens.Light.textOnPrimary))
                    }
                }
                Icon("chevron.right", 20f, Color.White.copy(alpha = 0.7f))
            }
        }

        // Quick actions
        Row(Modifier.padding(bottom = space.lg.udp), horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
            QuickAction(
                "phone.fill", t("quickActions.contacts"),
                if (contactCount == 0) t("quickActions.contactsNone") else t("quickActions.contactsSaved", mapOf("count" to contactCount)),
                "home-contacts", Modifier.weight(1f),
            ) {
                Analytics.track(AnalyticsEvent.SCREEN_VIEWED, mapOf("screen" to "contacts", "source" to "home"))
                navigate("contacts")
            }
            QuickAction("mappin.and.ellipse", t("quickActions.places"), t("quickActions.placesSubtitle"), "home-places", Modifier.weight(1f)) {
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

        placeCount?.let { places ->
            // Each line reports what its home shortcut reports (avatar, Contacts tile, Places tile).
            ReadinessCard(t, readinessChecklist(p, contactCount, places)) { item ->
                when (item) {
                    ReadinessItem.PHOTO, ReadinessItem.DESCRIPTION -> {
                        Analytics.track(AnalyticsEvent.SCREEN_VIEWED, mapOf("screen" to "profile", "source" to "home"))
                        navigate("profile")
                    }
                    ReadinessItem.CONTACTS -> {
                        Analytics.track(AnalyticsEvent.SCREEN_VIEWED, mapOf("screen" to "contacts", "source" to "home"))
                        navigate("contacts")
                    }
                    ReadinessItem.PLACES -> {
                        Analytics.track(AnalyticsEvent.DESTINATION_ADD_TAPPED, mapOf("source" to "home"))
                        navigate("destinations")
                    }
                }
            }
        }

        // Settings link: a plain row, not a card. It is used rarely, so it doesn't carry the
        // weight of Emergency Info or the quick actions above it.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = space.sm.udp)
                .heightIn(min = 48.udp)
                .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
                .testTag("home-settings")
                .clickable(role = Role.Button) {
                    Analytics.track(AnalyticsEvent.SCREEN_VIEWED, mapOf("screen" to "settings", "source" to "home"))
                    navigate("settings")
                }
                .padding(horizontal = space.lg.udp, vertical = space.md.udp),
            horizontalArrangement = Arrangement.spacedBy(space.sm.udp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon("gearshape", 18f, colors.textSecondary)
            Text(t("settingsLink"), style = type.body.style(), color = colors.textSecondary, modifier = Modifier.weight(1f))
            Icon("chevron.right", 16f, colors.textSecondary)
        }
    }
}

@Composable
private fun QuickAction(icon: String, title: String, subtitle: String, testTag: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val space = DesignTokens.Spacing
    Column(
        modifier
            .heightIn(min = 110.udp)
            .testTag(testTag)
            .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .background(colors.card)
            .rnBorder(1.udp, colors.border, RoundedCornerShape(DesignTokens.Radius.lg.dp))
            .padding(space.lg.udp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.xs.udp, Alignment.CenterVertically),
    ) {
        Box(Modifier.padding(bottom = space.xs.udp).size(44.udp).clip(CircleShape).background(colors.iconContainer), contentAlignment = Alignment.Center) {
            Icon(icon, 22f, colors.primaryText)
        }
        Text(title, style = DesignTokens.Typography.bodyBold.style(), color = colors.text)
        // 14sp, not caption: the counts are read at a glance by older eyes.
        Text(subtitle, style = DesignTokens.Typography.label.style(fontWeight = 400, letterSpacing = 0f), color = colors.textSecondary)
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
        // One set with the Contacts and Places cards above: the same 44dp round icon, a bodyBold
        // title and a smaller second line, laid out as a row because the card is full width.
        // The title stays on one line at large text; "911 Script" is the second line, in the
        // accent colour, and the chevron says the whole card opens it.
        Row(horizontalArrangement = Arrangement.spacedBy(space.md.udp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.udp).clip(CircleShape).background(colors.iconContainer), contentAlignment = Alignment.Center) {
                Icon("doc.text.fill", 22f, colors.primaryText)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xxs.udp)) {
                Text(
                    t("emergencyInfo.title"), style = type.bodyBold.style(), color = colors.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(t("emergencyInfo.script911"), style = type.caption.style(fontWeight = 600), color = colors.primaryText)
            }
            Icon("chevron.right", 20f, colors.primaryText)
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

/**
 * "Ready for an emergency": what's filled in for the script and the search, each line opening
 * the screen that fills it in. Done is said by the icon and by the word, not by colour alone;
 * a missing line carries an "Add" action in the accent colour.
 */
@Composable
private fun ReadinessCard(t: Translate, checks: List<ReadinessCheck>, onOpen: (ReadinessItem) -> Unit) {
    val colors = LocalAppColors.current
    val type = DesignTokens.Typography
    val space = DesignTokens.Spacing
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = space.lg.udp)
            .clip(shape)
            .background(colors.card)
            .rnBorder(1.udp, colors.border, shape)
            .padding(vertical = space.sm.udp),
    ) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }
                .padding(horizontal = space.lg.udp, vertical = space.sm.udp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t("readiness.title"), style = type.bodyBold.style(), color = colors.text, modifier = Modifier.weight(1f))
            Text(
                t("readiness.summary", mapOf("done" to checks.count { it.done }, "total" to checks.size)),
                style = type.label.style(fontWeight = 500, letterSpacing = 0f), color = colors.textSecondary,
                modifier = Modifier.padding(start = space.sm.udp),
            )
        }
        for (check in checks) {
            val label = t("readiness.items.${check.item.key}")
            val state = t(if (check.done) "readiness.done" else "readiness.add")
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.udp)
                    .clearAndSetSemantics { contentDescription = "$label, $state"; role = Role.Button }
                    .clickable { onOpen(check.item) }
                    .padding(horizontal = space.lg.udp, vertical = space.sm.udp),
                horizontalArrangement = Arrangement.spacedBy(space.md.udp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (check.done) "checkmark.circle.fill" else "circle", 22f, if (check.done) colors.success else colors.icon)
                Text(label, style = type.body.style(), color = colors.text, modifier = Modifier.weight(1f))
                Text(
                    state, style = type.label.style(letterSpacing = 0f),
                    color = if (check.done) colors.textSecondary else colors.primaryText,
                )
                Icon("chevron.right", 16f, if (check.done) colors.textSecondary else colors.primaryText)
            }
        }
    }
}

/**
 * The home button while a search runs: "EMERGENCY IN PROGRESS" as a small label, the time left
 * as the headline (display size, so it reads from arm's length), then the steps done.
 */
@Composable
private fun RunningEmergency(t: Translate, secondsLeft: Int, checked: Int) {
    val space = DesignTokens.Spacing
    val type = DesignTokens.Typography
    val white = Color(DesignTokens.Light.textOnPrimary)
    Row(Modifier.padding(vertical = space.lg.udp, horizontal = space.xl.udp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xxs.udp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(space.xs.udp), verticalAlignment = Alignment.CenterVertically) {
                Icon("timer", 18f, white)
                Text(t("emergencyButton.activeTitle").uppercase(), style = type.label.style(letterSpacing = 1f), color = white)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm.udp), verticalAlignment = Alignment.Bottom) {
                Text(formatCountdown(secondsLeft), style = type.display.style(), color = white)
                // Title size, so "left" reads as part of the countdown rather than a footnote to it.
                Text(t("emergencyButton.timeLeft"), style = type.title.style(), color = white, modifier = Modifier.padding(bottom = space.xxs.udp))
            }
            Text(
                t("emergencyButton.stepsDone", mapOf("checked" to checked, "total" to EMERGENCY_STEP_COUNT)),
                style = type.body.style(fontWeight = 500), color = white,
            )
        }
        Icon("chevron.right", 24f, white)
    }
}
