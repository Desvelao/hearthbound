package app.habitrpg.domain

/** Where an item goes. Pets stand beside the hero; the rest are drawn on the avatar. */
enum class Slot { WEAPON, ARMOUR, HEADGEAR, PET }

/** One thing the shop sells. Ids and prices must equal `art/manifest.yaml`; `GearTest` checks it. The name lives in string resources, keyed by id. */
data class Item(val id: String, val slot: Slot, val tier: Int, val price: Int)

enum class BonusKind { XP, GOLD, DAMAGE_REDUCTION }

/** What a piece of gear adds, in whole percent. The shop turns it into words in the player's language. */
data class ItemBonus(val kind: BonusKind, val pct: Int)

/**
 * Passive bonuses from equipped gear, in whole percent. Zero everywhere means gear is cosmetic, which is also
 * what the player gets when the "gear bonuses" setting is off.
 */
data class Bonuses(
    val xpPct: Int = 0,
    val goldPct: Int = 0,
    val damageReductionPct: Int = 0,
    val bossDamagePct: Int = 0,
) {
    operator fun plus(o: Bonuses) = Bonuses(
        xpPct + o.xpPct, goldPct + o.goldPct, damageReductionPct + o.damageReductionPct, bossDamagePct + o.bossDamagePct,
    )
    companion object { val NONE = Bonuses() }
}

object Catalog {
    private val tierNames = listOf("bronze", "steel", "gold")

    private fun gear(slot: Slot, base: String): List<Item> =
        tierNames.mapIndexed { i, tier -> Item("${base}_$tier", slot, i + 1, Constants.GEAR_PRICES[i]) }

    private fun pets(kind: String, vararg colours: String): List<Item> =
        colours.map { Item("pet_${kind}_$it", Slot.PET, 1, Constants.PET_PRICE) }

    val items: List<Item> =
        gear(Slot.WEAPON, "weapon_sword") + gear(Slot.WEAPON, "weapon_staff") +
            gear(Slot.ARMOUR, "armour_vest") + gear(Slot.ARMOUR, "armour_plate") +
            gear(Slot.HEADGEAR, "head_cap") + gear(Slot.HEADGEAR, "head_helm") +
            pets("fox", "red", "snow") + pets("owl", "brown", "grey") +
            pets("slime", "green", "blue") + pets("cat", "black", "ginger")

    private val byId = items.associateBy { it.id }

    fun byId(id: String?): Item? = id?.let(byId::get)

    fun forSlot(slot: Slot): List<Item> = items.filter { it.slot == slot }

    /** The bonuses for the items currently worn. Unknown ids and pets give nothing; one item per slot counts. */
    fun bonusesFor(equippedIds: Collection<String>): Bonuses {
        val worn = equippedIds.mapNotNull(::byId)
        fun tier(slot: Slot) = worn.firstOrNull { it.slot == slot }?.tier ?: 0
        fun pct(table: List<Int>, tier: Int) = if (tier in 1..table.size) table[tier - 1] else 0
        return Bonuses(
            xpPct = pct(Constants.WEAPON_XP_PCT, tier(Slot.WEAPON)),
            goldPct = pct(Constants.HEADGEAR_GOLD_PCT, tier(Slot.HEADGEAR)),
            damageReductionPct = pct(Constants.ARMOUR_DAMAGE_REDUCTION_PCT, tier(Slot.ARMOUR)),
        )
    }

    /** What an item adds, or null for items with no bonus (pets). */
    fun bonusOf(item: Item): ItemBonus? = when (item.slot) {
        Slot.WEAPON -> ItemBonus(BonusKind.XP, Constants.WEAPON_XP_PCT[item.tier - 1])
        Slot.HEADGEAR -> ItemBonus(BonusKind.GOLD, Constants.HEADGEAR_GOLD_PCT[item.tier - 1])
        Slot.ARMOUR -> ItemBonus(BonusKind.DAMAGE_REDUCTION, Constants.ARMOUR_DAMAGE_REDUCTION_PCT[item.tier - 1])
        Slot.PET -> null
    }
}
