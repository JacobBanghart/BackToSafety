package com.backtosafety.app.destinations

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.backtosafety.app.ui.FormDeleteButton
import com.backtosafety.app.ui.FormTextInput
import com.backtosafety.app.ui.HeaderSaveButton
import com.backtosafety.app.ui.Icon
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.OptionChip
import com.backtosafety.app.ui.ReorderableColumn
import com.backtosafety.app.ui.ScreenHeader
import com.backtosafety.app.ui.UnsavedChangesGuard
import com.backtosafety.app.ui.hairline
import com.backtosafety.app.ui.listCard
import com.backtosafety.app.ui.rnBorder
import com.backtosafety.app.ui.showAlert
import com.backtosafety.app.ui.style
import com.backtosafety.app.ui.udp
import com.backtosafety.core.Analytics
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.Translate
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.DestinationEntity
import com.backtosafety.core.invoke
import kotlinx.coroutines.launch

private val space = DesignTokens.Spacing
private val type = DesignTokens.Typography
private val white = Color(DesignTokens.Light.textOnPrimary)
private val p600 = Color(DesignTokens.Primary.c600)

private val CATEGORIES = listOf(
    "water" to "drop.fill", "former_workplace" to "briefcase.fill", "church" to "building.columns.fill",
    "store" to "cart.fill", "restaurant" to "fork.knife", "friend_family" to "person.2.fill",
    "walking_route" to "figure.walk", "other" to "mappin",
)
private val RISKS = listOf(
    "high" to DesignTokens.Semantic.error, "medium" to DesignTokens.Semantic.warning, "low" to DesignTokens.Semantic.success,
)

private data class PlaceForm(
    val name: String = "",
    val address: String = "",
    val category: String = "other",
    val riskLevel: String = "medium",
    val otherCategoryLabel: String = "",
    val reason: String = "",
    val distanceFromHome: String = "",
    val notes: String = "",
)

/** An "other" place keeps its custom type as a "[Type: ...]" first line of its notes. */
private fun parseOtherLabel(category: String?, notes: String): Pair<String, String> {
    if (category != "other" || !notes.startsWith("[Type:")) return "" to notes
    val close = notes.indexOf(']')
    if (close == -1) return "" to notes
    return notes.substring(7, close).trim() to notes.substring(close + 1).removePrefix("\n")
}

private fun notesWithOtherLabel(label: String, notes: String): String {
    if (label.isBlank()) return notes
    val prefix = "[Type: ${label.trim()}]"
    return if (notes.isNotBlank()) "$prefix\n$notes" else prefix
}

private fun categoryOf(d: DestinationEntity) = CATEGORIES.firstOrNull { it.first == d.category } ?: CATEGORIES.last()
private fun riskOf(d: DestinationEntity) = RISKS.firstOrNull { it.first == d.riskLevel } ?: RISKS[1]

