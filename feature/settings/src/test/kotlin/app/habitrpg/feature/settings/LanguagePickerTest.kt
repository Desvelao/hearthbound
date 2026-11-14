package app.habitrpg.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.habitrpg.data.repo.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class LanguagePickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theButtonShowsTheCurrentLanguageInItsOwnName() {
        compose.setContent { MaterialTheme { LanguageRow(AppLanguage.GERMAN, {}) } }
        compose.onNodeWithContentDescription("Language: Deutsch. Tap to change.").assertExists()
    }

    @Test fun systemDefaultIsSpelledOutInTheCurrentLanguage() {
        compose.setContent { MaterialTheme { LanguageRow(AppLanguage.SYSTEM, {}) } }
        compose.onNodeWithContentDescription("Language: System default. Tap to change.").assertExists()
    }

    @Test fun theDialogListsEveryLanguageAndMarksTheCurrentOne() {
        compose.setContent { MaterialTheme { LanguageRow(AppLanguage.FRENCH, {}) } }
        compose.onNodeWithContentDescription("Language: Français. Tap to change.").performClick()
        listOf("System default", "English", "Español", "Deutsch", "Italiano").forEach {
            compose.onNode(hasText(it) and isSelectable()).assertExists()
        }
        compose.onNode(hasText("Français") and isSelectable()).assertIsSelected()
    }

    @Test fun choosingALanguageReportsItAndClosesTheDialog() {
        var chosen: AppLanguage? = null
        compose.setContent { MaterialTheme { LanguageRow(AppLanguage.SYSTEM, { chosen = it }) } }
        compose.onNodeWithContentDescription("Language: System default. Tap to change.").performClick()
        compose.onNode(hasText("Español") and isSelectable()).performClick()
        assertEquals(AppLanguage.SPANISH, chosen)
        compose.onNodeWithText("Choose a language").assertDoesNotExist()
    }
}
