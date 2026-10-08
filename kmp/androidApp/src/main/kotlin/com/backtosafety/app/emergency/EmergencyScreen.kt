package com.backtosafety.app.emergency

import android.content.Context
import com.backtosafety.core.SEARCH_WINDOW_SECONDS
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.Analytics
import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.backtosafety.app.ui.Icon
import com.backtosafety.app.ui.udp
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.ScreenHeader
import com.backtosafety.app.ui.localeTime
import com.backtosafety.app.ui.negativeTopMargin
import com.backtosafety.app.ui.rnBorder
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.app.ui.style
import com.backtosafety.core.ActiveEmergency
import com.backtosafety.core.AppClock
import com.backtosafety.core.ChecklistStep
import com.backtosafety.core.CountdownAlert
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.Profile
import com.backtosafety.core.Translate
import com.backtosafety.core.buildAlertSms
import com.backtosafety.core.buildInitialSteps
import com.backtosafety.core.countdownAlerts
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.DestinationEntity
import com.backtosafety.core.directionHint
import com.backtosafety.core.formatCountdown
import com.backtosafety.core.invoke
import com.backtosafety.core.normalizeUniqueSmsRecipients
import com.backtosafety.core.secondsRemaining
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Modal { FOUND, LEAVE, NO_CONTACTS, SMS_ERROR }

