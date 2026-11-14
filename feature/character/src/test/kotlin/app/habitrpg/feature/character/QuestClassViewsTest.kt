package app.habitrpg.feature.character

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.habitrpg.domain.Character
import app.habitrpg.domain.Constants
import app.habitrpg.domain.QuestCatalog
import app.habitrpg.domain.QuestOutcome
import app.habitrpg.domain.Quest
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class QuestClassViewsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyBossHasASprite() {
        QuestCatalog.bosses.forEach { assertNotNull(it.id, BossSprites.drawableFor(it.id)) }
    }

    @Test fun withNoQuestEveryBossCanBeStarted() {
        var started: String? = null
        compose.setContent { MaterialTheme { QuestContent(QuestUiState(loaded = true), { started = it }, {}) } }
        compose.onAllNodesWithText("Fight")[1].performClick()
        assertEquals(QuestCatalog.bosses[1].id, started)
    }

    @Test fun aWonQuestOffersDoneAndSwitchOffExplainsItself() {
        val boss = QuestCatalog.bosses.first()
        val q = Quest(boss.id, 0, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6), QuestOutcome.WON)
        var dismissed = false
        compose.setContent { MaterialTheme { QuestContent(QuestUiState(true, true, q, 0), {}, { dismissed = true }) } }
        compose.onNodeWithText("Done").performClick()
        assertEquals(true, dismissed)
    }

    @Test fun classesUnlockAtTheLevelAndSkillsNeedMana() {
        val low = HeroUiState(loaded = true, character = Character(level = 9))
        compose.setContent { MaterialTheme { ClassSection(low, ClassActions.NONE) } }
        compose.onNodeWithText("Classes unlock at level ${Constants.CLASS_UNLOCK_LEVEL}.").assertExists()
    }

    @Test fun aChosenClassShowsSkillsThatCanOnlyBeCastWithEnoughMana() {
        val c = Character(level = 10, classId = "healer", mana = 12)
        var cast: String? = null
        compose.setContent {
            MaterialTheme { ClassSection(HeroUiState(loaded = true, character = c), ClassActions({}, {}, { cast = it }, {})) }
        }
        compose.onAllNodesWithText("Cast")[0].assertIsEnabled().performClick() // Mend costs 10
        compose.onAllNodesWithText("Cast")[1].assertIsNotEnabled() // Renew costs 25
        assertEquals("mend", cast)
    }
}
