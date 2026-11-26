package com.kimjisub.launchpad.splitscreen

/**
 * The stack holding [task] in the output of `am stack list`. Each stack's header line comes before
 * its task lines; Android 9–11 head it `Stack id=`, Android 12 `RootTask id=`.
 */
internal fun stackOfTask(list: String, task: Int): Int {
	var stack: Int? = null
	list.lineSequence().map { it.trim() }.forEach { line ->
		STACK_HEADER.find(line)?.let { stack = it.groupValues[1].toInt() }
		if (line.startsWith("taskId=$task:")) return checkNotNull(stack) { "Task $task is listed before any stack:\n$list" }
	}
	throw AssertionError("Task $task is in no stack:\n$list")
}

private val STACK_HEADER = Regex("""^(?:Stack|RootTask) id=(\d+)""")
