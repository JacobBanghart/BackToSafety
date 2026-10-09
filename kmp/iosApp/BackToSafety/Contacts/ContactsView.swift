import Contacts
import Shared
import SwiftUI
import UIKit

private let roles = [
    ("primary_caregiver", "star.fill"), ("caregiver", "heart.fill"), ("family", "person.2.fill"),
    ("neighbor", "house.fill"), ("friend", "person.fill"), ("other", "ellipsis"),
]

private struct ContactForm: Equatable {
    var name = ""
    var phone = ""
    var relationship = ""
    var role = "family"
    var address = ""
    var notifyOnEmergency = true
    var shareMedicalInfo = false
    var notes = ""

    init() {}

    init(name: String, phone: String) {
        self.name = name
        self.phone = phone
    }

    init(_ c: ContactEntity) {
        name = c.name
        phone = c.phone
        relationship = c.relationship ?? ""
        role = c.role ?? "other"
        address = c.address ?? ""
        notifyOnEmergency = c.notifyOnEmergency
        shareMedicalInfo = c.shareMedicalInfo
        notes = c.notes ?? ""
    }
}

/// The key two numbers share when they're the same contact (contacts.tsx toPhoneKey).
private func phoneKey(_ phone: String) -> String {
    let n = PhoneKt.normalizeSmsRecipient(phone: phone)
    return n.hasPrefix("+") ? String(n.dropFirst()) : n
}

/// Port of app/contacts.tsx: the list (drag to reorder) and the add/edit form.
struct ContactsView: View {
    let t: Translate
    let tCommon: Translate
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors
    @State private var contacts: [ContactEntity]?
    @State private var editing: ContactEntity?
    @State private var showForm = false
    @State private var form = ContactForm()
    @State private var initialForm = ContactForm()
    @State private var saving = false
    @State private var importing = false
    @State private var alert: AppAlert?

