package app.habitrpg.feature.character

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Shown while pause mode is on. It is a button: tapping it asks, then resumes the game. */
@Composable
fun PausedBanner(onResume: () -> Unit, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var asking by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier.fillMaxWidth(),
        color = if (dark) Color(0xFF1D3A5C) else Color(0xFFD6E8FF),
        contentColor = if (dark) Color(0xFFCFE4FF) else Color(0xFF0B2E57),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            Modifier.clickable(role = Role.Button) { asking = true }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.character_paused), style = MaterialTheme.typography.labelLarge)
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.character_resume_title)) },
            text = { Text(stringResource(R.string.character_resume_body)) },
            confirmButton = {
                TextButton(onClick = { asking = false; onResume() }) { Text(stringResource(R.string.character_resume_confirm)) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.character_resume_cancel)) } },
        )
    }
}
