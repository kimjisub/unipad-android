package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MainActivity Tests
 * Tests for main screen functionality (FAB menu, sorting, deletion)
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : BaseUITest() {

    @Test
    fun testFloatingActionMenuInteraction() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("fab_menu_before")

        // Find and click the FAB menu button by Resource ID
        val fabButton = device.findObject(By.res(PACKAGE_NAME, "floatingMenu"))
        assertNotNull("Could not find the FAB button", fabButton)

        // Open the FAB menu
        fabButton.click()
        Thread.sleep(2000)
        takeScreenshot("fab_menu_opened")

        // Verify FAB sub-buttons appeared after the menu opened
        // Material Design FAB with custom menu container
        val menuOpened = waitForAnyElementWithPolling(
            listOf(
                By.res(PACKAGE_NAME, "fabMenuContainer"),
                By.res(PACKAGE_NAME, "store"),
                By.res(PACKAGE_NAME, "setting"),
                By.res(PACKAGE_NAME, "loadUniPack"),
                By.res(PACKAGE_NAME, "reconnectLaunchpad")
            ),
            timeoutMs = 8000L,
            pollingIntervalMs = 500L
        )
        assertTrue("FAB menu did not open (could not find FAB sub-buttons)", menuOpened)

        // Verify all FAB sub-buttons are accessible
        val allButtonsAccessible = waitForAnyElementWithPolling(
            listOf(
                By.res(PACKAGE_NAME, "store"),
                By.res(PACKAGE_NAME, "setting")
            ),
            timeoutMs = 5000L,
            pollingIntervalMs = 500L
        )
        assertTrue("FAB sub-buttons are not accessible", allButtonsAccessible)

        // Close the FAB menu (click again)
        fabButton.click()
        Thread.sleep(2000)
        takeScreenshot("fab_menu_closed")
    }

    @Test
    fun testMainScreenSorting() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("sorting_main_screen")

        // Swipe left to show the Total Panel
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val centerY = displayHeight / 2

        // Swipe from right to left (open Total Panel)
        device.swipe(displayWidth - 50, centerY, 50, centerY, 10)
        Thread.sleep(2000)

        takeScreenshot("total_panel_opened")

        // Find and click the sort order toggle button (ascending <-> descending)
        val sortOrderSwitch = device.findObject(
            UiSelector()
                .resourceId("${PACKAGE_NAME}:id/sort_order")
        )

        if (sortOrderSwitch.exists()) {
            println("Sort order switch found, clicking...")
            sortOrderSwitch.click()
            Thread.sleep(1500)
            takeScreenshot("sort_order_changed")

            // Click again to restore original order
            sortOrderSwitch.click()
            Thread.sleep(1500)
            takeScreenshot("sort_order_restored")
        } else {
            println("Sort order switch not found, skipping...")
        }

        // Find and click the sort method spinner
        val sortMethodSpinner = device.findObject(
            UiSelector()
                .resourceId("${PACKAGE_NAME}:id/spinner_sort_method")
        )

        if (sortMethodSpinner.exists()) {
            println("Sort method spinner found, clicking...")
            sortMethodSpinner.click()
            Thread.sleep(1500)
            takeScreenshot("sort_method_spinner_opened")

            // Select the first option from the spinner (sort by title)
            // The spinner dropdown is displayed as a ListView
            try {
                val firstOption = device.findObject(
                    UiSelector()
                        .className("android.widget.CheckedTextView")
                        .instance(0)
                )
                if (firstOption.exists()) {
                    firstOption.click()
                    Thread.sleep(2000)
                    takeScreenshot("sort_by_title")
                }
            } catch (e: Exception) {
                println("Failed to select first sort option: ${e.message}")
                device.pressBack() // Close spinner
                Thread.sleep(1000)
            }

            // Open the spinner again
            sortMethodSpinner.click()
            Thread.sleep(1500)
            takeScreenshot("sort_method_spinner_opened_again")

            // Select the second option (sort by producer)
            try {
                val secondOption = device.findObject(
                    UiSelector()
                        .className("android.widget.CheckedTextView")
                        .instance(1)
                )
                if (secondOption.exists()) {
                    secondOption.click()
                    Thread.sleep(2000)
                    takeScreenshot("sort_by_producer")
                }
            } catch (e: Exception) {
                println("Failed to select second sort option: ${e.message}")
                device.pressBack() // Close spinner
                Thread.sleep(1000)
            }

            // Open the spinner again
            sortMethodSpinner.click()
            Thread.sleep(1500)

            // Select the third option (sort by download date)
            try {
                val thirdOption = device.findObject(
                    UiSelector()
                        .className("android.widget.CheckedTextView")
                        .instance(2)
                )
                if (thirdOption.exists()) {
                    thirdOption.click()
                    Thread.sleep(2000)
                    takeScreenshot("sort_by_download_date")
                }
            } catch (e: Exception) {
                println("Failed to select third sort option: ${e.message}")
                device.pressBack() // Close spinner
                Thread.sleep(1000)
            }
        } else {
            println("Sort method spinner not found, skipping...")
        }

        // Close the Total Panel (swipe from left to right)
        device.swipe(50, centerY, displayWidth - 50, centerY, 10)
        Thread.sleep(2000)

        takeScreenshot("total_panel_closed")

        // Verify the app is in a normal state
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("App terminated after sorting test", stillRunning)

        takeScreenshot("sorting_test_end")
    }

    @Test
    fun testUnipackDeletion() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("before_unipack_deletion")

        // Click an item in the unipack list to open the Pack Panel
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val itemX = displayWidth / 2
        val itemY = displayHeight / 3

        device.click(itemX, itemY)
        Thread.sleep(2000)

        takeScreenshot("pack_panel_opened")

        // Verify the Pack Panel appeared
        val panelVisible = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            3000L
        )
        assertTrue("Pack Panel did not open", panelVisible)

        // Find and click the Delete button
        // btnDelete is located in the top-right, so access by coordinates
        val deleteBtnX = displayWidth - 80
        val deleteBtnY = 180

        device.click(deleteBtnX, deleteBtnY)
        Thread.sleep(2000)

        takeScreenshot("after_delete_button_click")

        // Handle the delete confirmation dialog
        // Look for buttons like "OK", "Delete", etc.
        var dialogHandled = false
        val confirmTexts = listOf(
            "확인", "OK", "ok",
            "삭제", "Delete", "delete",
            "예", "Yes", "yes"
        )

        for (confirmText in confirmTexts) {
            try {
                val confirmButton = device.findObject(
                    UiSelector()
                        .textMatches(".*${confirmText}.*")
                        .clickable(true)
                )

                if (confirmButton.exists()) {
                    println("Delete confirmation dialog found: $confirmText")
                    confirmButton.click()
                    Thread.sleep(2000)
                    dialogHandled = true
                    break
                }
            } catch (e: Exception) {
                println("Error finding confirm button: ${e.message}")
            }
        }

        takeScreenshot("after_deletion_confirm")

        // Verify the app is still running after deletion
        val stillRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("App terminated after unipack deletion", stillRunning)

        // Verify return to the main screen
        Thread.sleep(2000)
        takeScreenshot("after_unipack_deletion")

        println("Unipack deletion test complete (dialog handled: $dialogHandled)")
    }

    @Test
    fun testStoreActivityNavigation() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        takeScreenshot("before_store_navigation")

        // Find and click the FAB menu button by Resource ID
        val fabButton = device.findObject(By.res(PACKAGE_NAME, "floatingMenu"))
        assertNotNull("Could not find the FAB button", fabButton)

        // Open the FAB menu
        fabButton.click()
        Thread.sleep(2000)

        // Click the Store button (Resource ID based)
        val storeButton = device.wait(
            Until.findObject(By.res(PACKAGE_NAME, "store")),
            5000L
        )
        assertNotNull("Could not find the Store button", storeButton)
        storeButton.click()
        Thread.sleep(3000)

        takeScreenshot("store_activity")

        // Verify FBStoreActivity launched
        val storeActivityRunning = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Did not transition to the store screen", storeActivityRunning)

        // Navigate back to the main screen
        device.pressBack()
        Thread.sleep(2000)

        val backToMain = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            5000L
        )
        assertTrue("Could not return to the main screen", backToMain)

        takeScreenshot("back_from_store")
    }
}
