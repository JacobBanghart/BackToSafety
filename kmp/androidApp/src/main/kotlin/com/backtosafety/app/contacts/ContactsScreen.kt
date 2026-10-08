package com.backtosafety.app.contacts

import android.Manifest
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.Analytics
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.backtosafety.app.ui.FormDeleteButton
import com.backtosafety.app.ui.FormTextInput
import com.backtosafety.app.ui.HeaderSaveButton
import com.backtosafety.app.ui.OptionChip
import com.backtosafety.app.ui.listCard
import com.backtosafety.app.ui.udp
import com.backtosafety.app.ui.Icon
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.ReorderableColumn
import com.backtosafety.app.ui.ScreenHeader
import com.backtosafety.app.ui.Toggle
import com.backtosafety.app.ui.UnsavedChangesGuard
import com.backtosafety.app.ui.rnBorder
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.app.ui.showAlert
import com.backtosafety.app.ui.style
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.Translate
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.ContactEntity
import com.backtosafety.core.formatPhoneInput
import com.backtosafety.core.invoke
import com.backtosafety.core.normalizeSmsRecipient
import kotlinx.coroutines.launch

private val space = DesignTokens.Spacing
private val type = DesignTokens.Typography
private val white = Color(DesignTokens.Light.textOnPrimary)

private val ROLES = listOf(
    "primary_caregiver" to "star.fill", "caregiver" to "heart.fill", "family" to "person.2.fill",
    "neighbor" to "house.fill", "friend" to "person.fill", "other" to "ellipsis",
)

private data class ContactForm(
    val name: String = "",
    val phone: String = "",
    val relationship: String = "",
    val role: String = "family",
    val address: String = "",
    val notifyOnEmergency: Boolean = true,
    val shareMedicalInfo: Boolean = false,
    val notes: String = "",
)

private fun ContactEntity.toForm() = ContactForm(
    name, phone, relationship.orEmpty(), role ?: "other", address.orEmpty(), notifyOnEmergency, shareMedicalInfo, notes.orEmpty(),
)

