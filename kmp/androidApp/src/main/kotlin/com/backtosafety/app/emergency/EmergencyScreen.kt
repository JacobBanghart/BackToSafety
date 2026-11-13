package com.backtosafety.app.emergency

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.backtosafety.app.BuildConfig
import com.backtosafety.app.ui.Icon
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.ScreenHeader
import com.backtosafety.app.ui.dial
import com.backtosafety.app.ui.localeTime
import com.backtosafety.app.ui.negativeTopMargin
import com.backtosafety.app.ui.reportSaveFailed
import com.backtosafety.app.ui.rnBorder
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.app.ui.runCatchingCancellable
import com.backtosafety.app.ui.style
import com.backtosafety.app.ui.udp
import com.backtosafety.core.ActiveEmergency
import com.backtosafety.core.Analytics
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.AppClock
import com.backtosafety.core.ChecklistStep
import com.backtosafety.core.CountdownAlert
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.DialTarget
import com.backtosafety.core.EmergencyAway
import com.backtosafety.core.Profile
import com.backtosafety.core.SmsResult
import com.backtosafety.core.Translate
import com.backtosafety.core.buildAlertSms
import com.backtosafety.core.buildInitialSteps
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.DestinationEntity
import com.backtosafety.core.directionHint
import com.backtosafety.core.emergency911CalledProperties
import com.backtosafety.core.emergencyEndedProperties
import com.backtosafety.core.formatCountdown
import com.backtosafety.core.invoke
import com.backtosafety.core.normalizeUniqueSmsRecipients
import com.backtosafety.core.secondsRemaining
import com.backtosafety.core.trackSmsResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Instant

private enum class Modal(val catchUp: CountdownAlert? = null) {
    FOUND,
    LEAVE,
    NO_CONTACTS,
    SMS_ERROR,

    /** A countdown alert that came due while the screen wasn't showing (EmergencyAway). */
    CATCH_UP_WARNING(CountdownAlert.WARNING),
    CATCH_UP_EXPIRED(CountdownAlert.EXPIRED),
}

