package app.habitrpg

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.habitrpg.data.repo.AppLanguage
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LanguageProviderTest {
    @get:Rule val compose = createComposeRule()
    private val original = Locale.getDefault()

    @Before fun remember() { Locale.setDefault(Locale.US) }
    @After fun restore() { Locale.setDefault(original) }

    @Composable
    private fun Sample() {
        Column {
            Text(stringResource(R.string.app_nav_today))
            Text(stringResource(R.string.app_msg_boss_damage, 15))
            Text(LocalConfiguration.current.locales[0].language)
        }
    }

    @Test fun systemDefaultLeavesTheEnglishWordsAlone() {
        compose.setContent { ProvideAppLanguage(AppLanguage.SYSTEM, Locale.US) { Sample() } }
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("15 damage to the boss").assertExists()
        compose.onNodeWithText("en").assertExists()
    }

    @Test fun eachChosenLanguageChangesTheWordsAndTheLocale() {
        val expected = mapOf(
            AppLanguage.SPANISH to Triple("Hoy", "15 de daño al jefe", "es"),
            AppLanguage.GERMAN to Triple("Heute", "15 Schaden am Boss", "de"),
            AppLanguage.FRENCH to Triple("Aujourd'hui", "15 dégâts au boss", "fr"),
            AppLanguage.ITALIAN to Triple("Oggi", "15 danni al boss", "it"),
        )
        var language by mutableStateOf(AppLanguage.SYSTEM)
        compose.setContent { ProvideAppLanguage(language, Locale.US) { Sample() } }
        expected.forEach { (lang, words) ->
            language = lang
            compose.waitForIdle()
            compose.onNodeWithText(words.first).assertExists()
            compose.onNodeWithText(words.second).assertExists()
            compose.onNodeWithText(words.third).assertExists()
        }
    }

    @Test fun choosingAnotherLanguageSetsTheJvmDefaultAndSystemPutsItBack() {
        var language by mutableStateOf(AppLanguage.GERMAN)
        compose.setContent { ProvideAppLanguage(language, Locale.US) { Sample() } }
        compose.waitForIdle()
        assertEquals("de", Locale.getDefault().language)
        language = AppLanguage.SYSTEM
        compose.waitForIdle()
        assertEquals(Locale.US, Locale.getDefault())
    }

    @Test fun pluralsFollowTheLanguage() {
        var language by mutableStateOf(AppLanguage.ENGLISH)
        compose.setContent {
            ProvideAppLanguage(language, Locale.US) {
                Column {
                    Text(pluralStringResource(app.habitrpg.feature.character.R.plurals.character_n_days, 1, 1))
                    Text(pluralStringResource(app.habitrpg.feature.character.R.plurals.character_n_days, 5, 5))
                }
            }
        }
        compose.onNodeWithText("1 day").assertExists()
        compose.onNodeWithText("5 days").assertExists()
        language = AppLanguage.GERMAN
        compose.waitForIdle()
        compose.onNodeWithText("1 Tag").assertExists()
        compose.onNodeWithText("5 Tage").assertExists()
    }
}
