package com.kimjisub.launchpad.viewmodel

import kotlinx.coroutines.MainCoroutineDispatcher
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

/** A main looper stand-in: tasks run only in [runAll], on the thread that calls it. */
class FakeMainDispatcher : MainCoroutineDispatcher() {
	private val queue = ConcurrentLinkedQueue<Runnable>()
	@Volatile var mainThread: Thread = Thread.currentThread()
	var posted = 0
	var maxQueued = 0

	override val immediate: MainCoroutineDispatcher = object : MainCoroutineDispatcher() {
		override val immediate: MainCoroutineDispatcher get() = this
		override fun isDispatchNeeded(context: CoroutineContext) = Thread.currentThread() !== mainThread
		override fun dispatch(context: CoroutineContext, block: Runnable) = this@FakeMainDispatcher.dispatch(context, block)
	}

	@Synchronized
	override fun dispatch(context: CoroutineContext, block: Runnable) {
		posted++
		queue.add(block)
		maxQueued = maxOf(maxQueued, queue.size)
	}

	fun runAll(afterEach: () -> Unit = {}) {
		while (true) {
			(queue.poll() ?: return).run()
			afterEach()
		}
	}
}
