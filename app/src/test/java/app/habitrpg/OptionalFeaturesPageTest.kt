package app.habitrpg

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.habitrpg.onboarding.OptionalFeatures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class OptionalFeaturesPageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threeSwitchesStartOnAndTappingARowFlipsOnlyThatOne() {
        var quests by mutableStateOf(true)
        var classes by mutableStateOf(true)
        var gear by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                Column { OptionalFeatures(quests, { quests = it }, classes, { classes = it }, gear, { gear = it }) }
            }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(3)
        compose.onAllNodes(isToggleable())[0].assertIsOn()
        compose.onNodeWithText("Boss quests").performClick()
        assertEquals(listOf(false, true, true), listOf(quests, classes, gear))
        compose.onNodeWithText("Classes and skills").performClick()
        compose.onNodeWithText("Gear bonuses").performClick()
        assertEquals(listOf(false, false, false), listOf(quests, classes, gear))
    }
}