/** Port of app/emergency.tsx. */
@Composable
fun EmergencyScreen(
    t: Translate,
    tCommon: Translate,
    store: Store,
    away: EmergencyAway,
    /** Outlives the screen, for the bookkeeping when it goes. */
    appScope: CoroutineScope,
    onLeave: () -> Unit,
    onViewReadout: () -> Unit,
    /** The emergency's start (ISO) once it's loaded or started, null once it ends: the countdown outside the app follows it. */
    onRunning: (startedAt: String?) -> Unit = {},
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val recorder = remember(store) { EmergencyRecorder(store, onRunning) }
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

    // Reading the saved emergency failed (not: there is none), so none was started or shown.
    var loadFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }

    // Whether the countdown alerts can be delivered while away: the Leave modal says when not.
    // Not in test builds, which never ask for the permission (App.kt), so their screens match the goldens.
    var notificationsOff by remember { mutableStateOf(false) }
    fun checkNotifications() {
        notificationsOff = !BuildConfig.TEST_SEAMS && !CountdownNotifications.enabled(context)
    }

    // Found or End emergency was chosen: the emergency is over and the screen is only on its
    // way out, so nothing on it does anything any more (the recorder, too, saves no more).
    var finished by remember { mutableStateOf(false) }

    // Load or start the emergency (spec/storage.md: active_emergency, incidents).
    LaunchedEffect(loadAttempt) {
        loadFailed = false
        // The profile and places only add to the screen: the emergency goes on without them.
        profile = runCatchingCancellable { store.profile() }.getOrNull()
        destinations = runCatchingCancellable { store.destinations() }.getOrDefault(emptyList())
        // A failed read is not "no emergency": starting one would replace the running emergency
        // (its start, its incident, its countdown). Say so and offer to try again instead.
        val saved = runCatchingCancellable { store.activeEmergency() }.getOrElse { e ->
            reportSaveFailed("emergency", "load", e)
            loadFailed = true
            return@LaunchedEffect
        }
        state = if (saved != null) {
            recorder.begin(saved)
            wearing = saved.wearing
            steps = steps.map { it.copy(checked = it.id in saved.checkedSteps) }
            saved
        } else {
            val startedAt = Instant.fromEpochMilliseconds(AppClock.nowMs()).toString()
            val incidentId = runCatchingCancellable { store.createIncident(startedAt) }.getOrNull()
            Analytics.track(AnalyticsEvent.EMERGENCY_STARTED)
            ActiveEmergency(startedAt, "", emptyList(), incidentId).also {
                recorder.begin(it)
                // Not saved is no reason to leave the emergency unstarted: the next change saves again.
                runCatchingCancellable { recorder.save(it) }
                vibrate(context, CountdownAlert.WARNING)
            }
        }
    }

    // Persist wearing and checked steps as they change. Through the recorder, which won't save
    // an emergency that has ended (Found or End emergency) however late this runs.
    LaunchedEffect(wearing, steps, state) {
        val current = state ?: return@LaunchedEffect
        runCatchingCancellable { recorder.save(current.copy(wearing = wearing, checkedSteps = steps.filter { it.checked }.map { it.id })) }
    }

    LaunchedEffect(modal) {
        if (modal == Modal.LEAVE) checkNotifications()
    }

    // Showing and not showing (another screen on top, or the app in the background): off the
    // screen the countdown alerts are notifications; back on it, one that came due is caught up.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        var showing = false
        fun left() {
            showing = false
            // The bookkeeping can't fail the app: if the store is failing, the screen says so (load).
            appScope.launch { runCatchingCancellable { away.left(AppClock.nowMs()) } }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    showing = true
                    checkNotifications()
                    appScope.launch {
                        when (runCatchingCancellable { away.shown(AppClock.nowMs()) }.getOrNull()) {
                            CountdownAlert.WARNING -> Modal.CATCH_UP_WARNING
                            CountdownAlert.EXPIRED -> Modal.CATCH_UP_EXPIRED
                            null -> null
                        }?.let {
                            vibrate(context, it.catchUp!!)
                            modal = it
                        }
                    }
                }
                Lifecycle.Event.ON_STOP -> left()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (showing) left()
        }
    }

    // Countdown: re-derived from the start time each tick; alerts fire on crossing (F-16). It
    // ticks only while the screen shows; what comes due otherwise is EmergencyAway's.
    LaunchedEffect(state?.startedAt) {
        val started = state?.let { Instant.parse(it.startedAt).toEpochMilliseconds() } ?: return@LaunchedEffect
        if (secondsRemaining(started, AppClock.nowMs()) <= 0) haptics.performHapticFeedback(HapticFeedbackType.Reject)
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var prev = secondsRemaining(started, AppClock.nowMs())
            away.inAppAlerts(prev, prev, AppClock.nowMs())
            secondsLeft = prev
            while (prev > 0) {
                delay(1000)
                val now = AppClock.nowMs()
                val next = secondsRemaining(started, now)
                for (alert in away.inAppAlerts(prev, next, now)) vibrate(context, alert)
                secondsLeft = next
                prev = next
            }
        }
    }

    val current = state
    if (current == null) {
        if (loadFailed) {
            LoadFailed(t, onRetry = { loadAttempt++ }, onBack = onLeave)
        } else {
            Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
                Text(t("loading"), color = colors.text)
            }
        }
        return
    }
    val expired = secondsLeft == 0
    val checked = steps.count { it.checked }
    val checkedIds = { steps.filter { it.checked }.map { it.id } }

    // Callers run this in appScope, not the screen's scope: Found and End emergency leave the
    // screen, and a write cut off by that would leave the incident without its end (ended_at
    // NULL for good). The ones that leave also wait for it before they do.
    suspend fun record(outcome: String? = null, ended: Boolean = false) = recorder.record(
        current.startedAt, checkedIds(), wearing, outcome,
        if (ended) Instant.fromEpochMilliseconds(AppClock.nowMs()).toString() else null,
    )

    fun endedProperties() = emergencyEndedProperties(
        Instant.parse(current.startedAt).toEpochMilliseconds(), AppClock.nowMs(), steps.count { it.checked },
    )

    fun toggle(id: String) {
        if (finished) return
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
            // No warning icon by the title: the red on this screen belongs to Call 911 alone.
            ScreenHeader(t("screenTitle"), "emergency", onBack = { modal = Modal.LEAVE })
            Column(
                Modifier
                    .weight(1f)
                    // The viewport stops above the gesture bar, so at rest no text sits under its
                    // handle (it read as a strikethrough); the keyboard inset takes over when open.
                    .imePadding()
                    .navigationBarsPadding()
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

                ActionButtons(
                    t, emergencyNumber,
                    onFound = {
                        if (!finished) {
                            finished = true
                            appScope.launch {
                                recorder.end()
                                Analytics.track(AnalyticsEvent.EMERGENCY_COMPLETED, endedProperties())
                                record(outcome = "found", ended = true)
                                modal = Modal.FOUND
                            }
                        }
                    },
                    onCall = {
                        if (finished) return@ActionButtons
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Only a call that was placed counts: a device with no phone app (a tablet)
                        // shows the number to call and records nothing as called.
                        if (!dial(context, emergencyNumber, DialTarget.EMERGENCY, "emergency", tCommon)) return@ActionButtons
                        Analytics.track(
                            AnalyticsEvent.EMERGENCY_911_CALLED,
                            emergency911CalledProperties(Instant.parse(current.startedAt).toEpochMilliseconds(), AppClock.nowMs(), steps.count { it.checked }),
                        )
                        // Calling always marks the step done; a second call must not un-check it (F-17).
                        if (steps.none { it.id == "call_911" && it.checked }) toggle("call_911")
                        appScope.launch { record(outcome = "911_called") }
                    },
                    onReadout = {
                        Analytics.track(AnalyticsEvent.SCREEN_VIEWED, mapOf("screen" to "readout", "source" to "emergency"))
                        onViewReadout()
                    },
                    onAlert = {
                        if (!finished) scope.launch {
                            val recipients = normalizeUniqueSmsRecipients(store.emergencyContacts().map { it.phone })
                            if (recipients.isEmpty()) {
                                modal = Modal.NO_CONTACTS
                                return@launch
                            }
                            val startedTime = localeTime(Instant.parse(current.startedAt).toEpochMilliseconds())
                            val message = buildAlertSms(t, profile?.name, startedTime, wearing)
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + recipients.joinToString(";")))
                                .putExtra("sms_body", message)
                            // The messaging app doesn't say whether the text went: handed off is all we know.
                            runCatching { context.startActivity(intent) }
                                .onSuccess { trackSmsResult(SmsResult.HANDED_OFF, recipients.size) }
                                .onFailure { e ->
                                    val result = if (e is ActivityNotFoundException) SmsResult.UNAVAILABLE else SmsResult.FAILED
                                    trackSmsResult(result, recipients.size)
                                    modal = Modal.SMS_ERROR
                                }
                        }
                    },
                )

                // The checklist is the guided task, so it starts in view: what they're wearing is
                // asked for after its first step (searching the home, while it can be checked),
                // not between the actions and the checklist, where it pushed the steps below the fold.
                Checklist(t, steps, destinations, ::toggle) {
                    if (showWearing) WearingCard(t, emergencyNumber, wearing, { wearing = it }) { showWearing = false }
                }
                TipsCard(t, profile)
                Box(Modifier.height(space.xxl.udp))
            }
        }

        modal?.let { m ->
            // Back closes the modal (RN Modal's onRequestClose), whichever one it is; but the
            // emergency's over once Found is shown, so there, as with OK, it leaves the screen.
            BackHandler {
                modal = null
                if (m == Modal.FOUND) onLeave()
            }
            EmergencyModal(
                m, t, tCommon, notificationsOff,
                onDismiss = {
                    modal = null
                    if (m == Modal.FOUND) onLeave()
                },
                onLeave = {
                    Analytics.track(AnalyticsEvent.EMERGENCY_LEAVE)
                    modal = null
                    onLeave()
                },
                onNotificationSettings = { openNotificationSettings(context) },
                onEnd = {
                    finished = true
                    Analytics.track(AnalyticsEvent.EMERGENCY_CANCELLED, endedProperties())
                    modal = null
                    // Ended without an outcome: stamp the end time, keep the outcome as it was.
                    appScope.launch {
                        record(ended = true)
                        recorder.end()
                        onLeave()
                    }
                },
            )
        }
    }
}

