import AVFoundation
import SwiftUI
import UIKit

private let white = Color(argb: Light.textOnPrimary)

private struct ProfileForm: Equatable {
    var text: [ProfileField: String] = Dictionary(uniqueKeysWithValues: ProfileFieldKt.PROFILE_TEXT_FIELDS.map { ($0, "") })
    var dominantHand = "unknown"
    var mobility: [String] = []
    var mobilityOther = ""

    subscript(field: ProfileField) -> String {
        get { text[field] ?? "" }
        set { text[field] = newValue }
    }

    /// The selected options, with "Other" replaced by its text.
    var resolvedMobility: String {
        let other = mobilityOther.trimmingCharacters(in: .whitespacesAndNewlines)
        return (mobility.filter { $0 != "Other" } + (mobility.contains("Other") && !other.isEmpty ? [other] : [])).joined(separator: ", ")
    }

    /// What would be saved (RN compares the form plus resolved mobility).
    var snapshot: [String] { ProfileFieldKt.PROFILE_TEXT_FIELDS.map { self[$0] } + [dominantHand, resolvedMobility] }
}

/// A mobility option's testID suffix: "Uses cane" -> "uses-cane".
private func slug(_ option: String) -> String { option.lowercased().replacingOccurrences(of: " ", with: "-") }

private extension ProfileKeyboard {
    var ui: UIKeyboardType {
        if self == ProfileKeyboard.number { return .numberPad }
        if self == ProfileKeyboard.phone { return .phonePad }
        return .default
    }
}

/// Port of app/profile.tsx: the full profile editor, in four collapsible sections.
struct ProfileView: View {
    let t: Translate
    let tCommon: Translate
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors
    @Environment(\.dynamicTypeSize) private var dynamicType
    @State private var form: ProfileForm?
    @State private var initial: ProfileForm?
    @State private var expanded: String? = "personal"
    @State private var saving = false
    @State private var alert: AppAlert?
    @State private var picker: PickerSource?
    /// The picked photo failed to save; said once the picker has gone, so the alert can show.
    @State private var photoFailed = false
    @State private var datePicking = false
    @State private var pendingDob = Date()
    /// Reading the saved profile failed (not: there is none), so no form was built.
    @State private var loadFailed = false

