import Shared
import SwiftUI
import UIKit

/// JS Date.toLocaleString() as Hermes formats it on iOS: the locale's "yMdjms" skeleton.
func localeDateTime(_ ms: Int64) -> String {
    let f = DateFormatter()
    f.setLocalizedDateFormatFromTemplate("yMdjms")
    return f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
}

/// Port of app/readout.tsx: the 911 info sheet.
struct ReadoutView: View {
    let t: Translate
    let tCommon: Translate
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors
    @State private var profile: Profile?
    @State private var contacts: [ContactEntity] = []
    @State private var lastSeenMs: Int64?
    @State private var expanded = false
    @State private var copied: String?

    private var warning: Color { Color(argb: Semantic.warning) }
    private var success: Color { Color(argb: Semantic.success) }
    private var error: Color { Color(argb: Semantic.error) }
    private var white: Color { Color(argb: Light.textOnPrimary) }
    private var emergencyNumber: String { tCommon("emergencyNumber") }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: t("screenTitle"), testID: "readout", onBack: {
                Analytics.shared.track(event: .screenViewed, properties: ["screen": "home", "source": "readout_back"])
                model.path.removeLast()
            })
            if let p = profile { sheet(p) } else { Spacer() }
        }
        .background(colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .task {
            contacts = (try? await model.store.emergencyContacts()) ?? []
            // Last seen is when the active emergency started (context/ProfileContext.tsx).
            lastSeenMs = (try? await model.store.activeEmergency()).flatMap { parseISO($0.startedAt)?.epochMs }
            profile = try? await model.store.profile()
        }
    }

    private func sheet(_ p: Profile) -> some View {
        let lastSeen = lastSeenMs.map(localeDateTime)
        let today = Calendar.current.dateComponents([.year, .month, .day], from: Date(timeIntervalSince1970: Double(AppClock.shared.nowMs()) / 1000))
        let input = ReadoutInput(profile: p, lastSeenTime: lastSeen,
                                 today: Kotlinx_datetimeLocalDate(year: Int32(today.year!), month: Int32(today.month!), day: Int32(today.day!)))
        let script = ReadoutKt.buildScript(input: input, t: t.lookup)
        let missing = ReadoutKt.missingScriptDetails(input: input, t: t.lookup)
        let hand = p.dominantHand.flatMap { $0.isEmpty || $0 == "unknown" ? nil : $0 }
        return ScrollView {
            VStack(alignment: .leading, spacing: Space.md) {
                Button {
                    Analytics.shared.track(event: .readout911Called, properties: [:])
                    dial(emergencyNumber)
                } label: {
                    RNText(t("callButton"), Typography.bodyLarge.spec.weight(700), color: white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Space.lg)
                        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(error))
                        .rnShadow(.sm, dark: colors.isDark)
                }
                .buttonStyle(.pressable)
                .accessibilityIdentifier("readout-call-911")

                card {
                    Button { expanded.toggle() } label: {
                        HStack {
                            sectionLabel("phone.connection.fill", t("sections.script.label"), success)
                            Spacer()
                            SFIcon(expanded ? "chevron.up" : "chevron.down", 16, colors.textSecondary)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.pressable)
                    .accessibilityIdentifier("readout-script-toggle")
                    if expanded {
                        RNText(t("sections.script.hint"), Typography.caption.spec, color: colors.textSecondary)
                            .padding(.top, Space.xxs).padding(.bottom, Space.xs)
                        RNText(script, TextSpec(size: 16, lineHeight: 22), color: colors.text)
                            .accessibilityIdentifier("readout-script-text")
                        if !missing.isEmpty {
                            RNText(t("sections.script.missingDetails", ["details": missing.joined(separator: ", ")]),
                                   Typography.caption.spec.lineHeight(18), color: warning)
                                .padding(.top, Space.sm)
                                .accessibilityIdentifier("readout-script-missing")
                        }
                    } else {
                        RNText(t("sections.script.collapsed"), Typography.caption.spec, color: colors.textSecondary)
                            .padding(.top, Space.xxs).padding(.bottom, Space.xs)
                    }
                }

                identityCard(p)

                if let lastSeen {
                    card {
                        sectionLabel("clock.fill", t("sections.location.title"), colors.primary)
                        infoRow(t("sections.location.time"), lastSeen)
                    }
                }

                // Mobility and dominant hand live here too, so they alone must show the card (F-26).
                if [p.height, p.weight, p.hairColor, p.eyeColor, p.identifyingMarks, hand, p.mobilityLevel].contains(where: { !($0 ?? "").isEmpty }) {
                    card {
                        sectionLabel("eye.fill", t("sections.appearance.title"), colors.primary)
                        FlowLayout(spacing: Space.sm) {
                            if let v = nonEmpty(p.height) { chip(t("sections.appearance.height"), v) }
                            if let v = nonEmpty(p.weight) { chip(t("sections.appearance.weight"), v) }
                            if let v = nonEmpty(p.hairColor) { chip(t("sections.appearance.hair"), v) }
                            if let v = nonEmpty(p.eyeColor) { chip(t("sections.appearance.eyes"), v) }
                            if let hand { chip(t("sections.appearance.dominantHand"), t(hand == "left" ? "sections.appearance.handLeft" : "sections.appearance.handRight")) }
                            if let v = nonEmpty(p.mobilityLevel) { chip(t("sections.appearance.mobility"), MobilityKt.describeMobility(stored: v, t: t.lookup)) }
                        }
                        .padding(.bottom, Space.xs)
                        if ReadoutKt.needsVehicleCheck(mobilityLevel: p.mobilityLevel), let level = p.mobilityLevel {
                            warnNote(t("vehicleCheck.\(ReadoutKt.vehicleCheckKind(mobilityLevel: level).key)"), 14).padding(.top, Space.sm)
                        }
                        if let v = nonEmpty(p.identifyingMarks) { infoRow(t("sections.appearance.identifyingMarks"), v) }
                    }
                }

                warnNote(t("wearingCard"), 16)

                section("cross.fill", t("sections.medical.title"), error, [
                    ("sections.medical.conditions", p.medicalConditions), ("sections.medical.medications", p.medications),
                    ("sections.medical.allergies", p.allergies), ("sections.medical.cognitiveStatus", p.cognitiveStatus),
                ])
                section("bubble.left.fill", t("sections.communication.title"), colors.primary, [
                    ("sections.communication.communication", p.communicationPreference), ("sections.communication.approach", p.approachGuidance),
                    ("sections.communication.escalation", p.escalationSigns), ("sections.communication.deescalation", p.deescalationTechniques),
                    ("sections.communication.likes", p.likes), ("sections.communication.triggers", p.dislikesTriggers),
                    ("sections.communication.safeWord", p.safeWord),
                ])
                devices(p)

                if !contacts.isEmpty {
                    card {
                        sectionLabel("phone.fill", t("contacts.title"), success)
                        ForEach(Array(contacts.enumerated()), id: \.offset) { index, c in contactRow(c, index) }
                    }
                }

                VStack(spacing: Space.sm) {
                    copyButton(script: true, label: copied == "script" ? t("copiedScriptButton") : t("copyScriptButton"),
                               icon: copied == "script" ? "checkmark.circle.fill" : "doc.on.clipboard.fill") {
                        copy(script, kind: "script", event: .readoutScriptCopied)
                    }
                    copyButton(script: false, label: copied == "all" ? t("copiedFullButton") : t("copyFullButton"),
                               icon: copied == "all" ? "checkmark.circle.fill" : "square.and.arrow.up") {
                        copy(ReadoutKt.buildCopyBlock(input: input, t: t.lookup), kind: "all", event: .readoutDetailsCopied)
                    }
                }

                VStack(alignment: .leading, spacing: Space.sm) {
                    RNText(t("silverAlert.title"), Typography.bodyBold.spec, color: Color(argb: colors.isDark ? Secondary.c100 : Primary.c900))
                    RNText(t("silverAlert.body", ["emergencyNumber": emergencyNumber]), TextSpec(size: 16, lineHeight: 22),
                           color: Color(argb: colors.isDark ? Neutral.c300 : Neutral.c700))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(Space.lg + 1)
                .background(RoundedRectangle(cornerRadius: Radius.lg).fill(Color(argb: colors.isDark ? Primary.c900 : Secondary.c100)))
                .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(Color(argb: colors.isDark ? Primary.c700 : Secondary.c300), lineWidth: 1))
            }
            .padding(Space.lg)
        }
        .scrollIndicators(.hidden)
    }

    private func nonEmpty(_ s: String?) -> String? { (s ?? "").isEmpty ? nil : s }

    private func dial(_ number: String) {
        if let url = URL(string: "tel:\(number)") { UIApplication.shared.open(url) }
    }

    private func copy(_ text: String, kind: String, event: AnalyticsEvent) {
        UIPasteboard.general.string = text
        Analytics.shared.track(event: event, properties: [:])
        copied = kind
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) { if copied == kind { copied = nil } }
    }

    /// A readout card: hairline border, radius lg, padding lg, gap xs.
    private func card<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        let hairline = 1 / UIScreen.main.scale
        return VStack(alignment: .leading, spacing: Space.xs, content: content)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Space.lg + hairline)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: hairline))
    }

    private func sectionLabel(_ icon: String, _ text: String, _ color: Color) -> some View {
        HStack(spacing: Space.xs) {
            SFIcon(icon, 14, color)
            // bodyBold with fontSize 12: the 24 line height stays.
            RNText(text.uppercased(), TextSpec(size: 12, lineHeight: 24, weight: 600, letterSpacing: 0.5), color: color)
        }
        .padding(.bottom, Space.xs)
    }

    private func infoRow(_ label: String, _ value: String) -> some View {
        let hairline = 1 / UIScreen.main.scale
        return VStack(alignment: .leading, spacing: 0) {
            Rectangle().fill(Color(.sRGB, red: 128 / 255, green: 128 / 255, blue: 128 / 255, opacity: 0.15)).frame(height: hairline)
            RNText(label.uppercased(), Typography.caption.spec.spacing(0.5), color: colors.textSecondary).padding(.bottom, 2).padding(.top, Space.sm)
            RNText(value, TextSpec(size: 16, lineHeight: 22), color: colors.text, lines: 4).padding(.bottom, Space.sm)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func chip(_ label: String, _ value: String) -> some View {
        let hairline = 1 / UIScreen.main.scale
        return VStack(spacing: 0) {
            RNText(label, Typography.small.spec, color: colors.textSecondary)
            RNText(value, Typography.bodyBold.spec, color: colors.text, lines: 1)
        }
        .padding(.horizontal, Space.md).padding(.vertical, Space.sm)
        .frame(minWidth: 80, maxWidth: 160)
        .background(RoundedRectangle(cornerRadius: Radius.md).fill(colors.surface))
        .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(colors.border, lineWidth: hairline))
    }

    private func warnNote(_ text: String, _ iconSize: CGFloat) -> some View {
        HStack(alignment: .top, spacing: Space.sm) {
            SFIcon("exclamationmark.triangle.fill", iconSize, warning)
            RNText(text, TextSpec(size: 16, lineHeight: 20), color: Color(argb: colors.isDark ? Secondary.c100 : Neutral.c700))
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(Space.md + 1)
        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(warning.opacity(0x15 / 255.0)))
        .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(warning.opacity(0x40 / 255.0), lineWidth: 1))
    }

    @ViewBuilder
    private func section(_ icon: String, _ title: String, _ color: Color, _ rows: [(String, String?)]) -> some View {
        let shown = rows.compactMap { key, value in nonEmpty(value).map { (key, $0) } }
        if !shown.isEmpty {
            card {
                sectionLabel(icon, title, color)
                ForEach(shown, id: \.0) { key, value in infoRow(t(key), value) }
            }
        }
    }

    @ViewBuilder
    private func devices(_ p: Profile) -> some View {
        let rows = [("sections.devices.locator", p.locativeDeviceInfo), ("sections.devices.idBracelet", p.idBracelets),
                    ("sections.devices.medicAlertId", p.medicAlertId)].compactMap { k, v in nonEmpty(v).map { (k, $0) } }
        let hotline = nonEmpty(p.medicAlertHotline)
        if !rows.isEmpty || hotline != nil {
            card {
                sectionLabel("location.fill", t("sections.devices.title"), colors.primary)
                ForEach(rows, id: \.0) { key, value in infoRow(t(key), value) }
                if let hotline {
                    Button {
                        Analytics.shared.track(event: .readoutMedicalertHotlineCalled, properties: [:])
                        dial(PhoneKt.stripPhoneFormatting(phone: hotline))
                    } label: { infoRow(t("sections.devices.medicAlertHotline"), hotline).contentShape(Rectangle()) }
                        .buttonStyle(.pressable)
                        .accessibilityIdentifier("readout-medicalert-hotline")
                }
            }
        }
    }

    private func identityCard(_ p: Profile) -> some View {
        card {
            HStack(spacing: Space.md) {
                if let image = loadPhoto(p.photoUri) {
                    Image(uiImage: image).resizable().scaledToFill().frame(width: 80, height: 80).clipShape(RoundedRectangle(cornerRadius: Radius.md))
                } else {
                    SFIcon("person.fill", 36, colors.primary)
                        .frame(width: 80, height: 80)
                        .background(RoundedRectangle(cornerRadius: Radius.md).fill(colors.primaryLight))
                }
                VStack(alignment: .leading, spacing: Space.xs) {
                    RNText(p.name, Typography.headline.spec, color: colors.text, lines: 1)
                    if let nick = nonEmpty(p.nickname) {
                        RNText(t("sections.identity.goesBy", ["nickname": nick]), Typography.body.spec.italic(), color: colors.textSecondary, lines: 1)
                    }
                    if let dob = nonEmpty(p.dateOfBirth) {
                        HStack(spacing: Space.xs) {
                            SFIcon("calendar", 12, colors.textSecondary)
                            RNText(t("sections.identity.dob", ["dob": dob]), Typography.caption.spec, color: colors.textSecondary)
                        }
                        .padding(.top, Space.xxs)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func contactRow(_ c: ContactEntity, _ index: Int) -> some View {
        let hairline = 1 / UIScreen.main.scale
        let role = nonEmpty(c.relationship) ?? c.role.map { t("roles.\($0)", ["ns": "contacts", "defaultValue": $0]) } ?? ""
        return VStack(spacing: 0) {
            Rectangle().fill(colors.border).frame(height: hairline)
            HStack(spacing: Space.md) {
                VStack(alignment: .leading, spacing: 2) {
                    RNText(c.name, Typography.bodyBold.spec, color: colors.text, lines: 1)
                    RNText(role, Typography.caption.spec, color: colors.textSecondary, lines: 1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    Analytics.shared.track(event: .readoutContactCalled, properties: [:])
                    dial(c.phone)
                } label: {
                    HStack(spacing: Space.xs) {
                        SFIcon("phone.fill", 14, white)
                        RNText(PhoneKt.formatPhoneNumber(phone: c.phone), Typography.bodyBold.spec, color: white)
                    }
                    .padding(.horizontal, Space.md).padding(.vertical, Space.sm)
                    .background(RoundedRectangle(cornerRadius: Radius.md).fill(success))
                }
                .buttonStyle(.pressable)
                .accessibilityIdentifier("readout-contact-\(index)-call")
            }
            .padding(.top, Space.sm)
        }
    }

    private func copyButton(script: Bool, label: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: Space.sm) {
                SFIcon(icon, 18, script ? white : (copied == "all" ? success : colors.text))
                RNText(label, Typography.body.spec, color: script ? colors.textOnPrimary : colors.text)
            }
            .frame(maxWidth: .infinity, minHeight: 48 - 2 * Space.md)
            .padding(.vertical, Space.md + (script ? 0 : 1))
            .background(RoundedRectangle(cornerRadius: Radius.md).fill(script ? colors.primary : colors.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(script ? .clear : colors.border, lineWidth: 1))
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier(script ? "readout-copy-script" : "readout-copy-all")
    }
}

/// A wrapping row of views (RN flexWrap: 'wrap' with a gap).
struct FlowLayout: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache _: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0, maxX: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width { x = 0; y += rowHeight + spacing; rowHeight = 0 }
            x += size.width + spacing
            maxX = max(maxX, x - spacing)
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: proposal.width ?? maxX, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal _: ProposedViewSize, subviews: Subviews, cache _: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX, x + size.width > bounds.maxX { x = bounds.minX; y += rowHeight + spacing; rowHeight = 0 }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
