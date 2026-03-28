package com.beoffline.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.scheduler.StartRuleWorker
import com.beoffline.app.vpn.VpnController
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
                        androidx.work.OneTimeWorkRequest.Builder(StartRuleWorker::class.java)
                            .setInputData(androidx.work.workDataOf(StartRuleWorker.KEY_RULE_ID to ruleId))
                            .build()
                            .also { androidx.work.WorkManager.getInstance(context).enqueue(it) }
                    }

                    ACTION_STOP -> {
                        repository.setRuleActive(ruleId, false)
                        repository.setTimerStartedAt(ruleId, null)

                        val remainingRules = repository.getActiveRulesOnce()
                        if (remainingRules.isEmpty()) {
                            vpnController.stopVpn()
                        } else {
                            val remainingPackages = remainingRules.flatMap { it.blockedPackages }.distinct()
                            vpnController.startVpn(remainingPackages)
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
