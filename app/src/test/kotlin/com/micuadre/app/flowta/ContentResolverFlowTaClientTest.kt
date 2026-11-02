package com.micuadre.app.flowta

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.BuildConfig
import com.micuadre.app.data.local.entity.PendingFlowTaIncomeEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real client against a fake provider registered under FlowTa's authority. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentResolverFlowTaClientTest {

    class FakeProvider : ContentProvider() {
        override fun onCreate() = true
        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            lastMethod = method
            lastExtras = extras
            return behaviour(method)
        }
        override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0

        companion object {
            var behaviour: (String) -> Bundle? = { null }
            var lastMethod: String? = null
            var lastExtras: Bundle? = null
        }
    }

    private val row = PendingFlowTaIncomeEntity(
        tripId = 9L, amountMinor = 5000L, paymentMethod = "CASH", endedAt = 100L, platformName = "Didi", createdAt = 1L
    )

    private lateinit var client: ContentResolverFlowTaClient

    @Before
    fun setUp() {
        FakeProvider.behaviour = { null }
        FakeProvider.lastMethod = null
        FakeProvider.lastExtras = null
        Robolectric.buildContentProvider(FakeProvider::class.java).create(BuildConfig.FLOWTA_AUTHORITY)
        client = ContentResolverFlowTaClient(ApplicationProvider.getApplicationContext<Application>())
    }

    @After
    fun tearDown() {
        FakeProvider.behaviour = { null }
    }

    private fun status(value: String) = Bundle().apply { putString("status", value) }

    @Test
    fun `recordIncome calls the provider with the contract extras and maps OK`() = runBlocking {
        FakeProvider.behaviour = { status("OK") }

        assertEquals(FlowTaSendResult.Delivered, client.recordIncome(row))
        assertEquals("recordIncome", FakeProvider.lastMethod)
        assertEquals("9", FakeProvider.lastExtras?.getString("external_id"))
    }

    @Test
    fun `a null reply is retried and link status is unavailable`() = runBlocking {
        FakeProvider.behaviour = { null }
        assertTrue(client.recordIncome(row) is FlowTaSendResult.Retry)
        assertEquals(FlowTaLinkStatus.UNAVAILABLE, client.linkStatus())
    }

    @Test
    fun `a provider that throws is retried and link status is unavailable`() = runBlocking {
        FakeProvider.behaviour = { throw SecurityException("key mismatch") }
        assertTrue(client.recordIncome(row) is FlowTaSendResult.Retry)
        assertEquals(FlowTaLinkStatus.UNAVAILABLE, client.linkStatus())
    }

    @Test
    fun `link status maps the provider reply`() = runBlocking {
        FakeProvider.behaviour = { status("NOT_LINKED") }
        assertEquals(FlowTaLinkStatus.NOT_LINKED, client.linkStatus())
    }
}
