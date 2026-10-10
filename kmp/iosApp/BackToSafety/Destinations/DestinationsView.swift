import Shared
import SwiftUI
import UIKit

private let categories = [
    ("water", "drop.fill"), ("former_workplace", "briefcase.fill"), ("church", "building.columns.fill"),
    ("store", "cart.fill"), ("restaurant", "fork.knife"), ("friend_family", "person.2.fill"),
    ("walking_route", "figure.walk"), ("other", "mappin"),
]
private let risks = [("high", Semantic.error), ("medium", Semantic.warning), ("low", Semantic.success)]
private let p600 = Color(argb: Primary.c600)
private let white = Color(argb: Light.textOnPrimary)

private struct PlaceForm: Equatable {
    var name = ""
    var address = ""
    var category = "other"
    var riskLevel = "medium"
    var otherCategoryLabel = ""
    var reason = ""
    var distanceFromHome = ""
    var notes = ""
}

/// An "other" place keeps its custom type as a "[Type: ...]" first line of its notes.
private func parseOtherLabel(_ category: String?, _ notes: String) -> (String, String) {
    guard category == "other", notes.hasPrefix("[Type:"), let close = notes.firstIndex(of: "]") else { return ("", notes) }
    let label = notes[notes.index(notes.startIndex, offsetBy: 7) ..< close].trimmingCharacters(in: .whitespaces)
    var rest = String(notes[notes.index(after: close)...])
    if rest.hasPrefix("\n") { rest.removeFirst() }
    return (label, rest)
}

private func notesWithOtherLabel(_ label: String, _ notes: String) -> String {
    let trimmed = label.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty { return notes }
    let prefix = "[Type: \(trimmed)]"
    return notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? prefix : "\(prefix)\n\(notes)"
}

private func categoryOf(_ d: DestinationEntity) -> (String, String) { categories.first { $0.0 == d.category } ?? categories.last! }
private func riskOf(_ d: DestinationEntity) -> (String, Int64) { risks.first { $0.0 == d.riskLevel } ?? risks[1] }

/// Port of app/destinations.tsx: places to check, with a detail sheet and add/edit form.
struct DestinationsView: View {
    let t: Translate
    let tCommon: Translate
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors
    @State private var places: [DestinationEntity]?
    @State private var personName: String?
    @State private var editing: DestinationEntity?
    @State private var viewing: DestinationEntity?
    @State private var showForm = false
    @State private var form = PlaceForm()
    @State private var initialForm = PlaceForm()
    @State private var saving = false
    @State private var alert: AppAlert?

