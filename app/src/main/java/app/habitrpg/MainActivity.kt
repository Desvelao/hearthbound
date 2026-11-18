package app.habitrpg

import androidx.compose.runtime.SideEffect
import app.habitrpg.data.repo.withLanguage
import app.habitrpg.data.repo.AppLanguage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.remember
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.CompositionLocalProvider
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.Color
import app.habitrpg.data.repo.ThemeMode
import app.habitrpg.onboarding.OnboardingScreen
import app.habitrpg.ui.HabitTheme
import app.habitrpg.ui.resolveDark
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.habitrpg.feature.character.HistoryScreen
import app.habitrpg.feature.settings.SettingsScreen
import app.habitrpg.feature.tasks.BULK_ADDED_KEY
import app.habitrpg.feature.tasks.BulkAddScreen
import app.habitrpg.feature.tasks.TaskEditorScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val main: MainViewModel by viewModels()
    /** What the phone itself says, read from the activity's own configuration before we ever change anything. */
    private val systemLocale: java.util.Locale by lazy(LazyThreadSafetyMode.NONE) { resources.configuration.locales[0] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every time the app comes to the foreground: catch up on any day that ended while it was closed.
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { main.rollover() } }
        setContent {
            val settings by main.settings.collectAsStateWithLifecycle()
            val dark = resolveDark(settings?.themeMode ?: ThemeMode.SYSTEM)
            // Bars follow the app's own theme, not just the phone's, so the clock and icons stay readable.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            // The language setting is applied here, at the root, so everything below follows it at once.
            ProvideAppLanguage(AppLanguage.fromTag(settings?.language), systemLocale) {
                HabitTheme(dark) {
                    Surface(Modifier.fillMaxSize()) {
                        // Wait for the first settings read: it decides between onboarding and the game.
                        settings?.let { AppNav(startWithOnboarding = !it.onboardingDone) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNav(startWithOnboarding: Boolean) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = if (startWithOnboarding) "onboarding" else "home") {
        composable("onboarding") {
            OnboardingScreen(onFinished = { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } })
        }
        composable("home") {
            HomeScreen(
                onOpenSettings = { nav.navigate("settings") },
                onOpenHistory = { nav.navigate("history") },
                onAdd = { type -> nav.navigate("editor/${type.name}") },
                onEdit = { type, id -> nav.navigate("editor/${type.name}?taskId=$id") },
                onAddFromText = { type -> nav.navigate("bulk/${type.name}") },
            )
        }
        composable("history") { HistoryScreen(onBack = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(
            onBack = { nav.popBackStack() },
            onDataDeleted = { nav.navigate("onboarding") { popUpTo(0) { inclusive = true } } },
            versionName = BuildConfig.VERSION_NAME, aboutUrl = BuildConfig.ABOUT_URL) }
        composable(
            "editor/{type}?taskId={taskId}&title={title}&lineId={lineId}",
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("taskId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("lineId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val lineId = entry.arguments?.getString("lineId")
            TaskEditorScreen(
                onClose = { nav.popBackStack() },
                // Saved from "Add from text": tell that screen which line is now a real task.
                onSaved = {
                    if (lineId != null) nav.previousBackStackEntry?.savedStateHandle?.set(BULK_ADDED_KEY, lineId)
                    nav.popBackStack()
                },
            )
        }
        composable(
            "bulk/{type}",
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) { entry ->
            // The editor leaves its result in this entry's saved state; the screen removes that line.
            val savedLine by entry.savedStateHandle.getStateFlow<String?>(BULK_ADDED_KEY, null).collectAsStateWithLifecycle()
            BulkAddScreen(
                onClose = { nav.popBackStack() },
                savedLine = savedLine,
                onSavedLineHandled = { entry.savedStateHandle[BULK_ADDED_KEY] = null },
                onEditLine = { type, lineId, title ->
                    nav.navigate("editor/${type.name}?title=${Uri.encode(title)}&lineId=$lineId")
                },
            )
        }
    }
}