/** Port of app/contacts.tsx: the list (drag to reorder) and the add/edit form. */
@Composable
fun ContactsScreen(t: Translate, tCommon: Translate, store: Store, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<ContactEntity>?>(null) }
    var editing by remember { mutableStateOf<ContactEntity?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf(ContactForm()) }
    var initialForm by remember { mutableStateOf(ContactForm()) }
    var saving by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val unsaved = showForm && form != initialForm

    suspend fun reload() {
        contacts = store.contacts()
    }
    LaunchedEffect(Unit) { reload() }

    fun open(entity: ContactEntity?, data: ContactForm) {
        editing = entity
        form = data
        initialForm = data
        showForm = true
    }
    fun close() {
        showForm = false
        editing = null
        form = ContactForm()
        initialForm = ContactForm()
    }
    fun alert(validation: Boolean, message: String) =
        showAlert(context, tCommon(if (validation) "required" else "error"), message)

    fun save() {
        if (form.name.isBlank()) return alert(true, t("errors.nameRequired"))
        if (form.phone.isBlank()) return alert(true, t("errors.phoneRequired"))
        saving = true
        scope.launch {
            runCatching {
                val entity = ContactEntity(
                    id = editing?.id ?: 0, name = form.name, phone = form.phone,
                    relationship = form.relationship.ifEmpty { null }, role = form.role,
                    address = form.address.ifEmpty { null }, notifyOnEmergency = form.notifyOnEmergency,
                    shareMedicalInfo = form.shareMedicalInfo, notes = form.notes.ifEmpty { null },
                )
                if (editing != null) store.updateContact(entity)
                else store.addContact(entity, sortOrder = (contacts.orEmpty().maxOfOrNull { it.sortOrder } ?: -1) + 1)
            }.onSuccess {
                Analytics.track(AnalyticsEvent.CONTACT_SAVED,
                    mapOf("is_edit" to (editing != null), "role" to form.role, "notify_on_emergency" to form.notifyOnEmergency),
                )
                reload()
                close()
            }.onFailure { alert(false, t("errors.saveFailed")) }
            saving = false
        }
    }

    fun delete(contact: ContactEntity) = showAlert(
        context, t("deleteModal.title"), t("deleteModal.message", mapOf("name" to contact.name)),
        cancel = t("deleteModal.cancel"),
        confirm = t("deleteModal.confirm") to {
            scope.launch {
                runCatching { store.deleteContact(contact.id) }
                    .onSuccess {
                        Analytics.track(AnalyticsEvent.CONTACT_DELETED)
                        reload()
                        if (editing?.id == contact.id) close()
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

    // expo-contacts: a picked contact's name and first phone number.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        importing = false
        val id = result.data?.data?.lastPathSegment ?: return@rememberLauncherForActivityResult
        val (name, phone) = runCatching { readContact(context, id) }.getOrElse {
            return@rememberLauncherForActivityResult alert(false, t("errors.importFailed"))
        }
        when {
            phone.isEmpty() -> alert(true, t("errors.noPhoneToImport"))
            name.isEmpty() -> alert(true, t("errors.noNameToImport"))
            contacts.orEmpty().map { phoneKey(it.phone) }.filter { it.isNotEmpty() }.contains(phoneKey(phone)) ->
                alert(true, t("errors.duplicatePhone"))
            else -> {
                Analytics.track(AnalyticsEvent.CONTACT_IMPORTED)
                open(null, ContactForm(name = name, phone = formatPhoneInput(phone)))
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.READ_CONTACTS] == true) {
            picker.launch(Intent(Intent.ACTION_PICK).setType(ContactsContract.Contacts.CONTENT_TYPE))
        } else {
            importing = false
            showAlert(
                context, t("permission.title"), t("permission.message"), cancel = tCommon("notNow"),
                confirm = tCommon("openSettings") to {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                },
            )
        }
    }
    fun import() {
        importing = true
        permission.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
    }

    UnsavedChangesGuard(
        enabled = unsaved && !saving,
        title = tCommon("discardChanges"), message = tCommon("unsavedChanges"),
        keepEditing = tCommon("keepEditing"), discard = tCommon("discard"),
        onDiscard = ::close, onLeave = onBack,
    )

    Column(
        Modifier.fillMaxSize().background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        ScreenHeader(
            title = if (showForm) t(if (editing != null) "form.editTitle" else "addContact") else t("screenTitle"),
            testID = "contacts",
            onBack = if (showForm) ::cancel else onBack,
            right = {
                if (showForm) {
                    HeaderSaveButton(
                        tCommon(if (saving) "saving" else if (editing != null) "update" else "add"), "contacts-save", !saving, ::save,
                    )
                }
            },
        )
        val list = contacts
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tCommon("loading"), color = colors.text) }
            showForm -> Form(t, form, { form = it }, editing, ::delete)
            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = space.lg.udp, end = space.lg.udp, top = space.lg.udp)) {
                if (list.isEmpty()) {
                    Empty(t, importing, onAdd = {
                        Analytics.track(AnalyticsEvent.CONTACT_ADD_TAPPED)
                        open(null, ContactForm())
                    }, onImport = ::import)
                } else {
                    Column(Modifier.padding(bottom = space.md.udp)) {
                        Text(tCommon("nContacts", mapOf("count" to list.size)), style = type.body.style(), color = colors.textSecondary)
                        Text(t("reorderHint"), style = type.caption.style(), color = colors.textSecondary, modifier = Modifier.padding(top = space.xxs.udp))
                    }
                    ReorderableColumn(
                        list, key = { it.id },
                        onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onReorder = { ordered ->
                            contacts = ordered.mapIndexed { i, c -> c.copy(sortOrder = i) }
                            scope.launch {
                                runCatching { store.reorderContacts(ordered) }.onFailure { alert(false, t("errors.reorderFailed")) }
                                reload()
                            }
                        },
                    ) { contact, index, dragging ->
                        ContactCard(t, contact, index, dragging, onCall = {
                            Analytics.track(AnalyticsEvent.CONTACT_CALL_TAPPED)
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}")))
                        }) {
                            Analytics.track(AnalyticsEvent.CONTACT_EDIT_TAPPED)
                            open(contact, contact.toForm())
                        }
                    }
                    Row(Modifier.padding(top = space.sm.udp), horizontalArrangement = Arrangement.spacedBy(space.sm.udp)) {
                        AddButton(t("addContact"), "contacts-add") {
                            Analytics.track(AnalyticsEvent.CONTACT_ADD_TAPPED)
                            open(null, ContactForm())
                        }
                        ImportButton(if (importing) t("importing") else t("importContact"), "contacts-import", !importing, ::import)
                    }
                }
                Box(Modifier.height(40.udp))
            }
        }
    }
}

