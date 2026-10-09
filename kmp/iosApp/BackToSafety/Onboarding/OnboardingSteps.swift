import Shared
import SwiftUI

/// app/onboarding/name.tsx
struct NameView: View {
    let t: Translate
    let tCommon: Translate
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors
    @State private var name = ""
    @State private var nickname = ""
    @State private var error = ""

    var body: some View {
        OnboardingScaffold {
            OnboardingStepHeader(active: 1, total: 4) { model.path.removeLast() }
            StepTitle(text: t("name.title"))
            RNText(t("name.subtitle"), Typography.body.spec, color: colors.textSecondary).padding(.bottom, Space.xxl)
            VStack(alignment: .leading, spacing: Space.xl) {
                Field(label: t("name.nameLabel")) {
                    AppInput(text: $name, placeholder: t("name.namePlaceholder"), testID: "onboarding-name-input", size: 18, capitalization: .words)
                        .onChange(of: name) { error = "" }
                }
                Field(label: t("name.nicknameLabel")) {
                    AppInput(text: $nickname, placeholder: t("name.nicknamePlaceholder"), testID: "onboarding-name-nickname", size: 18, capitalization: .words)
                    RNText(t("name.nicknameHint"), Typography.body.spec, color: colors.textDisabled)
                }
                if !error.isEmpty { RNText(error, Typography.body.spec, color: colors.error) }
            }
        } footer: {
            PrimaryButton(label: t("name.continue"), testID: "onboarding-name-continue",
                          spec: Typography.bodyLarge.spec.weight(600), enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) {
                Task {
                    do {
                        let nick = nickname.trimmingCharacters(in: .whitespaces)
                        try await model.store.updateProfile(values: ["name": name.trimmingCharacters(in: .whitespaces), "nickname": nick.isEmpty ? nil : nick])
                        try await model.store.completeStep(step: "profile_name")
                        trackStep(true, "profile_name")
                        model.path.append(.photo)
                    } catch { self.error = tCommon("saveFailed") }
                }
            }
        }
        .trackStepViewed("profile_name")
    }
}

/// app/onboarding/photo.tsx
struct PhotoView: View {
    let t: Translate
    let tCommon: Translate
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors
    @State private var photoUri: String?
    @State private var saving = false
    @State private var picker: PickerSource?

    var body: some View {
        OnboardingScaffold {
            OnboardingStepHeader(active: 2, total: 4) { model.path.removeLast() }
            StepTitle(text: t("photo.title"))
            RNText(t("photo.subtitle"), Typography.body.spec, color: colors.textSecondary).padding(.bottom, Space.xl)
            Group {
                if let image = loadPhoto(photoUri) {
                    Image(uiImage: image).resizable().scaledToFill().frame(width: 200, height: 200).clipShape(Circle())
                } else {
                    VStack(spacing: 0) {
                        RNText("📷", TextSpec(size: 48, lineHeight: 56), color: colors.text).padding(.top, Space.xs)
                        RNText(t("photo.noPhoto"), TextSpec(size: 16, lineHeight: 24), color: colors.textSecondary).padding(.top, Space.sm)
                    }
                    .frame(width: 200, height: 200)
                    .background(Circle().fill(colors.surface))
                    .overlay(Circle().strokeBorder(colors.border, style: StrokeStyle(lineWidth: 2, dash: [6, 6])))
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.bottom, Space.xl)
            VStack(spacing: Space.md) {
                outlineButton(t("photo.takePhoto"), "onboarding-photo-take") {
                    Analytics.shared.track(event: .profilePhotoTaken, properties: [:])
                    picker = .camera
                }
                outlineButton(t("photo.chooseLibrary"), "onboarding-photo-library") {
                    Analytics.shared.track(event: .profilePhotoChosen, properties: [:])
                    picker = .library
                }
            }
            .padding(.bottom, Space.xl)
            RNText(t("photo.tip"), TextSpec(size: 14, lineHeight: 24).italic(), color: colors.textDisabled, align: .center)
                .frame(maxWidth: .infinity)
        } footer: {
            SkipButton(label: t("photo.skip"), testID: "onboarding-photo-skip") {
                trackStep(false, "profile_photo")
                Task {
                    _ = try? await model.store.completeStep(step: "profile_photo")
                    model.path.append(.appearance)
                }
            }
            PrimaryButton(label: saving ? tCommon("saving") : t("photo.continue"), testID: "onboarding-photo-continue",
                          enabled: photoUri != nil && !saving) {
                saving = true
                Task {
                    _ = try? await model.store.updateProfile(values: ["photoUri": photoUri])
                    _ = try? await model.store.completeStep(step: "profile_photo")
                    trackStep(true, "profile_photo")
                    saving = false
                    model.path.append(.appearance)
                }
            }
        }
        .sheet(item: $picker) { source in
            ImagePicker(source: source.uiSource) { photoUri = $0 }.ignoresSafeArea()
        }
        .trackStepViewed("profile_photo")
    }

    private func outlineButton(_ label: String, _ testID: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            RNText(label, Typography.bodyBold.spec, color: colors.primary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 15)
                .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.primary, lineWidth: 1))
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier(testID)
    }
}

