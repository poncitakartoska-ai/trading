package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun samsungS25CompactViewport_rendersSimpleFourTabBottomBar() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_nav_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_LIVE_SIGNALS").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_PAPER_TRADING").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_ANALYTICS").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_SETTINGS").assertIsDisplayed()
    }
}
