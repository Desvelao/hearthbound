package app.habitrpg.domain

enum class Stat { MIGHT, MIND, VIGOR }

sealed interface SkillEffect {
    data class BossDamage(val amount: Int) : SkillEffect
    data class Heal(val amount: Int) : SkillEffect
    data object HealFull : SkillEffect
    data class Xp(val amount: Int) : SkillEffect
    data class Gold(val amount: Int) : SkillEffect
}

/** A skill. The name and the description live in string resources, keyed by [id] and built from [effect]. */
data class Skill(val id: String, val cost: Int, val effect: SkillEffect)

/** A class. Its name lives in string resources, keyed by [id]; the perk text is built from [perk]. */
data class HeroClass(val id: String, val perk: Bonuses, val skills: List<Skill>)

object Classes {
    val all: List<HeroClass> = listOf(
        HeroClass(
            "warrior", Bonuses(bossDamagePct = 10),
            listOf(
                Skill("strike", 10, SkillEffect.BossDamage(25)),
                Skill("second_wind", 20, SkillEffect.Heal(15)),
            ),
        ),
        HeroClass(
            "mage", Bonuses(xpPct = 5),
            listOf(
                Skill("insight", 15, SkillEffect.Xp(30)),
                Skill("fortune", 15, SkillEffect.Gold(20)),
            ),
        ),
        HeroClass(
            "healer", Bonuses(damageReductionPct = 10),
            listOf(
                Skill("mend", 10, SkillEffect.Heal(15)),
                Skill("renew", 25, SkillEffect.HealFull),
            ),
        ),
    )

    fun byId(id: String?): HeroClass? = all.firstOrNull { it.id == id }

    fun skill(classId: String?, skillId: String): Skill? = byId(classId)?.skills?.firstOrNull { it.id == skillId }

    fun statOf(c: Character, stat: Stat) = when (stat) {
        Stat.MIGHT -> c.statMight
        Stat.MIND -> c.statMind
        Stat.VIGOR -> c.statVigor
    }

    fun spent(c: Character) = c.statMight + c.statMind + c.statVigor

    /** One point per level above the unlock level. Points already spent are never refunded when a level is lost. */
    fun freePoints(c: Character): Int = ((c.level - Constants.CLASS_UNLOCK_LEVEL).coerceAtLeast(0) - spent(c)).coerceAtLeast(0)

    fun manaMax(c: Character): Int =
        Constants.MANA_BASE + Constants.MANA_PER_LEVEL * (c.level - Constants.CLASS_UNLOCK_LEVEL).coerceAtLeast(0) +
            Constants.MANA_PER_MIND * c.statMind

    /** Class perk plus stat points. Zero before a class is chosen. */
    fun bonusesFor(c: Character): Bonuses {
        val cls = byId(c.classId) ?: return Bonuses.NONE
        return cls.perk + Bonuses(
            xpPct = c.statMind * Constants.MIND_XP_PCT,
            damageReductionPct = c.statVigor * Constants.VIGOR_DAMAGE_REDUCTION_PCT,
            bossDamagePct = c.statMight * Constants.MIGHT_BOSS_DAMAGE_PCT,
        )
    }
}
