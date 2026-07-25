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
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.data.repository.OpenBlockRuleRepository
import com.beoffline.app.receiver.OpenBlockAlarmReceiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * OpenBlockScheduler — window/timer alarms for the open-block engine.
 *
 * Mirrors [RuleScheduler] (exact alarms primary + WorkManager redundancy for
 * timers) but is deliberately separate: the two restriction features must not
 * conflict, so each schedules its own rules against its own receiver. The
 * window MATH is shared via [RuleScheduler]'s ScheduledWindow helpers.
 *
 * Unlike the VPN there is no service to start — alarms only flip the rule's
 * isActive row; the AccessibilityService observes the DB Flow and reacts.
 *
 * Request-code offsets 30k/35k/40k — disjoint from RuleScheduler's 10k/15k/20k.
 */
object OpenBlockScheduler {

    private const val START_REQUEST_CODE_OFFSET = 30_000
    private const val STOP_REQUEST_CODE_OFFSET = 35_000
    private const val TIMER_STOP_REQUEST_CODE_OFFSET = 40_000

    fun scheduleRule(context: Context, rule: OpenBlockRule) {
        if (rule.ruleType != RuleType.SCHEDULED) return
        cancelAlarm(context, rule.id, OpenBlockAlarmReceiver.ACTION_START)
        cancelAlarm(context, rule.id, OpenBlockAlarmReceiver.ACTION_STOP)
        WorkManager.getInstance(context).cancelAllWorkByTag(stopWorkTag(rule.id))

        val startTriggerAtMillis = if (!rule.isActive && RuleScheduler.isWithinScheduledWindow(rule)) {
            System.currentTimeMillis() + 1_000L
        } else {
            RuleScheduler.calculateNextOccurrenceTimeMillis(
                hour = rule.startHour ?: 0,
                minute = rule.startMinute ?: 0,
                activeDays = rule.activeDays
            )
        }

        val stopTriggerAtMillis = RuleScheduler.calculateNextStopTimeMillis(rule)

        scheduleAlarm(context, rule.id, OpenBlockAlarmReceiver.ACTION_START, startTriggerAtMillis)
        scheduleAlarm(context, rule.id, OpenBlockAlarmReceiver.ACTION_STOP, stopTriggerAtMillis)
    }

