package app.habitrpg.feature.character

import app.habitrpg.data.Names
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.habitrpg.domain.Catalog
import app.habitrpg.domain.Item
import app.habitrpg.domain.Slot

@StringRes
internal fun Slot.titleRes() = when (this) {
    Slot.WEAPON -> R.string.character_slot_weapon
    Slot.ARMOUR -> R.string.character_slot_armour
    Slot.HEADGEAR -> R.string.character_slot_headgear
    Slot.PET -> R.string.character_slot_pet
}

/** The item's name in the player's language, or nothing for an id this version does not know. */
@Composable
internal fun Item.displayName(): String = Names.item(id)?.let { stringResource(it) } ?: ""

/** A picture of an item: gear on the hero, pets on their own. Decorative; the name next to it is the label. */
@Composable
internal fun ItemThumb(item: Item, hairId: String?, bodyId: String?, size: Int = 64) {
    val modifier = Modifier.size(size.dp).clearAndSetSemantics {}
    if (item.slot == Slot.PET) {
        ItemSprites.drawableFor(item.id)?.let { PixelSprite(it, modifier) } ?: Box(modifier)
    } else {
        Avatar(modifier, AvatarLook.forHair(hairId, bodyId, mapOf(item.slot to item.id)))
    }
}

/**
 * What to wear, from the gear the player owns: one radio-style choice per slot, with "None" to take it off.
 * Slots with nothing owned are left out. Every option is far larger than 48 dp.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GearPicker(
    owned: Set<String>,
    equipped: Map<Slot, String>,
    hairId: String?,
    bodyId: String?,
    onEquip: (String) -> Unit,
    onUnequip: (Slot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = Slot.entries.filter { slot -> Catalog.forSlot(slot).any { it.id in owned } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (slots.isEmpty()) {
            Text(
                stringResource(R.string.character_gear_empty),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        slots.forEach { slot ->
            Text(stringResource(slot.titleRes()), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GearOption(stringResource(R.string.character_gear_none), selected = equipped[slot] == null, onClick = { onUnequip(slot) }) {
                    Box(Modifier.size(64.dp))
                }
                Catalog.forSlot(slot).filter { it.id in owned }.forEach { item ->
                    GearOption(item.displayName(), selected = equipped[slot] == item.id, onClick = { onEquip(item.id) }) {
                        ItemThumb(item, hairId, bodyId)
                    }
                }
            }
        }
    }
}

@Composable
private fun GearOption(label: String, selected: Boolean, onClick: () -> Unit, picture: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            picture()
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
