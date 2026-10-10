import AVFoundation
import Shared
import SwiftUI
import UIKit

/// Every text field on the screen, by its RN form key (testID profile-field-<key>).
private let textFields = [
    "name", "nickname", "dateOfBirth", "photoUri", "height", "weight", "hairColor", "eyeColor", "identifyingMarks",
    "medicalConditions", "medications", "allergies", "cognitiveStatus",
    "communicationPreference", "escalationSigns", "deescalationTechniques", "approachGuidance", "likes",
    "dislikesTriggers", "safeWord", "locativeDeviceInfo", "idBracelets", "medicAlertId", "medicAlertHotline",
]
private let white = Color(argb: Light.textOnPrimary)

private struct ProfileForm: Equatable {
    var text: [String: String] = Dictionary(uniqueKeysWithValues: textFields.map { ($0, "") })
    var dominantHand = "unknown"
    var mobility: [String] = []
    var mobilityOther = ""

    subscript(field: String) -> String {
        get { text[field] ?? "" }
        set { text[field] = newValue }
    }

    /// The selected options, with "Other" replaced by its text.
    var resolvedMobility: String {
        let other = mobilityOther.trimmingCharacters(in: .whitespacesAndNewlines)
        return (mobility.filter { $0 != "Other" } + (mobility.contains("Other") && !other.isEmpty ? [other] : [])).joined(separator: ", ")
    }

    /// What would be saved (RN compares the form plus resolved mobility).
    var snapshot: [String] { textFields.map { self[$0] } + [dominantHand, resolvedMobility] }
}

/// A mobility option's testID suffix: "Uses cane" -> "uses-cane".
private func slug(_ option: String) -> String { option.lowercased().replacingOccurrences(of: " ", with: "-") }

