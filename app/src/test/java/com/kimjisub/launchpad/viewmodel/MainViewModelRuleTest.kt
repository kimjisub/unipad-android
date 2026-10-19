package com.kimjisub.launchpad.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MainViewModelRuleTest {

	private fun evaluate(rule: MainViewModelRule, body: () -> Unit) {
		rule.apply(object : Statement() {
			override fun evaluate() = body()
		}, Description.createTestDescription(javaClass, "view model fixture")).evaluate()
	}

	private class AsyncFailure : RuntimeException("uncaught background failure")

	@Test
	fun uncaughtBackgroundExceptionFailsAnOtherwiseSuccessfulTest() {
		val rule = MainViewModelRule()
		assertThrows(AsyncFailure::class.java) {
			evaluate(rule) {
				val vm = rule.track(object : ViewModel() {})
				rule.viewModelStore.put("panel", vm)
				val read = vm.viewModelScope.launch(Dispatchers.IO) { throw AsyncFailure() }
				// Joining a supervisor's child alone does not rethrow its exception.
				runBlocking { read.join() }
			}
		}
	}

	@Test
	fun uncaughtExceptionDuringCleanupAlsoFailsTheTest() {
		val rule = MainViewModelRule()
		val started = CountDownLatch(1)
		assertThrows(AsyncFailure::class.java) {
			evaluate(rule) {
				val vm = rule.track(object : ViewModel() {})
				rule.viewModelStore.put("panel", vm)
				vm.viewModelScope.launch(Dispatchers.IO) {
					try {
						started.countDown()
						awaitCancellation()
					} finally {
						throw AsyncFailure()
					}
				}
				assertTrue("background read started", started.await(5, TimeUnit.SECONDS))
			}
		}
	}

	@Test
	fun cancelledReadCanReturnToMainBeforeTheDispatcherIsReset() {
		checkCleanup(failBody = false)
	}

	@Test
	fun assertionFailureStillWaitsForViewModelCleanup() {
		checkCleanup(failBody = true)
	}

	private fun checkCleanup(failBody: Boolean) {
		val rule = MainViewModelRule()
		val vm = object : ViewModel() {}
		rule.viewModelStore.put("panel", vm)
		val started = CountDownLatch(1)
		var returnedToMain = false
		val body = {
			rule.track(vm)
			vm.viewModelScope.launch(Dispatchers.IO) {
				try {
					started.countDown()
					awaitCancellation()
				} finally {
					// A synchronous file read can finish after its owning scope is cancelled.
					withContext(NonCancellable) {
						withContext(Dispatchers.Main) { returnedToMain = true }
					}
				}
			}
			assertTrue("background read started", started.await(5, TimeUnit.SECONDS))
			if (failBody) throw AssertionError("fixture assertion")
		}
		if (failBody) {
			assertEquals("fixture assertion", assertThrows(AssertionError::class.java) { evaluate(rule, body) }.message)
		} else {
			evaluate(rule, body)
		}
		assertTrue("view model cleanup completed", vm.viewModelScope.coroutineContext.job.isCompleted)
		assertTrue("cancelled read reached Main before reset", returnedToMain)
	}
}
