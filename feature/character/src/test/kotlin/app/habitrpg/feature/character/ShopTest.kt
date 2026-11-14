package app.habitrpg.feature.character

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.habitrpg.domain.Catalog
import app.habitrpg.domain.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class ShopTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: ShopState, onBuy: (String) -> Unit = {}, onEquip: (String) -> Unit = {}, onUnequip: (Slot) -> Unit = {}) =
        compose.setContent { MaterialTheme { ShopContent(state, onBuy, onEquip, onUnequip) } }

    @Test fun everyCatalogItemHasASpriteAndAnUnknownIdHasNone() {
        Catalog.items.forEach { assertNotNull("${it.id} has no sprite", ItemSprites.drawableFor(it.id)) }
        assertNull(ItemSprites.drawableFor("from_the_future"))
        assertNull(ItemSprites.drawableFor(null))
    }

    @Test fun anUnknownEquippedIdDrawsNoLayerInsteadOfCrashing() {
        val look = AvatarLook.forHair(null, null, mapOf(Slot.WEAPON to "from_the_future", Slot.ARMOUR to "armour_vest_gold"))
        assertNull(look.weapon)
        assertNotNull(look.armour)
        assertNull(look.headgear)
    }

    @Test fun gearLayersAreDrawnInTheDocumentedOrder() {
        val look = AvatarLook.forHair("hair_long_brown", "body_male", Slot.entries.associateWith { Catalog.forSlot(it).first().id })
        assertNotNull(look.armour); assertNotNull(look.headgear); assertNotNull(look.weapon)
    }

    @Test fun buyIsDisabledWhenGoldIsShortAndEnabledWhenItIsNot() {
        show(ShopState(loaded = true, gold = 100))
        compose.onNodeWithContentDescription("Buy Bronze sword for 60 gold").performScrollTo().assertIsEnabled()
        compose.onNodeWithContentDescription("Buy Steel sword for 180 gold").performScrollTo().assertIsNotEnabled()
    }

    @Test fun tappingBuyReportsTheItemId() {
        var bought = ""
        show(ShopState(loaded = true, gold = 1000), onBuy = { bought = it })
        compose.onNodeWithContentDescription("Buy Gold sword for 450 gold").performScrollTo().performClick()
        assertEquals("weapon_sword_gold", bought)
    }

    @Test fun everyActionIsAtLeast48dpTall() {
        show(ShopState(loaded = true, gold = 1000, owned = setOf("weapon_sword_bronze"), equipped = mapOf(Slot.WEAPON to "weapon_sword_bronze")))
        compose.onNodeWithContentDescription("Buy Steel sword for 180 gold").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Take off Bronze sword").performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    @Test fun ownedItemsOfferWearOrTakeOffNotBuy() {
        var worn = ""
        var removed: Slot? = null
        show(
            ShopState(loaded = true, gold = 1000, owned = setOf("armour_vest_bronze", "armour_plate_bronze"), equipped = mapOf(Slot.ARMOUR to "armour_plate_bronze")),
            onEquip = { worn = it }, onUnequip = { removed = it },
        )
        compose.onNodeWithContentDescription("Wear Bronze vest").performScrollTo().performClick()
        assertEquals("armour_vest_bronze", worn)
        compose.onNodeWithContentDescription("Take off Bronze plate armour").performScrollTo().performClick()
        assertEquals(Slot.ARMOUR, removed)
        compose.onNodeWithText("Worn", useUnmergedTree = true).assertExists()
    }

    @Test fun bonusTextShowsOnlyWhileBonusesAreOn() {
        show(ShopState(loaded = true, gold = 0, bonusesOn = true))
        compose.onAllNodesWithText("+5% XP")[0].assertExists()
        compose.onNodeWithText("Gear gives small bonuses while you wear it. Pets are just company.").assertExists()
    }

    @Test fun withBonusesOffTheShopSaysGearIsOnlyForShow() {
        show(ShopState(loaded = true, gold = 0, bonusesOn = false))
        compose.onNodeWithText("Gear bonuses are off in Settings, so gear is only for show.").assertExists()
        assertEquals(0, compose.onAllNodesWithText("+5% XP").fetchSemanticsNodes().size)
    }

    // ---- the picker on the Hero page ----

    @Test fun theGearPickerListsOnlySlotsWithSomethingOwnedAndOffersNone() {
        var worn = ""
        var removed: Slot? = null
        compose.setContent {
            MaterialTheme {
                GearPicker(setOf("weapon_staff_steel", "pet_cat_black"), mapOf(Slot.WEAPON to "weapon_staff_steel"), null, null, { worn = it }, { removed = it })
            }
        }
        compose.onNodeWithText("Weapon").assertExists()
        compose.onNodeWithText("Pet").assertExists()
        assertEquals(0, compose.onAllNodesWithText("Armour").fetchSemanticsNodes().size)
        compose.onNode(hasText("Steel staff") and isSelectable()).assertIsSelected().assertHeightIsAtLeast(48.dp)
        compose.onNode(hasText("Black cat") and isSelectable()).performClick()
        assertEquals("pet_cat_black", worn)
        compose.onAllNodes(hasText("None") and isSelectable())[0].performClick()
        assertEquals(Slot.WEAPON, removed)
    }

    @Test fun theGearPickerPointsToTheShopWhenNothingIsOwned() {
        compose.setContent { MaterialTheme { GearPicker(emptySet(), emptyMap(), null, null, {}, {}) } }
        compose.onNodeWithText("Gear and pets you buy in the Shop (Rewards tab) can be worn here.").assertExists()
    }
}
