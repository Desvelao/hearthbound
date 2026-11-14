package app.habitrpg.time

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.glance.appwidget.updateAll
import androidx.work.WorkerParameters
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.domain.Event
import app.habitrpg.widget.DailiesWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * The scheduled half of day rollover. It calls the same engine entry point as opening the app, and rollover is
 * idempotent, so running often (and alongside an app open) is safe. WorkManager persists across reboots.
 */
@HiltWorker
class RolloverWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val game: GameRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        game.dispatch(Event.Rollover)
        // The widget lists today's dailies, so a new day must redraw it even when the app is not running.
        runCatching { DailiesWidget().updateAll(applicationContext) }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val NAME = "rollover"

        /** Hourly rather than at the day-start hour, so a changed setting or a phone that was off still catches up. */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<RolloverWorker>(1, TimeUnit.HOURS).build(),
            )
        }
    }
}
