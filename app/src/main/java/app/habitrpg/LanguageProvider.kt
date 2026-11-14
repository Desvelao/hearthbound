package app.habitrpg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import app.habitrpg.data.repo.AppLanguage
import app.habitrpg.data.repo.withLanguage
import java.util.Locale

/**
 * Applies the app language to everything inside [content]: strings, plurals, and the locale that dates are formatted
 * with all follow it at once, with no restart. [AppLanguage.SYSTEM] leaves the phone's own language untouched.
 * [systemLocale] is what the phone itself says, so choosing System default again puts that back.
 */
@Composable
fun ProvideAppLanguage(language: AppLanguage, systemLocale: Locale, content: @Composable () -> Unit) {
    // Library code that formats with the JVM default locale (the date picker, for one) follows the choice too.
    SideEffect {
        Locale.setDefault(if (language == AppLanguage.SYSTEM) systemLocale else Locale.forLanguageTag(language.tag))
    }
    val base = LocalContext.current
    val localized = remember(base, language) { base.withLanguage(language) }
    val overrides = remember(localized) {
        listOf<ProvidedValue<*>>(
            LocalContext provides localized,
            LocalConfiguration provides localized.resources.configuration,
            LocalResources provides localized.resources,
        )
    }
    CompositionLocalProvider(*overrides.toTypedArray(), content = content)
}