/** The key two numbers share when they're the same contact (contacts.tsx toPhoneKey). */
private fun phoneKey(phone: String) = normalizeSmsRecipient(phone).removePrefix("+")

/** A picked contact's display name and first non-empty phone number. */
private fun readContact(context: Context, id: String): Pair<String, String> {
    val resolver = context.contentResolver
    val name = resolver.query(
        Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id),
        arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null,
    )?.use { if (it.moveToFirst()) it.getString(0).orEmpty().trim() else "" }.orEmpty()
    val phone = resolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(id), null,
    )?.use { c ->
        generateSequence { if (c.moveToNext()) c.getString(0)?.trim().orEmpty() else null }.firstOrNull { it.isNotEmpty() }
    }.orEmpty()
    return name to phone
}

@Composable
private fun ContactCard(t: Translate, contact: ContactEntity, index: Int, dragging: Boolean, onCall: () -> Unit, onEdit: () -> Unit) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Box(Modifier.padding(bottom = space.md.udp)) {
        Column(Modifier.listCard(dragging).padding(space.lg.udp)) {
            Row(Modifier.padding(bottom = space.sm.udp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = space.md.udp)) {
                    Text(
                        contact.name, style = type.bodyLarge.style(fontWeight = 600), color = colors.text, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().testTag("contacts-item-$index-name"),
                    )
                    val role = t("roles.${ROLES.firstOrNull { it.first == contact.role }?.first ?: "other"}")
                    Text(
                        role + (contact.relationship?.ifEmpty { null }?.let { " • $it" } ?: ""),
                        style = type.caption.style(), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.udp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(space.sm.udp), verticalAlignment = Alignment.CenterVertically) {
                    CircleAction("contacts-item-$index-call", Color(DesignTokens.Semantic.success), "phone.fill", onCall)
                    CircleAction("contacts-item-$index-edit", colors.primary, "pencil", onEdit)
                }
            }
            contact.address?.ifEmpty { null }?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(space.sm.udp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("location", 14f, colors.icon)
                    Text(it, style = type.body.style(), color = colors.text, maxLines = 2, modifier = Modifier.weight(1f))
                }
            }
        }
        if (contact.notifyOnEmergency) {
            // Sits over the card's top edge (position absolute, top -10, right md).
            Row(
                Modifier.align(Alignment.TopEnd).offset(x = -space.md.udp, y = (-10).dp).clip(RoundedCornerShape(DesignTokens.Radius.md.dp))
                    .background(colors.primaryLight).rnBorder(1.udp, colors.primary, RoundedCornerShape(DesignTokens.Radius.md.dp))
                    .padding(horizontal = space.sm.udp, vertical = 3.udp),
                horizontalArrangement = Arrangement.spacedBy(4.udp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon("checkmark.circle.fill", 11f, colors.primary)
                Text(t("alertCircleBadge"), style = rnTextStyle(10f, 12f, 700, 0.5f), color = colors.primary)
            }
        }
    }
}

@Composable
private fun CircleAction(testID: String, color: Color, icon: String, onClick: () -> Unit) {
    Box(
        Modifier.testTag(testID).size(44.udp).clip(CircleShape).clickable(onClick = onClick).background(color),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, 16f, white) }
}

