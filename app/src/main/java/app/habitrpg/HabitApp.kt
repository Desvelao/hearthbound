package app.habitrpg

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import app.habitrpg.data.repo.withLanguage
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.AppLanguage
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.glance.appwidget.updateAll
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.TaskRepository
import app.habitrpg.domain.TaskType
import app.habitrpg.time.Notifications
import app.habitrpg.time.ReminderManager
import app.habitrpg.time.RolloverWorker
import app.habitrpg.widget.DailiesWidget
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@HiltAndroidApp
class HabitApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var reminders: ReminderManager
    @Inject lateinit var game: GameRepository
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var settings: SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        RolloverWorker.schedule(this)
        // Alarms are set from the database on every start, so a missed boot broadcast cannot lose reminders.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch { reminders.rescheduleAll() }
        // Ticks and rollovers made while the app is alive redraw the widget. The widget's own taps redraw it too, and
        // RolloverWorker covers a process that is not running.
        @OptIn(FlowPreview::class)
        // The notification channel's name and description, and the widget's words, follow the app language. This also
        // runs once at start with the saved language.
        scope.launch {
            settings.settings.map { it.language }.distinctUntilChanged().collect { tag ->
                Notifications.createChannels(this@HabitApp.withLanguage(AppLanguage.fromTag(tag)))
                runCatching { DailiesWidget().updateAll(this@HabitApp) }
            }
        }
        scope.launch {
            combine(game.observeCharacter(), tasks.observe(TaskType.DAILY)) { c, d -> c to d }
                .debounce(500)
                .collect { runCatching { DailiesWidget().updateAll(this@HabitApp) } }
        }
    }
}