/** Port of app/emergency.tsx. */
@Composable
fun EmergencyScreen(
    t: Translate,
    tCommon: Translate,
    store: Store,
    onLeave: () -> Unit,
    onViewReadout: () -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val emergencyNumber = tCommon("emergencyNumber")
    val space = DesignTokens.Spacing
    val type = DesignTokens.Typography

    var state by remember { mutableStateOf<ActiveEmergency?>(null) }
    var steps by remember { mutableStateOf(buildInitialSteps(t, emergencyNumber)) }
    var wearing by remember { mutableStateOf("") }
    var showWearing by remember { mutableStateOf(true) }
    var secondsLeft by remember { mutableIntStateOf(15 * 60) }
    var profile by remember { mutableStateOf<Profile?>(null) }
    var destinations by remember { mutableStateOf<List<DestinationEntity>>(emptyList()) }
    var modal by remember { mutableStateOf<Modal?>(null) }

    // Load or start the emergency (spec/storage.md: active_emergency, incidents).
    LaunchedEffect(Unit) {
        profile = store.profile()
        destinations = store.destinations()
        val saved = store.activeEmergency()
        state = if (saved != null) {
            wearing = saved.wearing
            steps = steps.map { it.copy(checked = it.id in saved.checkedSteps) }
            saved
        } else {
            val startedAt = Instant.fromEpochMilliseconds(AppClock.nowMs()).toString()
            val incidentId = runCatching { store.createIncident(startedAt) }.getOrNull()
            Analytics.track(AnalyticsEvent.EMERGENCY_STARTED)
            ActiveEmergency(startedAt, "", emptyList(), incidentId).also {
                store.saveActiveEmergency(it)
                vibrate(context, CountdownAlert.WARNING)
            }
        }
    }

    // Persist wearing and checked steps as they change.
    LaunchedEffect(wearing, steps, state) {
        val current = state ?: return@LaunchedEffect
        store.saveActiveEmergency(current.copy(wearing = wearing, checkedSteps = steps.filter { it.checked }.map { it.id }))
    }

    // Countdown: re-derived from the start time each tick; alerts fire on crossing (F-16).
    LaunchedEffect(state?.startedAt) {
        val started = state?.let { Instant.parse(it.startedAt).toEpochMilliseconds() } ?: return@LaunchedEffect
        var prev = secondsRemaining(started, AppClock.nowMs())
        secondsLeft = prev
        if (prev <= 0) haptics.performHapticFeedback(HapticFeedbackType.Reject)
        while (prev > 0) {
            delay(1000)
            val next = secondsRemaining(started, AppClock.nowMs())
            for (alert in countdownAlerts(prev, next)) vibrate(context, alert)
            secondsLeft = next
            prev = next
        }
    }

    val current = state
    if (current == null) {
        Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            Text(t("loading"), color = colors.text)
        }
        return
    }
    val expired = secondsLeft == 0
    val checked = steps.count { it.checked }
    val checkedIds = { steps.filter { it.checked }.map { it.id } }

    fun record(outcome: String? = null, ended: Boolean = false) = scope.launch {
        runCatching {
            val id = store.recordIncident(
                current.incidentId, current.startedAt, checkedIds(), wearing, outcome,
                if (ended) Instant.fromEpochMilliseconds(AppClock.nowMs()).toString() else null,
            )
            if (current.incidentId == null) state = current.copy(incidentId = id)
        }
    }

    fun toggle(id: String) {
        if (steps.none { it.id == id && it.checked }) Analytics.track(AnalyticsEvent.EMERGENCY_STEP_COMPLETED, mapOf("step" to id))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        steps = steps.map { if (it.id == id) it.copy(checked = !it.checked) else it }
    }

    Box(Modifier.fillMaxSize()) {
        // Under a modal the screen is hidden from accessibility, as behind RN's Modal window.
        Column(
            Modifier
                .fillMaxSize()
                .then(if (modal != null) Modifier.clearAndSetSemantics {} else Modifier.testTag("emergency-screen"))
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        ) {
            ScreenHeader(
                t("screenTitle"), "emergency", onBack = { modal = Modal.LEAVE },
                titleIcon = "exclamationmark.triangle.fill" to Color(DesignTokens.Semantic.error),
            )
            Column(
                Modifier
                    .weight(1f)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(space.lg.udp),
                verticalArrangement = Arrangement.spacedBy(space.lg.udp),
            ) {
                TimerCard(t, expired, secondsLeft, checked, steps.size, emergencyNumber)

                directionHint(t, profile?.dominantHand)?.let { hint ->
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
                            .background(Color(DesignTokens.Secondary.c100))
                            .rnBorder(1.udp, Color(DesignTokens.Secondary.c300), RoundedCornerShape(DesignTokens.Radius.lg.dp))
                            .padding(space.md.udp),
                    ) { Text(hint, style = type.bodyBold.style(), color = Color(DesignTokens.Primary.c800)) }
                }

                if (showWearing) WearingCard(t, emergencyNumber, wearing, { wearing = it }) { showWearing = false }

                ActionButtons(
                    t, emergencyNumber, expired,
                    onFound = {
                        scope.launch {
                            store.clearActiveEmergency()
                            Analytics.track(AnalyticsEvent.EMERGENCY_COMPLETED, mapOf("checked_count" to steps.count { it.checked }))
                            record(outcome = "found", ended = true)
                            modal = Modal.FOUND
                        }
                    },
                    onCall = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        Analytics.track(AnalyticsEvent.EMERGENCY_911_CALLED,
                            mapOf("seconds_elapsed" to SEARCH_WINDOW_SECONDS - secondsLeft, "checked_count" to steps.count { it.checked }),
                        )
                        // Calling always marks the step done; a second call must not un-check it (F-17).
                        if (steps.none { it.id == "call_911" && it.checked }) toggle("call_911")
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNumber")))
                        record(outcome = "911_called")
                    },
                    onReadout = {
                        Analytics.track(AnalyticsEvent.SCREEN_VIEWED, mapOf("screen" to "readout", "source" to "emergency"))
                        onViewReadout()
                    },
                    onAlert = {
                        scope.launch {
                            val recipients = normalizeUniqueSmsRecipients(store.emergencyContacts().map { it.phone })
                            if (recipients.isEmpty()) {
                                modal = Modal.NO_CONTACTS
                                return@launch
                            }
                            val startedTime = localeTime(Instant.parse(current.startedAt).toEpochMilliseconds())
                            val message = buildAlertSms(t, profile?.name, startedTime, wearing)
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + recipients.joinToString(";")))
                                .putExtra("sms_body", message)
                            runCatching { context.startActivity(intent) }
                                .onSuccess { Analytics.track(AnalyticsEvent.EMERGENCY_CONTACTS_ALERTED, mapOf("recipient_count" to recipients.size)) }
                                .onFailure { modal = Modal.SMS_ERROR }
                        }
                    },
                )

                Checklist(t, steps, destinations, ::toggle)
                TipsCard(t, profile)
                Box(Modifier.height(space.xxl.udp))
            }
        }

        modal?.let { m ->
            // Back closes the modal (RN Modal's onRequestClose), whichever one it is.
            BackHandler { modal = null }
            EmergencyModal(
                m, t, tCommon,
                onDismiss = {
                    modal = null
                    if (m == Modal.FOUND) onLeave()
                },
                onLeave = {
                    Analytics.track(AnalyticsEvent.EMERGENCY_LEAVE)
                    modal = null
                    onLeave()
                },
                onEnd = {
                    Analytics.track(AnalyticsEvent.EMERGENCY_CANCELLED, mapOf("checked_count" to steps.count { it.checked }))
                    modal = null
                    scope.launch {
                        record(ended = true)
                        store.clearActiveEmergency()
                        onLeave()
                    }
                },
            )
        }
    }
}

