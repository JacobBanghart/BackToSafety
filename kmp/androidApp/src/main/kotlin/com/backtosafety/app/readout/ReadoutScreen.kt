package com.backtosafety.app.readout

import android.content.ClipData
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.Analytics
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.backtosafety.app.ui.Icon
import com.backtosafety.app.ui.udp
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.ScreenHeader
import com.backtosafety.app.ui.hairline
import com.backtosafety.app.ui.localeDateTime
import com.backtosafety.app.ui.rememberPhoto
import com.backtosafety.app.ui.rnBorder
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.app.ui.style
import com.backtosafety.core.AppClock
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.Profile
import com.backtosafety.core.ReadoutInput
import com.backtosafety.core.Translate
import com.backtosafety.core.buildCopyBlock
import com.backtosafety.core.buildScript
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.ContactEntity
import com.backtosafety.core.describeMobility
import com.backtosafety.core.formatPhoneNumber
import com.backtosafety.core.invoke
import com.backtosafety.core.missingScriptDetails
import com.backtosafety.core.needsVehicleCheck
import com.backtosafety.core.stripPhoneFormatting
import com.backtosafety.core.vehicleCheckKind
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val space = DesignTokens.Spacing
private val type = DesignTokens.Typography
private val warning = Color(DesignTokens.Semantic.warning)
private val success = Color(DesignTokens.Semantic.success)
private val error = Color(DesignTokens.Semantic.error)
private val white = Color(DesignTokens.Light.textOnPrimary)

private enum class Copied { SCRIPT, ALL }

