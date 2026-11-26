package com.kimjisub.launchpad.splitscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StackListTest {
	@Test
	fun readsTheStackOfATaskOnAndroid9To11() {
		val list = """
			Stack id=29 bounds=[0,0][1080,2400] displayId=0 userId=0
			  taskId=43: com.example/.Pad bounds=[0,0][1080,2400] userId=0 visible=true topActivity=ComponentInfo{com.example/com.example.Pad}
			Stack id=28 bounds=[0,0][1080,2400] displayId=0 userId=0
			  taskId=42: com.example/.Play bounds=[0,0][1080,2400] userId=0 visible=false topActivity=ComponentInfo{com.example/com.example.Play}
			Stack id=0 bounds=[0,0][1080,2400] displayId=0 userId=0
			  taskId=5: com.example/.Launcher bounds=[0,0][1080,2400] userId=0 visible=false topActivity=ComponentInfo{com.example/com.example.Launcher}
		""".trimIndent()

		assertEquals(29, stackOfTask(list, 43))
		assertEquals(28, stackOfTask(list, 42))
		assertEquals(0, stackOfTask(list, 5))
	}

	@Test
	fun readsTheStackOfATaskOnAndroid12() {
		val list = """
			RootTask id=31 bounds=[0,0][1080,2400] displayId=0 userId=0
			  configuration={1.0 ?mcc?mnc [en_US] ldltr sw411dp w411dp h890dp 420dpi nrml long port finger -keyb/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 1080, 2400) mWindowingMode=fullscreen} s.7}
			  taskId=31: com.example/.Pad bounds=[0,0][1080,2400] userId=0 visible=true topActivity=ComponentInfo{com.example/com.example.Pad}
			RootTask id=30 bounds=[0,0][1080,2400] displayId=0 userId=0
			  taskId=30: com.example/.Play bounds=[0,0][1080,2400] userId=0 visible=false topActivity=ComponentInfo{com.example/com.example.Play}
		""".trimIndent()

		assertEquals(31, stackOfTask(list, 31))
		assertEquals(30, stackOfTask(list, 30))
	}

	@Test
	fun aTaskOutsideEveryStackIsAFailure() {
		val list = """
			  taskId=42: com.example/.Play bounds=[0,0][1080,2400] userId=0 visible=true
			Stack id=28 bounds=[0,0][1080,2400] displayId=0 userId=0
			  taskId=43: com.example/.Pad bounds=[0,0][1080,2400] userId=0 visible=true
		""".trimIndent()

		assertThrows(IllegalStateException::class.java) { stackOfTask(list, 42) }
		assertThrows(AssertionError::class.java) { stackOfTask(list, 7) }
		assertThrows(AssertionError::class.java) { stackOfTask(list, 4) }
	}
}