private fun vibrate(context: Context, alert: CountdownAlert) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    val effect = when (alert) {
        CountdownAlert.WARNING -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        CountdownAlert.EXPIRED -> VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1)
    }
    vibrator.vibrate(effect)
}

@Composable
private fun TimerCard(t: Translate, expired: Boolean, secondsLeft: Int, checked: Int, total: Int, emergencyNumber: String) {
    val space = DesignTokens.Spacing
    val type = DesignTokens.Typography
    val white = Color(DesignTokens.Light.textOnPrimary)
    val number = mapOf("emergencyNumber" to emergencyNumber)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(4.udp, RoundedCornerShape(DesignTokens.Radius.xl.dp))
            .clip(RoundedCornerShape(DesignTokens.Radius.xl.dp))
            .background(if (expired) Color(DesignTokens.Semantic.error) else Color(DesignTokens.Primary.c700))
            .padding(space.xl.udp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.xs.udp),
    ) {
        Text(
            (if (expired) t("timer.labelExpired", number) else t("timer.labelActive")).uppercase(),
            style = type.caption.style(fontWeight = 600, letterSpacing = 1f),
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.testTag("emergency-timer-label"),
        )
        Text(
            formatCountdown(secondsLeft),
            style = rnTextStyle(60f, 72f, fontWeight = 700, letterSpacing = 2f),
            color = white,
            modifier = Modifier.testTag("emergency-timer"),
        )
        Text(
            if (expired) t("timer.hintExpired", number) else t("timer.hintActive"),
            style = type.body.style(), color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = space.xs.udp),
        )
        Column(Modifier.fillMaxWidth().padding(top = space.md.udp), verticalArrangement = Arrangement.spacedBy(space.xs.udp)) {
            Box(Modifier.fillMaxWidth().height(6.udp).clip(RoundedCornerShape(3.udp)).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(checked.toFloat() / total).fillMaxHeight().clip(RoundedCornerShape(3.udp)).background(white))
            }
            Text(
                t("timer.stepsProgress", mapOf("checked" to checked, "total" to total)),
                style = type.caption.style(), color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("emergency-progress"),
            )
        }
    }
}

@Composable
private fun WearingCard(t: Translate, emergencyNumber: String, wearing: String, onChange: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val space = DesignTokens.Spacing
    val type = DesignTokens.Typography
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(DesignTokens.Radius.lg.dp)).background(colors.card)
            .rnBorder(1.udp, colors.border, RoundedCornerShape(DesignTokens.Radius.lg.dp)).padding(space.lg.udp),
        verticalArrangement = Arrangement.spacedBy(space.sm.udp),
    ) {
        Text(t("wearing.label"), style = type.bodyBold.style(), color = colors.text)
        Text(
            t("wearing.hint", mapOf("emergencyNumber" to emergencyNumber)), style = type.caption.style(),
            color = colors.textSecondary, modifier = Modifier.negativeTopMargin(space.xs.udp),
        )
        val input = rnTextStyle(16f, 20f)
        BasicTextField(
            wearing, onChange,
            textStyle = input.copy(color = colors.text),
            cursorBrush = SolidColor(colors.tint),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.udp, max = 120.udp)
                .testTag("emergency-wearing-input")
                .clip(RoundedCornerShape(DesignTokens.Radius.md.dp))
                .background(if (colors.isDark) Color(DesignTokens.Neutral.c800) else Color(DesignTokens.Neutral.c50))
                .border(1.udp, colors.inputBorder, RoundedCornerShape(DesignTokens.Radius.md.dp)),
            decorationBox = { field ->
                // RN's 1dp border takes space: padding plus border.
                Box(Modifier.padding(horizontal = (space.md + 1).udp, vertical = (space.sm + 1).udp)) {
                    if (wearing.isEmpty()) Text(t("wearing.placeholder"), style = input, color = Color(DesignTokens.Neutral.c400))
                    field()
                }
            },
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                t("wearing.dismiss"), style = type.caption.style(), color = colors.textSecondary,
                modifier = Modifier.testTag("emergency-wearing-dismiss").clickable(onClick = onDismiss).padding(vertical = space.xs.udp),
            )
        }
    }
}

