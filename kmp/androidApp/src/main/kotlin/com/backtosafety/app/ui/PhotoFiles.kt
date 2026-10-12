package com.backtosafety.app.ui

import java.io.File

/**
 * The files the photo flow writes: saved profile photos in filesDir (spec/storage.md) and, in
 * cacheDir, the camera's shot and the cropper's output on their way there. The person's face is
 * in all of them, so none outlives its use.
 */
object PhotoFiles {
    private const val PHOTO_PREFIX = "profile_photo_"
    private const val CAMERA_PREFIX = "camera_"
    private const val CROPPED_PREFIX = "cropped_"

    fun photo(filesDir: File, epochMs: Long) = File(filesDir, "$PHOTO_PREFIX$epochMs.jpg")

    fun camera(cacheDir: File, epochMs: Long) = File(cacheDir, "$CAMERA_PREFIX$epochMs.jpg")

    fun cropped(cacheDir: File, epochMs: Long) = File(cacheDir, "$CROPPED_PREFIX$epochMs.jpg")

    /**
     * Deletes every saved profile photo except the one at [keeping] (a file:// URI; none if
     * null) and the camera and cropper temp files. Best effort: a file that won't go is skipped,
     * and the next call takes it.
     */
    fun clean(filesDir: File, cacheDir: File, keeping: String? = null) {
        val keep = keeping?.substringAfterLast('/')
        filesDir.listFiles { f -> f.name.startsWith(PHOTO_PREFIX) && f.name != keep }?.forEach { it.delete() }
        deleteTemps(cacheDir)
    }

    /** Deletes the camera and cropper temp files, the ones the photo flow is done with. */
    fun deleteTemps(cacheDir: File) {
        cacheDir.listFiles { f -> f.name.startsWith(CAMERA_PREFIX) || f.name.startsWith(CROPPED_PREFIX) }
            ?.forEach { it.delete() }
    }
}