/// app/onboarding/appearance.tsx
struct AppearanceView: View {
    let t: Translate
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors
    @State private var height = ""
    @State private var weight = ""
    @State private var hair = ""
    @State private var eyes = ""
    @State private var marks = ""

    var body: some View {
        OnboardingScaffold(contentBottom: 20) {
            OnboardingStepHeader(active: 3, total: 4) { model.path.removeLast() }
            StepTitle(text: t("appearance.title"))
            RNText(t("appearance.subtitle"), Typography.body.spec, color: colors.textSecondary).opacity(0.7).padding(.bottom, Space.xxl)
            VStack(alignment: .leading, spacing: 20) {
                HStack(alignment: .top, spacing: Space.md) {
                    Field(label: t("appearance.heightLabel")) {
                        AppInput(text: $height, placeholder: t("appearance.heightPlaceholder"), testID: "onboarding-appearance-height",
                                 keyboard: .numberPad, format: { FormattersKt.formatHeightInput(value: $0) })
                    }
                    Field(label: t("appearance.weightLabel")) {
                        AppInput(text: $weight, placeholder: t("appearance.weightPlaceholder"), testID: "onboarding-appearance-weight",
                                 keyboard: .numberPad, format: { FormattersKt.formatWeightInput(value: $0) })
                    }
                }
                HStack(alignment: .top, spacing: Space.md) {
                    Field(label: t("appearance.hairLabel")) {
                        AppInput(text: $hair, placeholder: t("appearance.hairPlaceholder"), testID: "onboarding-appearance-hair")
                    }
                    Field(label: t("appearance.eyeLabel")) {
                        AppInput(text: $eyes, placeholder: t("appearance.eyePlaceholder"), testID: "onboarding-appearance-eyes")
                    }
                }
                Field(label: t("appearance.marksLabel")) {
                    AppInput(text: $marks, placeholder: t("appearance.marksPlaceholder"), testID: "onboarding-appearance-marks", multiline: true)
                }
            }
        } footer: {
            SkipButton(label: t("appearance.skip"), testID: "onboarding-appearance-skip") {
                trackStep(false, "profile_appearance")
                Task {
                    _ = try? await model.store.completeStep(step: "profile_appearance")
                    model.path.append(.contact)
                }
            }
            PrimaryButton(label: t("appearance.continue"), testID: "onboarding-appearance-continue") {
                Task {
                    func v(_ s: String) -> String? { let x = s.trimmingCharacters(in: .whitespaces); return x.isEmpty ? nil : x }
                    _ = try? await model.store.updateProfile(values: [
                        "height": v(height), "weight": v(weight), "hairColor": v(hair), "eyeColor": v(eyes), "identifyingMarks": v(marks),
                    ])
                    _ = try? await model.store.completeStep(step: "profile_appearance")
                    trackStep(true, "profile_appearance")
                    model.path.append(.contact)
                }
            }
        }
        .trackStepViewed("profile_appearance")
    }
}

/// app/onboarding/contact.tsx
struct ContactStepView: View {
    let t: Translate
    let tCommon: Translate
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors
    @State private var name = ""
    @State private var phone = ""
    @State private var relationship = ""
    @State private var error = ""

