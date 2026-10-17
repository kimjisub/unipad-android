package com.kimjisub.launchpad.activity

import android.content.Context
import android.widget.Toast
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
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
		activity = spyk(PlayActivity())
		every { activity.applicationContext } returns appContext
		every { activity.finish() } just Runs
		PlayActivity::class.java.getDeclaredField("vm").apply { isAccessible = true }.set(activity, vm)
	}

	@After
	fun tearDown() {
		unmockkStatic(Toast::class)
		ArchTaskExecutor.getInstance().setDelegate(null)
	}

	@Test
	fun loadFailure_tellsWhyOverTheListAndCloses() {
		PlayActivity::class.java.getDeclaredMethod("closeOnLoadFailure", String::class.java)
			.apply { isAccessible = true }
			.invoke(activity, "Out of memory")

		verifyOrder {
			Toast.makeText(appContext, "Out of memory", Toast.LENGTH_LONG)
			toast.show()
			activity.finish()
		}
		verify { vm.unipackLoading = false }
	}
}
