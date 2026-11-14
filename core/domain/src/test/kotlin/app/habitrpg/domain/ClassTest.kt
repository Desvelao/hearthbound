package app.habitrpg.domain

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ClassTest {
    private val clock = clockAt()
    private fun lvl(level: Int, gold: Int = 0, cls: String? = null, mana: Int = 0) =
        stateOf(character = Character(level = level, gold = gold, classId = cls, mana = mana, lastRolloverDate = D0))
    private fun next(s: GameState, e: Event, c: java.time.Clock = clock) = Engine.reduce(s, e, c)

    @Test fun classNeedsLevelTen() {
        assertEquals(null, next(lvl(9), Event.ChooseClass("mage")).state.character.classId)
        val s = next(lvl(10), Event.ChooseClass("mage")).state.character
        assertEquals("mage", s.classId)
        assertEquals(Constants.MANA_BASE, s.mana)
    }

    @Test fun statPointsOnePerLevelAboveTen() {
        var s = next(lvl(12), Event.ChooseClass("warrior")).state
        repeat(3) { s = next(s, Event.SpendStat(Stat.MIGHT)).state }
        assertEquals(2, s.character.statMight)
    }

    @Test fun skillSpendsManaAndHeals() {
        val s = lvl(10, cls = "healer", mana = 30).let { it.copy(character = it.character.copy(health = 10)) }
        val r = next(s, Event.CastSkill("mend")).state.character
        assertEquals(25, r.health)
        assertEquals(20, r.mana)
        assertEquals(s.character, next(s.copy(character = s.character.copy(mana = 5)), Event.CastSkill("mend")).state.character.copy(mana = 30))
    }

    @Test fun strikeNeedsABossAndKeepsManaWithout() {
        val s = lvl(10, cls = "warrior", mana = 30)
        assertEquals(30, next(s, Event.CastSkill("strike")).state.character.mana)
        val boss = QuestCatalog.bosses.first()
        val withBoss = s.copy(quest = QuestCatalog.start(boss, D0))
        val r = next(withBoss, Event.CastSkill("strike")).state
        assertEquals(boss.hp - 25, r.quest?.hp)
        assertEquals(20, r.character.mana)
    }

    @Test fun respecCostsGold() {
        val s = lvl(11, gold = 150, cls = "mage")
        val r = next(s, Event.Respec).state.character
        assertEquals(null, r.classId)
        assertEquals(50, r.gold)
        assertEquals(s.character, next(lvl(11, gold = 10, cls = "mage"), Event.Respec).state.character.copy(gold = 150))
    }

    @Test fun manaRegensAtRolloverAndOnPause() {
        val s = lvl(10, cls = "mage", mana = 0).copy(paused = true)
        val r = next(s, Event.Rollover, clockAt(D0.plusDays(1))).state.character
        assertEquals(Constants.MANA_REGEN_PER_DAY, r.mana)
        val capped = next(s, Event.Rollover, clockAt(D0.plusDays(40))).state.character
        assertEquals(Constants.MANA_BASE, capped.mana)
    }

    @Test fun switchOffDisablesEverything() {
        val off = lvl(10).copy(features = Features(classes = false))
        assertEquals(null, next(off, Event.ChooseClass("mage")).state.character.classId)
        val s = lvl(10, cls = "healer", mana = 30).copy(features = Features(classes = false))
        assertEquals(30, next(s, Event.CastSkill("mend")).state.character.mana)
    }

    @Test fun classBonusesAddUp() {
        val b = Classes.bonusesFor(Character(level = 12, classId = "warrior", statMight = 2))
        assertEquals(12, b.bossDamagePct)
        assertTrue(Classes.bonusesFor(Character()) == Bonuses.NONE)
    }
}
