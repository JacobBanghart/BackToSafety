package com.backtosafety.app.onboarding

import android.content.Context
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.Analytics
import com.backtosafety.app.ui.trackStep
import com.backtosafety.app.ui.TrackStepViewed
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.rememberPhotoPicker
import com.backtosafety.app.ui.udp
import com.backtosafety.app.ui.OnboardingStepHeader
import com.backtosafety.app.ui.PrimaryButton
import com.backtosafety.app.ui.SkipButton
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.app.ui.style
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.Translate
import com.backtosafety.core.data.Store
import com.backtosafety.core.invoke
import java.io.File
import kotlinx.coroutines.launch

/** Port of app/onboarding/photo.tsx. */
@Composable
fun PhotoScreen(t: Translate, tCommon: Translate, store: Store, onBack: () -> Unit, onContinue: () -> Unit) {
    TrackStepViewed("profile_photo")
    val colors = LocalAppColors.current
    val type = DesignTokens.Typography
    val space = DesignTokens.Spacing
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoUri by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val picker = rememberPhotoPicker { photoUri = it }

    OnboardingScaffold(
        scrolls = false,
        footer = {
            SkipButton(t("photo.skip"), "onboarding-photo-skip") {
                trackStep(false, "profile_photo")
                scope.launch { store.completeStep("profile_photo"); onContinue() }
            }
            PrimaryButton(
                if (saving) tCommon("saving") else t("photo.continue"), "onboarding-photo-continue",
                enabled = photoUri != null && !saving,
                textStyle = rnTextStyle(18f, 24f, fontWeight = 600),
                onClick = {
                    saving = true
                    scope.launch {
                        store.saveProfile { it.copy(photoUri = photoUri) }
                        store.completeStep("profile_photo")
                        trackStep(true, "profile_photo")
                        saving = false
                        onContinue()
                    }
                },
            )
        },
    ) {
        OnboardingStepHeader(activeStep = 2, totalSteps = 4, onBack = onBack)
        StepTitle(t("photo.title"))
        Text(t("photo.subtitle"), style = type.body.style(), color = colors.textSecondary, modifier = Modifier.padding(bottom = space.xl.udp))

        Box(Modifier.fillMaxWidth().padding(bottom = space.xl.udp), contentAlignment = Alignment.Center) {
            val bitmap = remember(photoUri) {
                photoUri?.let { BitmapFactory.decodeFile(Uri.parse(it).path)?.asImageBitmap() }
            }
            if (bitmap != null) {
                Image(bitmap, null, Modifier.size(200.udp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Column(
                    Modifier
                        .size(200.udp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .drawBehind {
                            val stroke = 2.dp.toPx()
                            drawCircle(
                                color = colors.border,
                                radius = size.minDimension / 2 - stroke / 2,
                                center = Offset(size.width / 2, size.height / 2),
                                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))),
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("📷", style = rnTextStyle(48f, 56f), modifier = Modifier.padding(top = space.xs.udp))
                    Text(t("photo.noPhoto"), color = colors.textSecondary, modifier = Modifier.padding(top = space.sm.udp))
                }
            }
        }

        Column(Modifier.padding(bottom = space.xl.udp), verticalArrangement = Arrangement.spacedBy(space.md.udp)) {
            OutlineButton(t("photo.takePhoto"), "onboarding-photo-take") {
                Analytics.track(AnalyticsEvent.PROFILE_PHOTO_TAKEN)
                picker.fromCamera()
            }
            OutlineButton(t("photo.chooseLibrary"), "onboarding-photo-library") {
                Analytics.track(AnalyticsEvent.PROFILE_PHOTO_CHOSEN)
                picker.fromLibrary()
            }
        }
        Text(
            t("photo.tip"),
            style = rnTextStyle(14f, 24f).copy(fontStyle = FontStyle.Italic),
            color = colors.textDisabled,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OutlineButton(label: String, testTag: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
            .border(1.udp, colors.primary, RoundedCornerShape(DesignTokens.Radius.lg.dp))
            .clickable(onClick = onClick)
            // RN's 1dp border adds to the height (14 padding + 1 border each side).
            .padding(vertical = 15.udp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = DesignTokens.Typography.bodyBold.style(), color = colors.primary)
    }
}
