package com.beoffline.app.scheduler

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.vpn.VpnController
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * RuleScheduler — manages WorkManager tasks for scheduled and timer rules.
 *
 * HOW SCHEDULING WORKS:
 * For SCHEDULED rules: We calculate the exact delay until next occurrence of
 * the start time, then enqueue a OneTimeWorkRequest with that delay. When that
 * worker fires, it activates the VPN and schedules its own follow-up stop worker.
 *
 * For TIMER rules: Simple countdown — enqueue a stop worker with the timer
 * duration as the delay.
 */
object RuleScheduler {

    /**
     * Enqueues WorkManager jobs for a scheduled rule.
     * Safe to call multiple times — uses unique work names to prevent duplicates.
     */
    fun scheduleRule(context: Context, rule: BlockRule) {
        if (rule.ruleType != RuleType.SCHEDULED) return
        val workManager = WorkManager.getInstance(context)

        val startDelay = calculateDelayToNextOccurrence(
            rule.startHour ?: 0,
            rule.startMinute ?: 0,
            rule.activeDays
        )

        val startWork = OneTimeWorkRequestBuilder<StartRuleWorker>()
            .setInitialDelay(startDelay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(StartRuleWorker.KEY_RULE_ID to rule.id))
            .addTag("rule_start_${rule.id}")
            .build()

        workManager.enqueueUniqueWork(
            "rule_start_${rule.id}",
            ExistingWorkPolicy.REPLACE,
            startWork
        )
    }

    /**
     * Enqueues a stop worker for a TIMER rule.
     */
    fun scheduleTimerStop(context: Context, rule: BlockRule) {
        if (rule.ruleType != RuleType.TIMER || rule.timerDurationMinutes == null) return
        val workManager = WorkManager.getInstance(context)

        val stopWork = OneTimeWorkRequestBuilder<StopRuleWorker>()
            .setInitialDelay(rule.timerDurationMinutes.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf(StopRuleWorker.KEY_RULE_ID to rule.id))
            .addTag("rule_stop_${rule.id}")
            .build()

        workManager.enqueueUniqueWork(
            "rule_stop_${rule.id}",
            ExistingWorkPolicy.REPLACE,
            stopWork
        )
    }

    /** Cancels all pending WorkManager jobs for a given rule. */
    fun cancelRule(context: Context, ruleId: Int) {
        WorkManager.getInstance(context).cancelAllWorkByTag("rule_start_$ruleId")
        WorkManager.getInstance(context).cancelAllWorkByTag("rule_stop_$ruleId")
    }

    /**
     * Calculates milliseconds until the next occurrence of [hour]:[minute]
     * on any of the [activeDays]. Returns 0 if the time is now or past for today.
     */
    private fun calculateDelayToNextOccurrence(
        hour: Int,
        minute: Int,
        activeDays: List<Int>?
    ): Long {
        val now = Calendar.getInstance()

        // If no specific days, default to daily
        val days = activeDays?.takeIf { it.isNotEmpty() } ?: listOf(1, 2, 3, 4, 5, 6, 7)

        // Map our 1=Mon, 7=Sun to Calendar.DAY_OF_WEEK constants
        val calDayMap = mapOf(
            1 to Calendar.MONDAY,
            2 to Calendar.TUESDAY,
            3 to Calendar.WEDNESDAY,
            4 to Calendar.THURSDAY,
            5 to Calendar.FRIDAY,
            6 to Calendar.SATURDAY,
            7 to Calendar.SUNDAY
        )

        // Find the next occurrence (within 7 days)
        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val candidateDayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
            val candidateCustomDay = calDayMap.entries.firstOrNull { it.value == candidateDayOfWeek }?.key
                ?: continue

            if (candidateCustomDay in days && candidate.timeInMillis > now.timeInMillis) {
                return candidate.timeInMillis - now.timeInMillis
            }
        }

        return 0L
    }
}

// ============================================================================
// Workers
// ============================================================================

@HiltWorker
class StartRuleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: BlockRuleRepository,
    private val vpnController: VpnController
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_RULE_ID = "rule_id"
    }

    override suspend fun doWork(): Result {
        val ruleId = inputData.getInt(KEY_RULE_ID, -1)
        if (ruleId == -1) return Result.failure()

        val rule = repository.getRuleById(ruleId) ?: return Result.failure()
        repository.setRuleActive(ruleId, true)
        vpnController.startVpn(rule.blockedPackages)

        // Schedule the stop based on end time
        val stopDelay = calculateStopDelay(rule)
        if (stopDelay > 0) {
            val stopWork = OneTimeWorkRequestBuilder<StopRuleWorker>()
                .setInitialDelay(stopDelay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(StopRuleWorker.KEY_RULE_ID to ruleId))
                .addTag("rule_stop_$ruleId")
                .build()
            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork("rule_stop_$ruleId", ExistingWorkPolicy.REPLACE, stopWork)
        }

        // Re-schedule for next occurrence (recurring schedule)
        if (rule.ruleType == RuleType.SCHEDULED) {
            RuleScheduler.scheduleRule(applicationContext, rule)
        }

        return Result.success()
    }

    private fun calculateStopDelay(rule: BlockRule): Long {
        val endHour = rule.endHour ?: return 0L
        val endMinute = rule.endMinute ?: 0
        val now = Calendar.getInstance()
        val end = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return end.timeInMillis - now.timeInMillis
    }
}

@HiltWorker
class StopRuleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: BlockRuleRepository,
    private val vpnController: VpnController
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_RULE_ID = "rule_id"
    }

    override suspend fun doWork(): Result {
        val ruleId = inputData.getInt(KEY_RULE_ID, -1)
        if (ruleId == -1) return Result.failure()

        repository.setRuleActive(ruleId, false)

        // Only stop VPN if no other rules are still active
        val activeRules = mutableListOf<com.beoffline.app.data.model.BlockRule>()
        repository.getActiveRules().collect { activeRules.addAll(it) }

        if (activeRules.isEmpty()) {
            vpnController.stopVpn()
        } else {
            // Restart VPN with remaining active packages only
            val remaining = activeRules.flatMap { it.blockedPackages }.distinct()
            vpnController.startVpn(remaining)
        }

        return Result.success()
    }
}
