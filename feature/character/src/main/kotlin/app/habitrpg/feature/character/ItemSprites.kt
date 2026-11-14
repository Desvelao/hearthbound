package app.habitrpg.feature.character

import androidx.annotation.DrawableRes

/**
 * The sprite for each shop item, keyed by the ids in `art/manifest.yaml` and `Catalog`. Written out rather than looked
 * up by name so the compiler and resource shrinker see every use; `ShopTest` checks the catalog is fully covered.
 */
object ItemSprites {
    private val sprites: Map<String, Int> = mapOf(
        "weapon_sword_bronze" to R.drawable.sprite_weapon_sword_bronze,
        "weapon_sword_steel" to R.drawable.sprite_weapon_sword_steel,
        "weapon_sword_gold" to R.drawable.sprite_weapon_sword_gold,
        "weapon_staff_bronze" to R.drawable.sprite_weapon_staff_bronze,
        "weapon_staff_steel" to R.drawable.sprite_weapon_staff_steel,
        "weapon_staff_gold" to R.drawable.sprite_weapon_staff_gold,
        "armour_vest_bronze" to R.drawable.sprite_armour_vest_bronze,
        "armour_vest_steel" to R.drawable.sprite_armour_vest_steel,
        "armour_vest_gold" to R.drawable.sprite_armour_vest_gold,
        "armour_plate_bronze" to R.drawable.sprite_armour_plate_bronze,
        "armour_plate_steel" to R.drawable.sprite_armour_plate_steel,
        "armour_plate_gold" to R.drawable.sprite_armour_plate_gold,
        "head_cap_bronze" to R.drawable.sprite_head_cap_bronze,
        "head_cap_steel" to R.drawable.sprite_head_cap_steel,
        "head_cap_gold" to R.drawable.sprite_head_cap_gold,
        "head_helm_bronze" to R.drawable.sprite_head_helm_bronze,
        "head_helm_steel" to R.drawable.sprite_head_helm_steel,
        "head_helm_gold" to R.drawable.sprite_head_helm_gold,
        "pet_fox_red" to R.drawable.sprite_pet_fox_red,
        "pet_fox_snow" to R.drawable.sprite_pet_fox_snow,
        "pet_owl_brown" to R.drawable.sprite_pet_owl_brown,
        "pet_owl_grey" to R.drawable.sprite_pet_owl_grey,
        "pet_slime_green" to R.drawable.sprite_pet_slime_green,
        "pet_slime_blue" to R.drawable.sprite_pet_slime_blue,
        "pet_cat_black" to R.drawable.sprite_pet_cat_black,
        "pet_cat_ginger" to R.drawable.sprite_pet_cat_ginger,
    )

    /** Null for an id from a newer version or backup: the caller then simply draws nothing. */
    @DrawableRes
    fun drawableFor(id: String?): Int? = id?.let(sprites::get)
}

/** Boss sprites, keyed by the ids in `art/manifest.yaml` and `QuestCatalog`; `QuestTest` in core checks the ids. */
object BossSprites {
    private val sprites: Map<String, Int> = mapOf(
        "boss_slime_king" to R.drawable.sprite_boss_slime_king,
        "boss_stone_golem" to R.drawable.sprite_boss_stone_golem,
        "boss_frost_wyrm" to R.drawable.sprite_boss_frost_wyrm,
    )

    @DrawableRes
    fun drawableFor(id: String?): Int? = id?.let(sprites::get)
}
