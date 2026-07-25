package com.beoffline.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.data.repository.OpenBlockRuleRepository
import com.beoffline.app.scheduler.OpenBlockScheduler
import com.beoffline.app.scheduler.RuleScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * OpenBlockAlarmReceiver — window start/stop + timer expiry for open-block rules.
 *
 * Mirrors [ScheduleAlarmReceiver], minus any service management: flipping the
 * rule's isActive row IS the whole action — the AccessibilityService observes
 * the active-rules Flow and enforces accordingly.
 */
@AndroidEntryPoint
class OpenBlockAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: OpenBlockRuleRepository

    companion object {
        private const val TAG = "OpenBlockAlarmReceiver"
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getIntExtra(EXTRA_RULE_ID, -1)
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        if (ruleId == -1) return

        Log.d(TAG, "Alarm received: action=$action ruleId=$ruleId")
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_START -> {
                        val rule = repository.getRuleById(ruleId) ?: return@launch
                        if (rule.ruleType == RuleType.SCHEDULED &&
                            !RuleScheduler.isWithinScheduledWindow(rule)
                        ) {
                            OpenBlockScheduler.scheduleRule(context, rule.copy(isActive = false))
                            return@launch
                        }

                        repository.setRuleActive(ruleId, true)

                        if (rule.ruleType == RuleType.SCHEDULED) {
                            OpenBlockScheduler.scheduleRule(context, rule.copy(isActive = true))
                        }
                    }

                    ACTION_STOP -> {
                        val rule = repository.getRuleById(ruleId)
                        repository.setRuleActive(ruleId, false)
                        repository.setTimerStartedAt(ruleId, null)

                        if (rule?.ruleType == RuleType.SCHEDULED) {
                            OpenBlockScheduler.scheduleRule(context, rule.copy(isActive = false))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed handling alarm action=$action ruleId=$ruleId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