/** The system page where this app's notifications are turned on: the app's own page before Android 8, which has no finer one. */
internal fun notificationSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }

private fun openNotificationSettings(context: Context) {
    runCatching { context.startActivity(notificationSettingsIntent(context)) }
}

/** The countdown alerts' vibration, on every Android the app supports (API 24 up). */
private fun vibrate(context: Context, alert: CountdownAlert) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    val pattern = CountdownNotifications.vibrationPattern(alert)
    when {
        Build.VERSION.SDK_INT >= 29 && alert == CountdownAlert.WARNING ->
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        Build.VERSION.SDK_INT >= 26 -> vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        else ->
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
    }
}

@Composable
private fun TimerCard(t: Translate, expired: Boolean, secondsLeft: Int, checked: Int, total: Int, emergencyNumber: String) {
    val space = DesignTokens.Spacing
    val type = DesignTokens.Typography
    val white = Color(DesignTokens.Light.textOnPrimary)
    val number = mapOf("emergencyNumber" to emergencyNumber)
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(4.udp, RoundedCornerShape(DesignTokens.Radius.xl.dp))
            .clip(RoundedCornerShape(DesignTokens.Radius.xl.dp))
            .background(if (expired) Color(DesignTokens.Semantic.errorDark) else colors.timer)
            .padding(horizontal = space.xl.udp, vertical = space.lg.udp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.xs.udp),
    ) {
        Text(
            (if (expired) t("timer.labelExpired", number) else t("timer.labelActive")).uppercase(),
            // 14sp and full white at least: smaller or tinted text faded into the card's purple.
            style = type.label.style(letterSpacing = 1f),
            color = white,
            modifier = Modifier.testTag("emergency-timer-label"),
        )
        // Already display-size, so it doesn't grow with the font scale: at large text a 78sp
        // countdown pushed the checklist below the fold. The text around it still scales.
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
            Text(
                formatCountdown(secondsLeft),
                style = rnTextStyle(56f, 64f, fontWeight = 700, letterSpacing = 2f),
                color = white,
                modifier = Modifier.testTag("emergency-timer"),
            )
        }
        Text(
            if (expired) t("timer.hintExpired", number) else t("timer.hintActive"),
            style = type.body.style(), color = white, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = space.xs.udp),
        )
        Column(Modifier.fillMaxWidth().padding(top = space.sm.udp), verticalArrangement = Arrangement.spacedBy(space.xs.udp)) {
            Box(Modifier.fillMaxWidth().height(6.udp).clip(RoundedCornerShape(3.udp)).background(Color.White.copy(alpha = 0.4f))) {
                Box(Modifier.fillMaxWidth(checked.toFloat() / total).fillMaxHeight().clip(RoundedCornerShape(3.udp)).background(white))
            }
            Text(
                t("timer.stepsProgress", mapOf("checked" to checked, "total" to total)),
                style = type.label.style(fontWeight = 500, letterSpacing = 0f), color = white, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("emergency-progress"),
            )
        }
    }
}