    private var unsaved: Bool { form != nil && form?.snapshot != initial?.snapshot }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: t("screenTitle"), testID: "profile", onBack: back) {
                HeaderSaveButton(label: t(saving ? "saving" : "save"), testID: "profile-save", enabled: !saving && form != nil, action: save)
            }
            if loadFailed { loadFailedView } else if form != nil { content } else { Spacer() }
        }
        .background(colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .backGuard(unsaved && !saving)
        .task { await load() }
        .appAlert($alert, ok: tCommon("ok"))
        .sheet(item: $picker, onDismiss: showPhotoFailed) { source in
            // A photo that failed to save leaves the form's photo as it was.
            ImagePicker(source: source.uiSource, screen: "profile", onPhoto: { form?[.photoUri] = $0 }, onFailed: { photoFailed = true })
                .ignoresSafeArea()
        }
        .overlay { if datePicking { dateModal.transition(.opacity) } }
        .animation(.easeInOut(duration: 0.2), value: datePicking)
    }

    private func load() async {
        loadFailed = false
        // A failed read is not an empty profile: Save would write the empty form over the stored
        // one (name, medical details, photo). Say so and offer to try again instead.
        let p: Profile?
        do {
            p = try await model.store.profile()
        } catch {
            reportSaveFailed(screen: "profile", action: "load", error: error)
            loadFailed = true
            return
        }
        let options = MOBILITY_OPTIONS
        let tokens = (p?.mobilityLevel ?? "").split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        let known = tokens.filter { options.contains($0) }
        let custom = tokens.filter { !options.contains($0) }
        var loaded = ProfileForm()
        for field in ProfileFieldKt.PROFILE_TEXT_FIELDS { loaded[field] = p?.get(field: field) ?? "" }
        if let hand = p?.dominantHand, !hand.isEmpty { loaded.dominantHand = hand }
        loaded.mobility = custom.isEmpty ? known : known + ["Other"]
        loaded.mobilityOther = custom.joined(separator: ", ")
        form = loaded
        initial = loaded
    }

    private func save() {
        guard let x = form else { return }
        // name is NOT NULL: an empty one is refused rather than failing silently (F-38).
        if x[.fullName].trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            alert = AppAlert(title: tCommon("required"), message: t("errors.nameRequired"))
            return
        }
        saving = true
        Task {
            func v(_ field: ProfileField) -> String? {
                let s = x[field].trimmingCharacters(in: .whitespacesAndNewlines)
                return s.isEmpty ? nil : s
            }
            // A nil must stay in the map (as NSNull, which Kotlin reads as null) so the field clears.
            var values: [ProfileField: Any] = [:]
            for field in ProfileFieldKt.PROFILE_TEXT_FIELDS where field != .photoUri { values[field] = v(field) as Any }
            values[.photoUri] = (x[.photoUri].isEmpty ? nil : x[.photoUri]) as Any
            values[.dominantHand] = x.dominantHand
            values[.mobilityLevel] = (x.resolvedMobility.isEmpty ? nil : x.resolvedMobility) as Any
            do {
                try await model.store.updateProfile(values: values)
                Analytics.shared.track(event: .profileSaved, properties: [
                    "has_photo": !x[.photoUri].isEmpty, "has_medical": !x[.medicalConditions].isEmpty,
                    "has_medications": !x[.medications].isEmpty,
                ])
                initial = x
                saving = false
                model.path = []
            } catch {
                reportSaveFailed(screen: "profile", action: "save", error: error)
                alert = AppAlert(title: tCommon("error"), message: tCommon("saveFailed"))
                saving = false
            }
        }
    }

    private func showPhotoFailed() {
        guard photoFailed else { return }
        photoFailed = false
        alert = AppAlert(title: tCommon("error"), message: t("errors.photoSaveFailed"))
    }

    private func back() {
        if !unsaved || saving {
            model.path.removeLast()
            return
        }
        alert = AppAlert(title: t("unsavedChanges.title"), message: t("unsavedChanges.message"),
                         cancel: tCommon("keepEditing"),
                         confirm: t("unsavedChanges.confirmLabel"), destructive: true) { model.path.removeLast() }
    }

    private func takePhoto() {
        Analytics.shared.track(event: .profilePhotoTaken, properties: [:])
        AVCaptureDevice.requestAccess(for: .video) { granted in
            DispatchQueue.main.async {
                if granted { picker = .camera } else { alert = AppAlert(title: t("cameraPermission")) }
            }
        }
    }

    private func binding(_ field: ProfileField) -> Binding<String> {
        Binding(get: { form?[field] ?? "" }, set: { form?[field] = $0 })
    }

    private var content: some View {
        let f = form!
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                photoSection(f[.photoUri])

                section("personal", t("sections.personal"), "person.fill") {
                    field(.fullName)
                    field(.nickname)
                    dateOfBirth(t("fields.dateOfBirth"))
                    HStack(alignment: .top, spacing: Space.md) {
                        field(.height)
                        field(.weight)
                    }
                    HStack(alignment: .top, spacing: Space.md) {
                        field(.hairColor)
                        field(.eyeColor)
                    }
                    field(.identifyingMarks)
                }

                section("medical", t("sections.medical"), "cross.fill") {
                    field(.medicalConditions)
                    field(.medications)
                    field(.allergies)
                    field(.cognitiveStatus)
                    group(t("fields.dominantHand"), t("fields.dominantHandHint")) {
                        HStack(spacing: Space.sm) {
                            ForEach(["left", "right", "unknown"], id: \.self) { hand in
                                let selected = f.dominantHand == hand
                                Button { form?.dominantHand = hand } label: {
                                    RNText(t("fields.dominantHand\(hand.prefix(1).uppercased() + hand.dropFirst())"),
                                           Typography.body.spec.weight(500), color: selected ? colors.textOnPrimary : colors.text)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, Space.sm + 1)
                                        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(selected ? colors.primary : .clear))
                                        .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(selected ? colors.primary : colors.border, lineWidth: 1))
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(.pressable)
                                .accessibilityIdentifier("profile-hand-\(hand)")
                            }
                        }
                    }
                    group(t("fields.mobilityLevel")) {
                        FlowLayout(spacing: Space.sm) {
                            ForEach(MOBILITY_OPTIONS, id: \.self) { option in
                                let selected = f.mobility.contains(option)
                                Button {
                                    if selected {
                                        form?.mobility.removeAll { $0 == option }
                                        if option == "Other" { form?.mobilityOther = "" }
                                    } else {
                                        form?.mobility.append(option)
                                    }
                                } label: {
                                    RNText(t(MOBILITY_OPTION_KEYS[option] ?? option), Typography.body.spec.weight(500),
                                           color: selected ? colors.textOnPrimary : colors.text)
                                        .padding(.horizontal, Space.md + 1).padding(.vertical, Space.xs + 1)
                                        .background(Capsule().fill(selected ? colors.primary : .clear))
                                        .overlay(Capsule().strokeBorder(selected ? colors.primary : colors.border, lineWidth: 1))
                                }
                                .buttonStyle(.pressable)
                                .accessibilityIdentifier("profile-mobility-\(slug(option))")
                            }
                        }
                        if f.mobility.contains("Other") {
                            input(Binding(get: { form?.mobilityOther ?? "" }, set: { form?.mobilityOther = $0 }),
                                  "profile-mobility-other-text", t("fields.mobilityOtherPlaceholder"))
                                .padding(.top, Space.sm)
                        }
                    }
                }

                section("communication", t("sections.communication"), "bubble.left.fill") {
                    field(.communicationPreference)
                    field(.escalationSigns)
                    field(.deescalationTechniques)
                    field(.approachGuidance)
                    field(.likes)
                    field(.dislikesTriggers)
                    field(.safeWord)
                }

                section("devices", t("sections.devices"), "location.fill") {
                    field(.locativeDeviceInfo)
                    field(.idBracelets)
                    field(.medicAlertId)
                    field(.medicAlertHotline)
                }
                Color.clear.frame(height: 40)
            }
            .padding(Space.lg)
        }
        .scrollDismissesKeyboard(.interactively)
        .scrollStopsAtSafeArea()
    }

    /// Reading the saved profile failed: said so, with Retry, under the header's Back.
    private var loadFailedView: some View {
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
            .accessibilityIdentifier("profile-load-retry")
        }
        .padding(Space.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("profile-load-failed")
    }

    private func photoSection(_ photoUri: String) -> some View {
        VStack(spacing: 0) {
            Group {
                if let image = loadPhoto(photoUri.isEmpty ? nil : photoUri) {
                    Image(uiImage: image).resizable().scaledToFill().frame(width: 120, height: 120).clipShape(Circle())
                        .accessibilityIdentifier("profile-photo")
                } else {
                    // An empty photo slot: a tinted fill and a dashed tint outline, visible on a dark
                    // background as well as a light one.
                    SFIcon("camera.fill", 38, colors.primaryText)
                        .frame(width: 120, height: 120)
                        .background(Circle().fill(colors.primaryLight))
                        .overlay(Circle().inset(by: 1).stroke(colors.tint, style: StrokeStyle(lineWidth: 2, dash: [6, 6])))
                }
            }
            .padding(.bottom, Space.md)
            // Side by side while they fit inside the screen's margins, stacked at large text.
            ButtonRowOrStack(gap: Space.md) {
                photoButton(t("takePhoto"), "profile-photo-take", "camera.fill", action: takePhoto)
                photoButton(t("choosePhoto"), "profile-photo-library", "photo.on.rectangle") {
                    Analytics.shared.track(event: .profilePhotoChosen, properties: [:])
                    picker = .library
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.bottom, Space.xl)
    }

    /// Two equal, secondary choices: outlined in the primary ink with its icon, 48pt tall, as on
    /// the onboarding photo step, so they don't weigh as much as Save.
    private func photoButton(_ label: String, _ testID: String, _ icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: Space.sm) {
                SFIcon(icon, 18, colors.primaryText).accessibilityHidden(true)
                RNText(label, Typography.bodyBold.spec, color: colors.primaryText)
            }
            .padding(.horizontal, Space.lg).padding(.vertical, Space.sm)
            .frame(maxWidth: .infinity, minHeight: 48)
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.primaryText, lineWidth: 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier(testID)
    }

    private func section<C: View>(_ key: String, _ title: String, _ icon: String, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { expanded = expanded == key ? nil : key } label: {
                HStack(spacing: Space.md) {
                    SFIcon(icon, 16, colors.primaryText).frame(width: 36, height: 36).background(Circle().fill(colors.iconContainer))
                    RNText(title, Typography.bodyBold.spec, color: colors.text).frame(maxWidth: .infinity, alignment: .leading)
                    SFIcon(expanded == key ? "chevron.up" : "chevron.down", 16, colors.textSecondary)
                }
                .padding(Space.lg)
                .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .accessibilityIdentifier("profile-section-\(key)")
            if expanded == key {
                VStack(alignment: .leading, spacing: 0, content: content).padding([.horizontal, .bottom], Space.lg)
            }
        }
        .padding(1)
        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: 1))
        .padding(.bottom, Space.md)
    }

    /// inputGroup: a bold label, an optional hint, then the content; margin below lg.
    private func group<C: View>(_ label: String, _ hint: String? = nil, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            RNText(label, Typography.bodyBold.spec, color: colors.text).padding(.bottom, Space.xs)
            if let hint, !hint.isEmpty { RNText(hint, Typography.caption.spec, color: colors.textSecondary).padding(.bottom, Space.xs) }
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, Space.lg)
    }

    /// The profile's input: body text, 1pt border, radius md, padding md; 48pt single-line or
    /// a text area from 80pt (padding 10).
    private func input(_ text: Binding<String>, _ testID: String, _ placeholder: String, multiline: Bool = false,
                       keyboard: UIKeyboardType = .default, capitalization: UITextAutocapitalizationType = .sentences,
                       format: ((String) -> String)? = nil) -> some View {
        RNTextInput(
            text: text, placeholder: placeholder, testID: testID,
            font: .systemFont(ofSize: 16 * rnFontMultiplier(dynamicType)), textColor: UIColor(colors.text),
            placeholderColor: UIColor(colors.inputPlaceholder), tint: UIColor(colors.tint),
            insets: UIEdgeInsets(top: multiline ? 9 : 0, left: multiline ? Space.md : Space.md - 1, bottom: multiline ? 9 : 0,
                                 right: multiline ? Space.md : Space.md - 1),
            multiline: multiline, keyboard: keyboard, capitalization: capitalization, minHeight: 78, format: format
        )
        .frame(height: multiline ? nil : 46)
        .padding(1)
        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: 1))
    }

    private func field(_ field: ProfileField) -> some View {
        group(t("fields.\(field.key)"), field.hasHint ? t("fields.\(field.key)Hint") : nil) {
            input(binding(field), "profile-field-\(field.key)", t("fields.\(field.key)Placeholder"), multiline: field.multiline,
                  keyboard: field.keyboard.ui, capitalization: field.capitalizeWords ? .words : .sentences,
                  format: { field.formatInput(value: $0) })
        }
    }

    /// The date of birth: typed as MM/DD/YYYY, or picked in a spinner sheet. As in RN, a
    /// bordered row (padding md) holds the 48pt input and the 48pt calendar button beside it.
    private func dateOfBirth(_ label: String) -> some View {
        group(label) {
            HStack(spacing: 0) {
                RNTextInput(
                    text: binding(.dateOfBirth), placeholder: "MM/DD/YYYY", testID: "profile-field-dateOfBirth",
                    font: .systemFont(ofSize: 16 * rnFontMultiplier(dynamicType)), textColor: UIColor(colors.text),
                    placeholderColor: UIColor(colors.inputPlaceholder), tint: UIColor(colors.tint), insets: .zero,
                    keyboard: .numberPad, maxLength: 10, format: { ProfileField.dateOfBirth.formatInput(value: $0) }
                )
                .frame(height: 48)
                Button {
                    pendingDob = parseDob(value: form?[.dateOfBirth] ?? "").map(toDate) ?? toDate(CalendarDate(year: 1940, month: 1, day: 1))
                    datePicking = true
                } label: {
                    SFIcon("calendar", 20, colors.textSecondary).frame(minWidth: 48 - Space.sm, minHeight: 48).padding(.leading, Space.sm).contentShape(Rectangle())
                }
                .buttonStyle(.pressable)
                .accessibilityLabel(t("fields.dateOfBirthPicker"))
                .accessibilityIdentifier("profile-dob-calendar")
            }
            .padding(.horizontal, Space.md - 1)
            .frame(minHeight: 46)
            .padding(1)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: 1))
        }
    }

    private func toDate(_ d: CalendarDate) -> Date {
        gregorian.date(from: DateComponents(year: Int(d.year), month: Int(d.month), day: Int(d.day))) ?? Date()
    }

    private var dateModal: some View {
        ZStack {
            Color.black.opacity(0.35).ignoresSafeArea()
            VStack(alignment: .leading, spacing: Space.md) {
                RNText(t("dateModal.title"), Typography.bodyBold.spec, color: colors.text)
                DatePicker("", selection: $pendingDob, in: ...Date(), displayedComponents: .date)
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                    .environment(\.calendar, gregorian)
                    .frame(maxWidth: .infinity)
                HStack(spacing: Space.sm) {
                    Spacer()
                    Button { datePicking = false } label: {
                        RNText(t("dateModal.cancel"), Typography.bodyBold.spec, color: colors.textSecondary)
                            .padding(.horizontal, Space.md).frame(minWidth: 88, minHeight: 40)
                            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: 1))
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.pressable)
                    .accessibilityIdentifier("profile-date-cancel")
                    Button {
                        let c = gregorian.dateComponents([.year, .month, .day], from: pendingDob)
                        form?[.dateOfBirth] = formatDob(date: CalendarDate(year: Int32(c.year!), month: Int32(c.month!), day: Int32(c.day!)))
                        datePicking = false
                    } label: {
                        RNText(t("dateModal.apply"), Typography.bodyBold.spec, color: colors.textOnPrimary)
                            .padding(.horizontal, Space.md).frame(minWidth: 88, minHeight: 40)
                            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.primary))
                    }
                    .buttonStyle(.pressable)
                    .accessibilityIdentifier("profile-date-apply")
                }
            }
            .padding(Space.md + 1)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: 1))
            .padding(Space.lg)
        }
    }
}