@Composable
private fun ActionButtons(
    t: Translate, emergencyNumber: String, expired: Boolean,
    onFound: () -> Unit, onCall: () -> Unit, onReadout: () -> Unit, onAlert: () -> Unit,
) {
    val colors = LocalAppColors.current
    val space = DesignTokens.Spacing
    val type = DesignTokens.Typography
    val error = Color(DesignTokens.Semantic.error)
    val white = Color(DesignTokens.Light.textOnPrimary)
    val large = rnTextStyle(18f, type.bodyBold.lineHeight, type.bodyBold.fontWeight)
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Column(verticalArrangement = Arrangement.spacedBy(space.sm.udp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.udp).testTag("emergency-found").shadow(2.udp, shape).clip(shape)
                .background(Color(DesignTokens.Semantic.success)).clickable(onClick = onFound)
                .padding(vertical = space.lg.udp, horizontal = space.xl.udp),
            horizontalArrangement = Arrangement.spacedBy(space.sm.udp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon("checkmark.circle.fill", 22f, white)
            Text(t("actions.foundSafe"), style = large, color = white)
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.udp).testTag("emergency-call-911")
                .then(if (expired) Modifier.shadow(2.udp, shape) else Modifier)
                .clip(shape)
                .clickable(onClick = onCall)
                .background(if (expired) error else Color.Transparent)
                .rnBorder(if (expired) 0.dp else 2.udp, error, shape)
                .padding(vertical = space.lg.udp, horizontal = space.xl.udp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t("actions.call911", mapOf("emergencyNumber" to emergencyNumber)), style = large, color = if (expired) white else error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.sm.udp)) {
            for ((label, tag, action) in listOf(
                Triple("actions.infoSheet", "emergency-readout", onReadout),
                Triple("actions.alertCircle", "emergency-alert-contacts", onAlert),
            )) {
                Box(
                    Modifier.weight(1f).heightIn(min = 48.udp).testTag(tag).clip(shape).clickable(onClick = action)
                        .background(colors.card).rnBorder(1.udp, colors.border, shape).padding(space.md.udp),
                    contentAlignment = Alignment.Center,
                ) { Text(t(label), style = type.bodyBold.style(), color = colors.text) }
            }
        }
    }
}