/**
 * What they're wearing, as one row in the checklist: at rest a single line (the question, a
 * shirt icon, a close button) so it doesn't sit as a large input in the middle of the urgent
 * flow; tapped, it opens into a text area with the 911 hint under it. Once something is
 * written it stays open, showing what was written. The field is always there (tapping the row
 * is tapping it), so `emergency-wearing-input` holds the text in both states.
 */
@Composable
private fun WearingCard(t: Translate, emergencyNumber: String, wearing: String, onChange: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val space = DesignTokens.Spacing
    val type = DesignTokens.Typography
    var focused by remember { mutableStateOf(false) }
    val expanded = focused || wearing.isNotEmpty()
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(colors.card)
            .rnBorder(if (focused) 2.udp else 1.udp, if (focused) colors.borderFocused else colors.inputBorder, shape)
            .padding(start = space.md.udp),
        horizontalArrangement = Arrangement.spacedBy(space.sm.udp),
        verticalAlignment = if (expanded) Alignment.Top else Alignment.CenterVertically,
    ) {
        Icon("tshirt.fill", 20f, colors.primaryText, if (expanded) Modifier.padding(top = space.md.udp) else Modifier)
        Column(Modifier.weight(1f).padding(vertical = if (expanded) space.sm.udp else 0.udp)) {
            if (expanded) Text(t("wearing.label"), style = type.label.style(letterSpacing = 0f), color = colors.text)
            val input = if (expanded) rnTextStyle(16f, 22f) else type.bodyBold.style()
            BasicTextField(
                wearing, onChange,
                textStyle = input.copy(color = colors.text),
                cursorBrush = SolidColor(colors.tint),
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.udp)
                    .testTag("emergency-wearing-input")
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { field ->
                    Box(Modifier.padding(vertical = space.sm.udp), contentAlignment = Alignment.CenterStart) {
                        if (wearing.isEmpty()) {
                            // At rest the question is the row's text; open, an example of an answer.
                            if (expanded) {
                                Text(t("wearing.placeholder"), style = input, color = colors.inputPlaceholder)
                            } else {
                                Text(t("wearing.label"), style = input, color = colors.text)
                            }
                        }
                        field()
                    }
                },
            )
            if (expanded) {
                Text(
                    t("wearing.hint", mapOf("emergencyNumber" to emergencyNumber)),
                    style = type.caption.style(), color = colors.textSecondary,
                )
            }
        }
        // A 48dp close button, labelled for TalkBack, at the row's end in both states, so it's
        // never pushed below the edge at large text as the old Dismiss button was.
        Box(
            Modifier.testTag("emergency-wearing-dismiss").size(48.udp).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = t("wearing.dismiss") },
            contentAlignment = Alignment.Center,
        ) { Icon("xmark", 20f, colors.textSecondary) }
    }
}

