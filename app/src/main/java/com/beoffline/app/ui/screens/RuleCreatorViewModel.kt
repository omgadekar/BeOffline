package com.beoffline.app.ui.screens

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beoffline.app.data.model.AppInfo
import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.scheduler.RuleScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleCreatorUiState(
    val id: Int? = null,
    val name: String = "",
    val selectedPackages: List<String> = emptyList(),
    /** Resolved AppInfo objects for selected packages (for icon/name display) */
    val selectedAppInfos: List<AppInfo> = emptyList(),
    val ruleType: RuleType = RuleType.PERMANENT,
    val startHour: Int? = null,
    val startMinute: Int? = null,
    val endHour: Int? = null,
    val endMinute: Int? = null,
    val activeDays: List<Int> = listOf(1, 2, 3, 4, 5),
    val timerMinutes: Int = 30,
    // Custom timer support
    val isCustomTimer: Boolean = false,
    val customTimerHours: Int = 0,
    val customTimerMinutes: Int = 30,
    val isLoading: Boolean = false
) {
    val isValid: Boolean
        get() = name.isNotBlank() && selectedPackages.isNotEmpty()
}

@HiltViewModel
class RuleCreatorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BlockRuleRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(RuleCreatorUiState())
    val uiState: StateFlow<RuleCreatorUiState> = _uiState.asStateFlow()

    /**
     * Tracks whether this rule has already been loaded into state.
     * This is a ViewModel-instance variable (survives recomposition).
     *
     * WHY THIS IS NEEDED:
     * Compose Navigation removes a destination from the composition when
     * another destination is pushed on top. When you pop back (e.g. from
     * AppPicker back to RuleCreator), the composable is re-added to the
     * composition and LaunchedEffect(ruleId) fires AGAIN. Without this
     * guard, loadRule() would re-fetch the original packages from the DB
     * and overwrite whatever the user just selected in AppPicker.
     */
    private var ruleLoaded = false

    fun loadRule(ruleId: Int) {
        if (ruleLoaded) return   // already loaded — preserve any user changes
        ruleLoaded = true

        viewModelScope.launch {
            val rule = repository.getRuleById(ruleId) ?: return@launch

            // Update all rule fields immediately (fast DB read, no icon wait)
            _uiState.update {
                it.copy(
                    id = rule.id,
                    name = rule.name,
                    selectedPackages = rule.blockedPackages,
                    ruleType = rule.ruleType,
                    startHour = rule.startHour,
                    startMinute = rule.startMinute,
                    endHour = rule.endHour,
                    endMinute = rule.endMinute,
                    activeDays = rule.activeDays ?: listOf(1, 2, 3, 4, 5),
                    timerMinutes = rule.timerDurationMinutes ?: 30
                )
            }

            // Resolve icons in background
            val appInfos = resolveAppInfos(rule.blockedPackages)
            _uiState.update { current ->
                // Only write icons if packages haven't changed
                if (current.selectedPackages == rule.blockedPackages) {
                    current.copy(selectedAppInfos = appInfos)
                } else {
                    current
                }
            }
        }
    }

    /** Called directly by AppPickerScreen (via shared ViewModel reference) */
    fun onPackagesSelected(packages: List<String>) {
        // Update selectedPackages IMMEDIATELY so the count shows correctly
        // when the user navigates back before icon resolution finishes.
        _uiState.update { it.copy(selectedPackages = packages) }
        // Resolve icons asynchronously (non-blocking)
        viewModelScope.launch {
            val appInfos = resolveAppInfos(packages)
            // Guard: only write icons if packages still match what we resolved for
            _uiState.update { current ->
                if (current.selectedPackages == packages) {
                    current.copy(selectedAppInfos = appInfos)
                } else {
                    current
                }
            }
        }
    }

    /** Remove a single app from the selection without opening the picker */
    fun deselectPackage(packageName: String) {
        val newPackages = _uiState.value.selectedPackages.filter { it != packageName }
        val newInfos = _uiState.value.selectedAppInfos.filter { it.packageName != packageName }
        _uiState.update { it.copy(selectedPackages = newPackages, selectedAppInfos = newInfos) }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }
    fun onRuleTypeChange(type: RuleType) = _uiState.update { it.copy(ruleType = type) }
    fun onStartTimeSet(hour: Int, minute: Int) = _uiState.update { it.copy(startHour = hour, startMinute = minute) }
    fun onEndTimeSet(hour: Int, minute: Int) = _uiState.update { it.copy(endHour = hour, endMinute = minute) }

    fun onTimerMinutesChange(mins: Int) = _uiState.update {
        it.copy(timerMinutes = mins, isCustomTimer = false)
    }

    fun onSelectCustomTimer() = _uiState.update { it.copy(isCustomTimer = true) }

    fun onCustomTimerHoursChange(hours: Int) {
        _uiState.update {
            val totalMins = hours * 60 + it.customTimerMinutes
            it.copy(customTimerHours = hours, timerMinutes = totalMins.coerceAtLeast(1))
        }
    }

    fun onCustomTimerMinutesChange(minutes: Int) {
        _uiState.update {
            val totalMins = it.customTimerHours * 60 + minutes
            it.copy(customTimerMinutes = minutes, timerMinutes = totalMins.coerceAtLeast(1))
        }
    }

    fun onToggleDay(day: Int) {
        val days = _uiState.value.activeDays.toMutableList()
        if (day in days) days.remove(day) else days.add(day)
        _uiState.update { it.copy(activeDays = days.sorted()) }
    }

    fun saveRule() {
        val state = _uiState.value
        val rule = BlockRule(
            id = state.id ?: 0,
            name = state.name.trim(),
            blockedPackages = state.selectedPackages,
            ruleType = state.ruleType,
            startHour = state.startHour,
            startMinute = state.startMinute,
            endHour = state.endHour,
            endMinute = state.endMinute,
            activeDays = state.activeDays,
            timerDurationMinutes = if (state.ruleType == RuleType.TIMER) state.timerMinutes else null
        )
        viewModelScope.launch {
            val id = repository.saveRule(rule)
            if (state.ruleType == RuleType.SCHEDULED) {
                RuleScheduler.scheduleRule(context, rule.copy(id = id.toInt()))
            }
        }
    }

    /** Resolve package names → AppInfo list (for displaying icons/names in the creator) */
    private suspend fun resolveAppInfos(packages: List<String>): List<AppInfo> {
        if (packages.isEmpty()) return emptyList()
        return try {
            repository.getAppInfoForPackages(packages)
        } catch (e: Exception) {
            packages.map { pkg -> AppInfo(packageName = pkg, appName = pkg.substringAfterLast(".")) }
        }
    }
}
