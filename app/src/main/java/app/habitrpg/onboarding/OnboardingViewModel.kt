package app.habitrpg.onboarding

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.habitrpg.R
import app.habitrpg.data.repo.AppLanguage
import app.habitrpg.data.repo.DEFAULT_BODY
import app.habitrpg.data.repo.RewardRepository
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.data.repo.TaskRepository
import app.habitrpg.data.repo.withLanguage
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.TaskType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * A suggestion on the last onboarding page. Ids are stable so the selection survives rotation. The words are string
 * resources, and the task or reward is written in the player's language when it is created.
 */
data class Starter(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val kindRes: Int,
    val type: TaskType?,
    val difficulty: Difficulty = Difficulty.EASY,
    val minusEnabled: Boolean = true,
    val schedule: Schedule? = null,
    val rewardCost: Int = 0,
) {
    /** The task to create, titled [title], or null for a reward. */
    fun draft(title: String): TaskDraft? = type?.let {
        TaskDraft(type = it, title = title, difficulty = difficulty, minusEnabled = minusEnabled, schedule = schedule)
    }
}

object Starters {
    val all = listOf(
        Starter("water", R.string.starter_water, R.string.starter_kind_habit, TaskType.HABIT, Difficulty.TRIVIAL, minusEnabled = false),
        Starter("stretch", R.string.starter_stretch, R.string.starter_kind_daily, TaskType.DAILY, Difficulty.EASY, schedule = Schedule.Weekdays.EVERY_DAY),
        Starter("read", R.string.starter_read, R.string.starter_kind_daily, TaskType.DAILY, Difficulty.MEDIUM, schedule = Schedule.Weekdays.EVERY_DAY),
        Starter("plan", R.string.starter_plan, R.string.starter_kind_todo, TaskType.TODO, Difficulty.EASY),
        Starter("tidy", R.string.starter_tidy, R.string.starter_kind_todo, TaskType.TODO, Difficulty.TRIVIAL),
        Starter("episode", R.string.starter_episode, R.string.starter_kind_reward, null, rewardCost = 20),
    )
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tasks: TaskRepository,
    private val rewards: RewardRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** The language in use, for the picker on the first page. */
    val language: StateFlow<AppLanguage> =
        settings.settings.map { AppLanguage.fromTag(it.language) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLanguage.SYSTEM)

    /** The language picker on the first page. It applies at once, so the rest of onboarding is in that language. */
    fun setLanguage(language: AppLanguage) { viewModelScope.launch { settings.setLanguage(language) } }

    /**
     * Creates the chosen starters, saves the look and the optional-feature choices, and marks onboarding done, then
     * calls [onDone]. The feature switches default to on, the same as in Settings. Starter titles are written in the
     * language the player chose.
     */
    fun finish(
        selected: Set<String>,
        hair: String,
        body: String = DEFAULT_BODY,
        quests: Boolean = true,
        classes: Boolean = true,
        gearBonuses: Boolean = true,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val words = context.withLanguage(AppLanguage.fromTag(settings.current().language))
            Starters.all.filter { it.id in selected }.forEach { starter ->
                val title = words.getString(starter.titleRes)
                starter.draft(title)?.let { tasks.save(it) }
                if (starter.rewardCost > 0) rewards.save(null, title, starter.rewardCost)
            }
            settings.setHair(hair)
            settings.setBody(body)
            settings.setQuests(quests)
            settings.setClasses(classes)
            settings.setGearBonuses(gearBonuses)
            settings.setOnboardingDone(true)
            onDone()
        }
    }

    /** Skipping leaves the app empty and the look as it is, and never asks again. */
    fun skip(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            onDone()
        }
    }
}
