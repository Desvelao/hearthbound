package app.habitrpg.domain

import java.time.LocalDate

/** A boss the player can fight. Ids must equal `art/manifest.yaml`; `QuestTest` checks it. Names live in string resources, keyed by id. */
data class Boss(val id: String, val hp: Int, val days: Int, val rewardXp: Int, val rewardGold: Int)

object QuestCatalog {
    val bosses: List<Boss> = listOf(
        Boss("boss_slime_king", hp = 150, days = 5, rewardXp = 60, rewardGold = 40),
        Boss("boss_stone_golem", hp = 400, days = 7, rewardXp = 150, rewardGold = 100),
        Boss("boss_frost_wyrm", hp = 800, days = 10, rewardXp = 320, rewardGold = 220),
    )

    private val byId = bosses.associateBy { it.id }
    fun byId(id: String?): Boss? = id?.let(byId::get)

    fun start(boss: Boss, today: LocalDate) =
        Quest(boss.id, boss.hp, today, today.plusDays((boss.days - 1).toLong()))

    /** Boss damage for one scoring action. Always at least 1. */
    fun damageFor(d: Difficulty, bonuses: Bonuses): Int =
        Math.round(Constants.BOSS_DAMAGE_BASE * d.multiplier * (1 + bonuses.bossDamagePct / 100.0)).toInt().coerceAtLeast(1)
}