/** Port of app/destinations.tsx: places to check, with a detail sheet and add/edit form. */
@Composable
fun DestinationsScreen(t: Translate, tCommon: Translate, store: Store, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var places by remember { mutableStateOf<List<DestinationEntity>?>(null) }
    var personName by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<DestinationEntity?>(null) }
    var viewing by remember { mutableStateOf<DestinationEntity?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf(PlaceForm()) }
    var initialForm by remember { mutableStateOf(PlaceForm()) }
    var saving by remember { mutableStateOf(false) }
    val unsaved = showForm && form != initialForm

    suspend fun reload() {
        places = store.destinations()
    }
    LaunchedEffect(Unit) {
        personName = store.profile()?.name
        reload()
    }

    fun open(entity: DestinationEntity?, data: PlaceForm) {
        editing = entity
        viewing = null
        form = data
        initialForm = data
        showForm = true
    }
    fun close() {
        showForm = false
        editing = null
        form = PlaceForm()
        initialForm = PlaceForm()
    }
    fun edit(d: DestinationEntity) {
        Analytics.track(AnalyticsEvent.DESTINATION_EDIT_TAPPED)
        val (label, notes) = parseOtherLabel(d.category, d.notes.orEmpty())
        open(
            d,
            PlaceForm(
                d.name, d.address.orEmpty(), d.category ?: "other", d.riskLevel ?: "medium", label,
                d.reason.orEmpty(), d.distanceFromHome.orEmpty(), notes,
            ),
        )
    }
    fun alert(validation: Boolean, message: String) =
        showAlert(context, tCommon(if (validation) "required" else "error"), message)

    fun save() {
        if (form.name.isBlank()) return alert(true, t("errors.nameRequired"))
        val notes = (if (form.category == "other") notesWithOtherLabel(form.otherCategoryLabel, form.notes) else form.notes).ifEmpty { null }
        saving = true
        scope.launch {
            runCatching {
                val entity = DestinationEntity(
                    id = editing?.id ?: 0, name = form.name, address = form.address.ifEmpty { null },
                    category = form.category, riskLevel = form.riskLevel, reason = form.reason.ifEmpty { null },
                    distanceFromHome = form.distanceFromHome.ifEmpty { null }, notes = notes,
                )
                if (editing != null) store.updateDestination(entity)
                else store.addDestination(entity, sortOrder = (places.orEmpty().maxOfOrNull { it.sortOrder } ?: -1) + 1)
            }.onSuccess {
                Analytics.track(AnalyticsEvent.DESTINATION_SAVED,
                    mapOf("is_edit" to (editing != null), "category" to form.category, "risk_level" to form.riskLevel),
                )
                reload()
                close()
            }.onFailure { alert(false, t("errors.saveFailed")) }
            saving = false
        }
    }

    fun delete(d: DestinationEntity) = showAlert(
        context, t("deleteModal.title"), t("deleteModal.message", mapOf("name" to d.name)),
        cancel = t("deleteModal.cancel"),
        confirm = t("deleteModal.confirm") to {
            scope.launch {
                runCatching { store.deleteDestination(d.id) }
                    .onSuccess {
                        Analytics.track(AnalyticsEvent.DESTINATION_DELETED, mapOf("category" to d.category, "risk_level" to d.riskLevel))
                        reload()
                        if (editing?.id == d.id) close()
                    }
                    .onFailure { alert(false, t("errors.deleteFailed")) }
            }
        },
    )

    fun cancel() {
        if (!unsaved) return close()
        showAlert(
            context, tCommon("discardChanges"), tCommon("unsavedChanges"), cancel = tCommon("keepEditing"),
            confirm = tCommon("discard") to ::close,
        )
    }

    fun openMaps(address: String) = runCatching {
        Analytics.track(AnalyticsEvent.DESTINATION_OPEN_IN_MAPS)
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}")))
    }

    UnsavedChangesGuard(
        enabled = unsaved && !saving,
        title = tCommon("discardChanges"), message = tCommon("unsavedChanges"),
        keepEditing = tCommon("keepEditing"), discard = tCommon("discard"),
        onDiscard = ::close, onLeave = onBack,
    )

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().background(colors.background)
                .then(if (viewing != null) Modifier.clearAndSetSemantics {} else Modifier)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        ) {
            ScreenHeader(
                title = if (showForm) t(if (editing != null) "form.editTitle" else "addDestination") else t("screenTitle"),
                testID = "destinations",
                onBack = if (showForm) ::cancel else onBack,
                right = {
                    if (showForm) {
                        HeaderSaveButton(
                            tCommon(if (saving) "saving" else if (editing != null) "update" else "add"), "destinations-save", !saving, ::save,
                        )
                    }
                },
            )
            val list = places
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tCommon("loading"), color = colors.text) }
                showForm -> Form(t, form, { form = it }, editing, ::delete)
                else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = space.lg.udp, end = space.lg.udp, top = space.lg.udp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = space.lg.udp).clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
                            .background(colors.primaryLight).padding(space.md.udp),
                        horizontalArrangement = Arrangement.spacedBy(space.md.udp),
                    ) {
                        Icon("info.circle.fill", 18f, p600)
                        Text(t("searchTip"), style = type.caption.style(), color = colors.text, modifier = Modifier.weight(1f))
                    }
                    if (list.isEmpty()) {
                        Empty(t, personName) {
                            Analytics.track(AnalyticsEvent.DESTINATION_ADD_TAPPED)
                            open(null, PlaceForm())
                        }
                    } else {
                        Column(Modifier.padding(bottom = space.md.udp)) {
                            Text(tCommon("nLocations", mapOf("count" to list.size)), style = type.body.style(), color = colors.textSecondary)
                            Text(t("reorderHint"), style = type.caption.style(), color = colors.textSecondary, modifier = Modifier.padding(top = space.xxs.udp))
                        }
                        ReorderableColumn(
                            list, key = { it.id },
                            onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onReorder = { ordered ->
                                places = ordered.mapIndexed { i, d -> d.copy(sortOrder = i) }
                                scope.launch {
                                    runCatching { store.reorderDestinations(ordered) }.onFailure { alert(false, t("errors.reorderFailed")) }
                                    reload()
                                }
                            },
                        ) { place, index, dragging ->
                            PlaceCard(t, place, index, dragging, onView = { viewing = place }, onEdit = { edit(place) }, onMaps = ::openMaps)
                        }
                        val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
                        Row(
                            Modifier.fillMaxWidth().padding(top = space.sm.udp).testTag("destinations-add").clip(shape)
                                .clickable {
                                    Analytics.track(AnalyticsEvent.DESTINATION_ADD_TAPPED)
                                    open(null, PlaceForm())
                                }.background(colors.primary).padding(space.lg.udp),
                            horizontalArrangement = Arrangement.spacedBy(space.sm.udp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon("plus", 20f, white)
                            Text(t("addDestination"), style = type.bodyBold.style(), color = white)
                        }
                    }
                    Box(Modifier.height(40.udp))
                }
            }
        }
        viewing?.let { Detail(t, tCommon, it, onClose = { viewing = null }, onEdit = { edit(it) }, onMaps = ::openMaps) }
    }
}

