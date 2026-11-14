package app.habitrpg.time

import app.habitrpg.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val REMINDERS_CHANNEL = "reminders"

    /**
     * Creates the channel, or updates its name and description. Pass a context with the app language applied
     * (`withLanguage`), because Android shows the channel text exactly as it is written here.
     */
    fun createChannels(context: Context) {
        val channel = NotificationChannel(REMINDERS_CHANNEL, context.getString(R.string.app_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.app_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