@Composable
private fun Checklist(t: Translate, steps: List<ChecklistStep>, destinations: List<DestinationEntity>, onToggle: (String) -> Unit) {
    val colors = LocalAppColors.current
    val space = DesignTokens.Spacing
    val type = DesignTokens.Typography
    val p = DesignTokens.Primary
    val n = DesignTokens.Neutral
    val error = Color(DesignTokens.Semantic.error)
    Column(verticalArrangement = Arrangement.spacedBy(space.sm.udp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = space.xs.udp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("checklist.title"), style = type.title.style(), color = colors.text, modifier = Modifier.weight(1f))
            Text("${steps.count { it.checked }}/${steps.size}", style = type.bodyBold.style(), color = colors.textSecondary)
        }
        for (step in steps) {
            val urgentOpen = step.urgent && !step.checked
            val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("emergency-step-${step.id}")
                    .alpha(if (step.checked) 0.75f else 1f)
                    .clip(shape)
                    .clickable { onToggle(step.id) }
                    .background(if (step.checked) Color(if (colors.isDark) p.c900 else p.c50) else colors.card)
                    .rnBorder(
                        if (urgentOpen) 2.udp else 1.udp,
                        if (urgentOpen) error else if (step.checked) Color(p.c300) else colors.border, shape,
                    )
                    .padding(space.md.udp),
                horizontalArrangement = Arrangement.spacedBy(space.md.udp),
            ) {
                Box(
                    Modifier
                        .padding(top = 2.udp)
                        .size(32.udp)
                        .clip(CircleShape)
                        .background(
                            when {
                                step.checked -> Color(p.c600)
                                step.urgent -> error.copy(alpha = 0x18 / 255f)
                                else -> Color(if (colors.isDark) n.c700 else n.c200)
                            },
                        )
                        .rnBorder(if (urgentOpen) 1.5.dp else 0.dp, error, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (step.checked) {
                        Icon("checkmark", 14f, Color(DesignTokens.Light.textOnPrimary))
                    } else {
                        Text(
                            "${step.step}",
                            style = type.caption.style().copy(fontWeight = FontWeight.Bold),
                            color = if (step.urgent) error else Color(if (colors.isDark) n.c300 else n.c600),
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xs.udp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.sm.udp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            step.title,
                            style = type.bodyBold.style().copy(textDecoration = if (step.checked) TextDecoration.LineThrough else null),
                            color = colors.text,
                            modifier = Modifier.weight(1f).alpha(if (step.checked) 0.6f else 1f),
                        )
                        if (urgentOpen) {
                            Box(Modifier.clip(RoundedCornerShape(DesignTokens.Radius.sm.dp)).background(error).padding(horizontal = space.sm.udp, vertical = space.xxs.udp)) {
                                Text(
                                    t("checklist.priority"),
                                    style = type.small.style(fontWeight = 700, letterSpacing = 0.5f),
                                    color = Color(DesignTokens.Light.textOnPrimary),
                                )
                            }
                        }
                    }
                    Text(step.description, style = rnTextStyle(16f, 20f), color = colors.textSecondary)
                    step.hint?.let {
                        Text("💡 $it", style = type.caption.style().copy(fontStyle = FontStyle.Italic), color = Color(p.c600))
                    }
                    if (step.id == "familiar_places" && destinations.isNotEmpty() && !step.checked) {
                        Box(Modifier.padding(top = space.sm.udp).fillMaxWidth().height(1.udp).background(colors.border))
                        Column(Modifier.negativeTopMargin(space.xs.udp).padding(top = space.sm.udp), verticalArrangement = Arrangement.spacedBy(space.xxs.udp)) {
                            Text(
                                t("checklist.savedPlaces").uppercase(),
                                style = type.small.style(fontWeight = 600, letterSpacing = 0.4f),
                                color = colors.textSecondary, modifier = Modifier.padding(bottom = space.xxs.udp),
                            )
                            Text(
                                destinations.take(5).joinToString(" • ") { it.name } +
                                    if (destinations.size > 5) " +${destinations.size - 5}" else "",
                                style = type.caption.style(), color = Color(p.c600), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TipsCard(t: Translate, profile: Profile?) {
    val colors = LocalAppColors.current
    val space = DesignTokens.Spacing
    val p = DesignTokens.Primary
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    val bg = if (colors.isDark) Color(p.c900).copy(alpha = 0x60 / 255f) else Color(p.c50)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(bg)
            .rnBorder(1.udp, Color(if (colors.isDark) p.c700 else p.c100), shape).padding(space.lg.udp),
        verticalArrangement = Arrangement.spacedBy(space.sm.udp),
    ) {
        Text(t("tips.title"), style = DesignTokens.Typography.bodyBold.style(), color = Color(if (colors.isDark) p.c200 else p.c800))
        val techniques = profile?.deescalationTechniques?.takeIf { it.isNotEmpty() }
        Text(
            if (techniques != null) "${t("tips.body")}\n• $techniques" else t("tips.body"),
            style = rnTextStyle(16f, 24f), color = Color(if (colors.isDark) p.c300 else p.c700),
            maxLines = 5, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmergencyModal(
    modal: Modal, t: Translate, tCommon: Translate,
    onDismiss: () -> Unit, onLeave: () -> Unit, onEnd: () -> Unit,
) {
    val colors = LocalAppColors.current
    val space = DesignTokens.Spacing
    val type = DesignTokens.Typography
    val white = Color(DesignTokens.Light.textOnPrimary)
    val success = Color(DesignTokens.Semantic.success)
    val buttonText = type.bodyBold.style()
    val buttonShape = RoundedCornerShape(DesignTokens.Radius.md.dp)
    Box(
        Modifier.fillMaxSize().background(Color(if (colors.isDark) DesignTokens.Dark.overlay else DesignTokens.Light.overlay))
            // Modal: touches outside the card must not reach the screen underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(space.xl.udp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 340.udp).fillMaxWidth().shadow(8.udp, RoundedCornerShape(DesignTokens.Radius.xl.dp))
                .clip(RoundedCornerShape(DesignTokens.Radius.xl.dp)).background(colors.card).padding(space.xl.udp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space.md.udp),
        ) {
            @Composable
            fun title(key: String) = Text(t(key), style = type.title.style(), color = colors.text, textAlign = TextAlign.Center)

            @Composable
            fun message(key: String) = Text(t(key), style = rnTextStyle(16f, 22f), color = colors.textSecondary, textAlign = TextAlign.Center)

            @Composable
            fun fullButton(label: String, tag: String, bg: Color, onClick: () -> Unit) = Box(
                Modifier.fillMaxWidth().heightIn(min = 48.udp).testTag(tag).clip(buttonShape).background(bg)
                    .clickable(onClick = onClick).padding(vertical = space.md.udp, horizontal = space.lg.udp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = buttonText, color = white) }

            when (modal) {
                Modal.FOUND -> {
                    Box(Modifier.padding(bottom = space.xs.udp).size(72.udp).clip(CircleShape).background(success.copy(alpha = 0x20 / 255f)), contentAlignment = Alignment.Center) {
                        Icon("checkmark.circle.fill", 40f, success)
                    }
                    title("modal.found.title")
                    message("modal.found.message")
                    fullButton(tCommon("ok"), "emergency-modal-found-ok", success, onDismiss)
                }
                Modal.LEAVE -> {
                    title("modal.leave.title")
                    message("modal.leave.message")
                    // RN row items stretch to the tallest one (the bordered button).
                    Row(Modifier.fillMaxWidth().padding(top = space.xs.udp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
                        Box(
                            Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.udp).testTag("emergency-modal-leave-stay").clip(buttonShape)
                                .clickable(onClick = onDismiss).rnBorder(1.udp, colors.border, buttonShape)
                                .padding(vertical = space.md.udp, horizontal = space.lg.udp),
                            contentAlignment = Alignment.Center,
                        ) { Text(t("modal.leave.stay"), style = buttonText, color = colors.text) }
                        Box(
                            Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.udp).testTag("emergency-modal-leave-leave").clip(buttonShape)
                                .background(colors.primary).clickable(onClick = onLeave)
                                .padding(vertical = space.md.udp, horizontal = space.lg.udp),
                            contentAlignment = Alignment.Center,
                        ) { Text(t("modal.leave.leave"), style = buttonText, color = white) }
                    }
                    Box(
                        Modifier.padding(top = space.xs.udp).testTag("emergency-modal-leave-end").clickable(onClick = onEnd).padding(vertical = space.md.udp),
                    ) { Text(t("modal.leave.end"), style = buttonText, color = Color(DesignTokens.Semantic.error)) }
                }
                Modal.NO_CONTACTS, Modal.SMS_ERROR -> {
                    val noContacts = modal == Modal.NO_CONTACTS
                    title(if (noContacts) "modal.noContacts.title" else "modal.smsError.title")
                    message(if (noContacts) "modal.noContacts.message" else "modal.smsError.message")
                    fullButton(tCommon("ok"), "emergency-modal-info-ok", colors.primary, onDismiss)
                }
            }
        }
    }
}
