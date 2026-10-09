import Shared
import SwiftUI

private let emergencyIdle = Color(argb: 0xFFEF_4444)
private let emergencyActive = Color(argb: 0xFFB9_1C1C)

/// Port of app/index.tsx.
struct HomeView: View {
    let t: Translate
    let emergencyNumber: String
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors
    @State private var profile: Profile?
    @State private var contactCount = 0
    @State private var emergency: ActiveEmergency?
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
                    quickAction("📞", t("quickActions.contacts"),
                                contactCount == 0 ? t("quickActions.contactsNone") : t("quickActions.contactsSaved", ["count": contactCount]),
                                "home-contacts") {
                        Analytics.shared.track(event: .screenViewed, properties: ["screen": "contacts", "source": "home"])
                        model.path.append(.contacts)
                    }
                    quickAction("📍", t("quickActions.places"), t("quickActions.placesSubtitle"), "home-places") {
                        Analytics.shared.track(event: .destinationAddTapped, properties: ["source": "home"])
                        model.path.append(.destinations)
                    }
                }
                .padding(.bottom, Space.lg)
                if hasProfile, let profile { infoCard(profile) }
                settingsRow
            }
            .padding(.horizontal, Space.lg)
            .padding(.top, Space.lg)
            .padding(.bottom, Space.xxl)
        }
        .scrollIndicators(.hidden)
        .background(colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        // Reloaded every time home is shown (useFocusEffect).
        .task {
            Analytics.shared.screen(path: "home", properties: [:])
            profile = try? await model.store.profile()
            contactCount = ((try? await model.store.contacts()) ?? []).count
            emergency = try? await model.store.activeEmergency()
            updateCountdown()
        }
        .onReceive(tick) { _ in updateCountdown() }
    }

    /// Re-derived from the start time every tick; counting ticks drifts in the background (F-16).
    private func updateCountdown() {
        guard let started = emergency.flatMap({ parseISO($0.startedAt) }) else { return }
        secondsLeft = EmergencyKt.secondsRemaining(startedAtMs: started.epochMs, nowMs: AppClock.shared.nowMs())
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
                    SFIcon("pencil", 10, Color(argb: Light.textOnPrimary))
                        .frame(width: 20, height: 20)
                        .background(Circle().fill(colors.tint))
                        .overlay(Circle().strokeBorder(Color(argb: Light.textOnPrimary), lineWidth: 2))
                }
            }
            .buttonStyle(.pressable)
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
                "time": EmergencyKt.formatCountdown(seconds: secondsLeft), "checked": emergency?.checkedSteps.count ?? 0, "total": 11,
            ]))
        }
        let elapsed = emergency != nil && !expired ? CGFloat(EmergencyKt.SEARCH_WINDOW_SECONDS - secondsLeft) / CGFloat(EmergencyKt.SEARCH_WINDOW_SECONDS) : 0
        return Button { model.path.append(.emergency) } label: {
            HStack(spacing: 0) {
                HStack(spacing: Space.md) {
                    SFIcon("exclamationmark.triangle.fill", 28, white)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(Color.white.opacity(0.2)))
                    VStack(alignment: .leading, spacing: 0) {
                        RNText(title, Typography.bodyBold.spec, color: white).padding(.bottom, 2)
                        RNText(subtitle, Typography.caption.spec, color: .white.opacity(0.8))
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                SFIcon("chevron.right", 20, .white.opacity(0.7))
            }
            .padding(.vertical, Space.lg)
            .padding(.horizontal, Space.xl)
            .background(alignment: .leading) {
                ZStack(alignment: .leading) {
                    emergency != nil ? emergencyActive : emergencyIdle
                    GeometryReader { geo in emergencyIdle.frame(width: geo.size.width * elapsed) }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: Radius.xl))
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

    private func quickAction(_ emoji: String, _ title: String, _ subtitle: String, _ testID: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: Space.xs) {
                RNText(emoji, TextSpec(size: 22, lineHeight: 24), color: colors.text)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(colors.primaryLight))
                    .padding(.bottom, Space.xs)
                RNText(title, Typography.bodyBold.spec, color: colors.text)
                RNText(subtitle, Typography.caption.spec, color: colors.textSecondary)
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
                HStack(spacing: 0) {
                    RNText(t("emergencyInfo.title"), Typography.bodyBold.spec, color: colors.text).frame(maxWidth: .infinity, alignment: .leading)
                    HStack(spacing: Space.xxs) {
                        RNText(t("emergencyInfo.script911"), Typography.caption.spec.weight(600), color: colors.tint)
                        SFIcon("chevron.right", 14, colors.tint)
                    }
                    .padding(.horizontal, Space.sm).padding(.vertical, Space.xs)
                    .background(RoundedRectangle(cornerRadius: Radius.md).fill(colors.primaryLight))
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

    /// AppCard holding a Pressable: the testID covers the inside of the card's border.
    private var settingsRow: some View {
        Button {
            Analytics.shared.track(event: .screenViewed, properties: ["screen": "settings", "source": "home"])
            model.path.append(.settings)
        } label: {
            HStack(spacing: Space.sm) {
                SFIcon("gearshape", 18, colors.textSecondary)
                RNText(t("settingsLink"), Typography.body.spec, color: colors.text).frame(maxWidth: .infinity, alignment: .leading)
                SFIcon("chevron.right", 16, colors.textSecondary)
            }
            .padding(Space.lg)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier("home-settings")
        .padding(1)
        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: 1))
        .padding(.bottom, Space.sm)
    }
}
