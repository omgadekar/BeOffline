package com.beoffline.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.scheduler.RuleScheduler
import com.beoffline.app.vpn.VpnController
import com.beoffline.app.vpn.VpnResilienceScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScheduleAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: BlockRuleRepository

    @Inject
    lateinit var vpnController: VpnController

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
                            RuleScheduler.scheduleRule(context, rule.copy(isActive = false))
                            return@launch
                        }

                        repository.setRuleActive(ruleId, true)

                        // Only the rules whose window (or timer) is live right
                        // now belong in the tunnel — see RuleScheduler.isEnforcingNow.
                        val enforced = RuleScheduler.enforcedPackages(repository.getActiveRulesOnce())
                        if (enforced.isNotEmpty()) {
                            VpnResilienceScheduler.ensureHealthMonitor(context)
                            vpnController.startVpn(enforced)
                        }

                        if (rule.ruleType == RuleType.SCHEDULED) {
                            RuleScheduler.scheduleRule(context, rule.copy(isActive = true))
                        }
                    }

                    ACTION_STOP -> {
                        val rule = repository.getRuleById(ruleId)
                        repository.setRuleActive(ruleId, false)
                        repository.setTimerStartedAt(ruleId, null)

                        val remaining = RuleScheduler.enforcedPackages(repository.getActiveRulesOnce())
                        if (remaining.isEmpty()) {
                            VpnResilienceScheduler.cancelHealthMonitor(context)
                            vpnController.stopVpn()
                        } else {
                            VpnResilienceScheduler.ensureHealthMonitor(context)
                            vpnController.startVpn(remaining)
                        }

                        if (rule?.ruleType == RuleType.SCHEDULED) {
                            RuleScheduler.scheduleRule(context, rule.copy(isActive = false))
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