@Composable
private fun ActionButtons(
    t: Translate, emergencyNumber: String,
    onFound: () -> Unit, onCall: () -> Unit, onReadout: () -> Unit, onAlert: () -> Unit,
) {
    val colors = LocalAppColors.current
    val space = DesignTokens.Spacing
    val type = DesignTokens.Typography
    val emergencyRed = Color(DesignTokens.Semantic.errorDark)
    val white = Color(DesignTokens.Light.textOnPrimary)
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    // Calling for help is the most urgent action: it comes first and is the largest, raised red
    // control with headline type. Found — Safe ends the search, so it is a solid green button too
    // (an outline vanished in dark mode, a pale tonal fill read as disabled), but shorter, flat and
    // with smaller type, so it never outranks Call 911. White on semantic success passes 4.5:1.
    Column(verticalArrangement = Arrangement.spacedBy(space.md.udp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.udp).testTag("emergency-call-911").shadow(6.udp, shape).clip(shape)
                .background(emergencyRed).clickable(role = Role.Button, onClick = onCall)
                .padding(vertical = space.lg.udp, horizontal = space.xl.udp),
            horizontalArrangement = Arrangement.spacedBy(space.md.udp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon("phone.fill", 28f, white)
            Text(
                t("actions.call911", mapOf("emergencyNumber" to emergencyNumber)),
                style = rnTextStyle(type.headline.fontSize, type.headline.lineHeight, type.headline.fontWeight), color = white,
            )
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.udp).testTag("emergency-found").clip(shape)
                .background(Color(DesignTokens.Semantic.success)).clickable(role = Role.Button, onClick = onFound)
                .padding(vertical = space.md.udp, horizontal = space.xl.udp),
            horizontalArrangement = Arrangement.spacedBy(space.sm.udp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon("checkmark.circle.fill", 22f, white)
            Text(t("actions.foundSafe"), style = rnTextStyle(18f, type.bodyBold.lineHeight, type.bodyBold.fontWeight), color = white)
        }
        // Icon above label, centred, at one height for the pair: beside a label that wrapped
        // ("Emergency Info" at large text) a small icon looked detached and the two unequal.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(space.sm.udp)) {
            for ((label, tag, icon, action) in listOf(
                SecondaryAction("actions.infoSheet", "emergency-readout", "doc.text.fill", onReadout),
                SecondaryAction("actions.alertCircle", "emergency-alert-contacts", "bell.fill", onAlert),
            )) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.udp).testTag(tag).clip(shape).clickable(role = Role.Button, onClick = action)
                        .background(colors.card).rnBorder(1.udp, colors.border, shape).padding(space.md.udp),
                    verticalArrangement = Arrangement.spacedBy(space.xs.udp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, 24f, colors.primaryText)
                    Text(t(label), style = type.bodyBold.style(), color = colors.text, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private data class SecondaryAction(val label: String, val tag: String, val icon: String, val action: () -> Unit)

@Composable
private fun Checklist(
    t: Translate, steps: List<ChecklistStep>, destinations: List<DestinationEntity>, onToggle: (String) -> Unit,
    afterFirst: @Composable () -> Unit = {},
) {
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
        for ((index, step) in steps.withIndex()) {
            val urgentOpen = step.urgent && !step.checked
            val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("emergency-step-${step.id}")
                    .clip(shape)
                    .clickable { onToggle(step.id) }
                    .background(if (step.checked) (if (colors.isDark) colors.primaryLight else Color(p.c50)) else colors.card)
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
                            // Done reads as done (struck through, on the tinted card) yet stays legible.
                            modifier = Modifier.weight(1f).alpha(if (step.checked) 0.7f else 1f),
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
                        Row(horizontalArrangement = Arrangement.spacedBy(space.xs.udp)) {
                            Icon("lightbulb.fill", 14f, colors.primaryText, Modifier.padding(top = 2.udp))
                            Text(it, style = type.caption.style().copy(fontStyle = FontStyle.Italic), color = colors.primaryText)
                        }
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
                                style = type.caption.style(), color = colors.primaryText, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (index == 0) afterFirst()
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
    modal: Modal, t: Translate, tCommon: Translate, notificationsOff: Boolean,
    onDismiss: () -> Unit, onLeave: () -> Unit, onNotificationSettings: () -> Unit, onEnd: () -> Unit,
) {
    val colors = LocalAppColors.current
    val space = DesignTokens.Spacing
    val type = DesignTokens.Typography
    val white = Color(DesignTokens.Light.textOnPrimary)
    val success = Color(DesignTokens.Semantic.success)
    val buttonText = type.bodyBold.style()
    val buttonShape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
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

            // On the primary fill the label is textOnPrimary (dark ink on the dark theme's light
            // primary); on the red and green fills it is white.
            @Composable
            fun fullButton(label: String, tag: String, bg: Color, onClick: () -> Unit, fg: Color = white) = Box(
                Modifier.fillMaxWidth().heightIn(min = 48.udp).testTag(tag).clip(buttonShape).background(bg)
                    .clickable(onClick = onClick).padding(vertical = space.md.udp, horizontal = space.lg.udp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = buttonText, color = fg) }

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
                    if (notificationsOff) {
                        // The message promises the timer goes on; with notifications off its alerts won't come.
                        Text(
                            t("modal.leave.notificationsOff"), style = rnTextStyle(16f, 22f), color = colors.error,
                            textAlign = TextAlign.Center, modifier = Modifier.testTag("emergency-modal-leave-notifications-off"),
                        )
                        Text(
                            tCommon("openSettings"), style = buttonText.copy(textDecoration = TextDecoration.Underline), color = colors.tint,
                            modifier = Modifier.testTag("emergency-modal-leave-notifications-settings")
                                .heightIn(min = 48.udp).clickable(onClick = onNotificationSettings).padding(vertical = space.md.udp),
                        )
                    }
                    // Three actions with long labels stack full width, as platform dialogs stack
                    // them. Staying is the safe choice, so it is first and filled. Going back says
                    // what it does (the timer keeps running), so it can't be read as ending the
                    // search; End Emergency is the only red one, set apart below the others.
                    Column(Modifier.fillMaxWidth().padding(top = space.xs.udp), verticalArrangement = Arrangement.spacedBy(space.sm.udp)) {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 48.udp).testTag("emergency-modal-leave-stay").clip(buttonShape)
                                .background(colors.primary).clickable(role = Role.Button, onClick = onDismiss)
                                .padding(vertical = space.md.udp, horizontal = space.lg.udp),
                            contentAlignment = Alignment.Center,
                        ) { Text(t("modal.leave.stay"), style = buttonText, color = colors.textOnPrimary, textAlign = TextAlign.Center) }
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 48.udp).testTag("emergency-modal-leave-leave").clip(buttonShape)
                                .clickable(role = Role.Button, onClick = onLeave).rnBorder(1.udp, colors.inputBorder, buttonShape)
                                .padding(vertical = space.md.udp, horizontal = space.lg.udp),
                            contentAlignment = Alignment.Center,
                        ) { Text(t("modal.leave.leave"), style = buttonText, color = colors.text, textAlign = TextAlign.Center) }
                    }
                    Box(Modifier.padding(top = space.xs.udp).fillMaxWidth().height(1.udp).background(colors.border))
                    // A destructive button, not a bare link: outlined in the theme's error color.
                    Box(
                        Modifier.padding(top = space.xs.udp).fillMaxWidth().heightIn(min = 48.udp).testTag("emergency-modal-leave-end")
                            .clip(buttonShape).clickable(role = Role.Button, onClick = onEnd).rnBorder(1.udp, colors.error, buttonShape)
                            .padding(vertical = space.md.udp, horizontal = space.lg.udp),
                        contentAlignment = Alignment.Center,
                    ) { Text(t("modal.leave.end"), style = buttonText, color = colors.error) }
                }
                Modal.CATCH_UP_WARNING, Modal.CATCH_UP_EXPIRED -> {
                    val key = modal.catchUp!!.key
                    val number = mapOf("emergencyNumber" to tCommon("emergencyNumber"))
                    Text(t("alerts.$key.title", number), style = type.title.style(), color = colors.text, textAlign = TextAlign.Center)
                    Text(t("alerts.$key.body", number), style = rnTextStyle(16f, 22f), color = colors.textSecondary, textAlign = TextAlign.Center)
                    val bg = if (modal == Modal.CATCH_UP_EXPIRED) Color(DesignTokens.Semantic.error) else colors.primary
                    fullButton(tCommon("ok"), "emergency-modal-catchup-ok", bg, onDismiss, if (modal == Modal.CATCH_UP_EXPIRED) white else colors.textOnPrimary)
                }
                Modal.NO_CONTACTS, Modal.SMS_ERROR -> {
                    val noContacts = modal == Modal.NO_CONTACTS
                    title(if (noContacts) "modal.noContacts.title" else "modal.smsError.title")
                    message(if (noContacts) "modal.noContacts.message" else "modal.smsError.message")
                    fullButton(tCommon("ok"), "emergency-modal-info-ok", colors.primary, onDismiss, colors.textOnPrimary)
                }
            }
        }
    }
}

/** Reading the saved emergency failed: said so, with Retry, and a way out. */
@Composable
private fun LoadFailed(t: Translate, onRetry: () -> Unit, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val space = DesignTokens.Spacing
    val type = DesignTokens.Typography
    Column(
        Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        ScreenHeader(t("screenTitle"), "emergency", onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(space.xl.udp).testTag("emergency-load-failed"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space.md.udp, Alignment.CenterVertically),
        ) {
            Text(t("loadFailed.title"), style = type.title.style(), color = colors.text, textAlign = TextAlign.Center)
            Text(t("loadFailed.message"), style = rnTextStyle(16f, 22f), color = colors.textSecondary, textAlign = TextAlign.Center)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.udp).testTag("emergency-load-retry")
                    .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp)).background(colors.primary)
                    .clickable(onClick = onRetry).padding(vertical = space.md.udp, horizontal = space.lg.udp),
                contentAlignment = Alignment.Center,
            ) { Text(t("loadFailed.retry"), style = type.bodyBold.style(), color = colors.textOnPrimary) }
        }
    }
}
