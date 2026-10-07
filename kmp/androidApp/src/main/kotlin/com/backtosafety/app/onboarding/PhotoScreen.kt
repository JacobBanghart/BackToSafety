package com.backtosafety.app.onboarding

import android.content.Context
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

/** Port of app/onboarding/photo.tsx. (RN also offers a square crop after picking; not yet here.) */
@Composable
fun PhotoScreen(t: Translate, tCommon: Translate, store: Store, onBack: () -> Unit, onContinue: () -> Unit) {
    val colors = LocalAppColors.current
    val type = DesignTokens.Typography
    val space = DesignTokens.Spacing
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoUri by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val pickFromLibrary = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUri = savePhotoLocally(context, uri)
    }
    var cameraTarget by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = cameraTarget
        if (taken && file != null) photoUri = savePhotoLocally(context, Uri.fromFile(file))
    }

    OnboardingScaffold(
        scrolls = false,
        footer = {
            SkipButton(t("photo.skip"), "onboarding-photo-skip") {
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
                        saving = false
                        onContinue()
                    }
                },
            )
        },
    ) {
        OnboardingStepHeader(activeStep = 2, totalSteps = 4, onBack = onBack)
        StepTitle(t("photo.title"))
        Text(t("photo.subtitle"), style = type.body.style(), color = colors.textSecondary, modifier = Modifier.padding(bottom = space.xl.dp))

        Box(Modifier.fillMaxWidth().padding(bottom = space.xl.dp), contentAlignment = Alignment.Center) {
            val bitmap = remember(photoUri) {
                photoUri?.let { BitmapFactory.decodeFile(Uri.parse(it).path)?.asImageBitmap() }
            }
            if (bitmap != null) {
                Image(bitmap, null, Modifier.size(200.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Column(
                    Modifier
                        .size(200.dp)
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
                    Text("📷", style = rnTextStyle(48f, 56f), modifier = Modifier.padding(top = space.xs.dp))
                    Text(t("photo.noPhoto"), color = colors.textSecondary, modifier = Modifier.padding(top = space.sm.dp))
                }
            }
        }

        Column(Modifier.padding(bottom = space.xl.dp), verticalArrangement = Arrangement.spacedBy(space.md.dp)) {
            OutlineButton(t("photo.takePhoto"), "onboarding-photo-take") {
                val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                cameraTarget = file
                takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
            }
            OutlineButton(t("photo.chooseLibrary"), "onboarding-photo-library") {
                pickFromLibrary.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
            .border(1.dp, colors.primary, RoundedCornerShape(DesignTokens.Radius.lg.dp))
            .clickable(onClick = onClick)
            // RN's 1dp border adds to the height (14 padding + 1 border each side).
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = DesignTokens.Typography.bodyBold.style(), color = colors.primary)
    }
}

/**
 * Copies the picked image into filesDir as profile_photo_<epochMs>.jpg and returns its
 * file:// URI, the same place and form the RN app stores (spec/storage.md).
 */
private fun savePhotoLocally(context: Context, source: Uri): String? = runCatching {
    val dest = File(context.filesDir, "profile_photo_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(source)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
    Uri.fromFile(dest).toString()
}.getOrNull()