    var body: some View {
        let missing = name.trimmingCharacters(in: .whitespaces).isEmpty || phone.trimmingCharacters(in: .whitespaces).isEmpty
        OnboardingScaffold(contentBottom: 20) {
            OnboardingStepHeader(active: 4, total: 4) { model.path.removeLast() }
            StepTitle(text: t("contact.title"))
            RNText(t("contact.subtitle"), Typography.body.spec, color: colors.textSecondary).padding(.bottom, Space.xxl)
            VStack(alignment: .leading, spacing: 20) {
                Field(label: t("contact.nameLabel")) {
                    AppInput(text: $name, placeholder: t("contact.namePlaceholder"), testID: "onboarding-contact-name", capitalization: .words)
                }
                Field(label: t("contact.phoneLabel")) {
                    AppInput(text: $phone, placeholder: t("contact.phonePlaceholder"), testID: "onboarding-contact-phone",
                             keyboard: .phonePad, format: { PhoneKt.formatPhoneInput(value: $0) })
                }
                Field(label: t("contact.relationshipLabel")) {
                    AppInput(text: $relationship, placeholder: t("contact.relationshipPlaceholder"), testID: "onboarding-contact-relationship",
                             capitalization: .words)
                }
                if !error.isEmpty { RNText(error, TextSpec(size: 14, lineHeight: 24), color: colors.error) }
            }
            .onChange(of: name) { error = "" }
            .onChange(of: phone) { error = "" }
            VStack(alignment: .leading, spacing: 0) {
                RNText(t("contact.infoBox.title"), Typography.bodyBold.spec, color: colors.text).padding(.bottom, Space.sm)
                RNText(t("contact.infoBox.body"), TextSpec(size: 14, lineHeight: 22), color: colors.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Space.lg)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.primaryLight))
            .padding(.top, Space.xl)
        } footer: {
            SkipButton(label: t("contact.skip"), testID: "onboarding-contact-skip") {
                trackStep(false, "emergency_contact")
                Task {
                    _ = try? await model.store.completeStep(step: "emergency_contact")
                    model.path.append(.complete)
                }
            }
            // Looks disabled until both fields are filled, but a tap explains what's missing.
            PrimaryButton(label: t("contact.continue"), testID: "onboarding-contact-continue", dimmed: missing) {
                if missing { error = t("contact.required"); return }
                Task {
                    do {
                        let rel = relationship.trimmingCharacters(in: .whitespaces)
                        _ = try await model.store.addContact(
                            contact: ContactEntity(
                                id: 0, name: name.trimmingCharacters(in: .whitespaces), phone: phone.trimmingCharacters(in: .whitespaces),
                                relationship: rel.isEmpty ? nil : rel, role: "primary_caregiver", address: nil,
                                notifyOnEmergency: true, shareMedicalInfo: true, notes: nil, sortOrder: 0, createdAt: nil, updatedAt: nil
                            ),
                            sortOrder: nil
                        )
                        try await model.store.completeStep(step: "emergency_contact")
                        trackStep(true, "emergency_contact")
                        model.path.append(.complete)
                    } catch { self.error = tCommon("saveFailed") }
                }
            }
        }
        .trackStepViewed("emergency_contact")
    }
}

/// app/onboarding/complete.tsx
struct CompleteView: View {
    let t: Translate
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors

    var body: some View {
        VStack(spacing: 0) {
            // Scrolls when large text makes it taller than the space above the button (F-42).
            ScrollView {
                VStack(spacing: 0) {
                    RNText(t("complete.icon"), TextSpec(size: 48, lineHeight: 56), color: Color(argb: Light.textOnPrimary))
                        .frame(width: 100, height: 100)
                        .background(Circle().fill(colors.success))
                        .padding(.bottom, Space.xxl)
                    RNText(t("complete.title"), Typography.display.spec, color: colors.text, align: .center).padding(.bottom, Space.lg)
                    RNText(t("complete.description"), Typography.body.spec, color: colors.textSecondary, align: .center).padding(.bottom, Space.xxl)
                    VStack(alignment: .leading, spacing: Space.sm) {
                        RNText(t("complete.addMoreLater"), Typography.title.spec, color: colors.text).padding(.bottom, Space.xs)
                        ForEach(["details", "deescalation", "destinations", "contacts", "checklist"], id: \.self) { key in
                            HStack(spacing: 0) {
                                RoundedRectangle(cornerRadius: 2).fill(colors.border).frame(width: 3)
                                RNText(t("complete.nextSteps.\(key)"), Typography.bodyBold.spec, color: colors.textSecondary)
                                    .padding(.vertical, Space.sm).padding(.horizontal, Space.md)
                            }
                            .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, Space.xl)
                .padding(.top, 60)
            }
            .scrollIndicators(.hidden)
            PrimaryButton(label: t("complete.goHome"), testID: "onboarding-complete-home", verticalPadding: Space.lg) {
                trackStep(true, "complete")
                Analytics.shared.track(event: .onboardingCompleted, properties: [:])
                Task {
                    _ = try? await model.store.completeStep(step: "complete")
                    model.onboarded = true
                    model.path = []
                }
            }
            .padding(.horizontal, Space.xl).padding(.top, Space.xl).padding(.bottom, Space.xxl)
        }
        .background(colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationBarBackButtonHidden()
        .trackStepViewed("complete")
    }
}
