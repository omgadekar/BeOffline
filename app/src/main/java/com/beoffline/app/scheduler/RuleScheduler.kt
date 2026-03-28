package com.beoffline.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.receiver.ScheduleAlarmReceiver
import com.beoffline.app.vpn.VpnController
import com.beoffline.app.vpn.VpnResilienceScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

object RuleScheduler {

    private const val TIMER_STOP_REQUEST_CODE_OFFSET = 20_000

    fun scheduleRule(context: Context, rule: BlockRule) {
        if (rule.ruleType != RuleType.SCHEDULED) return
        val workManager = WorkManager.getInstance(context)

        val startDelay = calculateDelayToNextOccurrence(
            hour = rule.startHour ?: 0,
            minute = rule.startMinute ?: 0,
            activeDays = rule.activeDays
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

    fun scheduleTimerStop(context: Context, rule: BlockRule) {
        if (rule.ruleType != RuleType.TIMER || rule.timerDurationMinutes == null) return

        scheduleExactTimerStopAlarm(context, rule.id, rule.timerDurationMinutes)

        val stopWork = OneTimeWorkRequestBuilder<StopRuleWorker>()
            .setInitialDelay(rule.timerDurationMinutes.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf(StopRuleWorker.KEY_RULE_ID to rule.id))
            .addTag("rule_stop_${rule.id}")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "rule_stop_${rule.id}",
            ExistingWorkPolicy.REPLACE,
            stopWork
        )
    }

    fun cancelRule(context: Context, ruleId: Int) {
        WorkManager.getInstance(context).cancelAllWorkByTag("rule_start_$ruleId")
        WorkManager.getInstance(context).cancelAllWorkByTag("rule_stop_$ruleId")
        cancelExactTimerStopAlarm(context, ruleId)
    }

    fun cancelTimerStop(context: Context, ruleId: Int) {
        WorkManager.getInstance(context).cancelAllWorkByTag("rule_stop_$ruleId")
        cancelExactTimerStopAlarm(context, ruleId)
    }

    private fun calculateDelayToNextOccurrence(
        hour: Int,
        minute: Int,
        activeDays: List<Int>?
    ): Long {
        val now = Calendar.getInstance()
        val days = activeDays?.takeIf { it.isNotEmpty() } ?: listOf(1, 2, 3, 4, 5, 6, 7)

        val calDayMap = mapOf(
            1 to Calendar.MONDAY,
            2 to Calendar.TUESDAY,
            3 to Calendar.WEDNESDAY,
            4 to Calendar.THURSDAY,
            5 to Calendar.FRIDAY,
            6 to Calendar.SATURDAY,
            7 to Calendar.SUNDAY
        )

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

    private fun scheduleExactTimerStopAlarm(context: Context, ruleId: Int, durationMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildTimerStopPendingIntent(
            context = context,
            ruleId = ruleId,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        val triggerAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes.toLong())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelExactTimerStopAlarm(context: Context, ruleId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildTimerStopPendingIntent(
            context = context,
            ruleId = ruleId,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildTimerStopPendingIntent(context: Context, ruleId: Int, flags: Int): PendingIntent? {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            putExtra(ScheduleAlarmReceiver.EXTRA_RULE_ID, ruleId)
            putExtra(ScheduleAlarmReceiver.EXTRA_ACTION, ScheduleAlarmReceiver.ACTION_STOP)
        }
        return PendingIntent.getBroadcast(
            context,
            TIMER_STOP_REQUEST_CODE_OFFSET + ruleId,
            intent,
            flags
        )
    }
}

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
        repository.setTimerStartedAt(ruleId, null)

        val activeRules = repository.getActiveRulesOnce()
        if (activeRules.isEmpty()) {
            VpnResilienceScheduler.cancelHealthMonitor(applicationContext)
            vpnController.stopVpn()
        } else {
            VpnResilienceScheduler.ensureHealthMonitor(applicationContext)
            vpnController.startVpn(activeRules.flatMap { it.blockedPackages }.distinct())
        }

        return Result.success()
    }
}
