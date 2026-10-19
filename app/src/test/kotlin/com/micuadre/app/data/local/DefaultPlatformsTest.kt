package com.micuadre.app.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A fresh install gets InDriver and Didi; reopening the database never seeds them again. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultPlatformsTest {

    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun tearDown() {
        context.deleteDatabase(NAME)
    }

    private fun open() = Room.databaseBuilder(context, MiCuadreDatabase::class.java, NAME)
        .addCallback(DefaultPlatforms.callback)
        .allowMainThreadQueries()
        .build()

    @Test
    fun `a fresh database starts with InDriver and Didi visible, in that order`() = runBlocking {
        val db = open()

        val platforms = db.platformDao().observeActive().first()

        assertEquals(listOf("InDriver" to true, "Didi" to true), platforms.map { it.name to it.visible })
        db.close()
    }

    @Test
    fun `reopening an existing database does not seed the defaults again`() = runBlocking {
        open().apply {
            platformDao().observeActive().first()
            close()
        }

        val db = open()

        assertEquals(listOf("InDriver", "Didi"), db.platformDao().observeActive().first().map { it.name })
        db.close()
    }

    private companion object {
        const val NAME = "default-platforms-test"
    }
}
