package app.habitrpg.data

import app.habitrpg.data.repo.BuyResult
import app.habitrpg.data.repo.Feedback
import app.habitrpg.data.repo.ShopRepository
import app.habitrpg.data.repo.StatsRepository
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Event
import app.habitrpg.domain.Slot
import app.habitrpg.domain.TaskType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShopRepositoryTest : RepoTestBase() {
    private lateinit var shop: ShopRepository

    @Before fun shopSetUp() { shop = ShopRepository(db, game, feedback, clock) }

    private suspend fun giveGold(amount: Int) {
        db.characterDao().upsert(app.habitrpg.data.db.CharacterEntity(1, 1, 0, 50, amount, null))
    }

    private suspend fun gold() = game.observeCharacter().first().gold

    @Test fun buyingPaysEquipsAndCannotBeDoneTwice() = runTest {
        giveGold(200)
        assertEquals(BuyResult.BOUGHT, shop.buy("weapon_sword_steel"))
        assertEquals(20, gold())
        assertEquals(mapOf(Slot.WEAPON to "weapon_sword_steel"), shop.observeEquipped().first())
        assertEquals(setOf("weapon_sword_steel"), shop.observeOwned().first())
        assertEquals(BuyResult.ALREADY_OWNED, shop.buy("weapon_sword_steel"))
        assertEquals("no second charge", 20, gold())
    }

    @Test fun shortGoldChangesNothing() = runTest {
        giveGold(59)
        assertEquals(BuyResult.NOT_ENOUGH_GOLD, shop.buy("head_cap_bronze"))
        assertEquals(59, gold())
        assertTrue(shop.observeOwned().first().isEmpty())
        assertTrue(shop.observeEquipped().first().isEmpty())
    }

    @Test fun anUnknownItemIsRefused() = runTest {
        giveGold(1000)
        assertEquals(BuyResult.UNKNOWN_ITEM, shop.buy("sword_of_cheating"))
        assertEquals(1000, gold())
    }

    @Test fun aFreshInstallWithNoCharacterRowCannotBuyAnything() = runTest {
        assertEquals(BuyResult.NOT_ENOUGH_GOLD, shop.buy("head_cap_bronze"))
    }

    @Test fun buyingASecondItemInASlotReplacesTheFirstAndEquipSwitchesBack() = runTest {
        giveGold(1000)
        shop.buy("armour_vest_bronze")
        shop.buy("armour_plate_bronze")
        assertEquals("armour_plate_bronze", shop.observeEquipped().first()[Slot.ARMOUR])
        assertTrue(shop.equip("armour_vest_bronze"))
        assertEquals("armour_vest_bronze", shop.observeEquipped().first()[Slot.ARMOUR])
        assertEquals(setOf("armour_vest_bronze", "armour_plate_bronze"), shop.observeOwned().first())
    }

    @Test fun youCannotWearWhatYouDoNotOwn() = runTest {
        giveGold(1000)
        assertFalse(shop.equip("head_helm_gold"))
        assertFalse(shop.equip("nonsense"))
        assertTrue(shop.observeEquipped().first().isEmpty())
    }

    @Test fun unequipEmptiesTheSlotButKeepsTheItem() = runTest {
        giveGold(300)
        shop.buy("pet_fox_red")
        shop.unequip(Slot.PET)
        assertTrue(shop.observeEquipped().first().isEmpty())
        assertEquals(setOf("pet_fox_red"), shop.observeOwned().first())
        assertTrue(shop.equip("pet_fox_red"))
    }

    @Test fun rowsTheCatalogDoesNotKnowAreIgnoredNotCrashed() = runTest {
        db.shopDao().setEquipped(app.habitrpg.data.db.EquippedEntity("WEAPON", "from_a_future_version"))
        db.shopDao().setEquipped(app.habitrpg.data.db.EquippedEntity("CAPE", "head_cap_gold"))
        db.shopDao().setEquipped(app.habitrpg.data.db.EquippedEntity("ARMOUR", "weapon_sword_gold")) // wrong slot
        assertTrue(shop.observeEquipped().first().isEmpty())
    }

    @Test fun purchasesAnnounceThemselvesAndCountAsGoldSpent() = runTest {
        giveGold(500)
        val seen = mutableListOf<Feedback>()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).let { scope ->
            scope.launch { feedback.events.collect { seen += it } }
        }
        shop.buy("head_helm_bronze")
        job.cancel()
        assertEquals(listOf(Feedback.Purchased(null, "head_helm_bronze", 60)), seen)
        assertEquals(60, StatsRepository(db, settings, clock).observeGoldSpent().first())
    }

    @Test fun equippedGearChangesWhatATickIsWorthAndTheSettingTurnsItOff() = runTest {
        giveGold(500)
        shop.buy("weapon_sword_gold") // +15% XP
        val id = tasks.save(TaskDraft(type = TaskType.TODO, title = "t", difficulty = Difficulty.HARD))
        game.dispatch(Event.CompleteTodo(id))
        assertEquals(23, db.eventDao().getAll().last().xp)

        settings.setGearBonuses(false)
        val other = tasks.save(TaskDraft(type = TaskType.TODO, title = "u", difficulty = Difficulty.HARD))
        game.dispatch(Event.CompleteTodo(other))
        assertEquals("cosmetic only: the MVP number", 20, db.eventDao().getAll().last().xp)
        assertEquals("the gear is still worn", "weapon_sword_gold", shop.observeEquipped().first()[Slot.WEAPON])
    }
}
