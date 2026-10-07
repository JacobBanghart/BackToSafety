package com.backtosafety.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.backtosafety.core.formatHeightInput
import com.backtosafety.core.formatWeightInput
import com.backtosafety.core.invoke
import kotlinx.coroutines.launch

/** Port of app/onboarding/appearance.tsx. */
@Composable
fun AppearanceScreen(t: Translate, store: Store, onBack: () -> Unit, onContinue: () -> Unit) {
    val colors = LocalAppColors.current
    val type = DesignTokens.Typography
    val space = DesignTokens.Spacing
    val scope = rememberCoroutineScope()
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var hair by remember { mutableStateOf("") }
    var eyes by remember { mutableStateOf("") }
    var marks by remember { mutableStateOf("") }
    val input = rnTextStyle(16f, 20f)

    OnboardingScaffold(
        contentBottom = 20f,
        footer = {
            SkipButton(t("appearance.skip"), "onboarding-appearance-skip") {
                scope.launch { store.completeStep("profile_appearance"); onContinue() }
            }
            PrimaryButton(
                t("appearance.continue"), "onboarding-appearance-continue",
                textStyle = rnTextStyle(18f, 24f, fontWeight = 600),
                onClick = {
                    scope.launch {
                        store.saveProfile {
                            it.copy(
                                height = height.trim().ifEmpty { null },
                                weight = weight.trim().ifEmpty { null },
                                hairColor = hair.trim().ifEmpty { null },
                                eyeColor = eyes.trim().ifEmpty { null },
                                identifyingMarks = marks.trim().ifEmpty { null },
                            )
                        }
                        store.completeStep("profile_appearance")
                        onContinue()
                    }
                },
            )
        },
    ) {
        OnboardingStepHeader(activeStep = 3, totalSteps = 4, onBack = onBack)
        StepTitle(t("appearance.title"))
        Text(
            t("appearance.subtitle"), style = type.body.style(), color = colors.textSecondary,
            modifier = Modifier.alpha(0.7f).padding(bottom = space.xxl.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(space.md.dp)) {
                Field(t("appearance.heightLabel"), Modifier.weight(1f)) {
                    AppInput(height, { height = formatHeightInput(it) }, t("appearance.heightPlaceholder"),
                        "onboarding-appearance-height", input, keyboardType = KeyboardType.Number)
                }
                Field(t("appearance.weightLabel"), Modifier.weight(1f)) {
                    AppInput(weight, { weight = formatWeightInput(it) }, t("appearance.weightPlaceholder"),
                        "onboarding-appearance-weight", input, keyboardType = KeyboardType.Number)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(space.md.dp)) {
                Field(t("appearance.hairLabel"), Modifier.weight(1f)) {
                    AppInput(hair, { hair = it }, t("appearance.hairPlaceholder"), "onboarding-appearance-hair", input)
                }
                Field(t("appearance.eyeLabel"), Modifier.weight(1f)) {
                    AppInput(eyes, { eyes = it }, t("appearance.eyePlaceholder"), "onboarding-appearance-eyes", input)
                }
            }
            Field(t("appearance.marksLabel")) {
                AppInput(marks, { marks = it }, t("appearance.marksPlaceholder"), "onboarding-appearance-marks", input, multiline = true)
            }
        }
    }
}
