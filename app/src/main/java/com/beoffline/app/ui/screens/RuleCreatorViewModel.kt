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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAX_CUSTOM_TIMER_HOURS = 24
private const val MAX_CUSTOM_TIMER_MINUTES = 59
private val PRESET_TIMER_MINUTES = listOf(15, 30, 45, 60)

data class RuleCreatorUiState(
    val id: Int? = null,
    val name: String = "",
    val selectedPackages: List<String> = emptyList(),
    val selectedAppInfos: List<AppInfo> = emptyList(),
    val ruleType: RuleType = RuleType.PERMANENT,
    val startHour: Int? = null,
    val startMinute: Int? = null,
    val endHour: Int? = null,
    val endMinute: Int? = null,
    val activeDays: List<Int> = listOf(1, 2, 3, 4, 5),
    val timerMinutes: Int = 30,
    val isCustomTimer: Boolean = false,
    val customTimerHours: Int = 0,
    val customTimerMinutes: Int = 30,
    val customTimerHoursInput: String = "0",
    val customTimerMinutesInput: String = "30",
    val customTimerError: String? = null,
    val isActive: Boolean = false,
    val timerStartedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false
) {
    val isValid: Boolean
        get() = name.isNotBlank() &&
            selectedPackages.isNotEmpty() &&
            (ruleType != RuleType.TIMER || !isCustomTimer || customTimerError == null)

    val currentCustomTimerMinutes: Int?
        get() = timerMinutes.takeIf { it !in PRESET_TIMER_MINUTES }
}

@HiltViewModel
class RuleCreatorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BlockRuleRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(RuleCreatorUiState())
    val uiState: StateFlow<RuleCreatorUiState> = _uiState.asStateFlow()

    private var ruleLoaded = false

    fun loadRule(ruleId: Int) {
        if (ruleLoaded) return
        ruleLoaded = true

        viewModelScope.launch {
            val rule = repository.getRuleById(ruleId) ?: return@launch
            val timerMinutes = rule.timerDurationMinutes ?: 30
            val customHours = timerMinutes / 60
            val customMinutes = timerMinutes % 60
            val isCustomTimer = rule.ruleType == RuleType.TIMER && timerMinutes !in PRESET_TIMER_MINUTES

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
                    timerMinutes = timerMinutes,
                    isCustomTimer = isCustomTimer,
                    customTimerHours = customHours,
                    customTimerMinutes = customMinutes,
                    customTimerHoursInput = customHours.toString(),
                    customTimerMinutesInput = customMinutes.toString(),
                    customTimerError = null,
                    isActive = rule.isActive,
                    timerStartedAt = rule.timerStartedAt,
                    createdAt = rule.createdAt
                )
            }

            val appInfos = resolveAppInfos(rule.blockedPackages)
            _uiState.update { current ->
                if (current.selectedPackages == rule.blockedPackages) {
                    current.copy(selectedAppInfos = appInfos)
                } else {
                    current
                }
            }
        }
    }

    fun onPackagesSelected(packages: List<String>) {
        _uiState.update { it.copy(selectedPackages = packages) }
        viewModelScope.launch {
            val appInfos = resolveAppInfos(packages)
            _uiState.update { current ->
                if (current.selectedPackages == packages) {
                    current.copy(selectedAppInfos = appInfos)
                } else {
                    current
                }
            }
        }
    }

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
        it.copy(timerMinutes = mins, isCustomTimer = false, customTimerError = null)
    }

    fun onSelectCustomTimer() = _uiState.update { state ->
        val hoursInput = if (state.isCustomTimer) state.customTimerHoursInput else (state.timerMinutes / 60).toString()
        val minutesInput = if (state.isCustomTimer) state.customTimerMinutesInput else (state.timerMinutes % 60).toString()
        updateCustomTimerState(
            state = state.copy(isCustomTimer = true),
            hoursInput = hoursInput,
            minutesInput = minutesInput
        )
    }

    fun onCustomTimerHoursChange(hoursInput: String) {
        _uiState.update { state ->
            updateCustomTimerState(state = state.copy(isCustomTimer = true), hoursInput = hoursInput)
        }
    }

    fun onCustomTimerMinutesChange(minutesInput: String) {
        _uiState.update { state ->
            updateCustomTimerState(state = state.copy(isCustomTimer = true), minutesInput = minutesInput)
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
            isActive = state.isActive,
            startHour = state.startHour,
            startMinute = state.startMinute,
            endHour = state.endHour,
            endMinute = state.endMinute,
            activeDays = state.activeDays,
            timerDurationMinutes = if (state.ruleType == RuleType.TIMER) state.timerMinutes else null,
            timerStartedAt = state.timerStartedAt,
            createdAt = state.createdAt
        )
        viewModelScope.launch {
            val id = repository.saveRule(rule)
            if (state.ruleType == RuleType.SCHEDULED) {
                RuleScheduler.scheduleRule(context, rule.copy(id = id.toInt()))
            }
        }
    }

    private suspend fun resolveAppInfos(packages: List<String>): List<AppInfo> {
        if (packages.isEmpty()) return emptyList()
        return try {
            repository.getAppInfoForPackages(packages)
        } catch (_: Exception) {
            packages.map { pkg -> AppInfo(packageName = pkg, appName = pkg.substringAfterLast(".")) }
        }
    }

    private fun updateCustomTimerState(
        state: RuleCreatorUiState,
        hoursInput: String = state.customTimerHoursInput,
        minutesInput: String = state.customTimerMinutesInput
    ): RuleCreatorUiState {
        val sanitizedHours = hoursInput.filter(Char::isDigit).take(4)
        val sanitizedMinutes = minutesInput.filter(Char::isDigit).take(4)

        val parsedHours = sanitizedHours.toIntOrNull()
        val parsedMinutes = sanitizedMinutes.toIntOrNull()

        val hoursValue = parsedHours ?: 0
        val minutesValue = parsedMinutes ?: 0

        val error = when {
            parsedHours != null && parsedHours !in 0..MAX_CUSTOM_TIMER_HOURS ->
                "Hours must be between 0 and $MAX_CUSTOM_TIMER_HOURS."
            parsedMinutes != null && parsedMinutes !in 0..MAX_CUSTOM_TIMER_MINUTES ->
                "Minutes must be between 0 and $MAX_CUSTOM_TIMER_MINUTES."
            hoursValue == 24 && minutesValue != 0 ->
                "24 hours must use 00 minutes."
            hoursValue == 0 && minutesValue == 0 ->
                "Timer must be at least 1 minute."
            else -> null
        }

        val nextHours = when {
            sanitizedHours.isBlank() -> 0
            parsedHours != null && parsedHours in 0..MAX_CUSTOM_TIMER_HOURS -> parsedHours
            else -> state.customTimerHours
        }

        val nextMinutes = when {
            sanitizedMinutes.isBlank() -> 0
            parsedMinutes != null && parsedMinutes in 0..MAX_CUSTOM_TIMER_MINUTES -> parsedMinutes
            else -> state.customTimerMinutes
        }

        return state.copy(
            isCustomTimer = true,
            customTimerHours = nextHours,
            customTimerMinutes = nextMinutes,
            customTimerHoursInput = sanitizedHours,
            customTimerMinutesInput = sanitizedMinutes,
            timerMinutes = if (error == null) (hoursValue * 60 + minutesValue) else state.timerMinutes,
            customTimerError = error
        )
    }
}