/** Port of app/readout.tsx: the 911 info sheet. */
@Composable
fun ReadoutScreen(t: Translate, tCommon: Translate, store: Store, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var profile by remember { mutableStateOf<Profile?>(null) }
    var contacts by remember { mutableStateOf<List<ContactEntity>>(emptyList()) }
    var lastSeenMs by remember { mutableStateOf<Long?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf<Copied?>(null) }

    LaunchedEffect(Unit) {
        contacts = store.emergencyContacts()
        // Last seen is when the active emergency started (context/ProfileContext.tsx).
        lastSeenMs = store.activeEmergency()?.let { Instant.parse(it.startedAt).toEpochMilliseconds() }
        profile = store.profile()
    }
    LaunchedEffect(copied) {
        if (copied != null) {
            delay(1800)
            copied = null
        }
    }

    // Onboarding always creates the profile; this only covers the first frame.
    val p = profile ?: run {
        Box(Modifier.fillMaxSize().background(colors.background))
        return
    }
    val lastSeen = lastSeenMs?.let(::localeDateTime)
    val input = ReadoutInput(
        profile = p,
        lastSeenTime = lastSeen,
        today = Instant.fromEpochMilliseconds(AppClock.nowMs()).toLocalDateTime(TimeZone.currentSystemDefault()).date,
    )
    val script = buildScript(input, t)
    val missing = missingScriptDetails(input, t)
    val emergencyNumber = tCommon("emergencyNumber")

    fun dial(number: String) = context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    fun copy(text: String, kind: Copied, failedKey: String) {
        val ok = runCatching {
            context.getSystemService(ClipboardManager::class.java)!!.setPrimaryClip(ClipData.newPlainText(null, text))
        }.isSuccess
        if (ok) Analytics.track(if (kind == Copied.SCRIPT) AnalyticsEvent.READOUT_SCRIPT_COPIED else AnalyticsEvent.READOUT_DETAILS_COPIED)
        if (ok) copied = kind else Toast.makeText(context, "${t("copyFailed")}: ${t(failedKey)}", Toast.LENGTH_LONG).show()
    }

    Column(
        Modifier.fillMaxSize().background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        ScreenHeader(t("screenTitle"), "readout", onBack = {
            Analytics.track(AnalyticsEvent.SCREEN_VIEWED, mapOf("screen" to "home", "source" to "readout_back"))
            onBack()
        })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(space.lg.udp),
            verticalArrangement = Arrangement.spacedBy(space.md.udp),
        ) {
            // Call 911: the most prominent action.
            val callShape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
            Box(
                Modifier.fillMaxWidth().testTag("readout-call-911").shadow(2.udp, callShape).clip(callShape)
                    .clickable {
                        Analytics.track(AnalyticsEvent.READOUT_911_CALLED)
                        dial(emergencyNumber)
                    }.background(error).padding(vertical = space.lg.udp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t("callButton"), style = rnTextStyle(type.bodyLarge.fontSize, type.bodyLarge.lineHeight, 700), color = white)
            }

            // 911 script
            Card {
                Row(
                    Modifier.fillMaxWidth().testTag("readout-script-toggle").clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("phone.connection.fill", t("sections.script.label"), success, Modifier.weight(1f))
                    Icon(if (expanded) "chevron.up" else "chevron.down", 16f, colors.textSecondary)
                }
                val hint = Modifier.padding(top = space.xxs.udp, bottom = space.xs.udp)
                if (expanded) {
                    Text(t("sections.script.hint"), style = type.caption.style(), color = colors.textSecondary, modifier = hint)
                    Text(script, style = rnTextStyle(16f, 22f), color = colors.text, modifier = Modifier.testTag("readout-script-text"))
                    if (missing.isNotEmpty()) {
                        Text(
                            t("sections.script.missingDetails", mapOf("details" to missing.joinToString(", "))),
                            style = rnTextStyle(type.caption.fontSize, 18f, type.caption.fontWeight, type.caption.letterSpacing),
                            color = warning,
                            modifier = Modifier.padding(top = space.sm.udp).testTag("readout-script-missing"),
                        )
                    }
                } else {
                    Text(t("sections.script.collapsed"), style = type.caption.style(), color = colors.textSecondary, modifier = hint)
                }
            }

            IdentityCard(t, p)

            if (lastSeen != null) {
                Card {
                    SectionLabel("clock.fill", t("sections.location.title"), colors.primary)
                    InfoRow(t("sections.location.time"), lastSeen)
                }
            }

            // Mobility and dominant hand live here too, so they alone must show the card (F-26).
            val hand = p.dominantHand?.takeIf { it.isNotEmpty() && it != "unknown" }
            if (listOf(p.height, p.weight, p.hairColor, p.eyeColor, p.identifyingMarks, hand, p.mobilityLevel).any { !it.isNullOrEmpty() }) {
                Card {
                    SectionLabel("eye.fill", t("sections.appearance.title"), colors.primary)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        Modifier.padding(bottom = space.xs.udp),
                        horizontalArrangement = Arrangement.spacedBy(space.sm.udp),
                        verticalArrangement = Arrangement.spacedBy(space.sm.udp),
                    ) {
                        p.height?.ifEmpty { null }?.let { InfoChip(t("sections.appearance.height"), it) }
                        p.weight?.ifEmpty { null }?.let { InfoChip(t("sections.appearance.weight"), it) }
                        p.hairColor?.ifEmpty { null }?.let { InfoChip(t("sections.appearance.hair"), it) }
                        p.eyeColor?.ifEmpty { null }?.let { InfoChip(t("sections.appearance.eyes"), it) }
                        hand?.let {
                            InfoChip(
                                t("sections.appearance.dominantHand"),
                                t(if (it == "left") "sections.appearance.handLeft" else "sections.appearance.handRight"),
                            )
                        }
                        p.mobilityLevel?.ifEmpty { null }?.let { InfoChip(t("sections.appearance.mobility"), describeMobility(it, t)) }
                    }
                    if (needsVehicleCheck(p.mobilityLevel)) {
                        WarnNote(t("vehicleCheck.${vehicleCheckKind(p.mobilityLevel!!).key}"), 14f, Modifier.padding(top = space.sm.udp))
                    }
                    p.identifyingMarks?.ifEmpty { null }?.let { InfoRow(t("sections.appearance.identifyingMarks"), it) }
                }
            }

            WarnNote(t("wearingCard"), 16f)

            val medical = listOf(
                "sections.medical.conditions" to p.medicalConditions,
                "sections.medical.medications" to p.medications,
                "sections.medical.allergies" to p.allergies,
                "sections.medical.cognitiveStatus" to p.cognitiveStatus,
            ).filter { !it.second.isNullOrEmpty() }
            if (medical.isNotEmpty()) {
                Card {
                    SectionLabel("cross.fill", t("sections.medical.title"), error)
                    for ((label, value) in medical) InfoRow(t(label), value!!)
                }
            }

            val communication = listOf(
                "sections.communication.communication" to p.communicationPreference,
                "sections.communication.approach" to p.approachGuidance,
                "sections.communication.escalation" to p.escalationSigns,
                "sections.communication.deescalation" to p.deescalationTechniques,
                "sections.communication.likes" to p.likes,
                "sections.communication.triggers" to p.dislikesTriggers,
                "sections.communication.safeWord" to p.safeWord,
            ).filter { !it.second.isNullOrEmpty() }
            if (communication.isNotEmpty()) {
                Card {
                    SectionLabel("bubble.left.fill", t("sections.communication.title"), colors.primary)
                    for ((label, value) in communication) InfoRow(t(label), value!!)
                }
            }

            val devices = listOf(
                "sections.devices.locator" to p.locativeDeviceInfo,
                "sections.devices.idBracelet" to p.idBracelets,
                "sections.devices.medicAlertId" to p.medicAlertId,
            ).filter { !it.second.isNullOrEmpty() }
            val hotline = p.medicAlertHotline?.ifEmpty { null }
            if (devices.isNotEmpty() || hotline != null) {
                Card {
                    SectionLabel("location.fill", t("sections.devices.title"), colors.primary)
                    for ((label, value) in devices) InfoRow(t(label), value!!)
                    if (hotline != null) {
                        InfoRow(
                            t("sections.devices.medicAlertHotline"), hotline,
                            Modifier.testTag("readout-medicalert-hotline").clickable {
                                Analytics.track(AnalyticsEvent.READOUT_MEDICALERT_HOTLINE_CALLED)
                                dial(stripPhoneFormatting(hotline))
                            },
                        )
                    }
                }
            }

            if (contacts.isNotEmpty()) {
                Card {
                    SectionLabel("phone.fill", t("contacts.title"), success)
                    contacts.forEachIndexed { index, c -> ContactRow(t, c, index) {
                        Analytics.track(AnalyticsEvent.READOUT_CONTACT_CALLED)
                        dial(c.phone)
                    } }
                }
            }

            // Copy actions
            Column(verticalArrangement = Arrangement.spacedBy(space.sm.udp)) {
                val shape = RoundedCornerShape(DesignTokens.Radius.md.dp)
                val label = type.body.style()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.udp).testTag("readout-copy-script").clip(shape)
                        .clickable { copy(script, Copied.SCRIPT, "copyScriptFailed") }
                        .background(colors.primary).padding(vertical = space.md.udp),
                    horizontalArrangement = Arrangement.spacedBy(space.sm.udp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val done = copied == Copied.SCRIPT
                    Icon(if (done) "checkmark.circle.fill" else "doc.on.clipboard.fill", 18f, white)
                    Text(t(if (done) "copiedScriptButton" else "copyScriptButton"), style = label, color = colors.textOnPrimary)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.udp).testTag("readout-copy-all").clip(shape)
                        .clickable { copy(buildCopyBlock(input, t), Copied.ALL, "copyDetailsFailed") }
                        .background(colors.card).rnBorder(1.udp, colors.border, shape).padding(vertical = space.md.udp),
                    horizontalArrangement = Arrangement.spacedBy(space.sm.udp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val done = copied == Copied.ALL
                    Icon(if (done) "checkmark.circle.fill" else "square.and.arrow.up", 18f, if (done) success else colors.text)
                    Text(t(if (done) "copiedFullButton" else "copyFullButton"), style = label, color = colors.text)
                }
            }

            // Silver Alert
            val p9 = DesignTokens.Primary
            val s1 = DesignTokens.Secondary
            val alertShape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
            Column(
                Modifier.fillMaxWidth().clip(alertShape)
                    .background(Color(if (colors.isDark) p9.c900 else s1.c100))
                    .rnBorder(1.udp, Color(if (colors.isDark) p9.c700 else s1.c300), alertShape)
                    .padding(space.lg.udp),
                verticalArrangement = Arrangement.spacedBy(space.sm.udp),
            ) {
                Text(t("silverAlert.title"), style = type.bodyBold.style(), color = Color(if (colors.isDark) s1.c100 else p9.c900))
                Text(
                    t("silverAlert.body", mapOf("emergencyNumber" to emergencyNumber)), style = rnTextStyle(16f, 22f),
                    color = Color(if (colors.isDark) DesignTokens.Neutral.c300 else DesignTokens.Neutral.c700),
                )
            }
        }
    }
}

