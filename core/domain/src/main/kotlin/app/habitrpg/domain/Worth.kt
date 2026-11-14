package app.habitrpg.domain

/** A hint for the task lists: this task currently pays noticeably more or less than its base reward. */
enum class Worth {
    MORE, LESS;

    companion object {
        /** Null while the value is close to zero, so ordinary tasks carry no label. */
        fun of(value: Double): Worth? = when {
            value < -Constants.WORTH_HINT_VALUE -> MORE
            value > Constants.WORTH_HINT_VALUE -> LESS
            else -> null
        }
    }
}
