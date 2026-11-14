package app.habitrpg.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.habitrpg.data.repo.AppLanguage

/** The name to show for a language: its own name, or "System default" in the current language. */
@Composable
fun AppLanguage.displayName(): String = nativeName ?: stringResource(R.string.settings_language_system)

/**
 * A button showing the current language that opens the language dialog. Shared by Settings and onboarding, so both
 * look and behave the same. Choosing a language applies it at once.
 */
@Composable
fun LanguageRow(current: AppLanguage, onSelect: (AppLanguage) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val name = current.displayName()
    val description = stringResource(R.string.settings_language_change_cd, name)
    OutlinedButton(
        onClick = { open = true },
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
    ) { Text(name, Modifier.clearAndSetSemantics {}) }
    if (open) {
        LanguageDialog(current, onSelect = { onSelect(it); open = false }, onDismiss = { open = false })
    }
}

@Composable
internal fun LanguageDialog(current: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language_dialog_title)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = language == current, role = Role.RadioButton, onClick = { onSelect(language) })
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == current, onClick = null, modifier = Modifier.clearAndSetSemantics {})
                        Text(language.displayName(), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) } },
    )
}
