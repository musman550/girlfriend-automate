package com.musfira.smsai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** Approve-mode notification ke Send / Ignore buttons. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        val to = intent.getStringExtra("to") ?: return
        val text = intent.getStringExtra("text") ?: return
        val label = intent.getStringExtra("label") ?: to
        val incoming = intent.getStringExtra("incoming") ?: ""
        val sub = intent.getIntExtra("sub", -1)
        if (intent.action == Notifier.ACTION_SEND) {
            try {
                SmsManagerHelper.send(context, to, text, sub)
                prefs.sentCount = prefs.sentCount + 1
                prefs.addHistory(label, incoming, text, "sent (approved)")
                prefs.lastLog = "Approved reply bheja -> $label"
            } catch (e: Exception) {
                prefs.lastLog = "Send fail: ${e.message}"
                prefs.addHistory(label, incoming, text, "send error")
            }
        } else {
            prefs.addHistory(label, incoming, text, "ignored")
        }
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra("nid", 0))
    }
}
