package app.habitrpg.feature.character

import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.habitrpg.domain.Slot
import kotlin.math.max

/** Draws [bitmap] scaled by a whole multiple, centred, with filtering off so the pixels stay sharp. */
private fun DrawScope.drawPixelArt(bitmap: ImageBitmap) {
    val scale = max(1, minOf((size.width / bitmap.width).toInt(), (size.height / bitmap.height).toInt()))
    val dst = IntSize(bitmap.width * scale, bitmap.height * scale)
    drawImage(
        bitmap,
        dstOffset = IntOffset(((size.width - dst.width) / 2).toInt(), ((size.height - dst.height) / 2).toInt()),
        dstSize = dst,
        filterQuality = FilterQuality.None,
    )
}

/** One pixel-art sprite. Pass a [description] when it carries meaning; decorative sprites stay silent. */
@Composable
fun PixelSprite(@DrawableRes id: Int, modifier: Modifier = Modifier, description: String? = null) {
    val bitmap = ImageBitmap.imageResource(id)
    val sem = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Canvas(modifier.then(sem)) { drawPixelArt(bitmap) }
}

/** The hair sprites, keyed by the ids in `art/manifest.yaml`. */
enum class HairStyle(val id: String, @StringRes val labelRes: Int, @DrawableRes val drawable: Int) {
    SHORT_BROWN("hair_short_brown", R.string.character_hair_short_brown, R.drawable.sprite_hair_short_brown),
    SHORT_BLACK("hair_short_black", R.string.character_hair_short_black, R.drawable.sprite_hair_short_black),
    SHORT_BLOND("hair_short_blond", R.string.character_hair_short_blond, R.drawable.sprite_hair_short_blond),
    LONG_BROWN("hair_long_brown", R.string.character_hair_long_brown, R.drawable.sprite_hair_long_brown),
    SPIKY_BROWN("hair_spiky_brown", R.string.character_hair_spiky_brown, R.drawable.sprite_hair_spiky_brown);

    companion object {
        /** An unknown id (from a newer backup, say) falls back to the default instead of crashing. */
        fun fromId(id: String?): HairStyle = entries.firstOrNull { it.id == id } ?: SHORT_BROWN
    }
}

/** The body sprites, keyed by the ids in `art/manifest.yaml`. */
enum class BodyType(val id: String, @StringRes val labelRes: Int, @DrawableRes val drawable: Int, @DrawableRes val outfit: Int) {
    MALE("body_male", R.string.character_body_male, R.drawable.sprite_body_male, R.drawable.sprite_outfit_tunic_male),
    FEMALE("body_female", R.string.character_body_female, R.drawable.sprite_body_female, R.drawable.sprite_outfit_tunic_female),
    NEUTRAL("body_default", R.string.character_body_neutral, R.drawable.sprite_body_default, R.drawable.sprite_outfit_tunic);

    companion object {
        /** An unknown id falls back to the neutral body instead of crashing. */
        fun fromId(id: String?): BodyType = entries.firstOrNull { it.id == id } ?: NEUTRAL
    }
}

/** The look of the character: one sprite per layer, drawn in a fixed order. Gear layers are empty until worn. */
data class AvatarLook(
    @DrawableRes val body: Int = R.drawable.sprite_body_default,
    @DrawableRes val outfit: Int = R.drawable.sprite_outfit_tunic,
    @DrawableRes val hair: Int = R.drawable.sprite_hair_short_brown,
    @DrawableRes val armour: Int? = null,
    @DrawableRes val headgear: Int? = null,
    @DrawableRes val weapon: Int? = null,
) {
    companion object {
        /** [equipped] maps a slot to the item worn there; ids the app does not know simply draw nothing. */
        fun forHair(id: String?, bodyId: String? = null, equipped: Map<Slot, String> = emptyMap()) =
            AvatarLook(
                body = BodyType.fromId(bodyId).drawable, outfit = BodyType.fromId(bodyId).outfit, hair = HairStyle.fromId(id).drawable,
                armour = ItemSprites.drawableFor(equipped[Slot.ARMOUR]),
                headgear = ItemSprites.drawableFor(equipped[Slot.HEADGEAR]),
                weapon = ItemSprites.drawableFor(equipped[Slot.WEAPON]),
            )
    }
}

/** Layers stack as body, outfit, armour, hair, headgear, weapon, all at the same scale. The pet stands beside the hero. */
@Composable
fun Avatar(modifier: Modifier = Modifier, look: AvatarLook = AvatarLook()) {
    val description = stringResource(R.string.character_avatar_cd)
    Box(modifier.semantics { contentDescription = description }) {
        listOfNotNull(look.body, look.outfit, look.armour, look.hair, look.headgear, look.weapon)
            .forEach { PixelSprite(it, Modifier.matchParentSize()) }
    }
}