    private var unsaved: Bool { showForm && form != initialForm }
    private let white = Color(argb: Light.textOnPrimary)

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(
                title: showForm ? t(editing != nil ? "form.editTitle" : "addContact") : t("screenTitle"),
                testID: "contacts", onBack: showForm ? cancel : { model.path.removeLast() }
            ) {
                if showForm {
                    HeaderSaveButton(label: tCommon(saving ? "saving" : editing != nil ? "update" : "add"),
                                     testID: "contacts-save", enabled: !saving, action: save)
                }
            }
            if let list = contacts {
                if showForm { formView } else { listView(list) }
            } else {
                Spacer()
                RNText(tCommon("loading"), Typography.body.spec, color: colors.text)
                Spacer()
            }
        }
        .background(colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .backGuard(unsaved && !saving)
        .task { await reload() }
        .appAlert($alert)
    }

    private func reload() async {
        contacts = (try? await model.store.contacts()) ?? []
    }

    private func open(_ entity: ContactEntity?, _ data: ContactForm) {
        editing = entity
        form = data
        initialForm = data
        showForm = true
    }

    private func close() {
        showForm = false
        editing = nil
        form = ContactForm()
        initialForm = ContactForm()
    }

    private func showError(validation: Bool, _ message: String) {
        alert = AppAlert(title: tCommon(validation ? "required" : "error"), message: message)
    }

    private func save() {
        if form.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return showError(validation: true, t("errors.nameRequired")) }
        if form.phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return showError(validation: true, t("errors.phoneRequired")) }
        saving = true
        let f = form, edit = editing
        Task {
            do {
                let entity = ContactEntity(
                    id: edit?.id ?? 0, name: f.name, phone: f.phone,
                    relationship: f.relationship.isEmpty ? nil : f.relationship, role: f.role,
                    address: f.address.isEmpty ? nil : f.address, notifyOnEmergency: f.notifyOnEmergency,
                    shareMedicalInfo: f.shareMedicalInfo, notes: f.notes.isEmpty ? nil : f.notes,
                    sortOrder: edit?.sortOrder ?? 0, createdAt: edit?.createdAt, updatedAt: edit?.updatedAt
                )
                if edit != nil {
                    try await model.store.updateContact(c: entity)
                } else {
                    let next = (contacts ?? []).map { Int($0.sortOrder) }.max().map { $0 + 1 } ?? 0
                    _ = try await model.store.addContact(contact: entity, sortOrder: KotlinInt(int: Int32(next)))
                }
                Analytics.shared.track(event: .contactSaved, properties: [
                    "is_edit": edit != nil, "role": f.role, "notify_on_emergency": f.notifyOnEmergency,
                ])
                await reload()
                close()
            } catch {
                showError(validation: false, t("errors.saveFailed"))
            }
            saving = false
        }
    }

    private func delete(_ contact: ContactEntity) {
        alert = AppAlert(title: t("deleteModal.title"), message: t("deleteModal.message", ["name": contact.name]),
                         cancel: t("deleteModal.cancel"), confirm: t("deleteModal.confirm"), destructive: true) {
            Task {
                do {
                    try await model.store.deleteContact(id: contact.id)
                    Analytics.shared.track(event: .contactDeleted, properties: [:])
                    await reload()
                    if editing?.id == contact.id { close() }
                } catch {
                    showError(validation: false, t("errors.deleteFailed"))
                }
            }
        }
    }

    private func cancel() {
        guard unsaved else { return close() }
        alert = AppAlert(title: tCommon("discardChanges"), message: tCommon("unsavedChanges"), cancel: tCommon("keepEditing"),
                         confirm: tCommon("discard"), destructive: true, action: close)
    }

    private func importContact() {
        importing = true
        CNContactStore().requestAccess(for: .contacts) { granted, _ in
            DispatchQueue.main.async {
                guard granted else {
                    importing = false
                    alert = AppAlert(title: t("permission.title"), message: t("permission.message"), cancel: tCommon("notNow"),
                                     confirm: tCommon("openSettings")) {
                        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                    }
                    return
                }
                ContactPicker.present { picked in
                    importing = false
                    guard let picked else { return }
                    imported(picked)
                }
            }
        }
    }

    private func imported(_ contact: CNContact) {
        let phone = contact.phoneNumbers.map { $0.value.stringValue.trimmingCharacters(in: .whitespaces) }.first { !$0.isEmpty } ?? ""
        let full = (CNContactFormatter.string(from: contact, style: .fullName) ?? "").trimmingCharacters(in: .whitespaces)
        let name = full.isEmpty ? [contact.givenName, contact.familyName].filter { !$0.isEmpty }.joined(separator: " ") : full
        if phone.isEmpty { return showError(validation: true, t("errors.noPhoneToImport")) }
        if name.isEmpty { return showError(validation: true, t("errors.noNameToImport")) }
        let existing = Set((contacts ?? []).map { phoneKey($0.phone) }.filter { !$0.isEmpty })
        if existing.contains(phoneKey(phone)) { return showError(validation: true, t("errors.duplicatePhone")) }
        Analytics.shared.track(event: .contactImported, properties: [:])
        open(nil, ContactForm(name: name, phone: PhoneKt.formatPhoneInput(value: phone)))
    }

    private func add() {
        Analytics.shared.track(event: .contactAddTapped, properties: [:])
        open(nil, ContactForm())
    }

    private func listView(_ list: [ContactEntity]) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if list.isEmpty {
                    empty
                } else {
                    VStack(alignment: .leading, spacing: 0) {
                        RNText(tCommon("nContacts", ["count": list.count]), Typography.body.spec, color: colors.textSecondary)
                        RNText(t("reorderHint"), Typography.caption.spec, color: colors.textSecondary).padding(.top, Space.xxs)
                    }
                    .padding(.bottom, Space.md)
                    ReorderableColumn(
                        items: list, id: { $0.id },
                        onDragStart: { UIImpactFeedbackGenerator(style: .medium).impactOccurred() },
                        onRelease: { UIImpactFeedbackGenerator(style: .light).impactOccurred() },
                        onReorder: { ordered in
                            contacts = ordered
                            Task {
                                do { try await model.store.reorderContacts(ordered: ordered) } catch { showError(validation: false, t("errors.reorderFailed")) }
                                await reload()
                            }
                        }
                    ) { contact, index, dragging in
                        contactCard(contact, index, dragging)
                    }
                    HStack(spacing: Space.sm) {
                        addButton("contacts-add", action: add)
                        importButton("contacts-import")
                    }
                    .padding(.top, Space.sm)
                }
                Color.clear.frame(height: 40)
            }
            .padding([.horizontal, .top], Space.lg)
        }
    }

    private var empty: some View {
        VStack(spacing: Space.md) {
            SFIcon("person.2.fill", 40, colors.primary)
                .frame(width: 88, height: 88)
                .background(Circle().fill(colors.primaryLight))
                .padding(.bottom, Space.sm)
            RNText(t("noContacts.title"), Typography.title.spec, color: colors.text, align: .center)
            RNText(t("noContacts.body"), Typography.body.spec, color: colors.textSecondary, align: .center)
            HStack(spacing: Space.sm) {
                addButton("contacts-empty-add", action: add)
                importButton("contacts-empty-import")
            }
            .frame(maxWidth: 560)
            .padding(.top, Space.sm)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 60).padding(.horizontal, Space.xxl)
    }

    private func addButton(_ testID: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: Space.sm) {
                SFIcon("plus", 20, white)
                RNText(t("addContact"), Typography.bodyBold.spec, color: white, lines: 1).fixedSize()
            }
            .frame(maxWidth: .infinity)
            .padding(Space.lg)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.primary))
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier(testID)
    }

    private func importButton(_ testID: String) -> some View {
        Button(action: importContact) {
            HStack(spacing: Space.sm) {
                SFIcon("square.and.arrow.down", 18, colors.tint)
                RNText(importing ? t("importing") : t("importContact"), Typography.bodyBold.spec, color: colors.text, lines: 1).fixedSize()
            }
            .frame(maxWidth: .infinity)
            .padding(Space.lg + 1)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: 1))
        }
        .buttonStyle(.pressable)
        .disabled(importing)
        .accessibilityIdentifier(testID)
    }

    private func contactCard(_ contact: ContactEntity, _ index: Int, _ dragging: Bool) -> some View {
        let role = t("roles.\(roles.first { $0.0 == contact.role }?.0 ?? "other")")
        let relationship = contact.relationship.flatMap { $0.isEmpty ? nil : " • \($0)" } ?? ""
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 0) {
                    RNText(contact.name, Typography.bodyLarge.spec.weight(600), color: colors.text, lines: 1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .rnID("contacts-item-\(index)-name", label: contact.name)
                    RNText(role + relationship, Typography.caption.spec, color: colors.textSecondary, lines: 1).padding(.top, 4)
                }
                .padding(.trailing, Space.md)
                HStack(spacing: Space.sm) {
                    circleAction("contacts-item-\(index)-call", Color(argb: Semantic.success), "phone.fill") {
                        Analytics.shared.track(event: .contactCallTapped, properties: [:])
                        if let url = URL(string: "tel:\(contact.phone)") { UIApplication.shared.open(url) }
                    }
                    circleAction("contacts-item-\(index)-edit", colors.primary, "pencil") {
                        Analytics.shared.track(event: .contactEditTapped, properties: [:])
                        open(contact, ContactForm(contact))
                    }
                }
            }
            .padding(.bottom, Space.sm)
            if let address = contact.address, !address.isEmpty {
                HStack(spacing: Space.sm) {
                    SFIcon("location", 14, colors.icon)
                    RNText(address, Typography.body.spec, color: colors.text, lines: 2).frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .padding(Space.lg + 1)
        .listCard(dragging: dragging, colors: colors)
        .overlay(alignment: .topTrailing) {
            if contact.notifyOnEmergency {
                // Sits over the card's top edge (position absolute, top -10, right md).
                HStack(spacing: 4) {
                    SFIcon("checkmark.circle.fill", 11, colors.primary)
                    RNText(t("alertCircleBadge"), TextSpec(size: 10, lineHeight: 12, weight: 700, letterSpacing: 0.5), color: colors.primary)
                }
                .padding(.horizontal, Space.sm + 1).padding(.vertical, 4)
                .background(RoundedRectangle(cornerRadius: Radius.md).fill(colors.primaryLight))
                .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(colors.primary, lineWidth: 1))
                .offset(x: -Space.md, y: -10)
            }
        }
        .padding(.bottom, Space.md)
    }

    private func circleAction(_ testID: String, _ color: Color, _ icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            SFIcon(icon, 16, white).frame(width: 44, height: 44).background(Circle().fill(color))
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier(testID)
    }

    private var formView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                RNText(t(editing != nil ? "form.editTitle" : "form.newTitle"), Typography.headline.spec, color: colors.text)
                    .padding(.bottom, Space.xl)
                FormTextInput(label: t("form.nameLabel"), text: $form.name, testID: "contacts-form-name",
                              placeholder: t("form.namePlaceholder"), required: true, capitalization: .words)
                FormTextInput(label: t("form.phoneLabel"), text: $form.phone, testID: "contacts-form-phone",
                              placeholder: t("form.phonePlaceholder"), required: true, keyboard: .phonePad,
                              format: { PhoneKt.formatPhoneInput(value: $0) })
                FormTextInput(label: t("form.relationshipLabel"), text: $form.relationship, testID: "contacts-form-relationship",
                              placeholder: t("form.relationshipPlaceholder"))
                FormTextInput(label: t("form.addressLabel"), text: $form.address, testID: "contacts-form-address",
                              placeholder: t("form.addressPlaceholder"), multiline: true)
                VStack(alignment: .leading, spacing: 0) {
                    RNText(t("form.roleLabel"), Typography.bodyBold.spec, color: colors.text).padding(.bottom, Space.xs)
                    FlowLayout(spacing: Space.sm) {
                        ForEach(roles, id: \.0) { role, icon in
                            OptionChip(label: t("roles.\(role)"), icon: icon, testID: "contacts-form-role-\(role)",
                                       selected: form.role == role, selectedColor: colors.primary) {
                                if form.role != role { form.role = role }
                            }
                        }
                    }
                }
                .padding(.bottom, Space.lg)
                Button { form.notifyOnEmergency.toggle() } label: {
                    HStack(spacing: 0) {
                        VStack(alignment: .leading, spacing: 0) {
                            RNText(t("notifyInEmergency"), Typography.bodyLarge.spec.weight(500), color: colors.text)
                            RNText(t("notifyHint"), Typography.caption.spec, color: colors.textSecondary).padding(.top, Space.xxs)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.trailing, Space.md)
                        SwitchToggle(on: form.notifyOnEmergency)
                    }
                    .padding(Space.md + 1)
                    .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.inputBackground))
                    .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.inputBorder, lineWidth: 1))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.pressable)
                .accessibilityIdentifier("contacts-form-notify")
                .padding(.bottom, Space.lg)
                FormTextInput(label: t("form.notesLabel"), text: $form.notes, testID: "contacts-form-notes",
                              placeholder: t("form.notesPlaceholder"), multiline: true)
                if let editing { FormDeleteButton(label: t("deleteModal.title"), testID: "contacts-form-delete") { delete(editing) } }
                Color.clear.frame(height: 40)
            }
            .padding(Space.lg)
        }
        .scrollDismissesKeyboard(.interactively)
    }
}
