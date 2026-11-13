import AudioToolbox
import MessageUI
import Shared
import SwiftUI
import UserNotifications

/// The unfilled part of the timer card's progress track: black over the card, a darker inset under the white fill.
private let timerTrackShadeOpacity = 0.35

private enum EmergencyModal: Identifiable {
    case found, leave, noContacts, smsError
    /// A countdown alert that came due while the screen wasn't showing (EmergencyAway).
    case catchUpWarning, catchUpExpired
    var id: Self { self }
}

/// JS toLocaleTimeString() as Hermes formats it on iOS: the locale's "jms" skeleton.
func localeTime(_ ms: Int64) -> String {
    let f = DateFormatter()
    f.setLocalizedDateFormatFromTemplate("jms")
    return f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
}

/// Port of app/emergency.tsx.
struct EmergencyView: View {
    let t: Translate
    let tCommon: Translate
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors
    @State private var state: ActiveEmergency?
    @State private var steps: [ChecklistStep] = []
    @State private var checked: Set<String> = []
    @State private var wearing = ""
    @State private var showWearing = true
    @State private var wearingFocused = false
    @State private var secondsLeft: Int32 = EmergencyKt.SEARCH_WINDOW_SECONDS
    @State private var profile: Profile?
    @State private var destinations: [DestinationEntity] = []
    @State private var modal: EmergencyModal?
    @State private var sms: SmsDraft?
    /// Reading the saved emergency failed (not: there is none), so none was started or shown.
    @State private var loadFailed = false
    /// Notifications are turned off for the app: the Leave modal says its alerts won't come.
    @State private var notificationsDenied = false
    @State private var visible = false
    @Environment(\.scenePhase) private var scenePhase
    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    private var emergencyNumber: String { tCommon("emergencyNumber") }

