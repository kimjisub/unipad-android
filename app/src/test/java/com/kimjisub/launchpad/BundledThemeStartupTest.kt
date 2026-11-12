package com.kimjisub.launchpad

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import com.kimjisub.launchpad.tool.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BundledThemeStartupTest {
    @get:Rule val files = TemporaryFolder()
    private val app = spyk(BaseApplication())
    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val assets = mockk<AssetManager>()
    private val parent = SupervisorJob()
    private val scope = CoroutineScope(parent + Dispatchers.IO)
    private lateinit var legacy: File

    @Before fun setUp() {
        mockkObject(Log)
        every { Log.log(any()) } returns Unit
        every { Log.err(any(), any()) } returns Unit
        every { Log.err(any<String>()) } returns Unit
        every { app.getSharedPreferences("data", Context.MODE_PRIVATE) } returns prefs
        every { app.assets } returns assets
        every { assets.list("themes") } returns arrayOf("sskin")
        every { assets.list("themes/sskin") } returns arrayOf("theme.json")
        every { prefs.getString("SelectedTheme", null) } returns "zip://sskin"
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.commit() } returns true
        val external = files.newFolder("external")
        legacy = File(external, "themes/sskin").apply { mkdirs() }
        File(legacy, "theme.json").writeText("{}")
        every { app.getExternalFilesDir(null) } returns external
        BaseApplication::class.java.getDeclaredField("ioScope").apply {
            isAccessible = true
            set(app, scope)
        }
    }

    @After fun tearDown() {
        scope.cancel()
        unmockkAll()
    }

    private fun startThemes() {
        BaseApplication::class.java.getDeclaredMethod("setupBundledThemes").apply {
            isAccessible = true
            invoke(app)
        }
    }

    private fun awaitWork() = runBlocking {
        parent.children.toList().forEach { it.join() }
    }

    @Test fun startupReturnsWhilePreferencesAreStillLoading() {
        val reading = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        every { prefs.getString("SelectedTheme", null) } answers {
            reading.countDown()
            check(releaseRead.await(10, TimeUnit.SECONDS))
            "zip://sskin"
        }
        val caller = Executors.newSingleThreadExecutor()
        try {
            val returned = caller.submit { startThemes() }
            assertTrue("The fake preference read never started", reading.await(5, TimeUnit.SECONDS))
            try {
                returned.get(2, TimeUnit.SECONDS)
            } catch (_: TimeoutException) {
                fail("Startup waited for the blocked preference read")
            }
        } finally {
            releaseRead.countDown()
            caller.shutdown()
            assertTrue(caller.awaitTermination(5, TimeUnit.SECONDS))
            awaitWork()
        }
    }

    @Test fun failedCommitKeepsLegacyFiles() {
        every { editor.commit() } returns false
        startThemes()
        awaitWork()
        assertTrue("An unpersisted selection must keep its files", legacy.exists())
        verify(exactly = 1) { editor.commit() }
    }

    @Test fun successfulCommitPrecedesDeletion() {
        every { editor.commit() } answers {
            assertTrue("Files were deleted before the selection was persisted", legacy.exists())
            true
        }
        startThemes()
        awaitWork()
        verify { editor.putString("SelectedTheme", "asset://sskin"); editor.commit() }
        assertFalse(legacy.exists())
    }

    @Test fun preferenceReadFailureKeepsLegacyFiles() {
        every { prefs.getString("SelectedTheme", null) } throws IllegalStateException("unreadable")
        startThemes()
        awaitWork()
        assertTrue(legacy.exists())
        verify(exactly = 0) { prefs.edit() }
    }

    @Test fun externalSelectionIsNeverRewritten() {
        every { prefs.getString("SelectedTheme", null) } returns "zip://my-skin"
        startThemes()
        awaitWork()
        verify(exactly = 0) { prefs.edit() }
    }

    @Test fun selectionChangedDuringAssetListingIsNotOverwritten() {
        var selection = "zip://sskin"
        every { prefs.getString("SelectedTheme", null) } answers { selection }
        every { assets.list("themes") } answers {
            selection = "zip://my-skin"
            arrayOf("sskin")
        }
        startThemes()
        awaitWork()
        verify(exactly = 0) { prefs.edit() }
        assertEquals("zip://my-skin", selection)
    }
}
