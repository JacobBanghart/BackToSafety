package com.backtosafety.app

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.compose.runtime.saveable.SaverScope
import com.backtosafety.app.core.PROFILE_TEXT_FIELDS
import com.backtosafety.app.core.key
import com.backtosafety.app.profile.ProfileForm
import com.backtosafety.app.profile.ProfileFormSaver
import com.backtosafety.core.ffi.ProfileField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Profile's form goes into the activity's saved-state Bundle (issue #7). It must come back as
 * the same form after being written to a Parcel, and stay well under the Bundle's limit: the
 * whole saved state is capped near 1 MB, and 50 KB is the usual ceiling for one screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileFormSaverTest {
    private val scope = SaverScope { true }

    private fun save(form: ProfileForm?) = with(ProfileFormSaver) { scope.save(form) }

    private fun restore(saved: List<Any>) = ProfileFormSaver.restore(saved)

    private fun form(text: (ProfileField) -> String) = ProfileForm(
        text = PROFILE_TEXT_FIELDS.associateWith(text),
        dominantHand = "left",
        mobility = listOf("Uses cane", "Manual wheelchair", "Other"),
        mobilityOther = "Tires after a block",
    )

    /** [values] as Compose writes saved state (each in a list, under its key), marshalled and read back. */
    private fun throughParcel(values: Map<String, List<Any>>): Pair<Bundle, Int> {
        val bundle = Bundle()
        @Suppress("UNCHECKED_CAST")
        for ((key, value) in values) bundle.putParcelableArrayList(key, arrayListOf<Any>(value) as ArrayList<Parcelable>)
        val parcel = Parcel.obtain()
        try {
            bundle.writeToParcel(parcel, 0)
            val size = parcel.dataSize()
            val bytes = parcel.marshall()
            val read = Parcel.obtain()
            try {
                read.unmarshall(bytes, 0, bytes.size)
                read.setDataPosition(0)
                return Bundle.CREATOR.createFromParcel(read).apply { classLoader = javaClass.classLoader } to size
            } finally {
                read.recycle()
            }
        } finally {
            parcel.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun Bundle.saved(key: String): List<Any> = (get(key) as List<*>).single() as List<Any>

    @Test
    fun aFullFormComesBackEqual() {
        val form = form { "${it.key} value" }
        val saved = checkNotNull(save(form))
        assertEquals(form, restore(saved))

        val (bundle, _) = throughParcel(mapOf("form" to saved))
        assertEquals(form, restore(bundle.saved("form")))
    }

    @Test
    fun emptyFieldsAreLeftOutAndComeBackEmpty() {
        val form = form { if (it == ProfileField.FULL_NAME) "Margaret Smith" else "" }
        val saved = checkNotNull(save(form))
        assertEquals(listOf("name", "Margaret Smith"), saved[0])
        assertEquals(form, restore(saved))
    }

    @Test
    fun noFormIsNotSaved() {
        assertNull(save(null))
    }

    @Test
    fun theFormAndWhatItIsComparedWithStayUnder50KB() {
        val form = form { "x".repeat(200) }
        val initial = form { "y".repeat(200) }
        val (bundle, size) = throughParcel(mapOf("form" to checkNotNull(save(form)), "initial" to checkNotNull(save(initial))))
        assertTrue("saved state is $size bytes", size < 50 * 1024)
        assertEquals(form, restore(bundle.saved("form")))
        assertEquals(initial, restore(bundle.saved("initial")))
    }
}
