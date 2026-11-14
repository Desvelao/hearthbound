package app.habitrpg.feature.character

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.habitrpg.domain.Character
import app.habitrpg.domain.Constants
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccessibilityTest {
    @get:Rule val compose = createComposeRule()
    private val context: android.content.Context = androidx.test.core.app.ApplicationProvider.getApplicationContext()

    private val hero = Character(level = 3, xp = 40, health = 31, gold = 17)

    @Test fun healthAndXpBarsAreReadAsNumbersNotJustColour() {
        compose.setContent { MaterialTheme { CharacterHeaderContent(hero, onClick = {}) } }
        compose.onNodeWithContentDescription("Health 31 of ${Constants.MAX_HEALTH}").assertExists()
        compose.onNodeWithContentDescription("XP 40 of ${Constants.xpToNextLevel(3)}").assertExists()
        compose.onNodeWithContentDescription("17 gold").assertExists()
        compose.onNodeWithText("Level 3").assertExists()
    }

    @Test fun theHeaderIsOneBigTapTargetThatOpensTheCharacter() {
        var opened = 0
        compose.setContent { MaterialTheme { CharacterHeaderContent(hero, onClick = { opened++ }) } }
        compose.onNodeWithText("Level 3").performClick()
        assertEquals(1, opened)
    }

    @Test fun theCharacterHasAnAccessibleName() {
        compose.setContent { MaterialTheme { CharacterHeaderContent(hero, onClick = {}) } }
        assertEquals(1, compose.onAllNodesWithContentDescription("Your character").fetchSemanticsNodes().size)
    }

    @Test fun everyHairOptionIsNamedSelectableAndBigEnoughToTap() {
        var chosen = ""
        compose.setContent { MaterialTheme { HairPicker("hair_long_brown", { chosen = it }) } }
        HairStyle.entries.forEach { style ->
            val option = compose.onNode(hasText(context.getString(style.labelRes)) and androidx.compose.ui.test.isSelectable())
            option.assertExists()
            option.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        compose.onNode(hasText("Long, brown") and androidx.compose.ui.test.isSelectable()).assertIsSelected()
        compose.onNodeWithText("Spiky, brown").performClick()
        assertEquals("hair_spiky_brown", chosen)
    }

    @Test fun everyBodyOptionIsNamedSelectableAndBigEnoughToTap() {
        var chosen = ""
        compose.setContent { MaterialTheme { BodyPicker("body_default", { chosen = it }) } }
        BodyType.entries.forEach { type ->
            val option = compose.onNode(hasText(context.getString(type.labelRes)) and androidx.compose.ui.test.isSelectable())
            option.assertExists()
            option.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        compose.onNode(hasText("Neutral") and androidx.compose.ui.test.isSelectable()).assertIsSelected()
        compose.onNodeWithText("Female").performClick()
        assertEquals("body_female", chosen)
    }

    @Test fun anUnknownBodyIdFallsBackToNeutral() {
        assertEquals(BodyType.NEUTRAL, BodyType.fromId("body_from_the_future"))
        assertEquals(BodyType.NEUTRAL, BodyType.fromId(null))
    }

    @Test fun bodyIdsMatchTheArtManifest() {
        val manifest = java.io.File("../../art/manifest.yaml").readText()
        BodyType.entries.forEach { assert(manifest.contains("id: ${it.id},")) { "${it.id} is not in art/manifest.yaml" } }
    }

    @Test fun anUnknownHairIdFallsBackToTheDefaultInsteadOfCrashing() {
        assertEquals(HairStyle.SHORT_BROWN, HairStyle.fromId("hair_from_the_future"))
        assertEquals(HairStyle.SHORT_BROWN, HairStyle.fromId(null))
    }

    @Test fun hairIdsMatchTheArtManifest() {
        // The ids in the app must equal the ids art/manifest.yaml renders; a typo would silently show the default.
        val manifest = java.io.File("../../art/manifest.yaml").readText()
        HairStyle.entries.forEach { assert(manifest.contains("id: ${it.id},")) { "${it.id} is not in art/manifest.yaml" } }
    }
}
