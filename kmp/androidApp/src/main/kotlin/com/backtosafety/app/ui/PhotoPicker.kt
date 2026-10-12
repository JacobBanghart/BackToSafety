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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import java.io.File

/** Opens the photo library or the camera; see [rememberPhotoPicker]. */
class PhotoPicker internal constructor(val fromLibrary: () -> Unit, val fromCamera: () -> Unit)

/** The longest side of a cropped photo: more than any screen shows, and a bitmap that stays small. */
private const val MAX_PHOTO_PX = 1024

/**
 * expo-image-picker with allowsEditing, aspect [1, 1], quality 0.8: pick (or shoot) an image,
 * crop it square in the same cropper activity (at most [MAX_PHOTO_PX] on a side, so a 50 MP
 * original doesn't become a 144 MB bitmap), then copy the result into filesDir as
 * profile_photo_<epochMs>.jpg. [onPhoto] gets its file:// URI, as the RN app stores it. The
 * camera's shot and the cropper's output are deleted once the photo is saved or dropped.
 */
@Composable
fun rememberPhotoPicker(onPhoto: (String) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val crop = rememberLauncherForActivityResult(CropImageContract()) { result ->
        val cropped = result.uriContent
        if (cropped != null) savePhotoLocally(context, cropped)?.let(onPhoto)
        PhotoFiles.deleteTemps(context.cacheDir)
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
                outputRequestWidth = MAX_PHOTO_PX
                outputRequestHeight = MAX_PHOTO_PX
                outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE
                customOutputUri = Uri.fromFile(PhotoFiles.cropped(context.cacheDir, System.currentTimeMillis()))
            },
        ),
    )
    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropSquare(uri)
    }
    // The camera app can outlast this process (the system reclaims it while the camera has the
    // screen), so where the shot goes survives it too, as a path.
    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = cameraTarget?.let(::File)
        if (taken && file != null) cropSquare(Uri.fromFile(file)) else PhotoFiles.deleteTemps(context.cacheDir)
    }
    return remember {
        PhotoPicker(
            fromLibrary = { library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            fromCamera = {
                val file = PhotoFiles.camera(context.cacheDir, System.currentTimeMillis())
                cameraTarget = file.path
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
            },
        )
    }
}

/**
 * Copies an image into filesDir as profile_photo_<epochMs>.jpg and returns its file:// URI,
 * the same place and form the RN app stores (spec/storage.md). A copy that fails leaves no file.
 */
private fun savePhotoLocally(context: Context, source: Uri): String? {
    val dest = PhotoFiles.photo(context.filesDir, System.currentTimeMillis())
    return runCatching {
        context.contentResolver.openInputStream(source)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
        Uri.fromFile(dest).toString()
    }.onFailure { dest.delete() }.getOrNull()
}
