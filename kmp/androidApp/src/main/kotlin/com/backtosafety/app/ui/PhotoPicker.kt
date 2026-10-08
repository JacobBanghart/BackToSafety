package com.backtosafety.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import java.io.File

/** Opens the photo library or the camera; see [rememberPhotoPicker]. */
class PhotoPicker internal constructor(val fromLibrary: () -> Unit, val fromCamera: () -> Unit)

/**
 * expo-image-picker with allowsEditing, aspect [1, 1], quality 0.8: pick (or shoot) an image,
 * crop it square in the same cropper activity, then copy the result into filesDir as
 * profile_photo_<epochMs>.jpg. [onPhoto] gets its file:// URI, as the RN app stores it.
 */
@Composable
fun rememberPhotoPicker(onPhoto: (String) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val crop = rememberLauncherForActivityResult(CropImageContract()) { result ->
        val cropped = result.uriContent ?: return@rememberLauncherForActivityResult
        savePhotoLocally(context, cropped)?.let(onPhoto)
    }
    fun cropSquare(source: Uri) = crop.launch(
        CropImageContractOptions(
            source,
            CropImageOptions().apply {
                aspectRatioX = 1
                aspectRatioY = 1
                fixAspectRatio = true
                initialCropWindowPaddingRatio = 0f
                outputCompressFormat = Bitmap.CompressFormat.JPEG
                outputCompressQuality = 80
            },
        ),
    )
    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropSquare(uri)
    }
    var cameraTarget by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = cameraTarget
        if (taken && file != null) cropSquare(Uri.fromFile(file))
    }
    return remember {
        PhotoPicker(
            fromLibrary = { library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            fromCamera = {
                val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                cameraTarget = file
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
            },
        )
    }
}

/**
 * Copies an image into filesDir as profile_photo_<epochMs>.jpg and returns its file:// URI,
 * the same place and form the RN app stores (spec/storage.md).
 */
private fun savePhotoLocally(context: Context, source: Uri): String? = runCatching {
    val dest = File(context.filesDir, "profile_photo_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(source)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
    Uri.fromFile(dest).toString()
}.getOrNull()
