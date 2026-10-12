package com.kimjisub.launchpad.basefeatures

import android.hardware.input.InputManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the input tools themselves, separately from the release preflight's pad expectations. */
@RunWith(AndroidJUnit4::class)
class InputToolsCleanupTest : HeldPadTest() {
    @Test fun overlappingTouchscreensHaveDistinctIdsAndBothDisappearOnClose() {
        startHeld()
        val inputs = screen.context.getSystemService(InputManager::class.java)
        var firstId: Int? = null
        var secondId: Int? = null
        try {
            Touchscreen(screen).use { first ->
                firstId = first.deviceId
                Touchscreen(screen).use { second ->
                    secondId = second.deviceId
                    assertNotEquals("A new touchscreen resolved to the previous device", first.deviceId, second.deviceId)
                }
                screen.await("Second touchscreen remained after close") { inputs.getInputDevice(secondId!!) == null }
                assertNotNull("Closing the second touchscreen removed the first", inputs.getInputDevice(first.deviceId))
            }
        } finally {
            screen.await("Touchscreens remained after close") {
                listOfNotNull(firstId, secondId).all { inputs.getInputDevice(it) == null }
            }
        }
        assertNull(inputs.getInputDevice(firstId!!))
    }

    @Test fun failedDeviceLookupRemovesTheRegisteredTouchscreen() {
        startHeld()
        val inputs = screen.context.getSystemService(InputManager::class.java)
        val expected = IllegalStateException("Device lookup failed")
        var registeredName: String? = null
        val failure = runCatching {
            Touchscreen(screen) { manager, name ->
                registeredName = name
                screen.await("Touchscreen was not registered before the simulated failure") {
                    manager.inputDeviceIds.any { manager.getInputDevice(it)?.name == name }
                }
                throw expected
            }
        }.exceptionOrNull()
        assertSame("Registration failure was replaced by a cleanup failure", expected, failure)
        screen.await("Failed registration left its touchscreen alive") {
            inputs.inputDeviceIds.none { inputs.getInputDevice(it)?.name == registeredName }
        }
    }
}
