package com.beoffline.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beoffline.app.background.BackgroundProtectionManager
import com.beoffline.app.data.model.AppInfo
import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.data.repository.OpenBlockRuleRepository
import com.beoffline.app.scheduler.OpenBlockScheduler
import com.beoffline.app.scheduler.RuleScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The three answers to "when?", in the user's words. */
enum class WhenKind(val label: String) {
    Always("Always"),
    Hours("Set hours"),
    Timer("For a while");

    val ruleType: RuleType
        get() = when (this) {
            Always -> RuleType.PERMANENT
            Hours -> RuleType.SCHEDULED
            Timer -> RuleType.TIMER
        }
}

data class BlockCreatorUiState(
    val id: Int? = null,
    val strictness: Strictness = Strictness.Locked,
    val selectedPackages: List<String> = emptyList(),
    val selectedAppInfos: List<AppInfo> = emptyList(),
    val whenKind: WhenKind = WhenKind.Always,
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 17,
    val endMinute: Int = 0,
    val activeDays: List<Int> = listOf(1, 2, 3, 4, 5),
    val timerMinutes: Int = 60,
    val isActive: Boolean = true,
    val timerStartedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** True once the system switch behind "will not open" is actually on. */
    val accessibilityOn: Boolean = false,
    val isEditing: Boolean = false,
    val isLoading: Boolean = false
) {
    val isLock: Boolean get() = strictness == Strictness.Locked

    /**
     * A window that ends where it starts is the only genuinely impossible one.
     * An end BEFORE the start is an overnight window, which both engines
     * already evaluate correctly — the old creator rejected it, which is why
     * "lock everything after 10pm" could not be expressed.
     */
    val scheduleError: String?
        get() = when {
            whenKind != WhenKind.Hours -> null
            startHour == endHour && startMinute == endMinute -> "Pick an end time that differs from the start."
            activeDays.isEmpty() -> "Pick at least one day."
            else -> null
        }

    val isValid: Boolean
        get() = selectedPackages.isNotEmpty() && scheduleError == null

    /** The tail of the summary sentence, in the same words the list will use. */
    val whenPhrase: String
        get() = when (whenKind) {
            WhenKind.Always -> "until you turn it off"
            WhenKind.Hours ->
                "${AppStateCopy.days(activeDays)}, " +
                    "${AppStateCopy.clock(startHour, startMinute)} to " +
                    AppStateCopy.clock(endHour, endMinute)
            WhenKind.Timer -> "for the next ${spokenTimer(timerMinutes)}"
        }

    val summary: String
        get() = AppStateCopy.summarise(
            appNames = selectedAppInfos.map { it.appName },
            strictness = strictness,
            whenPhrase = whenPhrase
        )

    /** Shown only when the chosen strictness needs a switch that is still off. */
    val needsAccessibility: Boolean get() = isLock && !accessibilityOn
}

