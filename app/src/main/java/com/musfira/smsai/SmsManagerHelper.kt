package com.musfira.smsai

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

object SmsManagerHelper {

    /** Intro mein "AI" na ho to "(AI)" khud lag jaata hai. */
    fun effectiveIntro(intro: String): String {
        var i = intro.trim()
        if (i.isEmpty()) i = "Main ek AI assistant hun."
        if (!Regex("(?i)\\bai\\b").containsMatchIn(i)) i = "$i (AI)"
        return i
    }

    fun compose(intro: String, reply: String): String = "${effectiveIntro(intro)} $reply".trim()

    /** subId >= 0 ho to usi SIM se reply jaata hai jis par message aaya (dual SIM). */
    fun send(context: Context, to: String, text: String, subId: Int = -1) {
        val base: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION") SmsManager.getDefault()
        }
        val sm: SmsManager = if (subId >= 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) base.createForSubscriptionId(subId)
            else @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(subId)
        } else base
        val parts = sm.divideMessage(text)
        if (parts.size > 1) {
            sm.sendMultipartTextMessage(to, null, parts, null, null)
        } else {
            sm.sendTextMessage(to, null, text, null, null)
        }
    }
}
