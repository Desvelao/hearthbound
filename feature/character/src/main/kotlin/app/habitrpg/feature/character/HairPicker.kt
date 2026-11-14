package app.habitrpg.feature.character

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.unit.dp

/** A radio-style choice of hairstyle, each shown on the character. Every option is far larger than 48dp. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HairPicker(selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, bodyId: String? = null) {
    val selected = HairStyle.fromId(selectedId)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HairStyle.entries.forEach { style ->
            val isSelected = style == selected
            Surface(
                modifier = Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(style.id) }),
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // The label is the accessible name; the picture is decorative.
                    Avatar(Modifier.size(64.dp).clearAndSetSemantics {}, AvatarLook.forHair(style.id, bodyId))
                    Text(stringResource(style.labelRes), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
