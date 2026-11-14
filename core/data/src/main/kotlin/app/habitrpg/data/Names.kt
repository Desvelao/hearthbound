package app.habitrpg.data

import androidx.annotation.StringRes
import app.habitrpg.core.data.R
import app.habitrpg.domain.Bonuses
import app.habitrpg.domain.BonusKind
import app.habitrpg.domain.Catalog
import app.habitrpg.domain.Classes
import app.habitrpg.domain.ItemBonus
import app.habitrpg.domain.QuestCatalog
import app.habitrpg.domain.SkillEffect

/** A string resource plus the number to put in it, for text such as "+10% XP". */
data class Numbered(@StringRes val res: Int, val value: Int)

/**
 * The display names of everything the domain only knows by id (gear, pets, bosses, classes, skills). Written out rather
 * than looked up by name so the compiler and resource shrinker see every use; `NamesTest` checks every id is covered.
 * The words are in the `strings.xml` files of this module, one per language.
 */
object Names {
    private val items: Map<String, Int> = mapOf(
        "weapon_sword_bronze" to R.string.data_item_weapon_sword_bronze,
        "weapon_sword_steel" to R.string.data_item_weapon_sword_steel,
        "weapon_sword_gold" to R.string.data_item_weapon_sword_gold,
        "weapon_staff_bronze" to R.string.data_item_weapon_staff_bronze,
        "weapon_staff_steel" to R.string.data_item_weapon_staff_steel,
        "weapon_staff_gold" to R.string.data_item_weapon_staff_gold,
        "armour_vest_bronze" to R.string.data_item_armour_vest_bronze,
        "armour_vest_steel" to R.string.data_item_armour_vest_steel,
        "armour_vest_gold" to R.string.data_item_armour_vest_gold,
        "armour_plate_bronze" to R.string.data_item_armour_plate_bronze,
        "armour_plate_steel" to R.string.data_item_armour_plate_steel,
        "armour_plate_gold" to R.string.data_item_armour_plate_gold,
        "head_cap_bronze" to R.string.data_item_head_cap_bronze,
        "head_cap_steel" to R.string.data_item_head_cap_steel,
        "head_cap_gold" to R.string.data_item_head_cap_gold,
        "head_helm_bronze" to R.string.data_item_head_helm_bronze,
        "head_helm_steel" to R.string.data_item_head_helm_steel,
        "head_helm_gold" to R.string.data_item_head_helm_gold,
        "pet_fox_red" to R.string.data_item_pet_fox_red,
        "pet_fox_snow" to R.string.data_item_pet_fox_snow,
        "pet_owl_brown" to R.string.data_item_pet_owl_brown,
        "pet_owl_grey" to R.string.data_item_pet_owl_grey,
        "pet_slime_green" to R.string.data_item_pet_slime_green,
        "pet_slime_blue" to R.string.data_item_pet_slime_blue,
        "pet_cat_black" to R.string.data_item_pet_cat_black,
        "pet_cat_ginger" to R.string.data_item_pet_cat_ginger,
    )

    private val bosses: Map<String, Int> = mapOf(
        "boss_slime_king" to R.string.data_boss_slime_king,
        "boss_stone_golem" to R.string.data_boss_stone_golem,
        "boss_frost_wyrm" to R.string.data_boss_frost_wyrm,
    )

    private val classes: Map<String, Int> = mapOf(
        "warrior" to R.string.data_class_warrior,
        "mage" to R.string.data_class_mage,
        "healer" to R.string.data_class_healer,
    )

    private val skills: Map<String, Int> = mapOf(
        "strike" to R.string.data_skill_strike,
        "second_wind" to R.string.data_skill_second_wind,
        "insight" to R.string.data_skill_insight,
        "fortune" to R.string.data_skill_fortune,
        "mend" to R.string.data_skill_mend,
        "renew" to R.string.data_skill_renew,
    )

    /** Null for an id from a newer version or backup: the caller then shows something neutral. */
    @StringRes fun item(id: String?): Int? = id?.let(items::get)
    @StringRes fun boss(id: String?): Int? = id?.let(bosses::get)
    @StringRes fun heroClass(id: String?): Int? = id?.let(classes::get)
    @StringRes fun skill(id: String?): Int? = id?.let(skills::get)

    /** "+10% XP", "+15% gold" or "-10% damage" for a piece of gear. */
    fun bonus(b: ItemBonus) = Numbered(
        when (b.kind) {
            BonusKind.XP -> R.string.data_bonus_xp
            BonusKind.GOLD -> R.string.data_bonus_gold
            BonusKind.DAMAGE_REDUCTION -> R.string.data_bonus_damage
        },
        b.pct,
    )

    /** What a class gives for free, such as "+10% boss damage". Null when it gives nothing. */
    fun perk(b: Bonuses): Numbered? = when {
        b.bossDamagePct > 0 -> Numbered(R.string.data_perk_boss_damage, b.bossDamagePct)
        b.xpPct > 0 -> Numbered(R.string.data_perk_xp, b.xpPct)
        b.damageReductionPct > 0 -> Numbered(R.string.data_perk_damage_taken, b.damageReductionPct)
        else -> null
    }

    /** What a skill does, such as "Heal 15 health". */
    fun effect(e: SkillEffect) = when (e) {
        is SkillEffect.BossDamage -> Numbered(R.string.data_effect_boss_damage, e.amount)
        is SkillEffect.Heal -> Numbered(R.string.data_effect_heal, e.amount)
        SkillEffect.HealFull -> Numbered(R.string.data_effect_heal_full, 0)
        is SkillEffect.Xp -> Numbered(R.string.data_effect_xp, e.amount)
        is SkillEffect.Gold -> Numbered(R.string.data_effect_gold, e.amount)
    }

    /** Every id the domain knows, so tests can check each has a name. */
    val allItemIds get() = Catalog.items.map { it.id }
    val allBossIds get() = QuestCatalog.bosses.map { it.id }
    val allClassIds get() = Classes.all.map { it.id }
    val allSkillIds get() = Classes.all.flatMap { c -> c.skills.map { it.id } }
}
