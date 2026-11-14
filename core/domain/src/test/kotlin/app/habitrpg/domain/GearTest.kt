package app.habitrpg.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GearTest {
    private val clock = clockAt()

    private fun reduce(state: GameState, event: Event) = Engine.reduce(state, event, clock)

    @Test fun noGearMeansTheMvpNumbers() {
        val s = stateOf(habit("h", Difficulty.MEDIUM))
        val c = reduce(s, Event.HabitPlus("h")).changes.single()
        assertEquals(15, c.xp)
        assertEquals(6, c.gold)
    }

    @Test fun aWeaponAddsXpAndHeadgearAddsGoldOnEveryScore() {
        val gear = Catalog.bonusesFor(listOf("weapon_sword_gold", "head_cap_gold")) // +15% XP, +15% gold
        val s = stateOf(daily("d", Difficulty.HARD), habit("h", Difficulty.HARD)).copy(bonuses = gear)
        val tick = reduce(s, Event.CompleteDaily("d")).changes.single()
        assertEquals(23, tick.xp)   // 10 x 2.0 x 1.15
        assertEquals(9, tick.gold)  // 4 x 2.0 x 1.15 = 9.2
        assertEquals(23, reduce(s, Event.HabitPlus("h")).changes.single().xp)
    }

    @Test fun armourTrimsMinusTapsAndMissedDailiesButNeverBelowOne() {
        val armour = Catalog.bonusesFor(listOf("armour_plate_gold")) // -30% damage
        assertEquals(4, Engine.damageFor(Difficulty.HARD))
        assertEquals(3, Engine.damageFor(Difficulty.HARD, armour))        // 4 x 0.7 = 2.8
        assertEquals(1, Engine.damageFor(Difficulty.TRIVIAL, armour))     // floor
        assertEquals(2, Engine.damageFor(Difficulty.MEDIUM, armour))      // 3 x 0.7 = 2.1

        val s = stateOf(habit("h", Difficulty.HARD)).copy(bonuses = armour)
        assertEquals(-3, reduce(s, Event.HabitMinus("h")).changes.single().health)

        val missed = stateOf(daily("d", Difficulty.HARD), character = Character(lastRolloverDate = D0)).copy(bonuses = armour)
        val result = Rollover.run(missed, clockAt(D0.plusDays(1)))
        assertEquals(-3, result.changes.single().health)
    }

    @Test fun bonusesCountOneItemPerSlotAndIgnoreUnknownIdsAndPets() {
        assertEquals(Bonuses(10, 5, 20), Catalog.bonusesFor(listOf("weapon_staff_steel", "head_helm_bronze", "armour_vest_steel", "pet_fox_red")))
        assertEquals(Bonuses.NONE, Catalog.bonusesFor(listOf("pet_cat_black", "gear_from_the_future")))
        assertEquals(Bonuses.NONE, Catalog.bonusesFor(emptyList()))
    }

    @Test fun undoReversesGearBoostedScoresExactly() {
        val gear = Catalog.bonusesFor(listOf("weapon_sword_gold", "head_cap_gold"))
        val s = stateOf(todo("t", Difficulty.HARD)).copy(bonuses = gear)
        val scored = reduce(s, Event.CompleteTodo("t"))
        val back = reduce(scored.state, Event.Undo(scored.changes.single()))
        assertEquals(s.character, back.state.character)
    }

    @Test fun everyItemHasAUniqueIdAndASlotMatchingItsName() {
        assertEquals(Catalog.items.size, Catalog.items.map { it.id }.toSet().size)
        assertEquals(26, Catalog.items.size)
        assertEquals(listOf(60, 180, 450), Catalog.forSlot(Slot.ARMOUR).take(3).map { it.price })
        assertNotNull(Catalog.byId("pet_owl_grey"))
        assertNull(Catalog.byId("nope"))
        assertNull(Catalog.byId(null))
    }

    @Test fun idsAndPricesMatchTheArtManifest() {
        val manifest = File("../../art/manifest.yaml").readText()
        Catalog.items.forEach {
            val line = manifest.lines().firstOrNull { l -> l.contains("id: ${it.id},") }
            assertNotNull("${it.id} is not in art/manifest.yaml", line)
            assertTrue("${it.id} price differs from the manifest", line!!.contains("price: ${it.price}"))
        }
        // and nothing is sold in the manifest that the catalog does not know
        val shopLines = manifest.lines().filter { l -> listOf("layer: weapon", "layer: armour", "layer: headgear", "layer: pet").any { l.contains(it) } }
        assertEquals(Catalog.items.size, shopLines.size)
    }

    @Test fun bonusOfDescribesTheEffect() {
        assertEquals(ItemBonus(BonusKind.XP, 10), Catalog.bonusOf(Catalog.byId("weapon_sword_steel")!!))
        assertEquals(ItemBonus(BonusKind.GOLD, 15), Catalog.bonusOf(Catalog.byId("head_cap_gold")!!))
        assertEquals(ItemBonus(BonusKind.DAMAGE_REDUCTION, 10), Catalog.bonusOf(Catalog.byId("armour_vest_bronze")!!))
        assertNull(Catalog.bonusOf(Catalog.byId("pet_fox_red")!!))
    }
}
