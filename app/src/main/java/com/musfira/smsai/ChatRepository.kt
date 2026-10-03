package com.musfira.smsai

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony

data class ChatMsg(val fromMe: Boolean, val text: String)

object ChatRepository {

    fun digits(s: String): String = s.filter { it.isDigit() }

    /** Number se (behtar) ya naam se match. Naam ke liye READ_CONTACTS zaroori hai. */
    fun matches(context: Context, sender: String, targets: List<Contact>): Boolean {
        val sDigits = digits(sender)
        val name by lazy { lookupName(context, sender) }
        return targets.any { c ->
            val td = digits(c.number)
            if (td.length >= 7) {
                val n = minOf(10, sDigits.length, td.length)
                n >= 7 && sDigits.takeLast(n) == td.takeLast(n)
            } else if (c.name.isNotBlank()) {
                name?.contains(c.name, ignoreCase = true) == true
            } else false
        }
    }

    fun lookupName(context: Context, number: String): String? = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(
            uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    /** Is number ki aakhri [limit] SMS (inbox + sent), purani se nayi tarteeb mein. */
    fun history(context: Context, sender: String, limit: Int = 20): List<ChatMsg> {
        val tail = digits(sender).takeLast(10)
        if (tail.length < 7) return emptyList()
        val out = mutableListOf<ChatMsg>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY, Telephony.Sms.TYPE),
                "${Telephony.Sms.ADDRESS} LIKE ? AND ${Telephony.Sms.TYPE} IN (?, ?)",
                arrayOf(
                    "%$tail",
                    Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
                    Telephony.Sms.MESSAGE_TYPE_SENT.toString()
                ),
                "${Telephony.Sms.DATE} DESC LIMIT $limit"
            )?.use { c ->
                while (c.moveToNext()) {
                    val body = c.getString(0)?.trim().orEmpty()
                    if (body.isEmpty()) continue
                    out.add(ChatMsg(c.getInt(1) == Telephony.Sms.MESSAGE_TYPE_SENT, body))
                }
            }
        } catch (e: Exception) {
            // Permission na ho to khaali history
        }
        return out.reversed()
    }
}
