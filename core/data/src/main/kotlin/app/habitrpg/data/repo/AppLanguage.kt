package app.habitrpg.data.repo

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * The languages the app is translated into. [tag] is what is stored in settings and in backups; [nativeName] is shown in
 * the language picker and is never translated, so a player who cannot read the current language can still find theirs.
 * [SYSTEM] means "whatever the phone says" (including a per-app language chosen in Android's own settings).
 */
enum class AppLanguage(val tag: String, val nativeName: String?) {
    SYSTEM("system", null),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    GERMAN("de", "Deutsch"),
    FRENCH("fr", "Français"),
    ITALIAN("it", "Italiano");

    companion object {
        /** An unknown tag (a newer backup, a hand-edited file) falls back to the system language. */
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * This context with the app language applied, for code that is not inside Compose (reminder notifications, the widget,
 * the notification channel). [AppLanguage.SYSTEM] returns the context unchanged. Resources, plurals and date names read
 * from the result follow the chosen language.
 */
fun Context.withLanguage(language: AppLanguage): Context {
    if (language == AppLanguage.SYSTEM) return this
    val locale = Locale.forLanguageTag(language.tag)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    val localized = createConfigurationContext(config).resources
    // A wrapper, not the configuration context itself, so the activity can still be found by unwrapping it.
    return object : ContextWrapper(this) {
        override fun getResources(): Resources = localized
    }
}
