import AudioToolbox
import MessageUI
import Shared
import SwiftUI

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
    @State private var secondsLeft: Int32 = EmergencyKt.SEARCH_WINDOW_SECONDS
    @State private var profile: Profile?
    @State private var destinations: [DestinationEntity] = []
    @State private var modal: EmergencyModal?
    @State private var sms: SmsDraft?
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
                    if result == .sent { Analytics.shared.track(event: .emergencyContactsAlerted, properties: ["recipient_count": draft.recipients.count]) }
                    let outcome: SmsResult = switch result {
                    case .sent: .sent
                    case .cancelled: .cancelled
                    default: .failed
                    }
                    Analytics.shared.track(event: .emergencySmsResult, properties: EmergencyAlertsKt.smsResultProperties(
                        result: outcome, recipients: Int32(draft.recipients.count)
                    ))
                }
                .ignoresSafeArea()
            }
    }

    @ViewBuilder private var content: some View {
        if state == nil {
            RNText(t("loading"), TextSpec(size: 16, lineHeight: 24), color: colors.text)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            VStack(spacing: 0) {
                ScreenHeader(title: t("screenTitle"), testID: "emergency", onBack: { modal = .leave },
                             titleIcon: ("exclamationmark.triangle.fill", Color(argb: Semantic.error)))
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
                        if showWearing { wearingCard }
                        actionButtons
                        checklist
                        tipsCard
                        Color.clear.frame(height: Space.xxl)
                    }
                    .padding(Space.lg)
                }
                .scrollIndicators(.hidden)
                .scrollDismissesKeyboard(.interactively)
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("emergency-screen")
        }
    }

    // MARK: - State

    /// Load or start the emergency (spec/storage.md: active_emergency, incidents).
    private func load() async {
        steps = EmergencyKt.buildInitialSteps(t: t.lookup, emergencyNumber: emergencyNumber)
        profile = try? await model.store.profile()
        destinations = (try? await model.store.destinations()) ?? []
        if let saved = try? await model.store.activeEmergency() {
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
        if let started = state.flatMap({ parseISO($0.startedAt) }) {
            secondsLeft = EmergencyKt.secondsRemaining(startedAtMs: started.epochMs, nowMs: AppClock.shared.nowMs())
        }
        Task {
            guard let alert = try? await model.away.shown(nowMs: AppClock.shared.nowMs()) else { return }
            buzz(alert)
            modal = alert == .warning ? .catchUpWarning : .catchUpExpired
        }
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
        Analytics.shared.track(event: .emergency911Called, properties: [
            "seconds_elapsed": EmergencyKt.SEARCH_WINDOW_SECONDS - secondsLeft, "checked_count": checked.count,
        ])
        // Calling always marks the step done; a second call must not un-check it (F-17).
        if !checked.contains("call_911") { toggle("call_911") }
        dial(emergencyNumber, target: .emergency, screen: "emergency", tCommon: tCommon)
        record(outcome: "911_called")
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

    private var timerCard: some View {
        let expired = secondsLeft == 0
        let white = Color(argb: Light.textOnPrimary)
        let progress = steps.isEmpty ? 0 : CGFloat(checked.count) / CGFloat(steps.count)
        return VStack(spacing: Space.xs) {
            RNText((expired ? t("timer.labelExpired", ["emergencyNumber": emergencyNumber]) : t("timer.labelActive")).uppercased(),
                   Typography.caption.spec.weight(600).spacing(1), color: .white.opacity(0.75))
                .accessibilityIdentifier("emergency-timer-label")
            RNText(EmergencyKt.formatCountdown(seconds: secondsLeft), TextSpec(size: 60, lineHeight: 72, weight: 700, letterSpacing: 2), color: white)
                .accessibilityIdentifier("emergency-timer")
            RNText(expired ? t("timer.hintExpired", ["emergencyNumber": emergencyNumber]) : t("timer.hintActive"),
                   Typography.body.spec, color: .white.opacity(0.9), align: .center)
                .padding(.top, Space.xs)
            VStack(spacing: Space.xs) {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.white.opacity(0.25))
                        Capsule().fill(white).frame(width: geo.size.width * progress)
                    }
                }
                .frame(height: 6)
                let progressText = t("timer.stepsProgress", ["checked": checked.count, "total": steps.count])
                RNText(progressText, Typography.caption.spec, color: .white.opacity(0.9), align: .center)
                    .frame(maxWidth: .infinity)
                    .rnID("emergency-progress", label: progressText)
            }
            .padding(.top, Space.md)
        }
        .frame(maxWidth: .infinity)
        .padding(Space.xl)
        .background(RoundedRectangle(cornerRadius: Radius.xl).fill(expired ? Color(argb: Semantic.error) : Color(argb: Primary.c700)))
        .rnShadow(.md, dark: colors.isDark)
    }

    private var wearingCard: some View {
        VStack(alignment: .leading, spacing: Space.sm) {
            RNText(t("wearing.label"), Typography.bodyBold.spec, color: colors.text)
            RNText(t("wearing.hint", ["emergencyNumber": emergencyNumber]), Typography.caption.spec, color: colors.textSecondary)
                .padding(.top, -Space.xs)
            RNTextInput(
                text: $wearing, placeholder: t("wearing.placeholder"), testID: "emergency-wearing-input",
                font: .systemFont(ofSize: 16 * fontMultiplier), textColor: UIColor(colors.text),
                placeholderColor: UIColor(Color(argb: Neutral.c400)), tint: UIColor(colors.tint),
                insets: UIEdgeInsets(top: Space.sm - 1, left: Space.md, bottom: Space.sm - 1, right: Space.md),
                multiline: true, lineHeight: 20 * fontMultiplier, minHeight: 62, maxHeight: 118
            )
            .padding(1)
            .background(RoundedRectangle(cornerRadius: Radius.md).fill(colors.isDark ? Color(argb: Neutral.c800) : Color(argb: Neutral.c50)))
            .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(colors.inputBorder, lineWidth: 1))
            Button { showWearing = false } label: {
                RNText(t("wearing.dismiss"), Typography.caption.spec, color: colors.textSecondary).padding(.vertical, Space.xs)
                    .contentShape(Rectangle()).accessibilityElement(children: .combine)
            }
            .buttonStyle(.pressable)
            .accessibilityIdentifier("emergency-wearing-dismiss")
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(Space.lg + 1)
        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: 1))
    }

    @Environment(\.dynamicTypeSize) private var dynamicType
    private var fontMultiplier: CGFloat { rnFontMultiplier(dynamicType) }

    private var actionButtons: some View {
        let expired = secondsLeft == 0
        let white = Color(argb: Light.textOnPrimary)
        let error = Color(argb: Semantic.error)
        let large = Typography.bodyBold.spec.size(18)
        return VStack(spacing: Space.sm) {
            Button(action: found) {
                HStack(spacing: Space.sm) {
                    SFIcon("checkmark.circle.fill", 22, white)
                    RNText(t("actions.foundSafe"), large, color: white)
                }
                .frame(maxWidth: .infinity, minHeight: 56 - 2 * Space.lg)
                .padding(.vertical, Space.lg).padding(.horizontal, Space.xl)
                .background(RoundedRectangle(cornerRadius: Radius.lg).fill(Color(argb: Semantic.success)))
                .rnShadow(.sm, dark: colors.isDark)
            }
            .buttonStyle(.pressable)
            .accessibilityIdentifier("emergency-found")
            Button(action: call911) {
                RNText(t("actions.call911", ["emergencyNumber": emergencyNumber]), large, color: expired ? white : error)
                    .frame(maxWidth: .infinity, minHeight: 56 - 2 * Space.lg)
                    .padding(.vertical, Space.lg + (expired ? 0 : 2)).padding(.horizontal, Space.xl)
                    .background(RoundedRectangle(cornerRadius: Radius.lg).fill(expired ? error : .clear))
                    .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(error, lineWidth: expired ? 0 : 2))
                    .rnShadow(.sm, dark: colors.isDark && expired)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .accessibilityIdentifier("emergency-call-911")
            HStack(spacing: Space.sm) {
                secondaryAction(t("actions.infoSheet"), "emergency-readout") {
                    Analytics.shared.track(event: .screenViewed, properties: ["screen": "readout", "source": "emergency"])
                    model.path.append(.readout)
                }
                secondaryAction(t("actions.alertCircle"), "emergency-alert-contacts", action: alertContacts)
            }
        }
    }

    private func secondaryAction(_ label: String, _ testID: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            RNText(label, Typography.bodyBold.spec, color: colors.text)
                .frame(maxWidth: .infinity, minHeight: 48 - 2 * Space.md - 2)
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
                            Circle().fill(isChecked ? Color(argb: Primary.c600)
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
                                RNText(step.title, Typography.bodyBold.spec, color: colors.text)
                                    .strikethrough(isChecked)
                                    .opacity(isChecked ? 0.6 : 1)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                if urgentOpen {
                                    RNText(t("checklist.priority"), Typography.small.spec.weight(700).spacing(0.5), color: Color(argb: Light.textOnPrimary))
                                        .padding(.horizontal, Space.sm).padding(.vertical, Space.xxs)
                                        .background(RoundedRectangle(cornerRadius: Radius.sm).fill(error))
                                }
                            }
                            RNText(step.description_, TextSpec(size: 16, lineHeight: 20), color: colors.textSecondary)
                            if let hint = step.hint {
                                RNText("💡 \(hint)", Typography.caption.spec.italic(), color: Color(argb: Primary.c600))
                            }
                            if step.id == "familiar_places", !destinations.isEmpty, !isChecked {
                                Rectangle().fill(colors.border).frame(height: 1).padding(.top, Space.sm)
                                VStack(alignment: .leading, spacing: Space.xxs) {
                                    RNText(t("checklist.savedPlaces").uppercased(), Typography.small.spec.weight(600).spacing(0.4), color: colors.textSecondary)
                                        .padding(.bottom, Space.xxs)
                                    RNText(destinations.prefix(5).map(\.name).joined(separator: " • ") + (destinations.count > 5 ? " +\(destinations.count - 5)" : ""),
                                           Typography.caption.spec, color: Color(argb: Primary.c600), lines: 2)
                                }
                                .padding(.top, Space.sm - Space.xs)
                            }
                        }
                    }
                    .padding(Space.md + border)
                    .background(RoundedRectangle(cornerRadius: Radius.lg).fill(isChecked ? Color(argb: colors.isDark ? Primary.c900 : Primary.c50) : colors.card))
                    .overlay(RoundedRectangle(cornerRadius: Radius.lg)
                        .strokeBorder(urgentOpen ? error : isChecked ? Color(argb: Primary.c300) : colors.border, lineWidth: border))
                    .opacity(isChecked ? 0.75 : 1)
                }
                .buttonStyle(.pressable)
                .accessibilityIdentifier("emergency-step-\(step.id)")
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
        func button(_ label: String, _ testID: String, _ bg: Color, _ textColor: Color, outline: Bool = false, rowItem: Bool = false, _ action: @escaping () -> Void) -> some View {
            Button(action: action) {
                RNText(label, Typography.bodyBold.spec, color: textColor)
                    .frame(maxWidth: .infinity, minHeight: 48 - 2 * Space.md)
                    .padding(.vertical, Space.md + (outline ? 1 : 0)).padding(.horizontal, Space.lg + (outline ? 1 : 0))
                    .frame(maxHeight: outline || rowItem ? .infinity : nil)
                    .background(RoundedRectangle(cornerRadius: Radius.md).fill(bg))
                    .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(outline ? colors.border : .clear, lineWidth: 1))
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
                    HStack(spacing: Space.md) {
                        button(t("modal.leave.stay"), "emergency-modal-leave-stay", .clear, colors.text, outline: true) { modal = nil }
                        button(t("modal.leave.leave"), "emergency-modal-leave-leave", colors.primary, white, rowItem: true) {
                            Analytics.shared.track(event: .emergencyLeave, properties: [:])
                            leave()
                        }
                    }
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, Space.xs)
                    Button {
                        Analytics.shared.track(event: .emergencyCancelled, properties: endedProperties())
                        // Ended without an outcome: stamp the end time, keep the outcome as it was.
                        record(ended: true)
                        Task {
                            _ = try? await model.store.clearActiveEmergency()
                            leave()
                        }
                    } label: {
                        RNText(t("modal.leave.end"), Typography.bodyBold.spec, color: Color(argb: Semantic.error))
                            .padding(.vertical, Space.md)
                            .contentShape(Rectangle()).accessibilityElement(children: .combine)
                    }
                    .buttonStyle(.pressable)
                    .accessibilityIdentifier("emergency-modal-leave-end")
                    .padding(.top, Space.xs)
                case .catchUpWarning, .catchUpExpired:
                    let key = which == .catchUpWarning ? "warning" : "expired"
                    let number = ["emergencyNumber": emergencyNumber]
                    RNText(t("alerts.\(key).title", number), Typography.title.spec, color: colors.text, align: .center)
                    RNText(t("alerts.\(key).body", number), TextSpec(size: 16, lineHeight: 22), color: colors.textSecondary, align: .center)
                    button(tCommon("ok"), "emergency-modal-catchup-ok",
                           which == .catchUpExpired ? Color(argb: Semantic.error) : colors.primary, white) { modal = nil }
                case .noContacts, .smsError:
                    title(which == .noContacts ? "modal.noContacts.title" : "modal.smsError.title")
                    message(which == .noContacts ? "modal.noContacts.message" : "modal.smsError.message")
                    button(tCommon("ok"), "emergency-modal-info-ok", colors.primary, white) { modal = nil }
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
