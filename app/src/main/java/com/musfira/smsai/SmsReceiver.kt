package com.musfira.smsai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        try {
            val prefs = Prefs(context)
            if (!prefs.isActive()) return

            val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            val sender = msgs.firstOrNull()?.originatingAddress ?: return
            val body = msgs.joinToString("") { it.messageBody ?: "" }.trim()
            if (body.isEmpty()) return

            val targets = prefs.contactList()
            if (targets.isEmpty() || !ChatRepository.matches(context, sender, targets)) return

            val key = ChatRepository.digits(sender).takeLast(10)
            val label = ChatRepository.lookupName(context, sender) ?: sender
            val lower = body.lowercase()

            // 1) Samne wale ne STOP likha ya pehle mute kiya tha
            if (prefs.isMuted(key)) return
            val stops = prefs.stopWords.split(",", "\n").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (lower.trim().trim('.', '!', ' ') in stops) {
                prefs.mute(key)
                prefs.addHistory(label, body, "", "muted (stop word)")
                prefs.lastLog = "$label ne stop likha, unhein mute kar diya."
                return
            }

            // 2) Urgent: AI jawab nahi dega, aap ko notification
            val urgents = prefs.urgentWords.split(",", "\n").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (urgents.any { lower.contains(it) }) {
                prefs.addHistory(label, body, "", "urgent - aap ke liye")
                Notifier.info(context, "Urgent message: $label", body)
                return
            }

            // 3) Active hours
            if (!prefs.inActiveHours()) {
                prefs.addHistory(label, body, "", "skipped (active hours ke bahar)")
                return
            }

            // 4) Cooldown + ghante ki hadd
            val now = System.currentTimeMillis()
            if (now - prefs.lastReplyAt(key) < prefs.cooldownSec * 1000L) return
            if (!prefs.hourlyAllowed()) {
                prefs.lastLog = "Ghante ki reply limit poori ho gayi."
                prefs.addHistory(label, body, "", "skipped (hourly limit)")
                return
            }
            prefs.setLastReplyAt(key, now)

            val sub = intent.getIntExtra(
                "android.telephony.extra.SUBSCRIPTION_INDEX",
                intent.getIntExtra("subscription", -1)
            )
            val req = OneTimeWorkRequestBuilder<ReplyWorker>()
                .setInputData(workDataOf("sender" to sender, "body" to body, "sub" to sub))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(req)
        } catch (e: Exception) {
            // Silent fail: kabhi crash nahi
        }
    }
}
