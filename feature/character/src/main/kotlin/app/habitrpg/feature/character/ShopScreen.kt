package app.habitrpg.feature.character

import app.habitrpg.data.Names
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.habitrpg.domain.Catalog
import app.habitrpg.domain.Item
import app.habitrpg.domain.Slot

/** The shop: gear and pets for gold, grouped by slot. What you own can be worn or taken off right here. */
@Composable
fun ShopScreen(modifier: Modifier = Modifier, viewModel: ShopViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShopContent(state, viewModel::buy, viewModel::equip, viewModel::unequip, modifier)
}

@Composable
internal fun ShopContent(
    state: ShopState,
    onBuy: (String) -> Unit,
    onEquip: (String) -> Unit,
    onUnequip: (Slot) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                stringResource(if (state.bonusesOn) R.string.character_shop_note_on else R.string.character_shop_note_off),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slot.entries.forEach { slot ->
            item(key = "head-$slot") {
                Text(
                    stringResource(
                        when (slot) {
                            Slot.WEAPON -> R.string.character_shop_weapons
                            Slot.ARMOUR -> R.string.character_shop_armours
                            Slot.HEADGEAR -> R.string.character_shop_headgear
                            Slot.PET -> R.string.character_shop_pets
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
            items(Catalog.forSlot(slot), key = { it.id }) { item -> ShopRow(item, state, onBuy, onEquip, onUnequip) }
        }
    }
}

@Composable
private fun ShopRow(item: Item, state: ShopState, onBuy: (String) -> Unit, onEquip: (String) -> Unit, onUnequip: (Slot) -> Unit) {
    val owned = item.id in state.owned
    val worn = state.equipped[item.slot] == item.id
    val name = item.displayName()
    val buyLabel = stringResource(R.string.character_shop_buy_cd, name, item.price)
    val takeOffLabel = stringResource(R.string.character_shop_take_off_cd, name)
    val wearLabel = stringResource(R.string.character_shop_wear_cd, name)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ItemThumb(item, state.hair, state.body)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                val bonus = if (state.bonusesOn) Catalog.bonusOf(item)?.let(Names::bonus) else null
                if (bonus != null) Text(stringResource(bonus.res, bonus.value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (owned) {
                        Text(stringResource(if (worn) R.string.character_shop_worn else R.string.character_shop_owned), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        PixelSprite(R.drawable.sprite_icon_gold, Modifier.size(18.dp))
                        Text("${item.price}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clearAndSetSemantics {})
                    }
                }
            }
            val action = Modifier.heightIn(min = 48.dp)
            when {
                !owned -> Button(
                    onClick = { onBuy(item.id) }, enabled = state.gold >= item.price,
                    modifier = action.semantics { contentDescription = buyLabel },
                ) { Text(stringResource(R.string.character_shop_buy)) }
                worn -> OutlinedButton(
                    onClick = { onUnequip(item.slot) }, modifier = action.semantics { contentDescription = takeOffLabel },
                ) { Text(stringResource(R.string.character_shop_take_off)) }
                else -> Button(
                    onClick = { onEquip(item.id) }, modifier = action.semantics { contentDescription = wearLabel },
                ) { Text(stringResource(R.string.character_shop_wear)) }
            }
        }
    }
}
