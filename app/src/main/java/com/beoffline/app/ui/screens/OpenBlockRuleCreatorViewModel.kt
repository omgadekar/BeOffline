package com.beoffline.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beoffline.app.data.model.AppInfo
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.data.repository.OpenBlockRuleRepository
import com.beoffline.app.scheduler.OpenBlockScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OpenBlockRuleCreatorUiState(
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
    val isActive: Boolean = false,
    val timerStartedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Unlike the internet-block creator, OVERNIGHT windows are allowed here
     * (e.g. 22:00 → 07:00) — RuleScheduler's window math handles the midnight
     * crossing. Only a zero-length window (start == end) is rejected.
     */
    val scheduleError: String?
        get() = when {
            ruleType != RuleType.SCHEDULED -> null
            startHour == null || startMinute == null -> "Choose a start time."
            endHour == null || endMinute == null -> "Choose an end time."
            activeDays.isEmpty() -> "Select at least one repeat day."
            startHour == endHour && startMinute == endMinute ->
                "End time must differ from start time."
            else -> null
        }

    /** Positive hint when the window crosses midnight. */
    val overnightHint: String?
        get() {
            if (ruleType != RuleType.SCHEDULED) return null
            val sh = startHour ?: return null
            val sm = startMinute ?: return null
            val eh = endHour ?: return null
            val em = endMinute ?: return null
            return if ((eh * 60 + em) <= (sh * 60 + sm)) "This window ends the next day." else null
        }

    val isValid: Boolean
        get() = name.isNotBlank() &&
            selectedPackages.isNotEmpty() &&
            (ruleType != RuleType.SCHEDULED || scheduleError == null)
}

@HiltViewModel
class OpenBlockRuleCreatorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: OpenBlockRuleRepository,
    private val blockRuleRepository: BlockRuleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpenBlockRuleCreatorUiState())
    val uiState: StateFlow<OpenBlockRuleCreatorUiState> = _uiState.asStateFlow()

    private var ruleLoaded = false

    fun loadRule(ruleId: Int) {
        if (ruleLoaded) return
        ruleLoaded = true

        viewModelScope.launch {
            val rule = repository.getRuleById(ruleId) ?: return@launch
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
                    timerMinutes = rule.timerDurationMinutes ?: 30,
                    isActive = rule.isActive,
                    timerStartedAt = rule.timerStartedAt,
                    createdAt = rule.createdAt
                )
            }
            resolveAndSetAppInfos(rule.blockedPackages)
        }
    }

    fun onPackagesSelected(packages: List<String>) {
        _uiState.update { it.copy(selectedPackages = packages) }
        viewModelScope.launch { resolveAndSetAppInfos(packages) }
    }

    fun deselectPackage(packageName: String) {
        _uiState.update { state ->
            state.copy(
                selectedPackages = state.selectedPackages.filter { it != packageName },
                selectedAppInfos = state.selectedAppInfos.filter { it.packageName != packageName }
            )
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }
    fun onRuleTypeChange(type: RuleType) = _uiState.update { it.copy(ruleType = type) }
    fun onStartTimeSet(hour: Int, minute: Int) =
        _uiState.update { it.copy(startHour = hour, startMinute = minute) }
    fun onEndTimeSet(hour: Int, minute: Int) =
        _uiState.update { it.copy(endHour = hour, endMinute = minute) }
    fun onTimerMinutesChange(mins: Int) = _uiState.update { it.copy(timerMinutes = mins) }

    fun onToggleDay(day: Int) {
        val days = _uiState.value.activeDays.toMutableList()
        if (day in days) days.remove(day) else days.add(day)
        _uiState.update { it.copy(activeDays = days.sorted()) }
    }

    fun saveRule(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.isValid) return

        val rule = OpenBlockRule(
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
            val savedRule = if (state.id != null) {
                repository.updateRule(rule)
                rule
            } else {
                val id = repository.saveRule(rule)
                rule.copy(id = id.toInt())
            }
            OpenBlockScheduler.cancelRule(context, savedRule.id)
            if (state.ruleType == RuleType.SCHEDULED) {
                OpenBlockScheduler.scheduleRule(context, savedRule)
            }
            onSaved()
        }
    }

    private suspend fun resolveAndSetAppInfos(packages: List<String>) {
        val infos = try {
            blockRuleRepository.getAppInfoForPackages(packages)
        } catch (_: Exception) {
            packages.map { AppInfo(packageName = it, appName = it.substringAfterLast('.')) }
        }
        _uiState.update { current ->
            if (current.selectedPackages == packages) current.copy(selectedAppInfos = infos) else current
        }
    }
}
