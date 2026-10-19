package com.kimjisub.launchpad.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Keeps Main installed until cancelled view models finish, and fails on uncaught coroutines. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelRule : TestRule, ViewModelStoreOwner {

	override val viewModelStore = ViewModelStore()
	private val jobs = mutableSetOf<Job>()

	fun <T : ViewModel> track(viewModel: T): T = viewModel.also {
		jobs += it.viewModelScope.coroutineContext.job
	}

	override fun apply(base: Statement, description: Description): Statement = object : Statement() {
		override fun evaluate() = runTest(UnconfinedTestDispatcher()) {
			Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
			try {
				base.evaluate()
			} finally {
				try {
					viewModelStore.clear()
					jobs.forEach { it.cancel() }
					jobs.toList().joinAll()
				} finally {
					Dispatchers.resetMain()
				}
			}
		}
	}
}
