package app.habitrpg.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.habitrpg.data.repo.withLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NamesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun everyItemBossClassAndSkillHasAName() {
        Names.allItemIds.forEach { assertNotNull("item $it", Names.item(it)) }
        Names.allBossIds.forEach { assertNotNull("boss $it", Names.boss(it)) }
        Names.allClassIds.forEach { assertNotNull("class $it", Names.heroClass(it)) }
        Names.allSkillIds.forEach { assertNotNull("skill $it", Names.skill(it)) }
    }

    @Test fun anUnknownIdHasNoName() {
        assertNull(Names.item("from_the_future")); assertNull(Names.boss(null))
    }

    @Test fun theNamesReadAsEnglishByDefault() {
        assertEquals("Bronze sword", context.getString(Names.item("weapon_sword_bronze")!!))
        assertEquals("Slime King", context.getString(Names.boss("boss_slime_king")!!))
        val bonus = Names.bonus(app.habitrpg.domain.Catalog.bonusOf(app.habitrpg.domain.Catalog.byId("weapon_sword_steel")!!)!!)
        assertEquals("+10% XP", context.getString(bonus.res, bonus.value))
    }

    @Test fun theNamesFollowTheChosenLanguage() {
        val es = context.withLanguage(app.habitrpg.data.repo.AppLanguage.SPANISH)
        val de = context.withLanguage(app.habitrpg.data.repo.AppLanguage.GERMAN)
        assertEquals("Espada de bronce", es.getString(Names.item("weapon_sword_bronze")!!))
        assertEquals("Schleimkönig", de.getString(Names.boss("boss_slime_king")!!))
        val bonus = Names.bonus(app.habitrpg.domain.Catalog.bonusOf(app.habitrpg.domain.Catalog.byId("head_cap_gold")!!)!!)
        assertEquals("+15 % de oro", es.getString(bonus.res, bonus.value))
    }
}
