import Shared
import SwiftUI

// Starting a search and a search under way are both the deep red of Call 911: an emergency in
// progress must never look calmer than the idle Start button (a purple running state did). The
// running state is told apart at a glance by its shape, not a subtitle: the live countdown is the
// headline and a brighter red sweep covers the time already used (an inset white ring was
// dropped: inside the red it read as a second outline, noise). White text passes 4.5:1 on both reds.
private let emergencyIdle = Color(argb: Semantic.errorDark)
private let emergencyActive = Color(argb: Semantic.errorDark)
private let emergencySweep = Color(argb: Semantic.error)

/// Port of app/index.tsx.
struct HomeView: View {
    let t: Translate
    let emergencyNumber: String
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors
    @State private var profile: Profile?
    @State private var contactCount = 0
    @State private var emergency: ActiveEmergency?
    /// Nil until read, and stays nil if the read fails: then the readiness card isn't shown,
    /// rather than telling the user to add places they already have.
    @State private var placeCount: Int?
    @State private var secondsLeft: Int32 = 0
    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        let hasProfile = !(profile?.name.isEmpty ?? true)
        let expired = emergency != nil && secondsLeft == 0
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header(hasProfile)
                emergencyButton(expired)
                HStack(spacing: Space.md) {
                    quickAction("phone.fill", t("quickActions.contacts"),
                                contactCount == 0 ? t("quickActions.contactsNone") : t("quickActions.contactsSaved", ["count": contactCount]),
                                "home-contacts") {
                        Analytics.shared.track(event: .screenViewed, properties: ["screen": "contacts", "source": "home"])
                        model.path.append(.contacts)
                    }
                    quickAction("mappin.and.ellipse", t("quickActions.places"), t("quickActions.placesSubtitle"), "home-places") {
                        Analytics.shared.track(event: .destinationAddTapped, properties: ["source": "home"])
                        model.path.append(.destinations)
                    }
                }
                .padding(.bottom, Space.lg)
                if hasProfile, let profile { infoCard(profile) }
                if let placeCount {
                    readinessCard(ReadinessKt.readinessChecklist(profile: profile, contacts: Int32(contactCount), places: Int32(placeCount)))
                }
                settingsRow
            }
            .padding(.horizontal, Space.lg)
            .padding(.top, Space.lg)
            .padding(.bottom, Space.xxl)
        }
        .scrollIndicators(.hidden)
        .scrollStopsAtSafeArea()
        .background(colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        // Reloaded every time home is shown (useFocusEffect).
        .task {
            Analytics.shared.screen(path: "home", properties: [:])
            // The active emergency first: nothing optional delays the Start/Resume button's state.
            emergency = try? await model.store.activeEmergency()
            profile = try? await model.store.profile()
            contactCount = ((try? await model.store.contacts()) ?? []).count
            placeCount = (try? await model.store.destinations())?.count
            updateCountdown()
        }
        .onReceive(tick) { _ in updateCountdown() }
    }

    /// Re-derived from the start time every tick; counting ticks drifts in the background (F-16).
    private func updateCountdown() {
        guard let started = emergency?.startedAtMs?.int64Value else { return }
        secondsLeft = EmergencyKt.secondsRemaining(startedAtMs: started, nowMs: AppClock.shared.nowMs())
    }

    private func header(_ hasProfile: Bool) -> some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: Space.xxs) {
                if hasProfile {
                    RNText(t("caringFor").uppercased(), Typography.caption.spec.weight(600).spacing(0.8), color: colors.textSecondary)
                }
                RNText(hasProfile ? profile!.name : t("appTitle"), Typography.headline.spec, color: colors.text, lines: 1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button {
                Analytics.shared.track(event: .screenViewed, properties: ["screen": "profile", "source": "home"])
                model.path.append(.profile)
            } label: {
                ZStack(alignment: .bottomTrailing) {
                    if let image = loadPhoto(profile?.photoUri) {
                        Image(uiImage: image).resizable().scaledToFill().frame(width: 56, height: 56).clipShape(Circle())
                            .accessibilityIdentifier("home-photo")
                    } else {
                        SFIcon("person.fill", 28, colors.textSecondary)
                            .frame(width: 56, height: 56)
                            .background(Circle().fill(colors.primaryLight))
                            .overlay(Circle().strokeBorder(colors.border, lineWidth: 1))
                    }
                    SFIcon("pencil", 10, colors.textOnPrimary)
                        .frame(width: 20, height: 20)
                        .background(Circle().fill(colors.primary))
                        .overlay(Circle().strokeBorder(colors.background, lineWidth: 2))
                }
            }
            .buttonStyle(.pressable)
            .accessibilityLabel(t("editProfile"))
            .accessibilityIdentifier("home-profile")
            .padding(.leading, Space.md)
        }
        .padding(.top, Space.xs)
        .padding(.bottom, Space.xl)
    }

    private func emergencyButton(_ expired: Bool) -> some View {
        let white = Color(argb: Light.textOnPrimary)
        let (title, subtitle): (String, String) = if emergency == nil {
            (t("emergencyButton.startTitle"), t("emergencyButton.startSubtitle"))
        } else if expired {
            (t("emergencyButton.timerExpiredTitle", ["emergencyNumber": emergencyNumber]), t("emergencyButton.timerExpiredSubtitle"))
        } else {
            (t("emergencyButton.activeTitle"), t("emergencyButton.remaining", [
                "time": EmergencyKt.formatCountdown(seconds: secondsLeft), "checked": emergency?.checkedSteps.count ?? 0, "total": EmergencyKt.EMERGENCY_STEP_COUNT,
            ]))
        }
        let running = emergency != nil && !expired
        let elapsed = running ? CGFloat(EmergencyKt.SEARCH_WINDOW_SECONDS - secondsLeft) / CGFloat(EmergencyKt.SEARCH_WINDOW_SECONDS) : 0
        return Button { model.path.append(.emergency) } label: {
            Group {
                if running {
                    runningEmergency(checked: emergency?.checkedSteps.count ?? 0)
                } else {
                    HStack(spacing: 0) {
                        HStack(spacing: Space.md) {
                            SFIcon("exclamationmark.triangle.fill", 28, white)
                                .frame(width: 44, height: 44)
                                .background(Circle().fill(Color.white.opacity(0.2)))
                            VStack(alignment: .leading, spacing: 0) {
                                RNText(title, Typography.bodyBold.spec, color: white).padding(.bottom, 2)
                                // 14pt at least: older eyes read it in sun, white on red.
                                RNText(subtitle, Typography.label.spec.weight(500).spacing(0), color: white)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        SFIcon("chevron.right", 20, .white.opacity(0.7))
                    }
                }
            }
            .padding(.vertical, Space.lg)
            .padding(.horizontal, Space.xl)
            .background(alignment: .leading) {
                ZStack(alignment: .leading) {
                    emergency != nil ? emergencyActive : emergencyIdle
                    GeometryReader { geo in emergencySweep.frame(width: geo.size.width * elapsed) }
                }
            }
            // The same radius as every other card on home.
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .rnShadow(.md, dark: colors.isDark)
        }
        .buttonStyle(.pressable)
        // One accessible element read as its texts, like RN's touchable (Maestro matches it).
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title), \(subtitle)")
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("home-start-emergency")
        .padding(.bottom, Space.lg)
    }

    /// The home button while a search runs: "EMERGENCY IN PROGRESS" as a small label, the time
    /// left as the headline (display size, so it reads from arm's length), then the steps done.
    private func runningEmergency(checked: Int) -> some View {
        let white = Color(argb: Light.textOnPrimary)
        return HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: Space.xxs) {
                HStack(spacing: Space.xs) {
                    SFIcon("timer", 18, white)
                    RNText(t("emergencyButton.activeTitle").uppercased(), Typography.label.spec.spacing(1), color: white)
                }
                HStack(alignment: .bottom, spacing: Space.sm) {
                    RNText(EmergencyKt.formatCountdown(seconds: secondsLeft), Typography.display.spec, color: white)
                    // Title size, so "left" reads as part of the countdown rather than a footnote to it.
                    RNText(t("emergencyButton.timeLeft"), Typography.title.spec, color: white).padding(.bottom, Space.xxs)
                }
                RNText(t("emergencyButton.stepsDone", ["checked": checked, "total": EmergencyKt.EMERGENCY_STEP_COUNT]),
                       Typography.body.spec.weight(500), color: white)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            SFIcon("chevron.right", 24, white)
        }
    }

    private func quickAction(_ icon: String, _ title: String, _ subtitle: String, _ testID: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: Space.xs) {
                SFIcon(icon, 22, colors.primaryText)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(colors.iconContainer))
                    .padding(.bottom, Space.xs)
                RNText(title, Typography.bodyBold.spec, color: colors.text)
                // 14pt, not caption: the counts are read at a glance by older eyes.
                RNText(subtitle, Typography.label.spec.weight(400).spacing(0), color: colors.textSecondary)
            }
            .frame(maxWidth: .infinity, minHeight: 110 - 2 * Space.lg - 2)
            .padding(Space.lg)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: 1))
            .padding(-1).padding(1)
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier(testID)
    }

    private func infoCard(_ p: Profile) -> some View {
        let items: [(String, String)] = [
            ("emergencyInfo.healthNotes", p.medicalConditions), ("emergencyInfo.medications", p.medications),
            ("emergencyInfo.cognitiveStatus", p.cognitiveStatus), ("emergencyInfo.deescalation", p.deescalationTechniques),
        ].compactMap { key, value in value.flatMap { $0.isEmpty ? nil : (key, $0) } }
        return Button {
            Analytics.shared.track(event: .screenViewed, properties: ["screen": "readout", "source": "home"])
            model.path.append(.readout)
        } label: {
            VStack(alignment: .leading, spacing: 0) {
                // One set with the Contacts and Places cards above: the same 44pt round icon, a
                // bodyBold title and a smaller second line, laid out as a row because the card is
                // full width. The title stays on one line at large text; "911 Script" is the second
                // line, in the accent colour, and the chevron says the whole card opens it.
                HStack(spacing: Space.md) {
                    SFIcon("doc.text.fill", 22, colors.primaryText)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(colors.iconContainer))
                    VStack(alignment: .leading, spacing: Space.xxs) {
                        RNText(t("emergencyInfo.title"), Typography.bodyBold.spec, color: colors.text, lines: 1)
                        RNText(t("emergencyInfo.script911"), Typography.caption.spec.weight(600), color: colors.primaryText)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    SFIcon("chevron.right", 20, colors.primaryText)
                }
                if !items.isEmpty {
                    Rectangle().fill(colors.border).frame(height: 1).padding(.top, Space.md)
                    VStack(alignment: .leading, spacing: Space.md) {
                        ForEach(items, id: \.0) { label, value in
                            VStack(alignment: .leading, spacing: 0) {
                                RNText(t(label).uppercased(), Typography.small.spec.weight(700).spacing(0.6), color: colors.textSecondary)
                                    .padding(.bottom, Space.xxs)
                                RNText(value, Typography.body.spec, color: colors.text, lines: 2)
                            }
                        }
                    }
                    .padding(.top, Space.md)
                }
            }
            .padding(Space.lg + 1)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: 1))
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier("home-readout")
        .padding(.bottom, Space.lg)
    }

    /// "Ready for an emergency": what's filled in for the script and the search, each line
    /// opening the screen that fills it in. Done is said by the icon and by the word, not by
    /// colour alone; a missing line carries an "Add" action in the accent colour.
    private func readinessCard(_ checks: [ReadinessCheck]) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: Space.sm) {
                RNText(t("readiness.title"), Typography.bodyBold.spec, color: colors.text).frame(maxWidth: .infinity, alignment: .leading)
                RNText(t("readiness.summary", ["done": checks.filter(\.done).count, "total": checks.count]),
                       Typography.label.spec.weight(500).spacing(0), color: colors.textSecondary)
            }
            .padding(.horizontal, Space.lg).padding(.vertical, Space.sm)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            ForEach(checks, id: \.item.key) { check in
                let label = t("readiness.items.\(check.item.key)")
                let state = t(check.done ? "readiness.done" : "readiness.add")
                Button { openReadiness(check.item) } label: {
                    HStack(spacing: Space.md) {
                        // To do is a plus in a circle (an empty circle read as a radio button),
                        // done a green check.
                        SFIcon(check.done ? "checkmark.circle.fill" : "plus.circle", 22, check.done ? colors.success : colors.primaryText)
                        RNText(label, Typography.body.spec, color: colors.text).frame(maxWidth: .infinity, alignment: .leading)
                        if check.done {
                            RNText(state, Typography.label.spec.spacing(0), color: colors.textSecondary)
                            SFIcon("chevron.right", 16, colors.textSecondary)
                        } else {
                            // A missing item's Add looks like the button it is: a tonal capsule at
                            // body size, still quieter than the red emergency banner above.
                            RNText(state, Typography.bodyBold.spec, color: colors.primaryText)
                                .padding(.horizontal, Space.lg)
                                .frame(minHeight: 36)
                                .background(Capsule().fill(colors.primaryLight))
                        }
                    }
                    .padding(.horizontal, Space.lg).padding(.vertical, Space.sm)
                    .frame(minHeight: 48)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.pressable)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(label), \(state)")
                .accessibilityAddTraits(.isButton)
            }
        }
        .padding(.vertical, Space.sm)
        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: 1))
        .padding(.bottom, Space.lg)
    }

    /// Each line reports what its home shortcut reports (avatar, Contacts tile, Places tile).
    private func openReadiness(_ item: ReadinessItem) {
        if item == .contacts {
            Analytics.shared.track(event: .screenViewed, properties: ["screen": "contacts", "source": "home"])
            model.path.append(.contacts)
        } else if item == .places {
            Analytics.shared.track(event: .destinationAddTapped, properties: ["source": "home"])
            model.path.append(.destinations)
        } else {
            Analytics.shared.track(event: .screenViewed, properties: ["screen": "profile", "source": "home"])
            model.path.append(.profile)
        }
    }

    /// A plain row, not a card: Settings is used rarely, so it doesn't carry the weight of
    /// Emergency Info or the quick actions above it.
    private var settingsRow: some View {
        Button {
            Analytics.shared.track(event: .screenViewed, properties: ["screen": "settings", "source": "home"])
            model.path.append(.settings)
        } label: {
            HStack(spacing: Space.sm) {
                SFIcon("gearshape", 18, colors.textSecondary)
                RNText(t("settingsLink"), Typography.body.spec, color: colors.textSecondary).frame(maxWidth: .infinity, alignment: .leading)
                SFIcon("chevron.right", 16, colors.textSecondary)
            }
            .padding(.horizontal, Space.lg).padding(.vertical, Space.md)
            .frame(minHeight: 48)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier("home-settings")
        .padding(.bottom, Space.sm)
    }
}
