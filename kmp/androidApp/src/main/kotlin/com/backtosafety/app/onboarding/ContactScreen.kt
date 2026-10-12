package com.backtosafety.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.backtosafety.app.ui.AppInput
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.OnboardingStepHeader
import com.backtosafety.app.ui.PrimaryButton
import com.backtosafety.app.ui.SkipButton
import com.backtosafety.app.ui.TrackStepViewed
import com.backtosafety.app.ui.reportSaveFailed
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.app.ui.runCatchingCancellable
import com.backtosafety.app.ui.style
import com.backtosafety.app.ui.trackStep
import com.backtosafety.app.ui.udp
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.Translate
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.ContactEntity
import com.backtosafety.core.formatPhoneInput
import com.backtosafety.core.invoke
import com.backtosafety.core.isUsablePhone
import kotlinx.coroutines.launch

/** Port of app/onboarding/contact.tsx. */
@Composable
fun ContactScreen(t: Translate, tCommon: Translate, store: Store, onBack: () -> Unit, onContinue: () -> Unit) {
    TrackStepViewed("emergency_contact")
    val colors = LocalAppColors.current
    val type = DesignTokens.Typography
    val space = DesignTokens.Spacing
    val scope = rememberCoroutineScope()
    // Saveable, so coming back from the next screen shows what was entered and the contact it saved.
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var relationship by rememberSaveable { mutableStateOf("") }
    var savedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val input = rnTextStyle(16f, 20f)

    OnboardingScaffold(
        contentBottom = 20f,
        footer = {
            SkipButton(t("contact.skip"), "onboarding-contact-skip") {
                trackStep(false, "emergency_contact")
                scope.launch { store.completeStep("emergency_contact"); onContinue() }
            }
            // Styled as disabled until both fields are filled, but still tappable: a tap
            // explains what's missing (as the RN screen does).
            PrimaryButton(
                t("contact.continue"), "onboarding-contact-continue",
                enabled = true,
                modifier = Modifier,
                textStyle = rnTextStyle(18f, 24f, fontWeight = 600),
                onClick = {
                    if (saving) return@PrimaryButton
                    if (name.isBlank() || phone.isBlank()) {
                        error = t("contact.required")
                        return@PrimaryButton
                    }
                    if (!isUsablePhone(phone)) {
                        error = t("contact.phoneInvalid")
                        return@PrimaryButton
                    }
                    saving = true
                    scope.launch {
                        runCatchingCancellable {
                            val contact = ContactEntity(
                                id = savedId ?: 0, name = name.trim(), phone = phone.trim(),
                                relationship = relationship.trim().ifEmpty { null },
                                role = "primary_caregiver", notifyOnEmergency = true, shareMedicalInfo = true,
                            )
                            // Back from the next screen and Continue again edits this contact
                            // rather than adding a second one.
                            if (savedId != null) store.updateContact(contact) else savedId = store.addContact(contact)
                            store.completeStep("emergency_contact")
                            trackStep(true, "emergency_contact")
                        }.onSuccess { onContinue() }.onFailure { e ->
                            reportSaveFailed("onboarding_contact", "save", e)
                            error = tCommon("saveFailed")
                        }
                        saving = false
                    }
                },
                dimmed = name.isBlank() || phone.isBlank(),
            )
        },
    ) {
        OnboardingStepHeader(activeStep = 4, totalSteps = 4, onBack = onBack)
        StepTitle(t("contact.title"))
        Text(t("contact.subtitle"), style = type.body.style(), color = colors.textSecondary, modifier = Modifier.padding(bottom = space.xxl.udp))
        Column(verticalArrangement = Arrangement.spacedBy(20.udp)) {
            Field(t("contact.nameLabel")) {
                AppInput(name, { name = it; error = "" }, t("contact.namePlaceholder"), "onboarding-contact-name",
                    input, capitalization = KeyboardCapitalization.Words)
            }
            Field(t("contact.phoneLabel")) {
                AppInput(phone, { phone = formatPhoneInput(it); error = "" }, t("contact.phonePlaceholder"),
                    "onboarding-contact-phone", input, keyboardType = KeyboardType.Phone)
            }
            Field(t("contact.relationshipLabel")) {
                AppInput(relationship, { relationship = it }, t("contact.relationshipPlaceholder"),
                    "onboarding-contact-relationship", input, capitalization = KeyboardCapitalization.Words)
            }
            if (error.isNotEmpty()) Text(error, style = rnTextStyle(14f, 24f), color = colors.error)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = space.xl.udp)
                .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
                .background(colors.primaryLight)
                .padding(space.lg.udp),
        ) {
            Text(t("contact.infoBox.title"), style = type.bodyBold.style(), color = colors.text, modifier = Modifier.padding(bottom = space.sm.udp))
            Text(t("contact.infoBox.body"), style = rnTextStyle(14f, 22f), color = colors.textSecondary)
        }
    }
}