@Composable
private fun Badge(text: String, fg: Color, bg: Color, border: Color, weight: Int) {
    val shape = RoundedCornerShape(DesignTokens.Radius.sm.dp)
    Box(
        Modifier.clip(shape).background(bg).rnBorder(1.dp, border, shape)
            .padding(horizontal = space.sm.udp, vertical = space.xxs.udp),
    ) { Text(text, style = type.small.style(fontWeight = weight), color = fg) }
}

@Composable
private fun RiskBadge(t: Translate, d: DestinationEntity) {
    val (risk, color) = riskOf(d)
    val c = Color(color)
    Badge(t("riskLevels.$risk"), c, c.copy(alpha = 0x1A / 255f), c.copy(alpha = 0x55 / 255f), 600)
}

@Composable
private fun CategoryBadge(t: Translate, d: DestinationEntity, otherLabel: String = "") {
    val colors = LocalAppColors.current
    Badge(
        t("categories.${categoryOf(d).first}") + if (otherLabel.isNotEmpty()) ": $otherLabel" else "",
        colors.textSecondary, colors.surface, colors.border, 500,
    )
}

@Composable
private fun CategoryIcon(d: DestinationEntity, size: Float) {
    Box(
        Modifier.size(28.udp).clip(RoundedCornerShape(DesignTokens.Radius.md.dp)).background(p600.copy(alpha = 0x15 / 255f)),
        contentAlignment = Alignment.Center,
    ) { Icon(categoryOf(d).second, size, p600) }
}

@Composable
private fun PlaceCard(
    t: Translate, d: DestinationEntity, index: Int, dragging: Boolean,
    onView: () -> Unit, onEdit: () -> Unit, onMaps: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier.padding(bottom = space.md.udp).testTag("destinations-item-$index").listCard(dragging)
            .clickable(onClick = onView).padding(space.lg.udp),
    ) {
        Row(Modifier.padding(bottom = space.sm.udp)) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.udp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(d, 16f)
                    Text(
                        d.name, style = type.bodyLarge.style(fontWeight = 600), color = colors.text, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("destinations-item-$index-name"),
                    )
                }
                Row(
                    Modifier.padding(top = 6.udp),
                    horizontalArrangement = Arrangement.spacedBy(space.sm.udp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    RiskBadge(t, d)
                    CategoryBadge(t, d)
                }
            }
            Box(
                Modifier.testTag("destinations-item-$index-edit").size(40.udp).clip(RoundedCornerShape(DesignTokens.Radius.md.dp))
                    .clickable(onClick = onEdit).background(p600.copy(alpha = 0x15 / 255f)),
                contentAlignment = Alignment.Center,
            ) { Icon("pencil", 18f, p600) }
        }
        d.address?.ifEmpty { null }?.let { address ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = space.xs.udp).testTag("destinations-item-$index-maps")
                    .clip(RoundedCornerShape(DesignTokens.Radius.md.dp)).clickable { onMaps(address) }.background(colors.surface)
                    .padding(vertical = space.xs.udp, horizontal = 10.udp),
                horizontalArrangement = Arrangement.spacedBy(6.udp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon("location", 14f, p600)
                Text(
                    address, style = type.caption.style(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    color = Color(if (colors.isDark) DesignTokens.Primary.c300 else DesignTokens.Primary.c700),
                )
            }
        }
    }
}