@Composable
private fun RowScope.AddButton(label: String, testID: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Row(
        Modifier.weight(1f).testTag(testID).clip(shape).clickable(onClick = onClick)
            .background(LocalAppColors.current.primary).padding(space.lg.udp),
        horizontalArrangement = Arrangement.spacedBy(space.sm.udp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon("plus", 20f, white)
        Text(label, style = type.bodyBold.style(), color = white, maxLines = 1)
    }
}

@Composable
private fun RowScope.ImportButton(label: String, testID: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Row(
        Modifier.weight(1f).testTag(testID).clip(shape).clickable(enabled = enabled, onClick = onClick)
            .background(colors.card).rnBorder(1.udp, colors.border, shape).padding(space.lg.udp),
        horizontalArrangement = Arrangement.spacedBy(space.sm.udp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon("square.and.arrow.down", 18f, colors.tint)
        Text(label, style = type.bodyBold.style(), color = colors.text, maxLines = 1)
    }
}

@Composable
private fun Empty(t: Translate, importing: Boolean, onAdd: () -> Unit, onImport: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 60.udp, horizontal = space.xxl.udp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.md.udp),
    ) {
        Box(
            Modifier.padding(bottom = space.sm.udp).size(88.udp).clip(CircleShape).background(colors.primaryLight),
            contentAlignment = Alignment.Center,
        ) { Icon("person.2.fill", 40f, colors.primary) }
        Text(t("noContacts.title"), style = type.title.style(), color = colors.text, textAlign = TextAlign.Center)
        Text(t("noContacts.body"), style = type.body.style(), color = colors.textSecondary, textAlign = TextAlign.Center)
        Row(
            Modifier.fillMaxWidth().widthIn(max = 560.udp).padding(top = space.sm.udp),
            horizontalArrangement = Arrangement.spacedBy(space.sm.udp),
        ) {
            AddButton(t("addContact"), "contacts-empty-add", onAdd)
            ImportButton(if (importing) t("importing") else t("importContact"), "contacts-empty-import", !importing, onImport)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(
    t: Translate,
    form: ContactForm,
    onChange: (ContactForm) -> Unit,
    editing: ContactEntity?,
    onDelete: (ContactEntity) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(space.lg.udp)) {
        Text(
            t(if (editing != null) "form.editTitle" else "form.newTitle"), style = type.headline.style(), color = colors.text,
            modifier = Modifier.padding(bottom = space.xl.udp),
        )
        FormTextInput(
            t("form.nameLabel"), form.name, { onChange(form.copy(name = it)) }, "contacts-form-name",
            placeholder = t("form.namePlaceholder"), required = true, capitalization = KeyboardCapitalization.Words,
        )
        FormTextInput(
            t("form.phoneLabel"), form.phone, { onChange(form.copy(phone = formatPhoneInput(it))) }, "contacts-form-phone",
            placeholder = t("form.phonePlaceholder"), required = true, keyboardType = KeyboardType.Phone,
        )
        FormTextInput(
            t("form.relationshipLabel"), form.relationship, { onChange(form.copy(relationship = it)) }, "contacts-form-relationship",
            placeholder = t("form.relationshipPlaceholder"),
        )
        FormTextInput(
            t("form.addressLabel"), form.address, { onChange(form.copy(address = it)) }, "contacts-form-address",
            placeholder = t("form.addressPlaceholder"), multiline = true,
        )
        Column(Modifier.padding(bottom = space.lg.udp)) {
            Text(t("form.roleLabel"), style = type.bodyBold.style(), color = colors.text, modifier = Modifier.padding(bottom = space.xs.udp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.sm.udp), verticalArrangement = Arrangement.spacedBy(space.sm.udp)) {
                for ((role, icon) in ROLES) {
                    OptionChip(t("roles.$role"), icon, "contacts-form-role-$role", form.role == role, colors.primary) {
                        if (form.role != role) onChange(form.copy(role = role))
                    }
                }
            }
        }
        val toggleShape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
        Row(
            Modifier.fillMaxWidth().padding(bottom = space.lg.udp).testTag("contacts-form-notify").clip(toggleShape)
                .clickable { onChange(form.copy(notifyOnEmergency = !form.notifyOnEmergency)) }
                .background(colors.inputBackground).rnBorder(1.udp, colors.inputBorder, toggleShape).padding(space.md.udp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = space.md.udp)) {
                Text(t("notifyInEmergency"), style = type.bodyLarge.style(fontWeight = 500), color = colors.text)
                Text(t("notifyHint"), style = type.caption.style(), color = colors.textSecondary, modifier = Modifier.padding(top = space.xxs.udp))
            }
            Toggle(form.notifyOnEmergency)
        }
        FormTextInput(
            t("form.notesLabel"), form.notes, { onChange(form.copy(notes = it)) }, "contacts-form-notes",
            placeholder = t("form.notesPlaceholder"), multiline = true,
        )
        if (editing != null) FormDeleteButton(t("deleteModal.title"), "contacts-form-delete") { onDelete(editing) }
        Box(Modifier.height(40.udp))
    }
}
