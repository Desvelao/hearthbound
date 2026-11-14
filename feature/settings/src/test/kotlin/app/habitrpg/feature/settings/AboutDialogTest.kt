package app.habitrpg.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AboutDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsVersionDescriptionAndLink() {
        var opened = 0
        compose.setContent { MaterialTheme { AboutDialog("1.2.3", "https://my.site", onOpen = { opened++ }, onDismiss = {}) } }
        compose.onNodeWithText("Version 1.2.3").assertExists()
        compose.onNodeWithText("A private, offline", substring = true).assertExists()
        compose.onNodeWithText("https://my.site").performClick()
        assertEquals(1, opened)
    }

    @Test fun blankUrlHidesLink() {
        compose.setContent { MaterialTheme { AboutDialog("1.2.3", "", onOpen = {}, onDismiss = {}) } }
        compose.onNodeWithText("Version 1.2.3").assertExists()
        compose.onNodeWithText("https://", substring = true).assertDoesNotExist()
    }
}