@Composable
private fun Empty(t: Translate, personName: String?, onAdd: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.udp, horizontal = space.xxl.udp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.md.udp),
    ) {
        Box(
            Modifier.padding(bottom = space.sm.udp).size(88.udp).clip(CircleShape).background(colors.primaryLight),
            contentAlignment = Alignment.Center,
        ) { Icon("mappin.and.ellipse", 40f, colors.primary) }
        Text(t("noDestinations.title"), style = type.title.style(), color = colors.text, textAlign = TextAlign.Center)
        Text(
            t("noDestinations.body", mapOf("name" to (personName?.ifEmpty { null } ?: t("noDestinations.nameFallback")))),
            style = type.body.style(), color = colors.textSecondary, textAlign = TextAlign.Center,
        )
        val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
        Row(
            Modifier.padding(top = space.sm.udp).heightIn(min = 48.udp).testTag("destinations-empty-add").clip(shape)
                .clickable(onClick = onAdd).background(colors.primary)
                .padding(horizontal = space.xl.udp, vertical = space.md.udp),
            horizontalArrangement = Arrangement.spacedBy(space.sm.udp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon("plus", 18f, white)
            Text(t("noDestinations.button"), style = type.bodyBold.style(), color = colors.textOnPrimary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(t: Translate, form: PlaceForm, onChange: (PlaceForm) -> Unit, editing: DestinationEntity?, onDelete: (DestinationEntity) -> Unit) {
    val colors = LocalAppColors.current
    val warning = Color(DesignTokens.Semantic.warning)
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(space.lg.udp)) {
        Text(
            t(if (editing != null) "form.editTitle" else "form.newTitle"), style = type.headline.style(), color = colors.text,
            modifier = Modifier.padding(bottom = space.sm.udp),
        )
        Text(t("form.hint"), style = type.body.style(), color = colors.textSecondary, modifier = Modifier.padding(bottom = space.xl.udp))
        FormTextInput(
            t("form.nameLabel"), form.name, { onChange(form.copy(name = it)) }, "destinations-form-name",
            placeholder = t("form.namePlaceholder"), required = true, capitalization = KeyboardCapitalization.Words,
        )
        Column(Modifier.padding(bottom = space.lg.udp)) {
            Text(t("form.categoryLabel"), style = type.bodyBold.style(), color = colors.text, modifier = Modifier.padding(bottom = space.xs.udp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.sm.udp), verticalArrangement = Arrangement.spacedBy(space.sm.udp)) {
                for ((category, icon) in CATEGORIES) {
                    OptionChip(
                        t("categories.$category"), icon, "destinations-form-category-$category", form.category == category,
                        if (category == "water") warning else p600,
                    ) { if (form.category != category) onChange(form.copy(category = category)) }
                }
            }
            if (form.category == "water") {
                Row(
                    Modifier.fillMaxWidth().padding(top = space.sm.udp).clip(RoundedCornerShape(DesignTokens.Radius.md.dp))
                        .background(warning.copy(alpha = 0x15 / 255f)).padding(10.udp),
                    horizontalArrangement = Arrangement.spacedBy(space.sm.udp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon("exclamationmark.triangle.fill", 16f, warning)
                    Text(t("waterWarning"), style = type.caption.style(), color = warning, modifier = Modifier.weight(1f))
                }
            }
            if (form.category == "other") {
                Column(Modifier.padding(top = space.sm.udp)) {
                    FormTextInput(
                        t("form.otherCategoryLabel"), form.otherCategoryLabel, { onChange(form.copy(otherCategoryLabel = it)) },
                        "destinations-form-other-category", placeholder = t("form.otherCategoryPlaceholder"),
                    )
                }
            }
        }
        Column(Modifier.padding(bottom = space.lg.udp)) {
            Text(t("form.riskLabel"), style = type.bodyBold.style(), color = colors.text, modifier = Modifier.padding(bottom = space.xs.udp))
            Row(horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
                for ((risk, color) in RISKS) {
                    val c = Color(color)
                    val selected = form.riskLevel == risk
                    val shape = RoundedCornerShape(DesignTokens.Radius.md.dp)
                    Box(
                        Modifier.weight(1f).testTag("destinations-form-risk-$risk").clip(shape)
                            .clickable { onChange(form.copy(riskLevel = risk)) }
                            .background(if (selected) c else Color.Transparent).rnBorder(2.dp, c, shape)
                            .padding(vertical = space.md.udp),
                        contentAlignment = Alignment.Center,
                    ) { Text(t("riskLevels.$risk"), style = type.body.style(fontWeight = 600), color = if (selected) white else c) }
                }
            }
        }
        FormTextInput(
            t("form.addressLabel"), form.address, { onChange(form.copy(address = it)) }, "destinations-form-address",
            placeholder = t("form.addressPlaceholder"), multiline = true,
        )
        FormTextInput(
            t("form.distanceLabel"), form.distanceFromHome, { onChange(form.copy(distanceFromHome = it)) }, "destinations-form-distance",
            placeholder = t("form.distancePlaceholder"),
        )
        FormTextInput(
            t("form.whyLabel"), form.reason, { onChange(form.copy(reason = it)) }, "destinations-form-reason",
            placeholder = t("form.whyPlaceholder"), multiline = true,
        )
        FormTextInput(
            t("form.notesLabel"), form.notes, { onChange(form.copy(notes = it)) }, "destinations-form-notes",
            placeholder = t("form.notesPlaceholder"), multiline = true,
        )
        if (editing != null) FormDeleteButton(t("deleteModal.title"), "destinations-form-delete") { onDelete(editing) }
        Box(Modifier.height(40.udp))
    }
}

/** The read-only detail sheet (a bottom sheet over a dimmed backdrop, as RN's slide Modal). */
@Composable
private fun Detail(
    t: Translate, tCommon: Translate, d: DestinationEntity,
    onClose: () -> Unit, onEdit: () -> Unit, onMaps: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    val (otherLabel, notes) = parseOtherLabel(d.category, d.notes.orEmpty())
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.8f).dp
    BackHandler(onBack = onClose)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val shape = RoundedCornerShape(topStart = DesignTokens.Radius.xl.dp, topEnd = DesignTokens.Radius.xl.dp)
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight).clip(shape).background(colors.card).rnBorder(1.dp, colors.border, shape)
                .windowInsetsPadding(WindowInsets.navigationBars).padding(top = space.lg.udp),
        ) {
            Row(
                Modifier.padding(start = space.lg.udp, end = space.lg.udp, bottom = space.md.udp),
                horizontalArrangement = Arrangement.spacedBy(space.md.udp),
            ) {
                CategoryIcon(d, 18f)
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = type.headline.style(), color = colors.text, modifier = Modifier.padding(bottom = space.xs.udp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(space.xs.udp), verticalArrangement = Arrangement.spacedBy(space.xs.udp)) {
                        RiskBadge(t, d)
                        CategoryBadge(t, d, otherLabel)
                    }
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = space.lg.udp)) {
                d.address?.ifEmpty { null }?.let {
                    DetailRow("location", p600, t("detail.address"), it, Color(if (colors.isDark) DesignTokens.Primary.c300 else DesignTokens.Primary.c700),
                        Modifier.testTag("destinations-detail-maps").clickable { onMaps(it) })
                }
                d.distanceFromHome?.ifEmpty { null }?.let { DetailRow("figure.walk", colors.textSecondary, t("detail.distance"), it, colors.text) }
                d.reason?.ifEmpty { null }?.let { DetailRow("questionmark.circle", colors.textSecondary, t("detail.why"), it, colors.text) }
                notes.ifEmpty { null }?.let { DetailRow("note.text", colors.textSecondary, t("detail.notes"), it, colors.text) }
            }
            Row(Modifier.padding(space.lg.udp), horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
                val buttonShape = RoundedCornerShape(DesignTokens.Radius.md.dp)
                Row(
                    Modifier.weight(1f).heightIn(min = 44.udp).testTag("destinations-detail-close").clip(buttonShape)
                        .clickable(onClick = onClose).rnBorder(1.dp, colors.border, buttonShape),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) { Text(tCommon("close"), style = type.bodyBold.style(), color = colors.textSecondary) }
                Row(
                    Modifier.weight(1f).heightIn(min = 44.udp).testTag("destinations-detail-edit").clip(buttonShape)
                        .clickable(onClick = onEdit).background(colors.tint),
                    horizontalArrangement = Arrangement.spacedBy(space.xs.udp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon("pencil", 14f, white)
                    Text(tCommon("edit"), style = type.bodyBold.style(), color = white)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: String, iconColor: Color, label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val rule = hairline
    Row(
        modifier.fillMaxWidth()
            .drawBehind { drawLine(colors.border, Offset(0f, size.height - rule.toPx() / 2), Offset(size.width, size.height - rule.toPx() / 2), rule.toPx()) }
            .padding(bottom = rule).padding(vertical = space.md.udp),
        horizontalArrangement = Arrangement.spacedBy(space.sm.udp),
    ) {
        Icon(icon, 14f, iconColor)
        Text(label, style = type.caption.style(), color = colors.textSecondary, modifier = Modifier.width(100.udp).padding(top = 2.udp))
        Text(value, style = type.body.style(), color = valueColor, modifier = Modifier.weight(1f))
    }
}
