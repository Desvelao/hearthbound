package app.habitrpg.domain

/** Every tunable game number lives here, so balance changes touch one file. */
object Constants {
    const val BASE_XP = 10
    const val BASE_GOLD = 4
    const val STREAK_BONUS_PER_DAY = 0.02
    const val STREAK_BONUS_CAP = 1.4
    const val XP_BASE_TO_LEVEL = 100
    const val XP_PER_LEVEL_STEP = 25
    const val MAX_HEALTH = 50
    const val DAMAGE_BASE = 2
    const val MAX_DAMAGE_DAYS = 2
    const val DEFAULT_DAY_START_HOUR = 0

    // Task value (milestone 6). Habits and to-dos only: dailies already have the streak bonus as their reward curve.
    /** A plus tap moves a habit's value up by this much, a minus tap down by it. */
    const val VALUE_STEP = 1.0
    const val VALUE_MIN = -10.0
    const val VALUE_MAX = 10.0
    /** XP and gold are scaled by `1 - VALUE_REWARD_PER_POINT x value`, kept between the two limits below. */
    const val VALUE_REWARD_PER_POINT = 0.05
    const val VALUE_MULTIPLIER_MIN = 0.5
    const val VALUE_MULTIPLIER_MAX = 1.5
    /** A habit with no plus tap on a day that counts loses this much value, so neglected habits pay more. */
    const val HABIT_IDLE_VALUE_STEP = 1.0
    /** An open to-do loses this much value on every day that counts, so old to-dos become worth more. */
    const val TODO_AGE_VALUE_STEP = 0.25
    /** Past this distance from zero a row says "worth more" or "worth less". */
    const val WORTH_HINT_VALUE = 2.0

    // Equipment (milestone 6). Gear is bought in tiers 1..3; the bonuses only apply while the setting is on.
    val GEAR_PRICES = listOf(60, 180, 450)
    const val PET_PRICE = 250
    /** Weapon: extra XP on every score, by tier. */
    val WEAPON_XP_PCT = listOf(5, 10, 15)
    /** Headgear: extra gold on every score, by tier. */
    val HEADGEAR_GOLD_PCT = listOf(5, 10, 15)
    /** Armour: less health lost to missed dailies and minus taps (never below 1), by tier. */
    val ARMOUR_DAMAGE_REDUCTION_PCT = listOf(10, 20, 30)

    // Quests and classes (milestone 7).
    /** Boss damage per plus tap, daily tick or to-do, before difficulty and class bonuses. */
    const val BOSS_DAMAGE_BASE = 10
    const val CLASS_UNLOCK_LEVEL = 10
    const val RESPEC_COST = 100
    const val MANA_BASE = 20
    const val MANA_PER_LEVEL = 2
    const val MANA_PER_MIND = 2
    const val MANA_REGEN_PER_DAY = 10
    /** Percent per stat point. */
    const val MIGHT_BOSS_DAMAGE_PCT = 1
    const val MIND_XP_PCT = 1
    const val VIGOR_DAMAGE_REDUCTION_PCT = 1

    fun xpToNextLevel(level: Int): Int = XP_BASE_TO_LEVEL + XP_PER_LEVEL_STEP * (level - 1)
}

enum class Difficulty(val multiplier: Double) {
    TRIVIAL(0.5), EASY(1.0), MEDIUM(1.5), HARD(2.0)
}