    var body: some View {
        content
            .rnModal(item: $modal) { modalView($0) }
            .background(colors.background.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .task { await load() }
            .task(id: modal) { if modal == .leave { await checkNotifications() } }
            .onReceive(tick) { _ in tickCountdown() }
            // Showing and not showing (another screen on top, or the app in the background):
            // off the screen the countdown alerts are notifications; back on it, one that came
            // due is caught up.
            .onAppear {
                visible = true
                shown()
            }
            .onDisappear {
                visible = false
                away()
            }
            .onChange(of: scenePhase) { _, phase in
                guard visible else { return }
                if phase == .background { away() } else if phase == .active { shown() }
            }
            .onChange(of: wearing) { persist() }
            .onChange(of: checked) { persist() }
            .sheet(item: $sms) { draft in
                MessageComposer(draft: draft) { result in
                    let outcome: SmsResult = switch result {
                    case .sent: .sent
                    case .cancelled: .cancelled
                    default: .failed
                    }
                    EmergencyAlertsKt.trackSmsResult(result: outcome, recipients: Int32(draft.recipients.count))
                }
                .ignoresSafeArea()
            }
    }

    @ViewBuilder private var content: some View {
        if state == nil {
            if loadFailed {
                loadFailedView
            } else {
                RNText(t("loading"), TextSpec(size: 16, lineHeight: 24), color: colors.text)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        } else {
            VStack(spacing: 0) {
                // No warning icon by the title: the red on this screen belongs to Call 911 alone.
                ScreenHeader(title: t("screenTitle"), testID: "emergency", onBack: { modal = .leave })
                ScrollView {
                    VStack(alignment: .leading, spacing: Space.lg) {
                        timerCard
                        if let hint = EmergencyKt.directionHint(t: t.lookup, dominantHand: profile?.dominantHand) {
                            RNText(hint, Typography.bodyBold.spec, color: Color(argb: Primary.c800))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(Space.md + 1)
                                .background(RoundedRectangle(cornerRadius: Radius.lg).fill(Color(argb: Secondary.c100)))
                                .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(Color(argb: Secondary.c300), lineWidth: 1))
                        }
                        actionButtons
                        // The checklist is the guided task, so it starts in view: what they're wearing
                        // is asked for after its first step (in `checklist`), not between the actions
                        // and the checklist, where it pushed the steps below the fold.
                        checklist
                        tipsCard
                        Color.clear.frame(height: Space.xxl)
                    }
                    .padding(Space.lg)
                }
                .scrollIndicators(.hidden)
                .scrollDismissesKeyboard(.interactively)
                .scrollStopsAtSafeArea()
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("emergency-screen")
        }
    }

    // MARK: - State

    /// Load or start the emergency (spec/storage.md: active_emergency, incidents).
    private func load() async {
        steps = EmergencyKt.buildInitialSteps(t: t.lookup, emergencyNumber: emergencyNumber)
        loadFailed = false
        // The profile and places only add to the screen: the emergency goes on without them.
        profile = try? await model.store.profile()
        destinations = (try? await model.store.destinations()) ?? []
        // A failed read is not "no emergency": starting one would replace the running emergency
        // (its start, its incident, its countdown). Say so and offer to try again instead.
        let saved: ActiveEmergency?
        do {
            saved = try await model.store.activeEmergency()
        } catch {
            reportSaveFailed(screen: "emergency", action: "load", error: error)
            loadFailed = true
            return
        }
        if let saved {
            wearing = saved.wearing
            checked = Set(saved.checkedSteps)
            state = saved
        } else {
            let startedAt = isoString(AppClock.shared.nowMs())
            let incidentId = try? await model.store.createIncident(startedAt: startedAt)
            Analytics.shared.track(event: .emergencyStarted, properties: [:])
            let started = ActiveEmergency(startedAt: startedAt, wearing: "", checkedSteps: [], incidentId: incidentId)
            _ = try? await model.store.saveActiveEmergency(e: started)
            UINotificationFeedbackGenerator().notificationOccurred(.warning)
            state = started
        }
        if let started = state.flatMap({ parseISO($0.startedAt) }) {
            secondsLeft = EmergencyKt.secondsRemaining(startedAtMs: started.epochMs, nowMs: AppClock.shared.nowMs())
            if secondsLeft <= 0 { UINotificationFeedbackGenerator().notificationOccurred(.error) }
        }
    }

    /// Re-derived from the start time each tick; alerts fire when a threshold is crossed (F-16).
    /// It ticks only while the screen shows; what comes due otherwise is EmergencyAway's.
    private func tickCountdown() {
        guard visible, scenePhase != .background, secondsLeft > 0, let started = state.flatMap({ parseISO($0.startedAt) }) else { return }
        let now = AppClock.shared.nowMs()
        let next = EmergencyKt.secondsRemaining(startedAtMs: started.epochMs, nowMs: now)
        for alert in model.away.inAppAlerts(prevSecondsLeft: secondsLeft, nextSecondsLeft: next, nowMs: now) {
            buzz(alert)
        }
        secondsLeft = next
    }

    private func buzz(_ alert: CountdownAlert) {
        if alert == .warning {
            UINotificationFeedbackGenerator().notificationOccurred(.warning)
        } else {
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        }
    }

    /// Back on the screen: the countdown picks up from now (what came due while away is the
    /// catch-up's, not the tick's), and an alert that came due shows.
    private func shown() {
        Task { await checkNotifications() }
        if let started = state.flatMap({ parseISO($0.startedAt) }) {
            secondsLeft = EmergencyKt.secondsRemaining(startedAtMs: started.epochMs, nowMs: AppClock.shared.nowMs())
        }
        Task {
            guard let alert = try? await model.away.shown(nowMs: AppClock.shared.nowMs()) else { return }
            buzz(alert)
            modal = alert == .warning ? .catchUpWarning : .catchUpExpired
        }
    }

    private func checkNotifications() async {
        notificationsDenied = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .denied
    }

    private func away() {
        Task { _ = try? await model.away.left(nowMs: AppClock.shared.nowMs()) }
    }

    /// Persists wearing and checked steps as they change.
    private func persist() {
        guard let state else { return }
        let saved = ActiveEmergency(startedAt: state.startedAt, wearing: wearing, checkedSteps: checkedIds, incidentId: state.incidentId)
        Task { _ = try? await model.store.saveActiveEmergency(e: saved) }
    }

    private var checkedIds: [String] { steps.map(\.id).filter(checked.contains) }

    /// Records what happened in the incidents table (F-22); never blocks the UI.
    private func record(outcome: String? = nil, ended: Bool = false) {
        guard let state else { return }
        let endedAt = ended ? isoString(AppClock.shared.nowMs()) : nil
        Task {
            if let id = try? await model.store.recordIncident(incidentId: state.incidentId, startedAt: state.startedAt,
                                                              checkedSteps: checkedIds, wearing: wearing, outcome: outcome, endedAt: endedAt),
                state.incidentId == nil {
                self.state = ActiveEmergency(startedAt: state.startedAt, wearing: state.wearing, checkedSteps: state.checkedSteps, incidentId: id)
            }
        }
    }

    private func toggle(_ id: String) {
        let checking = !checked.contains(id)
        if checking { Analytics.shared.track(event: .emergencyStepCompleted, properties: ["step": id]) }
        UIImpactFeedbackGenerator(style: checking ? .medium : .light).impactOccurred()
        if checking { checked.insert(id) } else { checked.remove(id) }
    }

    private func leave() {
        modal = nil
        if !model.path.isEmpty { model.path.removeLast() }
    }

    // MARK: - Actions

    private func found() {
        Task {
            _ = try? await model.store.clearActiveEmergency()
            Analytics.shared.track(event: .emergencyCompleted, properties: endedProperties())
            record(outcome: "found", ended: true)
            modal = .found
        }
    }

    private func endedProperties() -> [String: Any] {
        let started = state.flatMap { parseISO($0.startedAt) }?.epochMs ?? AppClock.shared.nowMs()
        return EmergencyAlertsKt.emergencyEndedProperties(startedAtMs: started, nowMs: AppClock.shared.nowMs(), checkedCount: Int32(checked.count))
    }

    private func call911() {
        UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
        // Only a call that was placed counts: the phone app opening (the system asks first, and a
        // device that can't call shows the number instead) is what records it, not the tap.
        dial(emergencyNumber, target: .emergency, screen: "emergency", tCommon: tCommon) {
            let started = state.flatMap { parseISO($0.startedAt) }?.epochMs ?? AppClock.shared.nowMs()
            Analytics.shared.track(event: .emergency911Called, properties: EmergencyAlertsKt.emergency911CalledProperties(
                startedAtMs: started, nowMs: AppClock.shared.nowMs(), checkedCount: Int32(checked.count)
            ))
            // Calling always marks the step done; a second call must not un-check it (F-17).
            if !checked.contains("call_911") { toggle("call_911") }
            record(outcome: "911_called")
        }
    }

    private func alertContacts() {
        Task {
            let contacts = (try? await model.store.emergencyContacts()) ?? []
            let recipients = PhoneKt.normalizeUniqueSmsRecipients(phones: contacts.map(\.phone))
            if recipients.isEmpty { modal = .noContacts; return }
            guard MFMessageComposeViewController.canSendText(), let state, let started = parseISO(state.startedAt) else {
                Analytics.shared.track(event: .emergencySmsResult, properties: EmergencyAlertsKt.smsResultProperties(
                    result: .unavailable, recipients: Int32(recipients.count)
                ))
                modal = .smsError
                return
            }
            let body = EmergencyKt.buildAlertSms(t: t.lookup, name: profile?.name, startedTime: localeTime(started.epochMs), wearing: wearing)
            sms = SmsDraft(recipients: recipients, body: body)
        }
    }

    // MARK: - Pieces

    /// Reading the saved emergency failed: said so, with Retry, and a way out.
    private var loadFailedView: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: t("screenTitle"), testID: "emergency", onBack: { leave() })
            VStack(spacing: Space.md) {
                RNText(t("loadFailed.title"), Typography.title.spec, color: colors.text, align: .center)
                RNText(t("loadFailed.message"), TextSpec(size: 16, lineHeight: 22), color: colors.textSecondary, align: .center)
                Button { Task { await load() } } label: {
                    RNText(t("loadFailed.retry"), Typography.bodyBold.spec, color: colors.textOnPrimary)
                        .frame(maxWidth: .infinity, minHeight: 48 - 2 * Space.md)
                        .padding(.vertical, Space.md).padding(.horizontal, Space.lg)
                        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.primary))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.pressable)
                .accessibilityIdentifier("emergency-load-retry")
            }
            .padding(Space.xl)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("emergency-load-failed")
        }
    }

    private var timerCard: some View {
        let expired = secondsLeft == 0
        let white = Color(argb: Light.textOnPrimary)
        let progress = steps.isEmpty ? 0 : CGFloat(checked.count) / CGFloat(steps.count)
        return VStack(spacing: Space.xs) {
            RNText((expired ? t("timer.labelExpired", ["emergencyNumber": emergencyNumber]) : t("timer.labelActive")).uppercased(),
                   // 14pt and full white at least: smaller or tinted text faded into the card's purple.
                   Typography.label.spec.spacing(1), color: white)
                .accessibilityIdentifier("emergency-timer-label")
            // Already display-size, so it doesn't grow with Dynamic Type: at large text the
            // countdown pushed the checklist below the fold. The text around it still scales.
            RNText(EmergencyKt.formatCountdown(seconds: secondsLeft), TextSpec(size: 56, lineHeight: 64, weight: 700, letterSpacing: 2), color: white)
                .dynamicTypeSize(.large)
                .accessibilityIdentifier("emergency-timer")
            // Body size at medium weight: the calming line is read in a panic, so it isn't a caption.
            RNText(expired ? t("timer.hintExpired", ["emergencyNumber": emergencyNumber]) : t("timer.hintActive"),
                   Typography.body.spec.weight(500), color: white, align: .center)
                .padding(.top, Space.xs)
            VStack(spacing: Space.xs) {
                // A 10pt track that reads at arm's length. The unfilled part is a darker inset
                // (black over the card) and the filled part solid white, so done and to-do differ
                // in lightness, not only in a white tint that faded into the card.
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.black.opacity(timerTrackShadeOpacity))
                        Capsule().fill(white).frame(width: geo.size.width * progress)
                    }
                    .overlay(Capsule().strokeBorder(white.opacity(0.5), lineWidth: 1))
                }
                .frame(height: 10)
                let progressText = t("timer.stepsProgress", ["checked": checked.count, "total": steps.count])
                RNText(progressText, Typography.body.spec.weight(500), color: white, align: .center)
                    .frame(maxWidth: .infinity)
                    .rnID("emergency-progress", label: progressText)
            }
            .padding(.top, Space.sm)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Space.xl).padding(.vertical, Space.lg)
        .background(RoundedRectangle(cornerRadius: Radius.xl).fill(expired ? Color(argb: Semantic.errorDark) : colors.timer))
        .rnShadow(.md, dark: colors.isDark)
    }

    /// What they're wearing, as one row in the checklist: at rest a single line (the question, a
    /// shirt icon, a close button) so it doesn't sit as a large input in the middle of the urgent
    /// flow; tapped, it opens into a text area with the 911 hint under it. Once something is
    /// written it stays open, showing what was written. The field is always there (tapping the
    /// row is tapping it), so `emergency-wearing-input` holds the text in both states.
    private var wearingCard: some View {
        let expanded = wearingFocused || !wearing.isEmpty
        let m = fontMultiplier
        return HStack(alignment: expanded ? .top : .center, spacing: Space.sm) {
            SFIcon("tshirt.fill", 20, colors.primaryText)
                .padding(.top, expanded ? Space.md : 0)
            VStack(alignment: .leading, spacing: 0) {
                if expanded {
                    RNText(t("wearing.label"), Typography.label.spec.spacing(0), color: colors.text)
                }
                // At rest the question is the row's text; open, an example of an answer.
                RNTextInput(
                    text: $wearing, placeholder: expanded ? t("wearing.placeholder") : t("wearing.label"),
                    testID: "emergency-wearing-input",
                    font: .systemFont(ofSize: 16 * m, weight: expanded ? .regular : .semibold), textColor: UIColor(colors.text),
                    placeholderColor: UIColor(expanded ? colors.inputPlaceholder : colors.text), tint: UIColor(colors.tint),
                    insets: UIEdgeInsets(top: Space.sm, left: 0, bottom: Space.sm, right: 0),
                    multiline: true, lineHeight: 22 * m, minHeight: 48, maxHeight: 140,
                    onFocus: { wearingFocused = $0 }
                )
                if expanded {
                    RNText(t("wearing.hint", ["emergencyNumber": emergencyNumber]), Typography.caption.spec, color: colors.textSecondary)
                }
            }
            .padding(.vertical, expanded ? Space.sm : 0)
            // A 48pt close button, labelled for VoiceOver, at the row's end in both states, so it's
            // never pushed below the edge at large text as the old Dismiss button was.
            Button { showWearing = false } label: {
                SFIcon("xmark", 20, colors.textSecondary).frame(width: 48, height: 48).contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .accessibilityLabel(t("wearing.dismiss"))
            .accessibilityIdentifier("emergency-wearing-dismiss")
        }
        .padding(.leading, Space.md)
        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.lg)
            .strokeBorder(wearingFocused ? colors.borderFocused : colors.inputBorder, lineWidth: wearingFocused ? 2 : 1))
    }

    @Environment(\.dynamicTypeSize) private var dynamicType
    private var fontMultiplier: CGFloat { rnFontMultiplier(dynamicType) }

    /// Calling for help is the most urgent action: it comes first and is the largest, raised red
    /// control with headline type. Found — Safe ends the search, so it is a solid green button too
    /// (an outline vanished in dark mode, a pale tonal fill read as disabled), but shorter, flat and
    /// with smaller type, so it never outranks Call 911. White on semantic success passes 4.5:1.
    private var actionButtons: some View {
        let white = Color(argb: Light.textOnPrimary)
        let emergencyRed = Color(argb: Semantic.errorDark)
        let shape = RoundedRectangle(cornerRadius: Radius.lg)
        return VStack(spacing: Space.md) {
            Button(action: call911) {
                HStack(spacing: Space.md) {
                    SFIcon("phone.fill", 28, white)
                    RNText(t("actions.call911", ["emergencyNumber": emergencyNumber]), Typography.headline.spec, color: white)
                }
                .frame(maxWidth: .infinity, minHeight: 72 - 2 * Space.lg)
                .padding(.vertical, Space.lg).padding(.horizontal, Space.xl)
                .background(shape.fill(emergencyRed))
                .rnShadow(.lg, dark: colors.isDark)
                .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .accessibilityIdentifier("emergency-call-911")
            Button(action: found) {
                HStack(spacing: Space.sm) {
                    SFIcon("checkmark.circle.fill", 22, white)
                    RNText(t("actions.foundSafe"), Typography.bodyBold.spec.size(18), color: white)
                }
                .frame(maxWidth: .infinity, minHeight: 56 - 2 * Space.md)
                .padding(.vertical, Space.md).padding(.horizontal, Space.xl)
                .background(shape.fill(Color(argb: Semantic.success)))
                .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .accessibilityIdentifier("emergency-found")
            HStack(spacing: Space.sm) {
                secondaryAction(t("actions.infoSheet"), "emergency-readout", icon: "doc.text.fill") {
                    Analytics.shared.track(event: .screenViewed, properties: ["screen": "readout", "source": "emergency"])
                    model.path.append(.readout)
                }
                secondaryAction(t("actions.alertCircle"), "emergency-alert-contacts", icon: "bell.fill", action: alertContacts)
            }
            // One height for the pair when one label wraps ("Emergency Info" at large text).
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// Icon above label, centred, at one height for the pair: beside a label that wrapped
    /// ("Emergency Info" at large text) a small icon looked detached and the two unequal.
    private func secondaryAction(_ label: String, _ testID: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: Space.xs) {
                SFIcon(icon, 24, colors.primaryText)
                RNText(label, Typography.bodyBold.spec, color: colors.text, align: .center)
            }
            .frame(maxWidth: .infinity, minHeight: 48 - 2 * Space.md - 2, maxHeight: .infinity)
            .padding(Space.md + 1)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: 1))
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier(testID)
    }

    private var checklist: some View {
        let error = Color(argb: Semantic.error)
        return VStack(alignment: .leading, spacing: Space.sm) {
            HStack {
                RNText(t("checklist.title"), Typography.title.spec, color: colors.text)
                Spacer()
                RNText("\(checked.count)/\(steps.count)", Typography.bodyBold.spec, color: colors.textSecondary)
            }
            .padding(.bottom, Space.xs)
            ForEach(steps, id: \.id) { step in
                let isChecked = checked.contains(step.id)
                let urgentOpen = step.urgent && !isChecked
                let border: CGFloat = urgentOpen ? 2 : 1
                Button { toggle(step.id) } label: {
                    HStack(alignment: .top, spacing: Space.md) {
                        ZStack {
                            Circle().fill(isChecked ? Color(argb: Semantic.success)
                                : step.urgent ? error.opacity(0x18 / 255.0)
                                : Color(argb: colors.isDark ? Neutral.c700 : Neutral.c200))
                            if urgentOpen { Circle().strokeBorder(error, lineWidth: 1.5) }
                            if isChecked {
                                SFIcon("checkmark", 14, Color(argb: Light.textOnPrimary))
                            } else {
                                RNText("\(step.step)", Typography.caption.spec.weight(700),
                                       color: step.urgent ? error : Color(argb: colors.isDark ? Neutral.c300 : Neutral.c600))
                            }
                        }
                        .frame(width: 32, height: 32)
                        .padding(.top, 2)
                        VStack(alignment: .leading, spacing: Space.xs) {
                            HStack(spacing: Space.sm) {
                                // Done reads as done (struck through, secondary ink) yet stays legible.
                                RNText(step.title, Typography.bodyBold.spec, color: isChecked ? colors.textSecondary : colors.text)
                                    .strikethrough(isChecked)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                if urgentOpen {
                                    RNText(t("checklist.priority"), Typography.small.spec.weight(700).spacing(0.5), color: Color(argb: Light.textOnPrimary))
                                        .padding(.horizontal, Space.sm).padding(.vertical, Space.xxs)
                                        .background(RoundedRectangle(cornerRadius: Radius.sm).fill(error))
                                }
                            }
                            RNText(step.description_, TextSpec(size: 16, lineHeight: 20), color: colors.textSecondary)
                            if let hint = step.hint {
                                // Useful advice, so at body size, upright and in ink, not the smallest
                                // text on the screen; the lightbulb alone marks it as a tip.
                                HStack(alignment: .top, spacing: Space.sm) {
                                    SFIcon("lightbulb.fill", 18, colors.primaryText).padding(.top, 3)
                                    RNText(hint, Typography.body.spec, color: colors.text)
                                }
                                .padding(.top, Space.xxs)
                            }
                            if step.id == "familiar_places", !destinations.isEmpty, !isChecked {
                                Rectangle().fill(colors.border).frame(height: 1).padding(.top, Space.sm)
                                VStack(alignment: .leading, spacing: Space.xxs) {
                                    RNText(t("checklist.savedPlaces").uppercased(), Typography.small.spec.weight(600).spacing(0.4), color: colors.textSecondary)
                                        .padding(.bottom, Space.xxs)
                                    RNText(destinations.prefix(5).map(\.name).joined(separator: " • ") + (destinations.count > 5 ? " +\(destinations.count - 5)" : ""),
                                           Typography.caption.spec, color: colors.primaryText, lines: 2)
                                }
                                .padding(.top, Space.sm - Space.xs)
                            }
                        }
                    }
                    .padding(Space.md + border)
                    // A done step steps back: the quiet surface tone, the plain border and secondary
                    // text, so the steps still to do are the loudest cards (a purple done card
                    // outshone them in dark mode). The green check says it is done.
                    .background(RoundedRectangle(cornerRadius: Radius.lg).fill(isChecked ? colors.surface : colors.card))
                    .overlay(RoundedRectangle(cornerRadius: Radius.lg)
                        .strokeBorder(urgentOpen ? error : colors.border, lineWidth: border))
                }
                .buttonStyle(.pressable)
                .accessibilityIdentifier("emergency-step-\(step.id)")
                if step.id == steps.first?.id, showWearing { wearingCard }
            }
        }
    }

    private var tipsCard: some View {
        let techniques = profile?.deescalationTechniques.flatMap { $0.isEmpty ? nil : $0 }
        return VStack(alignment: .leading, spacing: Space.sm) {
            RNText(t("tips.title"), Typography.bodyBold.spec, color: Color(argb: colors.isDark ? Primary.c200 : Primary.c800))
            RNText(techniques.map { "\(t("tips.body"))\n• \($0)" } ?? t("tips.body"), TextSpec(size: 16, lineHeight: 24),
                   color: Color(argb: colors.isDark ? Primary.c300 : Primary.c700), lines: 5)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Space.lg + 1)
        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.isDark ? Color(argb: Primary.c900).opacity(0x60 / 255.0) : Color(argb: Primary.c50)))
        .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(Color(argb: colors.isDark ? Primary.c700 : Primary.c100), lineWidth: 1))
    }

    private func modalView(_ which: EmergencyModal) -> some View {
        let white = Color(argb: Light.textOnPrimary)
        let success = Color(argb: Semantic.success)
        func title(_ key: String) -> some View { RNText(t(key), Typography.title.spec, color: colors.text, align: .center) }
        func message(_ key: String) -> some View { RNText(t(key), TextSpec(size: 16, lineHeight: 22), color: colors.textSecondary, align: .center) }
        func button(_ label: String, _ testID: String, _ bg: Color, _ textColor: Color, outline: Bool = false, _ action: @escaping () -> Void) -> some View {
            Button(action: action) {
                RNText(label, Typography.bodyBold.spec, color: textColor, align: .center)
                    .frame(maxWidth: .infinity, minHeight: 48 - 2 * Space.md)
                    .padding(.vertical, Space.md + (outline ? 1 : 0)).padding(.horizontal, Space.lg + (outline ? 1 : 0))
                    .background(RoundedRectangle(cornerRadius: Radius.lg).fill(bg))
                    .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(outline ? colors.inputBorder : .clear, lineWidth: 1))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .accessibilityIdentifier(testID)
        }
        return ZStack {
            colors.overlay.ignoresSafeArea()
            VStack(spacing: Space.md) {
                switch which {
                case .found:
                    SFIcon("checkmark.circle.fill", 40, success)
                        .frame(width: 72, height: 72)
                        .background(Circle().fill(success.opacity(0x20 / 255.0)))
                        .padding(.bottom, Space.xs)
                    title("modal.found.title")
                    message("modal.found.message")
                    button(tCommon("ok"), "emergency-modal-found-ok", success, white) { leave() }
                case .leave:
                    title("modal.leave.title")
                    message("modal.leave.message")
                    if notificationsDenied {
                        // The message promises the timer goes on; with notifications off its alerts won't come.
                        RNText(t("modal.leave.notificationsOff"), TextSpec(size: 16, lineHeight: 22), color: colors.error, align: .center)
                            .accessibilityIdentifier("emergency-modal-leave-notifications-off")
                        Button {
                            if let url = URL(string: UIApplication.openNotificationSettingsURLString) { UIApplication.shared.open(url) }
                        } label: {
                            RNText(tCommon("openSettings"), Typography.bodyBold.spec, color: colors.tint)
                                .underline()
                                .frame(minHeight: 48 - 2 * Space.md)
                                .padding(.vertical, Space.md)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.pressable)
                        .accessibilityIdentifier("emergency-modal-leave-notifications-settings")
                    }
                    // Two choices, then a third set apart. Keep Searching is the safe one, so it
                    // is the only filled button. Leaving is a text action that says what happens
                    // (the timer keeps running), so the two can't be read as the same choice. End
                    // Emergency is the destructive one: below a rule, edged and washed in red.
                    VStack(spacing: Space.xs) {
                        button(t("modal.leave.stay"), "emergency-modal-leave-stay", colors.primary, colors.textOnPrimary) { modal = nil }
                        button(t("modal.leave.leave"), "emergency-modal-leave-leave", .clear, colors.primaryText) {
                            Analytics.shared.track(event: .emergencyLeave, properties: [:])
                            leave()
                        }
                    }
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, Space.xs)
                    Rectangle().fill(colors.border).frame(height: 1)
                    Button {
                        Analytics.shared.track(event: .emergencyCancelled, properties: endedProperties())
                        // Ended without an outcome: stamp the end time, keep the outcome as it was.
                        record(ended: true)
                        Task {
                            _ = try? await model.store.clearActiveEmergency()
                            leave()
                        }
                    } label: {
                        HStack(spacing: Space.sm) {
                            SFIcon("xmark.circle.fill", 20, colors.errorText)
                            RNText(t("modal.leave.end"), Typography.bodyBold.spec, color: colors.errorText)
                        }
                        .frame(maxWidth: .infinity, minHeight: 48 - 2 * Space.md)
                        .padding(.vertical, Space.md)
                        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.error.opacity(dangerFillOpacity)))
                        .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.error, lineWidth: 1.5))
                        .contentShape(Rectangle()).accessibilityElement(children: .combine)
                    }
                    .buttonStyle(.pressable)
                    .accessibilityIdentifier("emergency-modal-leave-end")
                case .catchUpWarning, .catchUpExpired:
                    let key = which == .catchUpWarning ? "warning" : "expired"
                    let number = ["emergencyNumber": emergencyNumber]
                    RNText(t("alerts.\(key).title", number), Typography.title.spec, color: colors.text, align: .center)
                    RNText(t("alerts.\(key).body", number), TextSpec(size: 16, lineHeight: 22), color: colors.textSecondary, align: .center)
                    button(tCommon("ok"), "emergency-modal-catchup-ok",
                           which == .catchUpExpired ? Color(argb: Semantic.error) : colors.primary,
                           which == .catchUpExpired ? white : colors.textOnPrimary) { modal = nil }
                case .noContacts, .smsError:
                    title(which == .noContacts ? "modal.noContacts.title" : "modal.smsError.title")
                    message(which == .noContacts ? "modal.noContacts.message" : "modal.smsError.message")
                    button(tCommon("ok"), "emergency-modal-info-ok", colors.primary, colors.textOnPrimary) { modal = nil }
                }
            }
            .padding(Space.xl)
            .frame(maxWidth: 340)
            .background(RoundedRectangle(cornerRadius: Radius.xl).fill(colors.card))
            .rnShadow(.lg, dark: colors.isDark)
            .padding(Space.xl)
        }
    }
}

/// An SMS to send (expo-sms sendSMSAsync).
struct SmsDraft: Identifiable {
    let id = UUID()
    let recipients: [String]
    let body: String
}

/// The system message composer, as expo-sms presents it.
struct MessageComposer: UIViewControllerRepresentable {
    let draft: SmsDraft
    let onFinish: (MessageComposeResult) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> MFMessageComposeViewController {
        let composer = MFMessageComposeViewController()
        composer.recipients = draft.recipients
        composer.body = draft.body
        composer.messageComposeDelegate = context.coordinator
        return composer
    }

    func updateUIViewController(_: MFMessageComposeViewController, context _: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, MFMessageComposeViewControllerDelegate {
        let parent: MessageComposer
        init(_ parent: MessageComposer) { self.parent = parent }
        func messageComposeViewController(_: MFMessageComposeViewController, didFinishWith result: MessageComposeResult) {
            parent.onFinish(result)
            parent.dismiss()
        }
    }
}
