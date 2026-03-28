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

    private const val SCHEDULE_START_REQUEST_CODE_OFFSET = 10_000
    private const val SCHEDULE_STOP_REQUEST_CODE_OFFSET = 15_000
    private const val TIMER_STOP_REQUEST_CODE_OFFSET = 20_000

    fun scheduleRule(context: Context, rule: BlockRule) {
        if (rule.ruleType != RuleType.SCHEDULED) return
        cancelScheduledAlarm(context, rule.id, ScheduleAlarmReceiver.ACTION_START)
        cancelScheduledAlarm(context, rule.id, ScheduleAlarmReceiver.ACTION_STOP)
        WorkManager.getInstance(context).cancelAllWorkByTag("rule_start_${rule.id}")
        WorkManager.getInstance(context).cancelAllWorkByTag("rule_stop_${rule.id}")

        val startTriggerAtMillis = if (!rule.isActive && isWithinScheduledWindow(rule)) {
            System.currentTimeMillis() + 1_000L
        } else {
            calculateNextOccurrenceTimeMillis(
                hour = rule.startHour ?: 0,
                minute = rule.startMinute ?: 0,
                activeDays = rule.activeDays
            )
        }

        val stopTriggerAtMillis = calculateNextStopTimeMillis(rule)

        scheduleScheduledAlarm(context, rule.id, ScheduleAlarmReceiver.ACTION_START, startTriggerAtMillis)
        scheduleScheduledAlarm(context, rule.id, ScheduleAlarmReceiver.ACTION_STOP, stopTriggerAtMillis)
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
        cancelScheduledAlarm(context, ruleId, ScheduleAlarmReceiver.ACTION_START)
        cancelScheduledAlarm(context, ruleId, ScheduleAlarmReceiver.ACTION_STOP)
        cancelExactTimerStopAlarm(context, ruleId)
    }

    fun cancelTimerStop(context: Context, ruleId: Int) {
        WorkManager.getInstance(context).cancelAllWorkByTag("rule_stop_$ruleId")
        cancelExactTimerStopAlarm(context, ruleId)
    }

    fun isWithinScheduledWindow(rule: BlockRule, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val startHour = rule.startHour ?: return false
        val startMinute = rule.startMinute ?: return false
        val endHour = rule.endHour ?: return false
        val endMinute = rule.endMinute ?: return false
        val activeDays = rule.activeDays?.takeIf { it.isNotEmpty() } ?: listOf(1, 2, 3, 4, 5, 6, 7)
        val calendarDays = mapOf(
            Calendar.MONDAY to 1,
            Calendar.TUESDAY to 2,
            Calendar.WEDNESDAY to 3,
            Calendar.THURSDAY to 4,
            Calendar.FRIDAY to 5,
            Calendar.SATURDAY to 6,
            Calendar.SUNDAY to 7
        )

        for (offset in -1..0) {
            val start = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, startHour)
                set(Calendar.MINUTE, startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val customDay = calendarDays[start.get(Calendar.DAY_OF_WEEK)] ?: continue
            if (customDay !in activeDays) continue

            val end = (start.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, endHour)
                set(Calendar.MINUTE, endMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= start.timeInMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            if (nowMillis in start.timeInMillis until end.timeInMillis) {
                return true
            }
        }

        return false
    }

    private fun calculateNextOccurrenceTimeMillis(
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
                return candidate.timeInMillis
            }
        }

        return now.timeInMillis + TimeUnit.DAYS.toMillis(1)
    }

    private fun calculateNextStopTimeMillis(rule: BlockRule): Long {
        val startHour = rule.startHour ?: return System.currentTimeMillis() + 60_000L
        val startMinute = rule.startMinute ?: 0
        val endHour = rule.endHour ?: return System.currentTimeMillis() + 60_000L
        val endMinute = rule.endMinute ?: 0
        val now = System.currentTimeMillis()
        val days = rule.activeDays?.takeIf { it.isNotEmpty() } ?: listOf(1, 2, 3, 4, 5, 6, 7)
        val calendarDays = mapOf(
            Calendar.MONDAY to 1,
            Calendar.TUESDAY to 2,
            Calendar.WEDNESDAY to 3,
            Calendar.THURSDAY to 4,
            Calendar.FRIDAY to 5,
            Calendar.SATURDAY to 6,
            Calendar.SUNDAY to 7
        )

        var closest: Long? = null
        for (offset in -1..7) {
            val start = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, startHour)
                set(Calendar.MINUTE, startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val customDay = calendarDays[start.get(Calendar.DAY_OF_WEEK)] ?: continue
            if (customDay !in days) continue

            val end = (start.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, endHour)
                set(Calendar.MINUTE, endMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= start.timeInMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            if (end.timeInMillis > now && (closest == null || end.timeInMillis < closest)) {
                closest = end.timeInMillis
            }
        }

        return closest ?: (now + TimeUnit.DAYS.toMillis(1))
    }

    private fun scheduleScheduledAlarm(
        context: Context,
        ruleId: Int,
        action: String,
        triggerAtMillis: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildScheduledPendingIntent(
            context = context,
            ruleId = ruleId,
            action = action,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelScheduledAlarm(context: Context, ruleId: Int, action: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildScheduledPendingIntent(
            context = context,
            ruleId = ruleId,
            action = action,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildScheduledPendingIntent(
        context: Context,
        ruleId: Int,
        action: String,
        flags: Int
    ): PendingIntent? {
        val requestCode = when (action) {
            ScheduleAlarmReceiver.ACTION_START -> SCHEDULE_START_REQUEST_CODE_OFFSET + ruleId
            ScheduleAlarmReceiver.ACTION_STOP -> SCHEDULE_STOP_REQUEST_CODE_OFFSET + ruleId
            else -> return null
        }
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            putExtra(ScheduleAlarmReceiver.EXTRA_RULE_ID, ruleId)
            putExtra(ScheduleAlarmReceiver.EXTRA_ACTION, action)
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
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
        val activeRules = repository.getActiveRulesOnce()
        vpnController.startVpn(activeRules.flatMap { it.blockedPackages }.distinct())

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