    private var unsaved: Bool { showForm && form != initialForm }

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                ScreenHeader(
                    title: showForm ? t(editing != nil ? "form.editTitle" : "addDestination") : t("screenTitle"),
                    testID: "destinations", onBack: showForm ? cancel : { model.path.removeLast() }
                ) {
                    if showForm {
                        HeaderSaveButton(label: tCommon(saving ? "saving" : editing != nil ? "update" : "add"),
                                         testID: "destinations-save", enabled: !saving, action: save)
                    }
                }
                if let list = places {
                    if showForm { formView } else { listView(list) }
                } else {
                    Spacer()
                    RNText(tCommon("loading"), Typography.body.spec, color: colors.text)
                    Spacer()
                }
            }
        }
        .rnModal(item: $viewing, fade: false) { detail($0) }
        .background(colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .backGuard(unsaved && !saving)
        .task {
            personName = try? await model.store.profile()?.name
            await reload()
        }
        .appAlert($alert)
    }

    private func reload() async {
        places = (try? await model.store.destinations()) ?? []
    }

    private func open(_ entity: DestinationEntity?, _ data: PlaceForm) {
        editing = entity
        viewing = nil
        form = data
        initialForm = data
        showForm = true
    }

    private func close() {
        showForm = false
        editing = nil
        form = PlaceForm()
        initialForm = PlaceForm()
    }

    private func edit(_ d: DestinationEntity) {
        Analytics.shared.track(event: .destinationEditTapped, properties: [:])
        let (label, notes) = parseOtherLabel(d.category, d.notes ?? "")
        open(d, PlaceForm(name: d.name, address: d.address ?? "", category: d.category ?? "other", riskLevel: d.riskLevel ?? "medium",
                          otherCategoryLabel: label, reason: d.reason ?? "", distanceFromHome: d.distanceFromHome ?? "", notes: notes))
    }

    private func add() {
        Analytics.shared.track(event: .destinationAddTapped, properties: [:])
        open(nil, PlaceForm())
    }

    private func showError(validation: Bool, _ message: String) {
        alert = AppAlert(title: tCommon(validation ? "required" : "error"), message: message)
    }

    private func save() {
        if form.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return showError(validation: true, t("errors.nameRequired")) }
        let f = form, edit = editing
        let notes = f.category == "other" ? notesWithOtherLabel(f.otherCategoryLabel, f.notes) : f.notes
        saving = true
        Task {
            do {
                let entity = DestinationEntity(
                    id: edit?.id ?? 0, name: f.name, address: f.address.isEmpty ? nil : f.address,
                    latitude: edit?.latitude, longitude: edit?.longitude, category: f.category,
                    reason: f.reason.isEmpty ? nil : f.reason, distanceFromHome: f.distanceFromHome.isEmpty ? nil : f.distanceFromHome,
                    riskLevel: f.riskLevel, notes: notes.isEmpty ? nil : notes,
                    sortOrder: edit?.sortOrder ?? 0, createdAt: edit?.createdAt, updatedAt: edit?.updatedAt
                )
                if edit != nil {
                    try await model.store.updateDestination(d: entity)
                } else {
                    let next = (places ?? []).map { Int($0.sortOrder) }.max().map { $0 + 1 } ?? 0
                    _ = try await model.store.addDestination(d: entity, sortOrder: KotlinInt(int: Int32(next)))
                }
                Analytics.shared.track(event: .destinationSaved, properties: [
                    "is_edit": edit != nil, "category": f.category, "risk_level": f.riskLevel,
                ])
                await reload()
                close()
            } catch {
                reportSaveFailed(screen: "destinations", action: "save", error: error)
                showError(validation: false, t("errors.saveFailed"))
            }
            saving = false
        }
    }

    private func delete(_ d: DestinationEntity) {
        alert = AppAlert(title: t("deleteModal.title"), message: t("deleteModal.message", ["name": d.name]),
                         cancel: t("deleteModal.cancel"), confirm: t("deleteModal.confirm"), destructive: true) {
            Task {
                do {
                    try await model.store.deleteDestination(id: d.id)
                    Analytics.shared.track(event: .destinationDeleted, properties: ["category": d.category ?? "", "risk_level": d.riskLevel ?? ""])
                    await reload()
                    if editing?.id == d.id { close() }
                } catch {
                    reportSaveFailed(screen: "destinations", action: "delete", error: error)
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

    private func openMaps(_ address: String) {
        Analytics.shared.track(event: .destinationOpenInMaps, properties: [:])
        let encoded = address.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? address
        if let url = URL(string: "maps:?q=\(encoded)") { UIApplication.shared.open(url) }
    }

    private func listView(_ list: [DestinationEntity]) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top, spacing: Space.md) {
                    SFIcon("info.circle.fill", 18, p600)
                    RNText(t("searchTip"), Typography.caption.spec, color: colors.text).frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(Space.md)
                .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.primaryLight))
                .padding(.bottom, Space.lg)
                if list.isEmpty {
                    empty
                } else {
                    VStack(alignment: .leading, spacing: 0) {
                        RNText(tCommon("nLocations", ["count": list.count]), Typography.body.spec, color: colors.textSecondary)
                        RNText(t("reorderHint"), Typography.caption.spec, color: colors.textSecondary).padding(.top, Space.xxs)
                    }
                    .padding(.bottom, Space.md)
                    ReorderableColumn(
                        items: list, id: { $0.id },
                        onDragStart: { UIImpactFeedbackGenerator(style: .medium).impactOccurred() },
                        onRelease: { UIImpactFeedbackGenerator(style: .light).impactOccurred() },
                        onReorder: { ordered in
                            places = ordered
                            Task {
                                do { try await model.store.reorderDestinations(ordered: ordered) } catch {
                                    reportSaveFailed(screen: "destinations", action: "reorder", error: error)
                                    showError(validation: false, t("errors.reorderFailed"))
                                }
                                await reload()
                            }
                        }
                    ) { place, index, dragging in
                        placeCard(place, index, dragging)
                    }
                    Button(action: add) {
                        HStack(spacing: Space.sm) {
                            SFIcon("plus", 20, white)
                            RNText(t("addDestination"), Typography.bodyBold.spec, color: white)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(Space.lg)
                        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.primary))
                    }
                    .buttonStyle(.pressable)
                    .accessibilityIdentifier("destinations-add")
                    .padding(.top, Space.sm)
                }
                Color.clear.frame(height: 40)
            }
            .padding([.horizontal, .top], Space.lg)
        }
    }

    private var empty: some View {
        VStack(spacing: Space.md) {
            SFIcon("mappin.and.ellipse", 40, colors.primary)
                .frame(width: 88, height: 88)
                .background(Circle().fill(colors.primaryLight))
                .padding(.bottom, Space.sm)
            RNText(t("noDestinations.title"), Typography.title.spec, color: colors.text, align: .center)
            RNText(t("noDestinations.body", ["name": (personName ?? "").isEmpty ? t("noDestinations.nameFallback") : personName!]),
                   Typography.body.spec, color: colors.textSecondary, align: .center)
            Button(action: add) {
                HStack(spacing: Space.sm) {
                    SFIcon("plus", 18, white)
                    RNText(t("noDestinations.button"), Typography.bodyBold.spec, color: colors.textOnPrimary)
                }
                .padding(.horizontal, Space.xl).padding(.vertical, Space.md)
                .frame(minHeight: 48)
                .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.primary))
            }
            .buttonStyle(.pressable)
            .accessibilityIdentifier("destinations-empty-add")
            .padding(.top, Space.sm)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40).padding(.horizontal, Space.xxl)
    }

    private func badge(_ text: String, _ fg: Color, _ bg: Color, _ border: Color, _ weight: Int) -> some View {
        RNText(text, Typography.small.spec.weight(weight), color: fg)
            .padding(.horizontal, Space.sm + 1).padding(.vertical, Space.xxs + 1)
            .background(RoundedRectangle(cornerRadius: Radius.sm).fill(bg))
            .overlay(RoundedRectangle(cornerRadius: Radius.sm).strokeBorder(border, lineWidth: 1))
    }

    private func riskBadge(_ d: DestinationEntity) -> some View {
        let (risk, argb) = riskOf(d)
        let c = Color(argb: argb)
        return badge(t("riskLevels.\(risk)"), c, c.opacity(0x1A / 255.0), c.opacity(0x55 / 255.0), 600)
    }

    private func categoryBadge(_ d: DestinationEntity, _ otherLabel: String = "") -> some View {
        badge(t("categories.\(categoryOf(d).0)") + (otherLabel.isEmpty ? "" : ": \(otherLabel)"), colors.textSecondary, colors.surface, colors.border, 500)
    }

    private func categoryIcon(_ d: DestinationEntity, _ size: CGFloat) -> some View {
        SFIcon(categoryOf(d).1, size, p600)
            .frame(width: 28, height: 28)
            .background(RoundedRectangle(cornerRadius: Radius.md).fill(p600.opacity(0x15 / 255.0)))
    }

    private var addressColor: Color { Color(argb: colors.isDark ? Primary.c300 : Primary.c700) }

    private func placeCard(_ d: DestinationEntity, _ index: Int, _ dragging: Bool) -> some View {
        // A tap gesture rather than a Button: a Button's own tap handling swallows the long
        // press that starts a reorder drag (RN's Pressable allows both).
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 0) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 10) {
                        categoryIcon(d, 16)
                        RNText(d.name, Typography.bodyLarge.spec.weight(600), color: colors.text, lines: 1)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .rnID("destinations-item-\(index)-name", label: d.name)
                    }
                    HStack(spacing: Space.sm) {
                        riskBadge(d)
                        categoryBadge(d)
                    }
                    .padding(.top, 6)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Button { edit(d) } label: {
                    SFIcon("pencil", 18, p600)
                        .frame(width: 40, height: 40)
                        .background(RoundedRectangle(cornerRadius: Radius.md).fill(p600.opacity(0x15 / 255.0)))
                }
                .buttonStyle(.pressable)
                .accessibilityIdentifier("destinations-item-\(index)-edit")
            }
            .padding(.bottom, Space.sm)
            if let address = d.address, !address.isEmpty {
                Button { openMaps(address) } label: {
                    HStack(spacing: 6) {
                        SFIcon("location", 14, p600)
                        RNText(address, Typography.caption.spec, color: addressColor, lines: 1).frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.vertical, Space.xs).padding(.horizontal, 10)
                    .background(RoundedRectangle(cornerRadius: Radius.md).fill(colors.surface))
                }
                .buttonStyle(.pressable)
                .accessibilityIdentifier("destinations-item-\(index)-maps")
                .padding(.bottom, Space.xs)
            }
        }
        .padding(Space.lg + 1)
        .listCard(dragging: dragging, colors: colors)
        .contentShape(Rectangle())
        .onTapGesture { viewing = d }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("destinations-item-\(index)")
        .padding(.bottom, Space.md)
    }

    /// The read-only detail sheet (a bottom sheet over a dimmed backdrop, as RN's slide Modal).
    private func detail(_ d: DestinationEntity) -> some View {
        let (otherLabel, notes) = parseOtherLabel(d.category, d.notes ?? "")
        return VStack(spacing: 0) {
            Spacer(minLength: 0)
            VStack(spacing: 0) {
                HStack(alignment: .top, spacing: Space.md) {
                    categoryIcon(d, 18)
                    VStack(alignment: .leading, spacing: 0) {
                        RNText(d.name, Typography.headline.spec, color: colors.text).padding(.bottom, Space.xs)
                        FlowLayout(spacing: Space.xs) {
                            riskBadge(d)
                            categoryBadge(d, otherLabel)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, Space.lg).padding(.bottom, Space.md)
                ScrollView {
                    VStack(spacing: 0) {
                        if let address = d.address, !address.isEmpty {
                            Button { openMaps(address) } label: {
                                detailRow("location", p600, t("detail.address"), address, addressColor).contentShape(Rectangle())
                            }
                            .buttonStyle(.pressable)
                            .accessibilityIdentifier("destinations-detail-maps")
                        }
                        if let v = d.distanceFromHome, !v.isEmpty { detailRow("figure.walk", colors.textSecondary, t("detail.distance"), v, colors.text) }
                        if let v = d.reason, !v.isEmpty { detailRow("questionmark.circle", colors.textSecondary, t("detail.why"), v, colors.text) }
                        if !notes.isEmpty { detailRow("note.text", colors.textSecondary, t("detail.notes"), notes, colors.text) }
                    }
                    .padding(.horizontal, Space.lg)
                }
                .scrollBounceBehavior(.basedOnSize)
                .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: Space.md) {
                    Button { viewing = nil } label: {
                        RNText(tCommon("close"), Typography.bodyBold.spec, color: colors.textSecondary)
                            .frame(maxWidth: .infinity, minHeight: 42)
                            .padding(1)
                            .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(colors.border, lineWidth: 1))
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.pressable)
                    .accessibilityIdentifier("destinations-detail-close")
                    Button { edit(d) } label: {
                        HStack(spacing: Space.xs) {
                            SFIcon("pencil", 14, white)
                            RNText(tCommon("edit"), Typography.bodyBold.spec, color: white)
                        }
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(RoundedRectangle(cornerRadius: Radius.md).fill(colors.tint))
                    }
                    .buttonStyle(.pressable)
                    .accessibilityIdentifier("destinations-detail-edit")
                }
                .padding(Space.lg)
            }
            .padding(.top, Space.lg + 1).padding(.horizontal, 1).padding(.bottom, 1)
            .frame(maxHeight: UIScreen.main.bounds.height * 0.8)
            .background(UnevenRoundedRectangle(topLeadingRadius: Radius.xl, topTrailingRadius: Radius.xl).fill(colors.card))
            .overlay(UnevenRoundedRectangle(topLeadingRadius: Radius.xl, topTrailingRadius: Radius.xl).strokeBorder(colors.border, lineWidth: 1))
        }
        .background(Color.black.opacity(0.5).onTapGesture {})
        .ignoresSafeArea()
    }

    private func detailRow(_ icon: String, _ iconColor: Color, _ label: String, _ value: String, _ valueColor: Color) -> some View {
        let hairline = 1 / UIScreen.main.scale
        return HStack(alignment: .top, spacing: Space.sm) {
            SFIcon(icon, 14, iconColor)
            RNText(label, Typography.caption.spec, color: colors.textSecondary).frame(width: 100, alignment: .leading).padding(.top, 2)
            RNText(value, Typography.body.spec, color: valueColor).frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, Space.md)
        .padding(.bottom, hairline)
        .overlay(alignment: .bottom) { Rectangle().fill(colors.border).frame(height: hairline) }
    }

    private var formView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                RNText(t(editing != nil ? "form.editTitle" : "form.newTitle"), Typography.headline.spec, color: colors.text).padding(.bottom, Space.sm)
                RNText(t("form.hint"), Typography.body.spec, color: colors.textSecondary).padding(.bottom, Space.xl)
                FormTextInput(label: t("form.nameLabel"), text: $form.name, testID: "destinations-form-name",
                              placeholder: t("form.namePlaceholder"), required: true, capitalization: .words)
                VStack(alignment: .leading, spacing: 0) {
                    RNText(t("form.categoryLabel"), Typography.bodyBold.spec, color: colors.text).padding(.bottom, Space.xs)
                    FlowLayout(spacing: Space.sm) {
                        ForEach(categories, id: \.0) { category, icon in
                            OptionChip(label: t("categories.\(category)"), icon: icon, testID: "destinations-form-category-\(category)",
                                       selected: form.category == category,
                                       selectedColor: category == "water" ? Color(argb: Semantic.warning) : p600) {
                                if form.category != category { form.category = category }
                            }
                        }
                    }
                    if form.category == "water" {
                        let warning = Color(argb: Semantic.warning)
                        HStack(spacing: Space.sm) {
                            SFIcon("exclamationmark.triangle.fill", 16, warning)
                            RNText(t("waterWarning"), Typography.caption.spec, color: warning).frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: Radius.md).fill(warning.opacity(0x15 / 255.0)))
                        .padding(.top, Space.sm)
                    }
                    if form.category == "other" {
                        FormTextInput(label: t("form.otherCategoryLabel"), text: $form.otherCategoryLabel,
                                      testID: "destinations-form-other-category", placeholder: t("form.otherCategoryPlaceholder"))
                            .padding(.top, Space.sm)
                    }
                }
                .padding(.bottom, Space.lg)
                VStack(alignment: .leading, spacing: 0) {
                    RNText(t("form.riskLabel"), Typography.bodyBold.spec, color: colors.text).padding(.bottom, Space.xs)
                    HStack(spacing: Space.md) {
                        ForEach(risks, id: \.0) { risk, argb in
                            let c = Color(argb: argb)
                            let selected = form.riskLevel == risk
                            Button { form.riskLevel = risk } label: {
                                RNText(t("riskLevels.\(risk)"), Typography.body.spec.weight(600), color: selected ? white : c)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, Space.md + 2)
                                    .background(RoundedRectangle(cornerRadius: Radius.md).fill(selected ? c : .clear))
                                    .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(c, lineWidth: 2))
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.pressable)
                            .accessibilityIdentifier("destinations-form-risk-\(risk)")
                        }
                    }
                }
                .padding(.bottom, Space.lg)
                FormTextInput(label: t("form.addressLabel"), text: $form.address, testID: "destinations-form-address",
                              placeholder: t("form.addressPlaceholder"), multiline: true)
                FormTextInput(label: t("form.distanceLabel"), text: $form.distanceFromHome, testID: "destinations-form-distance",
                              placeholder: t("form.distancePlaceholder"))
                FormTextInput(label: t("form.whyLabel"), text: $form.reason, testID: "destinations-form-reason",
                              placeholder: t("form.whyPlaceholder"), multiline: true)
                FormTextInput(label: t("form.notesLabel"), text: $form.notes, testID: "destinations-form-notes",
                              placeholder: t("form.notesPlaceholder"), multiline: true)
                if let editing { FormDeleteButton(label: t("deleteModal.title"), testID: "destinations-form-delete") { delete(editing) } }
                Color.clear.frame(height: 40)
            }
            .padding(Space.lg)
        }
        .scrollDismissesKeyboard(.interactively)
    }
}

extension DestinationEntity: @retroactive Identifiable {}