    fun scheduleTimerStop(context: Context, rule: OpenBlockRule) {
        if (rule.ruleType != RuleType.TIMER || rule.timerDurationMinutes == null) return

        val triggerAtMillis =
            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(rule.timerDurationMinutes.toLong())
        buildTimerStopPendingIntent(
            context, rule.id,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )?.let { pendingIntent ->
            scheduleExactAlarm(context, pendingIntent, triggerAtMillis)
        }

        // WorkManager backup — survives reboot (exact alarms do not).
        val stopWork = OneTimeWorkRequestBuilder<StopOpenBlockRuleWorker>()
            .setInitialDelay(rule.timerDurationMinutes.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf(StopOpenBlockRuleWorker.KEY_RULE_ID to rule.id))
            .addTag(stopWorkTag(rule.id))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            stopWorkTag(rule.id),
            ExistingWorkPolicy.REPLACE,
            stopWork
        )
    }

    fun cancelRule(context: Context, ruleId: Int) {
        WorkManager.getInstance(context).cancelAllWorkByTag(stopWorkTag(ruleId))
        cancelAlarm(context, ruleId, OpenBlockAlarmReceiver.ACTION_START)
        cancelAlarm(context, ruleId, OpenBlockAlarmReceiver.ACTION_STOP)
        cancelTimerStopAlarm(context, ruleId)
    }

    fun cancelTimerStop(context: Context, ruleId: Int) {
        WorkManager.getInstance(context).cancelAllWorkByTag(stopWorkTag(ruleId))
        cancelTimerStopAlarm(context, ruleId)
    }

    // ── Disable cooldown (accountability) ──────────────────────────────────────
    // WorkManager (not an alarm) so the finalize survives reboot without a boot
    // receiver. Correctness doesn't depend on it firing on time — enforcement
    // already treats the rule as off once its cooldown elapses; this just
    // persists isActive=false and tears the schedule down.

    fun scheduleDisableFinalize(context: Context, ruleId: Int, delayMillis: Long) {
        val work = OneTimeWorkRequestBuilder<FinalizeOpenBlockDisableWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(FinalizeOpenBlockDisableWorker.KEY_RULE_ID to ruleId))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(disableWorkName(ruleId), ExistingWorkPolicy.REPLACE, work)
    }

    fun cancelDisableFinalize(context: Context, ruleId: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(disableWorkName(ruleId))
    }

    private fun disableWorkName(ruleId: Int) = "open_rule_disable_finalize_$ruleId"

    private fun stopWorkTag(ruleId: Int) = "open_rule_stop_$ruleId"

    private fun scheduleAlarm(context: Context, ruleId: Int, action: String, triggerAtMillis: Long) {
        val pendingIntent = buildPendingIntent(
            context, ruleId, action,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        scheduleExactAlarm(context, pendingIntent, triggerAtMillis)
    }

    private fun scheduleExactAlarm(context: Context, pendingIntent: PendingIntent, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAlarm(context: Context, ruleId: Int, action: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(
            context, ruleId, action,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun cancelTimerStopAlarm(context: Context, ruleId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildTimerStopPendingIntent(
            context, ruleId,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildPendingIntent(
        context: Context,
        ruleId: Int,
        action: String,
        flags: Int
    ): PendingIntent? {
        val requestCode = when (action) {
            OpenBlockAlarmReceiver.ACTION_START -> START_REQUEST_CODE_OFFSET + ruleId
            OpenBlockAlarmReceiver.ACTION_STOP -> STOP_REQUEST_CODE_OFFSET + ruleId
            else -> return null
        }
        val intent = Intent(context, OpenBlockAlarmReceiver::class.java).apply {
            putExtra(OpenBlockAlarmReceiver.EXTRA_RULE_ID, ruleId)
            putExtra(OpenBlockAlarmReceiver.EXTRA_ACTION, action)
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun buildTimerStopPendingIntent(context: Context, ruleId: Int, flags: Int): PendingIntent? {
        val intent = Intent(context, OpenBlockAlarmReceiver::class.java).apply {
            putExtra(OpenBlockAlarmReceiver.EXTRA_RULE_ID, ruleId)
            putExtra(OpenBlockAlarmReceiver.EXTRA_ACTION, OpenBlockAlarmReceiver.ACTION_STOP)
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
class StopOpenBlockRuleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: OpenBlockRuleRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_RULE_ID = "rule_id"
    }

    override suspend fun doWork(): Result {
        val ruleId = inputData.getInt(KEY_RULE_ID, -1)
        if (ruleId == -1) return Result.failure()

        repository.setRuleActive(ruleId, false)
        repository.setTimerStartedAt(ruleId, null)
        return Result.success()
    }
}

/**
 * Finalizes a disable-cooldown: flips the lock fully off and tears down its
 * schedule. Skips if the cooldown was cancelled in the meantime (the row's
 * disableEffectiveAt is back to null), so a stale worker can't force a lock off.
 */
@HiltWorker
class FinalizeOpenBlockDisableWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: OpenBlockRuleRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_RULE_ID = "rule_id"
    }

    override suspend fun doWork(): Result {
        val ruleId = inputData.getInt(KEY_RULE_ID, -1)
        if (ruleId == -1) return Result.failure()

        val rule = repository.getRuleById(ruleId)
        if (rule?.disableEffectiveAt == null) return Result.success() // cancelled/finalized already

        repository.finalizeDisable(ruleId)
        OpenBlockScheduler.cancelRule(applicationContext, ruleId)
        return Result.success()
    }
}