/** A readout card: hairline border, radius lg, padding lg, gap xs. */
@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(colors.card).rnBorder(hairline, colors.border, shape).padding(space.lg.udp),
        verticalArrangement = Arrangement.spacedBy(space.xs.udp),
        content = content,
    )
}

@Composable
private fun SectionLabel(icon: String, text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(bottom = space.xs.udp),
        horizontalArrangement = Arrangement.spacedBy(space.xs.udp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 14f, color)
        // bodyBold with fontSize 12: the 24 line height stays.
        Text(text.uppercase(), style = rnTextStyle(12f, type.bodyBold.lineHeight, type.bodyBold.fontWeight, 0.5f), color = color)
    }
}

private val InfoRowRule = Color(128, 128, 128, (0.15f * 255).toInt())

@Composable
private fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val rule = hairline
    Column(
        modifier.fillMaxWidth()
            .drawBehind { drawLine(InfoRowRule, Offset(0f, rule.toPx() / 2), Offset(size.width, rule.toPx() / 2), rule.toPx()) }
            .padding(top = rule).padding(vertical = space.sm.udp),
    ) {
        Text(
            label.uppercase(), style = rnTextStyle(type.caption.fontSize, type.caption.lineHeight, type.caption.fontWeight, 0.5f),
            color = colors.textSecondary, modifier = Modifier.padding(bottom = 2.udp),
        )
        Text(value, style = rnTextStyle(16f, 22f), color = colors.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(DesignTokens.Radius.md.dp)
    Column(
        Modifier.widthIn(min = 80.udp, max = 160.udp).clip(shape).background(colors.surface)
            .rnBorder(hairline, colors.border, shape).padding(horizontal = space.md.udp, vertical = space.sm.udp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = type.small.style(), color = colors.textSecondary)
        Text(value, style = type.bodyBold.style(), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The amber "check this" note: the wearing reminder and the vehicle check. */
@Composable
private fun WarnNote(text: String, iconSize: Float, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(warning.copy(alpha = 0x15 / 255f))
            .rnBorder(1.udp, warning.copy(alpha = 0x40 / 255f), shape).padding(space.md.udp),
        horizontalArrangement = Arrangement.spacedBy(space.sm.udp),
    ) {
        Icon("exclamationmark.triangle.fill", iconSize, warning)
        Text(
            text, style = rnTextStyle(16f, 20f), modifier = Modifier.weight(1f),
            color = Color(if (colors.isDark) DesignTokens.Secondary.c100 else DesignTokens.Neutral.c700),
        )
    }
}

@Composable
private fun IdentityCard(t: Translate, p: Profile) {
    val colors = LocalAppColors.current
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(space.md.udp), verticalAlignment = Alignment.CenterVertically) {
            val photoShape = RoundedCornerShape(DesignTokens.Radius.md.dp)
            val photo = rememberPhoto(p.photoUri)
            if (photo != null) {
                Image(photo, null, Modifier.size(80.udp).clip(photoShape), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.size(80.udp).clip(photoShape).background(colors.primaryLight), contentAlignment = Alignment.Center) {
                    Icon("person.fill", 36f, colors.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xs.udp)) {
                Text(p.name, style = type.headline.style(), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                p.nickname?.ifEmpty { null }?.let {
                    Text(
                        t("sections.identity.goesBy", mapOf("nickname" to it)),
                        style = type.body.style().copy(fontStyle = FontStyle.Italic), color = colors.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                p.dateOfBirth?.ifEmpty { null }?.let {
                    Row(
                        Modifier.padding(top = space.xxs.udp),
                        horizontalArrangement = Arrangement.spacedBy(space.xs.udp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon("calendar", 12f, colors.textSecondary)
                        Text(t("sections.identity.dob", mapOf("dob" to it)), style = type.caption.style(), color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(t: Translate, c: ContactEntity, index: Int, onCall: () -> Unit) {
    val colors = LocalAppColors.current
    val rule = hairline
    Row(
        Modifier.fillMaxWidth()
            .drawBehind { drawLine(colors.border, Offset(0f, rule.toPx() / 2), Offset(size.width, rule.toPx() / 2), rule.toPx()) }
            .padding(top = rule).padding(top = space.sm.udp),
        horizontalArrangement = Arrangement.spacedBy(space.md.udp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.udp)) {
            Text(c.name, style = type.bodyBold.style(), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val role = c.relationship?.ifEmpty { null }
                ?: c.role?.let { t("roles.$it", mapOf("ns" to "contacts", "defaultValue" to it)) } ?: ""
            Text(role, style = type.caption.style(), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val shape = RoundedCornerShape(DesignTokens.Radius.md.dp)
        Row(
            Modifier.testTag("readout-contact-$index-call").clip(shape).clickable(onClick = onCall).background(success)
                .padding(horizontal = space.md.udp, vertical = space.sm.udp),
            horizontalArrangement = Arrangement.spacedBy(space.xs.udp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon("phone.fill", 14f, white)
            Text(formatPhoneNumber(c.phone), style = type.bodyBold.style(), color = white)
        }
    }
}
