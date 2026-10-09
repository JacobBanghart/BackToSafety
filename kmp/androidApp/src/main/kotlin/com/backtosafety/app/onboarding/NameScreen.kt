package com.backtosafety.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.backtosafety.app.ui.AppInput
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.OnboardingStepHeader
import com.backtosafety.app.ui.PrimaryButton
import com.backtosafety.app.ui.TrackStepViewed
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.app.ui.style
import com.backtosafety.app.ui.trackStep
import com.backtosafety.app.ui.udp
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.Translate
import com.backtosafety.core.data.Store
import com.backtosafety.core.invoke
import kotlinx.coroutines.launch

/** Port of app/onboarding/name.tsx. */
@Composable
fun NameScreen(t: Translate, tCommon: Translate, store: Store, onBack: () -> Unit, onContinue: () -> Unit) {
    TrackStepViewed("profile_name")
    val colors = LocalAppColors.current
    val type = DesignTokens.Typography
    val space = DesignTokens.Spacing
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    OnboardingScaffold(
        footer = {
            PrimaryButton(
                t("name.continue"), "onboarding-name-continue",
                enabled = name.isNotBlank(),
                textStyle = type.bodyLarge.style().copy(fontWeight = FontWeight.SemiBold),
                onClick = {
                    scope.launch {
                        runCatching {
                            store.saveProfile { it.copy(name = name.trim(), nickname = nickname.trim().ifEmpty { null }) }
                            store.completeStep("profile_name")
                            trackStep(true, "profile_name")
                        }.onSuccess { onContinue() }.onFailure { error = tCommon("saveFailed") }
                    }
                },
            )
        },
    ) {
        OnboardingStepHeader(activeStep = 1, totalSteps = 4, onBack = onBack)
        StepTitle(t("name.title"))
        Text(t("name.subtitle"), style = type.body.style(), color = colors.textSecondary, modifier = Modifier.padding(bottom = space.xxl.udp))
        Column(verticalArrangement = Arrangement.spacedBy(space.xl.udp)) {
            Field(t("name.nameLabel")) {
                AppInput(name, { name = it; error = "" }, t("name.namePlaceholder"), "onboarding-name-input",
                    textStyle = rnTextStyle(18f, 22f), capitalization = KeyboardCapitalization.Words)
            }
            Field(t("name.nicknameLabel")) {
                AppInput(nickname, { nickname = it }, t("name.nicknamePlaceholder"), "onboarding-name-nickname",
                    textStyle = rnTextStyle(18f, 22f), capitalization = KeyboardCapitalization.Words)
                Text(t("name.nicknameHint"), style = type.body.style(), color = colors.textDisabled)
            }
            if (error.isNotEmpty()) Text(error, style = type.body.style(), color = colors.error)
        }
    }
}

/** ThemedText type="title" with marginBottom sm, the heading on every onboarding step. */
@Composable
fun StepTitle(text: String) {
    Text(
        text,
        style = DesignTokens.Typography.title.style(),
        color = LocalAppColors.current.text,
        modifier = Modifier.padding(bottom = DesignTokens.Spacing.sm.udp),
    )
}

/** A labeled input group: bodyBold label, then the field, sm apart. */
@Composable
fun Field(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.udp)) {
        Text(label, style = DesignTokens.Typography.bodyBold.style(), color = LocalAppColors.current.text)
        content()
    }
}