/// What each field's input does to typed text (profile.tsx formatFieldInput).
private func formatField(_ field: String, _ value: String) -> String {
    switch field {
    case "height": FormattersKt.formatHeightInput(value: value)
    case "weight": FormattersKt.formatWeightInput(value: value)
    case "medicAlertId": FormattersKt.formatMedicAlertIdInput(value: value)
    case "medicAlertHotline": PhoneKt.formatPhoneInput(value: value)
    default: value
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
    @State private var datePicking = false
    @State private var pendingDob = Date()

    private var unsaved: Bool { form != nil && form?.snapshot != initial?.snapshot }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: t("screenTitle"), testID: "profile", onBack: back) {
                Button(action: save) {
                    RNText(t(saving ? "saving" : "save"), Typography.bodyBold.spec, color: white, lines: 1)
                        .padding(.horizontal, Space.lg).padding(.vertical, Space.sm)
                        .frame(minWidth: 72)
                        .background(RoundedRectangle(cornerRadius: Radius.md).fill(colors.tint))
                }
                .buttonStyle(.pressable)
                .disabled(saving)
                .accessibilityIdentifier("profile-save")
            }
            if form != nil { content } else { Spacer() }
        }
        .background(colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .backGuard(unsaved && !saving)
        .task { await load() }
        .appAlert($alert)
        .sheet(item: $picker) { source in
            ImagePicker(source: source.uiSource) { form?["photoUri"] = $0 }.ignoresSafeArea()
        }
        .overlay { if datePicking { dateModal.transition(.opacity) } }
        .animation(.easeInOut(duration: 0.2), value: datePicking)
    }

    private func load() async {
        let p = try? await model.store.profile()
        let options = MobilityKt.MOBILITY_OPTIONS
        let tokens = (p?.mobilityLevel ?? "").split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        let known = tokens.filter { options.contains($0) }
        let custom = tokens.filter { !options.contains($0) }
        var loaded = ProfileForm()
        let values: [String: String?] = [
            "name": p?.name, "nickname": p?.nickname, "dateOfBirth": p?.dateOfBirth, "photoUri": p?.photoUri,
            "height": p?.height, "weight": p?.weight, "hairColor": p?.hairColor, "eyeColor": p?.eyeColor,
            "identifyingMarks": p?.identifyingMarks, "medicalConditions": p?.medicalConditions,
            "medications": p?.medications, "allergies": p?.allergies, "cognitiveStatus": p?.cognitiveStatus,
            "communicationPreference": p?.communicationPreference, "escalationSigns": p?.escalationSigns,
            "deescalationTechniques": p?.deescalationTechniques, "approachGuidance": p?.approachGuidance,
            "likes": p?.likes, "dislikesTriggers": p?.dislikesTriggers, "safeWord": p?.safeWord,
            "locativeDeviceInfo": p?.locativeDeviceInfo, "idBracelets": p?.idBracelets,
            "medicAlertId": p?.medicAlertId, "medicAlertHotline": p?.medicAlertHotline,
        ]
        for (k, v) in values { loaded[k] = v ?? "" }
        loaded.dominantHand = (p?.dominantHand ?? "").isEmpty ? "unknown" : p!.dominantHand!
        loaded.mobility = custom.isEmpty ? known : known + ["Other"]
        loaded.mobilityOther = custom.joined(separator: ", ")
        form = loaded
        initial = loaded
    }

    private func save() {
        guard let x = form else { return }
        // name is NOT NULL: an empty one is refused rather than failing silently (F-38).
        if x["name"].trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            alert = AppAlert(title: tCommon("required"), message: t("errors.nameRequired"))
            return
        }
        saving = true
        Task {
            func v(_ field: String) -> String? {
                let s = x[field].trimmingCharacters(in: .whitespacesAndNewlines)
                return s.isEmpty ? nil : s
            }
            // A nil must stay in the map (as NSNull, which Kotlin reads as null) so the field clears.
            var values: [String: Any] = [:]
            for field in textFields where field != "photoUri" { values[field] = v(field) as Any }
            values["photoUri"] = (x["photoUri"].isEmpty ? nil : x["photoUri"]) as Any
            values["dominantHand"] = x.dominantHand
            values["mobilityLevel"] = (x.resolvedMobility.isEmpty ? nil : x.resolvedMobility) as Any
            do {
                try await model.store.updateProfile(values: values)
                Analytics.shared.track(event: .profileSaved, properties: [
                    "has_photo": !x["photoUri"].isEmpty, "has_medical": !x["medicalConditions"].isEmpty,
                    "has_medications": !x["medications"].isEmpty,
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

    private func back() {
        if !unsaved || saving {
            model.path.removeLast()
            return
        }
        alert = AppAlert(title: t("unsavedChanges.title"), message: t("unsavedChanges.message"),
                         cancel: t("unsavedChanges.cancelLabel", ["defaultValue": "Keep Editing"]),
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

    private func binding(_ field: String) -> Binding<String> {
        Binding(get: { form?[field] ?? "" }, set: { form?[field] = $0 })
    }

    private var content: some View {
        let f = form!
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                photoSection(f["photoUri"])

                section("personal", t("sections.personal"), "person.fill") {
                    field(t("fields.name"), "name", t("fields.namePlaceholder"), capitalization: .words)
                    field(t("fields.nickname"), "nickname", t("fields.nicknamePlaceholder"), capitalization: .words)
                    dateOfBirth(t("fields.dateOfBirth"))
                    HStack(alignment: .top, spacing: Space.md) {
                        field(t("fields.height"), "height", t("fields.heightPlaceholder"), keyboard: .numberPad)
                        field(t("fields.weight"), "weight", t("fields.weightPlaceholder"), keyboard: .numberPad)
                    }
                    HStack(alignment: .top, spacing: Space.md) {
                        field(t("fields.hairColor"), "hairColor", t("fields.hairColorPlaceholder"))
                        field(t("fields.eyeColor"), "eyeColor", t("fields.eyeColorPlaceholder"))
                    }
                    field(t("fields.identifyingMarks"), "identifyingMarks", t("fields.identifyingMarksPlaceholder"),
                          multiline: true, hint: t("fields.identifyingMarksHint"))
                }

                section("medical", t("sections.medical"), "cross.fill") {
                    field(t("fields.medicalConditions"), "medicalConditions", t("fields.medicalConditionsPlaceholder"), multiline: true)
                    field(t("fields.medications"), "medications", t("fields.medicationsPlaceholder"), multiline: true)
                    field(t("fields.allergies"), "allergies", t("fields.allergiesPlaceholder"), multiline: true)
                    field(t("fields.cognitiveStatus"), "cognitiveStatus", t("fields.cognitiveStatusPlaceholder"),
                          multiline: true, hint: t("fields.cognitiveStatusHint"))
                    group(t("fields.dominantHand"), t("fields.dominantHandHint")) {
                        HStack(spacing: Space.sm) {
                            ForEach(["left", "right", "unknown"], id: \.self) { hand in
                                let selected = f.dominantHand == hand
                                Button { form?.dominantHand = hand } label: {
                                    RNText(t("fields.dominantHand\(hand.prefix(1).uppercased() + hand.dropFirst())"),
                                           Typography.body.spec.weight(500), color: selected ? white : colors.text)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, Space.sm + 1)
                                        .background(RoundedRectangle(cornerRadius: Radius.md).fill(selected ? colors.tint : .clear))
                                        .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(selected ? colors.tint : colors.border, lineWidth: 1))
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(.pressable)
                                .accessibilityIdentifier("profile-hand-\(hand)")
                            }
                        }
                    }
                    group(t("fields.mobilityLevel")) {
                        FlowLayout(spacing: Space.sm) {
                            ForEach(MobilityKt.MOBILITY_OPTIONS, id: \.self) { option in
                                let selected = f.mobility.contains(option)
                                Button {
                                    if selected {
                                        form?.mobility.removeAll { $0 == option }
                                        if option == "Other" { form?.mobilityOther = "" }
                                    } else {
                                        form?.mobility.append(option)
                                    }
                                } label: {
                                    RNText(t(MobilityKt.MOBILITY_OPTION_KEYS[option] ?? option), Typography.body.spec.weight(500),
                                           color: selected ? white : colors.text)
                                        .padding(.horizontal, Space.md + 1).padding(.vertical, Space.xs + 1)
                                        .background(Capsule().fill(selected ? colors.tint : .clear))
                                        .overlay(Capsule().strokeBorder(selected ? colors.tint : colors.border, lineWidth: 1))
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
                    field(t("fields.communicationPreference"), "communicationPreference", t("fields.communicationPreferencePlaceholder"), multiline: true)
                    field(t("fields.escalationSigns"), "escalationSigns", t("fields.escalationSignsPlaceholder"), multiline: true, hint: t("fields.escalationSignsHint"))
                    field(t("fields.deescalationTechniques"), "deescalationTechniques", t("fields.deescalationTechniquesPlaceholder"), multiline: true, hint: t("fields.deescalationTechniquesHint"))
                    field(t("fields.approachGuidance"), "approachGuidance", t("fields.approachGuidancePlaceholder"), multiline: true)
                    field(t("fields.likes"), "likes", t("fields.likesPlaceholder"), multiline: true, hint: t("fields.likesHint"))
                    field(t("fields.dislikesTriggers"), "dislikesTriggers", t("fields.dislikesTriggersPlaceholder"), multiline: true, hint: t("fields.dislikesTriggersHint"))
                    field(t("fields.safeWord"), "safeWord", t("fields.safeWordPlaceholder"), hint: t("fields.safeWordHint"))
                }

                section("devices", t("sections.devices"), "location.fill") {
                    field(t("fields.locativeDeviceInfo"), "locativeDeviceInfo", t("fields.locativeDeviceInfoPlaceholder"), multiline: true, hint: t("fields.locativeDeviceInfoHint"))
                    field(t("fields.idBracelets"), "idBracelets", t("fields.idBraceletsPlaceholder"), multiline: true, hint: t("fields.idBraceletsHint"))
                    field(t("fields.medicAlertId"), "medicAlertId", t("fields.medicAlertIdPlaceholder"))
                    field(t("fields.medicAlertHotline"), "medicAlertHotline", t("fields.medicAlertHotlinePlaceholder"),
                          hint: t("fields.medicAlertHotlineHint"), keyboard: .phonePad)
                }
                Color.clear.frame(height: 40)
            }
            .padding(Space.lg)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func photoSection(_ photoUri: String) -> some View {
        VStack(spacing: 0) {
            Group {
                if let image = loadPhoto(photoUri.isEmpty ? nil : photoUri) {
                    Image(uiImage: image).resizable().scaledToFill().frame(width: 120, height: 120).clipShape(Circle())
                        .accessibilityIdentifier("profile-photo")
                } else {
                    SFIcon("camera.fill", 38, colors.textSecondary)
                        .frame(width: 120, height: 120)
                        .background(Circle().fill(colors.card))
                        .overlay(Circle().inset(by: 1).stroke(colors.border, style: StrokeStyle(lineWidth: 2, dash: [6, 6])))
                }
            }
            .padding(.bottom, Space.md)
            HStack(spacing: Space.md) {
                photoButton(t("takePhoto"), "profile-photo-take", colors.tint, action: takePhoto)
                photoButton(t("choosePhoto"), "profile-photo-library", colors.primary) {
                    Analytics.shared.track(event: .profilePhotoChosen, properties: [:])
                    picker = .library
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.bottom, Space.xl)
    }

    private func photoButton(_ label: String, _ testID: String, _ bg: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            RNText(label, Typography.bodyBold.spec, color: white)
                .padding(.horizontal, Space.lg).padding(.vertical, Space.sm)
                .background(RoundedRectangle(cornerRadius: Radius.md).fill(bg))
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier(testID)
    }

    private func section<C: View>(_ key: String, _ title: String, _ icon: String, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { expanded = expanded == key ? nil : key } label: {
                HStack(spacing: Space.md) {
                    SFIcon(icon, 16, colors.primary).frame(width: 36, height: 36).background(Circle().fill(colors.primaryLight))
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

    /// The profile's input: body text, 1pt border, radius md, padding md; 44pt single-line or
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
        .frame(height: multiline ? nil : 42)
        .padding(1)
        .background(RoundedRectangle(cornerRadius: Radius.md).fill(colors.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(colors.border, lineWidth: 1))
    }

    private func field(_ label: String, _ key: String, _ placeholder: String, multiline: Bool = false, hint: String? = nil,
                       keyboard: UIKeyboardType = .default, capitalization: UITextAutocapitalizationType = .sentences) -> some View {
        group(label, hint) {
            input(binding(key), "profile-field-\(key)", placeholder, multiline: multiline, keyboard: keyboard,
                  capitalization: capitalization, format: { formatField(key, $0) })
        }
    }

    /// The date of birth: typed as MM/DD/YYYY, or picked in a spinner sheet. As in RN, a
    /// bordered row (padding md) holds the 44pt input and the calendar button beside it.
    private func dateOfBirth(_ label: String) -> some View {
        group(label) {
            HStack(spacing: 0) {
                RNTextInput(
                    text: binding("dateOfBirth"), placeholder: "MM/DD/YYYY", testID: "profile-field-dateOfBirth",
                    font: .systemFont(ofSize: 16 * rnFontMultiplier(dynamicType)), textColor: UIColor(colors.text),
                    placeholderColor: UIColor(colors.inputPlaceholder), tint: UIColor(colors.tint), insets: .zero,
                    keyboard: .numberPad, maxLength: 10, format: { FormattersKt.formatDobInput(value: $0) }
                )
                .frame(height: 44)
                Button {
                    pendingDob = AgeKt.parseDob(value: form?["dateOfBirth"] ?? "").map(toDate) ?? toDate(Kotlinx_datetimeLocalDate(year: 1940, month: 1, day: 1))
                    datePicking = true
                } label: {
                    SFIcon("calendar", 18, colors.textSecondary).frame(minWidth: 44 - Space.sm, minHeight: 44).padding(.leading, Space.sm).contentShape(Rectangle())
                }
                .buttonStyle(.pressable)
                .accessibilityIdentifier("profile-dob-calendar")
            }
            .padding(.horizontal, Space.md - 1)
            .frame(minHeight: 42)
            .padding(1)
            .background(RoundedRectangle(cornerRadius: Radius.md).fill(colors.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(colors.border, lineWidth: 1))
        }
    }

    private func toDate(_ d: Kotlinx_datetimeLocalDate) -> Date {
        Calendar.current.date(from: DateComponents(year: Int(d.year), month: Int(d.month.ordinal) + 1, day: Int(d.day))) ?? Date()
    }

    private var dateModal: some View {
        ZStack {
            Color.black.opacity(0.35).ignoresSafeArea()
            VStack(alignment: .leading, spacing: Space.md) {
                RNText(t("dateModal.title"), Typography.bodyBold.spec, color: colors.text)
                DatePicker("", selection: $pendingDob, in: ...Date(), displayedComponents: .date)
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                    .frame(maxWidth: .infinity)
                HStack(spacing: Space.sm) {
                    Spacer()
                    Button { datePicking = false } label: {
                        RNText(t("dateModal.cancel"), Typography.bodyBold.spec, color: colors.textSecondary)
                            .padding(.horizontal, Space.md).frame(minWidth: 88, minHeight: 40)
                            .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(colors.border, lineWidth: 1))
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.pressable)
                    .accessibilityIdentifier("profile-date-cancel")
                    Button {
                        let c = Calendar.current.dateComponents([.year, .month, .day], from: pendingDob)
                        form?["dateOfBirth"] = AgeKt.formatDob(date: Kotlinx_datetimeLocalDate(year: Int32(c.year!), month: Int32(c.month!), day: Int32(c.day!)))
                        datePicking = false
                    } label: {
                        RNText(t("dateModal.apply"), Typography.bodyBold.spec, color: white)
                            .padding(.horizontal, Space.md).frame(minWidth: 88, minHeight: 40)
                            .background(RoundedRectangle(cornerRadius: Radius.md).fill(colors.tint))
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
