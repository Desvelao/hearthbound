package app.habitrpg.widget

import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import app.habitrpg.data.repo.withLanguage
import app.habitrpg.R
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.habitrpg.MainActivity
import app.habitrpg.domain.Constants
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Glance widgets are not created by Hilt, so they reach the singletons through an entry point. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun actions(): WidgetActions
}

private fun actionsOf(context: Context): WidgetActions =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).actions()

internal val TaskIdKey = ActionParameters.Key<String>("taskId")

/** Today's dailies with a tick box each, and one line about the hero. Tapping the header opens the app. */
class DailiesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val actions = actionsOf(context)
        val first = actions.view()
        provideContent {
            // The widget is not drawn inside the app's own Compose tree, so it follows the language itself. It watches the
            // data too: Glance keeps a session alive for a while, and an update sent to it would not rebuild what was
            // captured here, so a language change would show only after the session ended.
            val view by actions.observe().collectAsState(first)
            val words = remember(view.language) { context.withLanguage(view.language) }
            GlanceTheme { DailiesContent(view.state, words) }
        }
    }
}

class DailiesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailiesWidget()
}

/** The tick box. Takes the daily on or off through [WidgetActions], then redraws every copy of the widget. */
class ToggleDailyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        actionsOf(context).toggle(id)
        DailiesWidget().updateAll(context)
    }
}

@Composable
private fun DailiesContent(state: WidgetState, words: Context) {
    val text = GlanceTheme.colors.onSurface
    val muted = GlanceTheme.colors.onSurfaceVariant
    Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(12.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.ready) {
                Text(words.getString(R.string.widget_level, state.level), style = TextStyle(color = text, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    words.getString(R.string.widget_stats, state.health, Constants.MAX_HEALTH, state.xp, state.xpToNext),
                    style = TextStyle(color = muted, fontSize = 12.sp),
                )
            } else {
                Text(words.getString(R.string.widget_open), style = TextStyle(color = text, fontSize = 14.sp))
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        when {
            !state.ready -> Unit
            state.dailies.isEmpty() -> Text(words.getString(R.string.widget_none), style = TextStyle(color = muted, fontSize = 14.sp))
            else -> {
                if (state.finished == state.dailies.size) {
                    Text(words.getString(R.string.widget_all_done), style = TextStyle(color = muted, fontSize = 12.sp))
                }
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(state.dailies, itemId = { it.id.hashCode().toLong() }) { daily ->
                        CheckBox(
                            checked = daily.done,
                            onCheckedChange = actionRunCallback<ToggleDailyAction>(actionParametersOf(TaskIdKey to daily.id)),
                            text = daily.title,
                            style = TextStyle(color = text, fontSize = 14.sp),
                            modifier = GlanceModifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
