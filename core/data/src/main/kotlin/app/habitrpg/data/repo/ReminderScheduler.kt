package app.habitrpg.data.repo

import app.habitrpg.data.db.ReminderEntity

/**
 * Turns stored reminders into OS alarms. The Android implementation lives in `:app`; storage code only
 * talks to this interface so it can be tested without an alarm service.
 */
interface ReminderScheduler {
    /** Schedules the next occurrence of [reminder], replacing any earlier alarm for it. */
    fun schedule(reminder: ReminderEntity)

    fun cancel(reminderId: String)
}

object NoopReminderScheduler : ReminderScheduler {
    override fun schedule(reminder: ReminderEntity) = Unit
    override fun cancel(reminderId: String) = Unit
}
