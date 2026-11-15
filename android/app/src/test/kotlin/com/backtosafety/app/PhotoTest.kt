package com.backtosafety.app

import android.graphics.Bitmap
import com.backtosafety.app.ui.PhotoFiles
import com.backtosafety.app.ui.decodePhoto
import com.backtosafety.app.ui.photoSampleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Photos are decoded no larger than the screen needs and never throw, and Delete Account
 * (and a replaced photo) removes the photo files and the camera's leftovers, only those.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhotoTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun jpeg(file: File, width: Int, height: Int): File {
        file.outputStream().use { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return file
    }

    @Test
    fun sampleSizeKeepsTheShortSideAtTheTargetOrMore() {
        assertEquals(1, photoSampleSize(300, 300, 200))
        assertEquals(2, photoSampleSize(400, 400, 200))
        assertEquals(4, photoSampleSize(4000, 800, 200)) // 800 / 4 = 200; the long side doesn't count
        assertEquals(16, photoSampleSize(8160, 6120, 360)) // a 50 MP photo for a 120dp circle at 3x
        assertEquals(1, photoSampleSize(0, 0, 200)) // bounds that couldn't be read
    }

    @Test
    fun aLargePhotoIsDecodedSmaller() {
        val bitmap = decodePhoto(jpeg(folder.newFile("big.jpg"), 2000, 1500).path, 300)!!
        assertEquals(500, bitmap.width) // sample size 4
        assertEquals(375, bitmap.height)
    }

    @Test
    fun aSmallPhotoIsLeftAlone() {
        val bitmap = decodePhoto(jpeg(folder.newFile("small.jpg"), 120, 120).path, 300)!!
        assertEquals(120, bitmap.width)
    }

    @Test
    fun aMissingFileDecodesToNull() {
        assertNull(decodePhoto(null, 300))
        assertNull(decodePhoto(File(folder.root, "gone.jpg").path, 300))
    }

    @Test
    fun cleaningKeepsTheCurrentPhotoAndNothingElseOfTheFlow() {
        val files = folder.newFolder("files")
        val cache = folder.newFolder("cache")
        val old = File(files, "profile_photo_1.jpg").apply { writeText("old") }
        val current = File(files, "profile_photo_2.jpg").apply { writeText("current") }
        val database = File(files, "app.db").apply { writeText("db") }
        val shot = File(cache, "camera_3.jpg").apply { writeText("shot") }
        val cropped = File(cache, "cropped_4.jpg").apply { writeText("cropped") }
        val other = File(cache, "image_cache_5.bin").apply { writeText("other") }

        PhotoFiles.clean(files, cache, keeping = "file://${current.path}")

        assertFalse(old.exists())
        assertTrue(current.exists())
        assertFalse(shot.exists())
        assertFalse(cropped.exists())
        assertTrue("only the photo flow's files go", database.exists() && other.exists())
    }

    @Test
    fun deleteAccountRemovesEveryPhoto() {
        val files = folder.newFolder("files")
        val cache = folder.newFolder("cache")
        val photos = (1..3).map { File(files, "profile_photo_$it.jpg").apply { writeText("photo") } }
        val database = File(files, "app.db").apply { writeText("db") }
        val shot = File(cache, "camera_9.jpg").apply { writeText("shot") }

        PhotoFiles.clean(files, cache)

        assertTrue(photos.none { it.exists() })
        assertFalse(shot.exists())
        assertTrue(database.exists())
    }

    @Test
    fun cleaningAMissingFolderIsHarmless() {
        PhotoFiles.clean(File(folder.root, "nope"), File(folder.root, "nada"))
    }
}
