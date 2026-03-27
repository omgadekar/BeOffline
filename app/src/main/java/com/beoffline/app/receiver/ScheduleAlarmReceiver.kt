package com.beoffline.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * ScheduleAlarmReceiver — placeholder for AlarmManager-based exact alarms.
 *
 * WorkManager is our primary scheduling mechanism, but for cases where
 * Android's Doze mode might defer WorkManager tasks past the scheduled time,
 * we can optionally use AlarmManager with SCHEDULE_EXACT_ALARM permission
 * as a backup trigger for time-critical schedules.
 *
 * This receiver is a thin dispatcher that delegates to the appropriate
 * WorkManager worker based on the intent extra.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduleAlarmReceiver"
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getIntExtra(EXTRA_RULE_ID, -1)
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        Log.d(TAG, "AlarmReceiver: action=$action ruleId=$ruleId")

        // Trigger the appropriate WorkManager worker immediately
        // This ensures work runs even if WorkManager was deferred by Doze
        when (action) {
            ACTION_START -> {
                androidx.work.OneTimeWorkRequest.Builder(
                    com.beoffline.app.scheduler.StartRuleWorker::class.java
                ).setInputData(
                    androidx.work.workDataOf(
                        com.beoffline.app.scheduler.StartRuleWorker.KEY_RULE_ID to ruleId
                    )
                ).build().also {
                    androidx.work.WorkManager.getInstance(context).enqueue(it)
                }
            }
            ACTION_STOP -> {
                androidx.work.OneTimeWorkRequest.Builder(
                    com.beoffline.app.scheduler.StopRuleWorker::class.java
                ).setInputData(
                    androidx.work.workDataOf(
                        com.beoffline.app.scheduler.StopRuleWorker.KEY_RULE_ID to ruleId
                    )
                ).build().also {
                    androidx.work.WorkManager.getInstance(context).enqueue(it)
                }
            }
        }
    }
}
