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
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.app.ui.style
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.Translate
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.ContactEntity
import com.backtosafety.core.formatPhoneInput
import com.backtosafety.core.invoke
import kotlinx.coroutines.launch

/** Port of app/onboarding/contact.tsx. */
@Composable
fun ContactScreen(t: Translate, tCommon: Translate, store: Store, onBack: () -> Unit, onContinue: () -> Unit) {
    val colors = LocalAppColors.current
    val type = DesignTokens.Typography
    val space = DesignTokens.Spacing
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val input = rnTextStyle(16f, 20f)

    OnboardingScaffold(
        contentBottom = 20f,
        footer = {
            SkipButton(t("contact.skip"), "onboarding-contact-skip") {
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
                    if (name.isBlank() || phone.isBlank()) {
                        error = t("contact.required")
                        return@PrimaryButton
                    }
                    scope.launch {
                        runCatching {
                            store.addContact(
                                ContactEntity(
                                    name = name.trim(), phone = phone.trim(),
                                    relationship = relationship.trim().ifEmpty { null },
                                    role = "primary_caregiver", notifyOnEmergency = true, shareMedicalInfo = true,
                                ),
                            )
                            store.completeStep("emergency_contact")
                        }.onSuccess { onContinue() }.onFailure { error = tCommon("saveFailed") }
                    }
                },
                dimmed = name.isBlank() || phone.isBlank(),
            )
        },
    ) {
        OnboardingStepHeader(activeStep = 4, totalSteps = 4, onBack = onBack)
        StepTitle(t("contact.title"))
        Text(t("contact.subtitle"), style = type.body.style(), color = colors.textSecondary, modifier = Modifier.padding(bottom = space.xxl.dp))
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
                .padding(top = space.xl.dp)
                .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
                .background(colors.primaryLight)
                .padding(space.lg.dp),
        ) {
            Text(t("contact.infoBox.title"), style = type.bodyBold.style(), color = colors.text, modifier = Modifier.padding(bottom = space.sm.dp))
            Text(t("contact.infoBox.body"), style = rnTextStyle(14f, 22f), color = colors.textSecondary)
        }
    }
}
