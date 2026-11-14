package app.habitrpg.onboarding

import app.habitrpg.feature.settings.LanguageRow
import app.habitrpg.data.repo.AppLanguage
import app.habitrpg.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.habitrpg.data.repo.DEFAULT_BODY
import app.habitrpg.data.repo.DEFAULT_HAIR
import app.habitrpg.feature.character.Avatar
import app.habitrpg.feature.character.AvatarLook
import app.habitrpg.feature.character.BodyPicker
import app.habitrpg.feature.character.HairPicker
import kotlinx.coroutines.launch

private const val PAGES = 4

/** Four screens, skippable at any point. The third picks the optional features, the last starter tasks and a look. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val language by viewModel.language.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(Starters.all.map { it.id }) }
    var hair by rememberSaveable { mutableStateOf(DEFAULT_HAIR) }
    var body by rememberSaveable { mutableStateOf(DEFAULT_BODY) }
    var quests by rememberSaveable { mutableStateOf(true) }
    var classes by rememberSaveable { mutableStateOf(true) }
    var gearBonuses by rememberSaveable { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { viewModel.skip(onFinished) }) { Text(stringResource(R.string.onboarding_skip)) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (page) {
                    0 -> Welcome(hair, body, language, viewModel::setLanguage)
                    1 -> HowItWorks()
                    2 -> OptionalFeatures(quests, { quests = it }, classes, { classes = it }, gearBonuses, { gearBonuses = it })
                    else -> Setup(
                        selected = selected.toSet(),
                        onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
                        hair = hair,
                        onHair = { hair = it },
                        body = body,
                        onBody = { body = it },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // The dots are decoration; the text says the same thing for screen readers.
            Text(stringResource(R.string.onboarding_step, pager.currentPage + 1, PAGES), style = MaterialTheme.typography.labelLarge)
            if (pager.currentPage < PAGES - 1) {
                Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) { Text(stringResource(R.string.onboarding_next)) }
            } else {
                Button(onClick = { viewModel.finish(selected.toSet(), hair, body, quests, classes, gearBonuses, onFinished) }) { Text(stringResource(R.string.onboarding_start)) }
            }
        }
    }
}

@Composable
private fun Welcome(hair: String, body: String, language: AppLanguage, onLanguage: (AppLanguage) -> Unit) {
    Avatar(Modifier.size(160.dp).padding(top = 16.dp), AvatarLook.forHair(hair, body))
    Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() })
    Text(
        stringResource(R.string.onboarding_welcome_text),
        style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
    )
    // Changing the language here applies at once, so the next pages already speak it.
    Text(stringResource(R.string.onboarding_language), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    LanguageRow(language, onLanguage)
}

@Composable
private fun HowItWorks() {
    Text(stringResource(R.string.onboarding_how_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Explain(stringResource(R.string.app_tab_habits), stringResource(R.string.onboarding_how_habits))
    Explain(stringResource(R.string.app_tab_dailies), stringResource(R.string.onboarding_how_dailies))
    Explain(stringResource(R.string.app_tab_todos), stringResource(R.string.onboarding_how_todos))
    Explain(stringResource(R.string.app_nav_rewards), stringResource(R.string.onboarding_how_rewards))
    Text(
        stringResource(R.string.onboarding_how_warning),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Which of the optional game features to play with. Everything here can be changed later in Settings. */
@Composable
internal fun OptionalFeatures(
    quests: Boolean, onQuests: (Boolean) -> Unit,
    classes: Boolean, onClasses: (Boolean) -> Unit,
    gearBonuses: Boolean, onGearBonuses: (Boolean) -> Unit,
) {
    Text(stringResource(R.string.onboarding_features_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text(
        stringResource(R.string.onboarding_features_intro),
        style = MaterialTheme.typography.bodyMedium,
    )
    FeatureRow(
        stringResource(R.string.onboarding_feature_quests), stringResource(R.string.onboarding_feature_quests_text),
        quests, onQuests,
    )
    FeatureRow(
        stringResource(R.string.onboarding_feature_classes), stringResource(R.string.onboarding_feature_classes_text),
        classes, onClasses,
    )
    FeatureRow(
        stringResource(R.string.onboarding_feature_gear), stringResource(R.string.onboarding_feature_gear_text),
        gearBonuses, onGearBonuses,
    )
}

@Composable
private fun FeatureRow(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The row is the control; the switch only draws its state.
        Switch(checked, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun Explain(title: String, text: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Setup(selected: Set<String>, onToggle: (String) -> Unit, hair: String, onHair: (String) -> Unit, body: String, onBody: (String) -> Unit) {
    Text(stringResource(R.string.onboarding_setup_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.onboarding_setup_hint), style = MaterialTheme.typography.bodyMedium)
    Column(Modifier.fillMaxWidth()) {
        Starters.all.forEach { starter ->
            Row(
                Modifier.fillMaxWidth().toggleable(starter.id in selected, role = Role.Checkbox, onValueChange = { onToggle(starter.id) })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(starter.id in selected, onCheckedChange = null, modifier = Modifier.padding(12.dp).clearAndSetSemantics {})
                Column {
                    Text(stringResource(starter.titleRes), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(starter.kindRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Text(stringResource(R.string.onboarding_pick_look), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    BodyPicker(body, onBody, hairId = hair)
    HairPicker(hair, onHair, bodyId = body)
}
