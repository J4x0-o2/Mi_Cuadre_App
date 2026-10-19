package com.micuadre.app.util

import com.micuadre.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parse reads major minor patch`() {
        assertEquals(AppVersion(0, 2, 0), AppVersion.parse("0.2.0"))
        assertEquals(AppVersion(12, 34, 56), AppVersion.parse("12.34.56"))
    }

    @Test
    fun `parse rejects malformed names`() {
        listOf("", "1", "1.2", "1.2.3.4", "v1.2.3", "1.2.x", "1.2.3-beta", " 1.2.3", "01.2.3", "-1.2.3")
            .forEach { assertNull("'$it' should be invalid", AppVersion.parse(it)) }
    }

    @Test
    fun `parse rejects minor or patch above 99`() {
        assertNull(AppVersion.parse("1.100.0"))
        assertNull(AppVersion.parse("1.0.100"))
        assertEquals(AppVersion(1, 99, 99), AppVersion.parse("1.99.99"))
    }

    @Test
    fun `code derives major times 10000 plus minor times 100 plus patch`() {
        assertEquals(200, AppVersion(0, 2, 0).code)
        assertEquals(100, AppVersion(0, 1, 0).code)
        assertEquals(10_203, AppVersion(1, 2, 3).code)
    }

    @Test
    fun `code increases with every semver bump`() {
        assertEquals(true, AppVersion(0, 2, 1).code > AppVersion(0, 2, 0).code)
        assertEquals(true, AppVersion(0, 3, 0).code > AppVersion(0, 2, 99).code)
        assertEquals(true, AppVersion(1, 0, 0).code > AppVersion(0, 99, 99).code)
    }

    @Test
    fun `label shows plain version for release`() {
        assertEquals("0.2.0", AppVersion.label("0.2.0", "release"))
    }

    @Test
    fun `label appends build type for non release builds`() {
        assertEquals("0.2.0 (debug)", AppVersion.label("0.2.0", "debug"))
    }

    @Test
    fun `build config version code matches the derivation from the version name`() {
        assertEquals(AppVersion.parse(BuildConfig.VERSION_NAME)?.code, BuildConfig.VERSION_CODE)
    }
}