internal fun spokenTimer(minutes: Int): String = when {
    minutes < 60 -> "$minutes minutes"
    minutes % 60 == 0 -> "${minutes / 60} ${if (minutes == 60) "hour" else "hours"}"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

/**
 * The one creator, for both engines.
 *
 * There used to be two of these — RuleCreator (723 lines) and
 * OpenBlockRuleCreator (482) — identical but for their nouns, which forced the
 * user to pick an engine before they could pick an app. Here strictness is the
 * second of three questions, and which table the row lands in is this class's
 * problem, not the user's.
 */
@HiltViewModel
class BlockCreatorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockRuleRepository: BlockRuleRepository,
    private val openBlockRuleRepository: OpenBlockRuleRepository,
    private val backgroundProtectionManager: BackgroundProtectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockCreatorUiState())
    val uiState: StateFlow<BlockCreatorUiState> = _uiState.asStateFlow()

    /** Apps worth offering first on the welcome screen. Empty until asked for. */
    private val _suggestions = MutableStateFlow<List<AppInfo>>(emptyList())
    val suggestions: StateFlow<List<AppInfo>> = _suggestions.asStateFlow()

    private var loaded = false

    /**
     * The welcome screen opens with a question, so it needs something to offer.
     *
     * Ranking by actual screen time would be better, and is what the design
     * calls for — but that needs the usage-access permission, and asking for a
     * permission before showing any value is exactly the wall this redesign
     * removes. So: the apps people most often come here to put down, filtered
     * to the ones actually installed, with the rest of the list one tap away.
     */
    fun loadSuggestions() {
        if (_suggestions.value.isNotEmpty()) return
        viewModelScope.launch {
            val installed = try {
                blockRuleRepository.getInstalledApps()
            } catch (_: Exception) {
                emptyList()
            }
            val byPackage = installed.associateBy { it.packageName }
            val known = COMMON_DISTRACTIONS.mapNotNull { byPackage[it] }
            _suggestions.value = (known + installed.filterNot { it in known }).take(6)
        }
    }

    /** Call on resume: the accessibility switch lives outside the app. */
    fun refreshPermissions() {
        _uiState.update {
            it.copy(accessibilityOn = backgroundProtectionManager.isAccessibilityServiceEnabled())
        }
    }

    fun load(ruleId: Int?, startAsLock: Boolean) {
        if (loaded) return
        loaded = true
        refreshPermissions()

        if (ruleId == null) {
            _uiState.update {
                it.copy(strictness = if (startAsLock) Strictness.Locked else Strictness.Offline)
            }
            return
        }

        viewModelScope.launch {
            if (startAsLock) {
                val rule = openBlockRuleRepository.getRuleById(ruleId) ?: return@launch
                applyLoaded(
                    id = rule.id,
                    strictness = Strictness.Locked,
                    packages = rule.blockedPackages,
                    ruleType = rule.ruleType,
                    startHour = rule.startHour,
                    startMinute = rule.startMinute,
                    endHour = rule.endHour,
                    endMinute = rule.endMinute,
                    activeDays = rule.activeDays,
                    timerMinutes = rule.timerDurationMinutes,
                    isActive = rule.isActive,
                    timerStartedAt = rule.timerStartedAt,
                    createdAt = rule.createdAt
                )
            } else {
                val rule = blockRuleRepository.getRuleById(ruleId) ?: return@launch
                applyLoaded(
                    id = rule.id,
                    strictness = Strictness.Offline,
                    packages = rule.blockedPackages,
                    ruleType = rule.ruleType,
                    startHour = rule.startHour,
                    startMinute = rule.startMinute,
                    endHour = rule.endHour,
                    endMinute = rule.endMinute,
                    activeDays = rule.activeDays,
                    timerMinutes = rule.timerDurationMinutes,
                    isActive = rule.isActive,
                    timerStartedAt = rule.timerStartedAt,
                    createdAt = rule.createdAt
                )
            }
        }
    }

    @Suppress("LongParameterList")
    private suspend fun applyLoaded(
        id: Int,
        strictness: Strictness,
        packages: List<String>,
        ruleType: RuleType,
        startHour: Int?,
        startMinute: Int?,
        endHour: Int?,
        endMinute: Int?,
        activeDays: List<Int>?,
        timerMinutes: Int?,
        isActive: Boolean,
        timerStartedAt: Long?,
        createdAt: Long
    ) {
        _uiState.update {
            it.copy(
                id = id,
                strictness = strictness,
                selectedPackages = packages,
                whenKind = when (ruleType) {
                    RuleType.PERMANENT -> WhenKind.Always
                    RuleType.SCHEDULED -> WhenKind.Hours
                    RuleType.TIMER -> WhenKind.Timer
                },
                startHour = startHour ?: 9,
                startMinute = startMinute ?: 0,
                endHour = endHour ?: 17,
                endMinute = endMinute ?: 0,
                activeDays = activeDays ?: listOf(1, 2, 3, 4, 5),
                timerMinutes = timerMinutes ?: 60,
                isActive = isActive,
                timerStartedAt = timerStartedAt,
                createdAt = createdAt,
                isEditing = true
            )
        }
        val infos = resolveAppInfos(packages)
        _uiState.update { current ->
            if (current.selectedPackages == packages) current.copy(selectedAppInfos = infos) else current
        }
    }

    fun onPackagesSelected(packages: List<String>) {
        _uiState.update { it.copy(selectedPackages = packages) }
        viewModelScope.launch {
            val infos = resolveAppInfos(packages)
            _uiState.update { current ->
                if (current.selectedPackages == packages) current.copy(selectedAppInfos = infos) else current
            }
        }
    }

    fun deselectPackage(packageName: String) = _uiState.update {
        it.copy(
            selectedPackages = it.selectedPackages.filter { pkg -> pkg != packageName },
            selectedAppInfos = it.selectedAppInfos.filter { info -> info.packageName != packageName }
        )
    }

    fun setStrictness(strictness: Strictness) = _uiState.update { it.copy(strictness = strictness) }
    fun setWhenKind(kind: WhenKind) = _uiState.update { it.copy(whenKind = kind) }
    fun setStart(hour: Int, minute: Int) = _uiState.update { it.copy(startHour = hour, startMinute = minute) }
    fun setEnd(hour: Int, minute: Int) = _uiState.update { it.copy(endHour = hour, endMinute = minute) }
    fun setTimerMinutes(minutes: Int) = _uiState.update { it.copy(timerMinutes = minutes) }

    fun toggleDay(day: Int) = _uiState.update { state ->
        val days = state.activeDays.toMutableList()
        if (day in days) days.remove(day) else days.add(day)
        state.copy(activeDays = days.sorted())
    }

    fun openAccessibilitySettings() = backgroundProtectionManager.openAccessibilitySettings()

    /**
     * Saves and turns the block ON. Someone who has just answered three
     * questions about an app expects it blocked, not saved into a list of
     * things they must then remember to switch on.
     */
    fun save(onNeedVpnPermission: (List<String>) -> Unit, onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.isValid) return

        viewModelScope.launch {
            val name = autoName(state)
            val timerStartedAt =
                if (state.whenKind == WhenKind.Timer) System.currentTimeMillis() else null

            if (state.isLock) {
                val rule = OpenBlockRule(
                    id = state.id ?: 0,
                    name = name,
                    blockedPackages = state.selectedPackages,
                    ruleType = state.whenKind.ruleType,
                    isActive = true,
                    startHour = state.startHour.takeIf { state.whenKind == WhenKind.Hours },
                    startMinute = state.startMinute.takeIf { state.whenKind == WhenKind.Hours },
                    endHour = state.endHour.takeIf { state.whenKind == WhenKind.Hours },
                    endMinute = state.endMinute.takeIf { state.whenKind == WhenKind.Hours },
                    activeDays = state.activeDays.takeIf { state.whenKind == WhenKind.Hours },
                    timerDurationMinutes = state.timerMinutes.takeIf { state.whenKind == WhenKind.Timer },
                    timerStartedAt = timerStartedAt,
                    disableEffectiveAt = null,
                    createdAt = state.createdAt
                )
                val saved = if (state.id != null) {
                    openBlockRuleRepository.updateRule(rule); rule
                } else {
                    rule.copy(id = openBlockRuleRepository.saveRule(rule).toInt())
                }
                OpenBlockScheduler.cancelRule(context, saved.id)
                when (state.whenKind) {
                    WhenKind.Hours -> OpenBlockScheduler.scheduleRule(context, saved)
                    WhenKind.Timer -> OpenBlockScheduler.scheduleTimerStop(context, saved)
                    WhenKind.Always -> Unit
                }
            } else {
                val rule = BlockRule(
                    id = state.id ?: 0,
                    name = name,
                    blockedPackages = state.selectedPackages,
                    ruleType = state.whenKind.ruleType,
                    isActive = true,
                    startHour = state.startHour.takeIf { state.whenKind == WhenKind.Hours },
                    startMinute = state.startMinute.takeIf { state.whenKind == WhenKind.Hours },
                    endHour = state.endHour.takeIf { state.whenKind == WhenKind.Hours },
                    endMinute = state.endMinute.takeIf { state.whenKind == WhenKind.Hours },
                    activeDays = state.activeDays.takeIf { state.whenKind == WhenKind.Hours },
                    timerDurationMinutes = state.timerMinutes.takeIf { state.whenKind == WhenKind.Timer },
                    timerStartedAt = timerStartedAt,
                    createdAt = state.createdAt
                )
                val saved = if (state.id != null) {
                    blockRuleRepository.updateRule(rule); rule
                } else {
                    rule.copy(id = blockRuleRepository.saveRule(rule).toInt())
                }
                RuleScheduler.cancelRule(context, saved.id)
                when (state.whenKind) {
                    WhenKind.Hours -> RuleScheduler.scheduleRule(context, saved)
                    WhenKind.Timer -> RuleScheduler.scheduleTimerStop(context, saved)
                    WhenKind.Always -> Unit
                }

                // The tunnel has to carry every offline package, not just this one.
                val allOffline = blockRuleRepository.getActiveRulesOnce()
                    .flatMap { it.blockedPackages }
                    .distinct()
                if (allOffline.isNotEmpty()) onNeedVpnPermission(allOffline)
            }
            onSaved()
        }
    }

    /** Deletes the block outright. Only offered for one that is not enforcing. */
    fun delete(onDeleted: () -> Unit) {
        val state = _uiState.value
        val id = state.id ?: return onDeleted()
        viewModelScope.launch {
            if (state.isLock) {
                openBlockRuleRepository.getRuleById(id)?.let { rule ->
                    OpenBlockScheduler.cancelRule(context, id)
                    openBlockRuleRepository.deleteRule(rule)
                }
            } else {
                blockRuleRepository.getRuleById(id)?.let { rule ->
                    RuleScheduler.cancelRule(context, id)
                    blockRuleRepository.deleteRule(rule)
                }
            }
            onDeleted()
        }
    }

    /**
     * The entity still wants a name; the user no longer has to invent one. The
     * apps are the name, which is also what every row in the list shows.
     */
    private fun autoName(state: BlockCreatorUiState): String {
        val names = state.selectedAppInfos.map { it.appName }.ifEmpty { state.selectedPackages }
        return when {
            names.isEmpty() -> "Block"
            names.size == 1 -> names.first()
            names.size == 2 -> "${names[0]} and ${names[1]}"
            else -> "${names[0]} and ${names.size - 1} more"
        }
    }

    private suspend fun resolveAppInfos(packages: List<String>): List<AppInfo> {
        if (packages.isEmpty()) return emptyList()
        return try {
            blockRuleRepository.getAppInfoForPackages(packages)
        } catch (_: Exception) {
            packages.map { pkg -> AppInfo(packageName = pkg, appName = pkg.substringAfterLast(".")) }
        }
    }

    private companion object {
        /** Offered first on the welcome screen, in this order, if installed. */
        val COMMON_DISTRACTIONS = listOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.google.android.youtube",
            "com.twitter.android",
            "com.x.android",
            "com.reddit.frontpage",
            "com.facebook.katana",
            "com.snapchat.android",
            "com.linkedin.android",
            "com.whatsapp",
            "com.netflix.mediaclient",
            "com.pinterest"
        )
    }
}
