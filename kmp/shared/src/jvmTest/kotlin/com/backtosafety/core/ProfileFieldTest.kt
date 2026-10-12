package com.backtosafety.core

import com.backtosafety.core.data.toProfile
import com.backtosafety.core.db.ProfileEntity
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ProfileField] is the one list of profile fields: a column added to the `profile` table (or a
 * property to [Profile]) without an entry fails here, and so does an entry that [toProfile] or
 * the field accessors don't carry through.
 */
class ProfileFieldTest {
    /** The names of the string properties of [type] (its backing fields). */
    private fun stringProperties(type: Class<*>) = type.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        .map { it.name }

    @Test
    fun keysAreUniqueAndEveryColumnHasAField() {
        val keys = ProfileField.entries.map { it.key }
        assertEquals(keys.toSet().size, keys.size, "duplicate keys: $keys")
        // created_at and updated_at are bookkeeping, not profile fields.
        val columns = stringProperties(ProfileEntity::class.java) - setOf("createdAt", "updatedAt")
        assertEquals(columns.sorted(), keys.sorted(), "ProfileEntity's text columns vs ProfileField")
        assertEquals(columns.sorted(), stringProperties(Profile::class.java).sorted(), "Profile's properties vs ProfileEntity's")
    }

    @Test
    fun theTextFieldsAreAllButTheChosenOnes() {
        assertEquals(
            ProfileField.entries.toSet() - setOf(ProfileField.DOMINANT_HAND, ProfileField.MOBILITY_LEVEL),
            PROFILE_TEXT_FIELDS.toSet(),
        )
    }

    @Test
    fun eachFieldIsReadAndWrittenOnItsOwn() {
        for (field in ProfileField.entries) {
            val written = ProfileEntity(name = "").with(field, "v-${field.key}").toProfile()
            for (other in ProfileField.entries) {
                val expected = if (other == field) "v-${field.key}" else if (other == ProfileField.FULL_NAME) "" else null
                assertEquals(expected, written[other], "${field.key} written, ${other.key} read")
            }
        }
    }
}
