package com.kimjisub.launchpad.activity

import android.content.Context
import com.kimjisub.launchpad.R
import android.widget.Toast
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A pack that ran out of memory or failed to read closed the play screen with nothing said: the
 * prepared "out of memory" / "error occurred" text was stored where no screen read it.
 */
class PlayActivityLoadFailureTest {
	private val appContext = mockk<Context>(relaxed = true)
	private val toast = mockk<Toast>(relaxed = true)
	private val vm = mockk<PlayActivityViewModel>(relaxed = true)
	private lateinit var activity: PlayActivity

	@Before
	fun setUp() {
		ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
			override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
			override fun postToMainThread(runnable: Runnable) = runnable.run()
			override fun isMainThread() = true
		})
		mockkStatic(Toast::class)
		every { Toast.makeText(any<Context>(), any<CharSequence>(), any()) } returns toast
		activity = PlayActivity()
		mockkObject(activity)
		every { activity.applicationContext } returns appContext
		every { activity.finish() } just Runs
		PlayActivity::class.java.getDeclaredField("vm").apply { isAccessible = true }.set(activity, vm)
	}

	@After
	fun tearDown() {
		unmockkObject(activity)
		unmockkStatic(Toast::class)
		ArchTaskExecutor.getInstance().setDelegate(null)
	}

	@Test
	fun loadFailure_tellsWhyOverTheListAndCloses() {
		PlayActivity::class.java.getDeclaredMethod("closeWithNotice", String::class.java)
			.apply { isAccessible = true }
			.invoke(activity, "Out of memory")

		verifyOrder {
			Toast.makeText(appContext, "Out of memory", Toast.LENGTH_LONG)
			toast.show()
			activity.finish()
		}
		verify { vm.unipackLoading = false }
	}
	@Test
	fun soundFailureMessageSurvivesClosingThePlayScreen() {
		every { activity.getString(R.string.pack_no_playable_sounds) } returns "No playable sounds"
		val callback = PlayActivity::class.java.getDeclaredField("uiCallback")
			.apply { isAccessible = true }.get(activity) as PlayActivityViewModel.UiCallback

		callback.finishWithNotice(R.string.pack_no_playable_sounds)

		verifyOrder {
			Toast.makeText(appContext, "No playable sounds", Toast.LENGTH_LONG)
			toast.show()
			activity.finish()
		}
	}

}
