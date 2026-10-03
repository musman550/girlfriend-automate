package com.musfira.smsai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.random.Random

data class Contact(val name: String, val number: String) {
    fun label(): String = when {
        name.isNotBlank() && number.isNotBlank() -> "$name · $number"
        name.isNotBlank() -> name
        else -> number
    }
}

/** Saari settings yahan. Naya setting: yahan property add karein. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    init {
        // Purani version ki single API key ko naye format mein le aao
        val legacy = sp.getString("apiKey", "") ?: ""
        if (legacy.isNotBlank() && !sp.contains("groqKey") && !sp.contains("geminiKey")) {
            val e = sp.edit()
            if (sp.getString("provider", PROVIDER_GROQ) == PROVIDER_GEMINI) e.putString("geminiKey", legacy)
            else e.putString("groqKey", legacy)
            e.apply()
        }
    }

    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun putI(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    private fun putB(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    var enabled: Boolean get() = sp.getBoolean("enabled", false); set(v) = putB("enabled", v)
    var contacts: String get() = str("contacts", ""); set(v) = put("contacts", v)
    var provider: String get() = str("provider", PROVIDER_GROQ); set(v) = put("provider", v)
    var groqKey: String get() = str("groqKey", ""); set(v) = put("groqKey", v)
    var geminiKey: String get() = str("geminiKey", ""); set(v) = put("geminiKey", v)
    var groqModel: String get() = str("groqModel2", DEFAULT_GROQ_MODEL); set(v) = put("groqModel2", v)
    var geminiModel: String get() = str("geminiModel2", DEFAULT_GEMINI_MODEL); set(v) = put("geminiModel2", v)
    var fallbackEnabled: Boolean get() = sp.getBoolean("fallback", true); set(v) = putB("fallback", v)
    var ownerName: String get() = str("ownerName", ""); set(v) = put("ownerName", v)
    var intro: String get() = str("intro", DEFAULT_INTRO); set(v) = put("intro", v)
    var persona: String get() = str("persona", DEFAULT_PERSONA); set(v) = put("persona", v)
    var approveMode: Boolean get() = sp.getBoolean("approve", false); set(v) = putB("approve", v)
    var cooldownSec: Int get() = sp.getInt("cooldownSec", 20); set(v) = putI("cooldownSec", v)
    var maxPerHour: Int get() = sp.getInt("maxPerHour", 10); set(v) = putI("maxPerHour", v)
    var delayMin: Int get() = sp.getInt("delayMin", 5); set(v) = putI("delayMin", v)
    var delayMax: Int get() = sp.getInt("delayMax", 20); set(v) = putI("delayMax", v)
    var useHours: Boolean get() = sp.getBoolean("useHours", false); set(v) = putB("useHours", v)
    var startHour: Int get() = sp.getInt("startHour", 22); set(v) = putI("startHour", v)
    var endHour: Int get() = sp.getInt("endHour", 8); set(v) = putI("endHour", v)
    var stopWords: String get() = str("stopWords", "stop, unsubscribe"); set(v) = put("stopWords", v)
    var urgentWords: String get() = str("urgentWords", "urgent, emergency, hospital, accident, ambulance"); set(v) = put("urgentWords", v)
    var pausedUntil: Long get() = sp.getLong("pausedUntil", 0L); set(v) = sp.edit().putLong("pausedUntil", v).apply()
    var sentCount: Int get() = sp.getInt("sentCount", 0); set(v) = putI("sentCount", v)
    var lastLog: String get() = str("lastLog", "Abhi tak koi activity nahi."); set(v) = put("lastLog", v)

    fun nameOrDefault(): String = ownerName.trim().ifBlank { "mere malik" }
    fun resolvedIntro(): String = intro.replace("{NAME}", nameOrDefault())
    fun resolvedPersona(): String = persona.replace("{NAME}", nameOrDefault())

    fun keyFor(p: String) = if (p == PROVIDER_GEMINI) geminiKey else groqKey
    fun modelFor(p: String) = (if (p == PROVIDER_GEMINI) geminiModel else groqModel).ifBlank {
        if (p == PROVIDER_GEMINI) DEFAULT_GEMINI_MODEL else DEFAULT_GROQ_MODEL
    }
    fun setModelFor(p: String, m: String) { if (p == PROVIDER_GEMINI) geminiModel = m else groqModel = m }

    fun lastReplyAt(key: String): Long = sp.getLong("last_$key", 0L)
    fun setLastReplyAt(key: String, t: Long) = sp.edit().putLong("last_$key", t).apply()

    fun isActive(): Boolean = enabled && System.currentTimeMillis() >= pausedUntil

    // ---- Contacts ----
    fun contactList(): List<Contact> =
        contacts.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            .flatMap { if ("|" in it) listOf(it) else it.split(",") }
            .map { it.trim() }.filter { it.isNotEmpty() }
            .map { line ->
                if ("|" in line) {
                    val parts = line.split("|", limit = 2)
                    Contact(parts[0].trim(), parts[1].trim())
                } else if (line.none { it.isLetter() } && line.count { it.isDigit() } >= 7) Contact("", line)
                else Contact(line, "")
            }

    fun setContacts(list: List<Contact>) {
        contacts = list.joinToString("\n") { "${it.name}|${it.number}" }
    }

    // ---- Mute (recipient ne STOP likha) ----
    private fun muted(): MutableSet<String> = sp.getStringSet("muted", emptySet())!!.toMutableSet()
    fun mute(key: String) = sp.edit().putStringSet("muted", muted().apply { add(key) }).apply()
    fun isMuted(key: String) = key in muted()
    fun mutedCount() = muted().size
    fun clearMuted() = sp.edit().remove("muted").apply()

    // ---- Hourly cap ----
    fun hourlyAllowed(): Boolean {
        val now = System.currentTimeMillis()
        var start = sp.getLong("hourStart", 0L)
        var cnt = sp.getInt("hourCount", 0)
        if (now - start > 3_600_000L) { start = now; cnt = 0 }
        if (cnt >= maxPerHour) return false
        sp.edit().putLong("hourStart", start).putInt("hourCount", cnt + 1).apply()
        return true
    }

    fun inActiveHours(): Boolean {
        if (!useHours) return true
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            startHour == endHour -> true
            startHour < endHour -> h in startHour until endHour
            else -> h >= startHour || h < endHour
        }
    }

    fun randomDelaySec(): Int {
        val lo = delayMin.coerceAtLeast(0)
        val hi = delayMax.coerceAtLeast(lo)
        return if (hi == 0) 0 else Random.nextInt(lo, hi + 1)
    }

    // ---- History ----
    fun addHistory(contact: String, incoming: String, outgoing: String, status: String) {
        val list = mutableListOf(
            JSONObject().put("t", System.currentTimeMillis()).put("c", contact)
                .put("i", incoming.take(160)).put("o", outgoing.take(320)).put("s", status)
        )
        try {
            val arr = JSONArray(str("history", "[]"))
            for (i in 0 until arr.length()) if (list.size < 50) list.add(arr.getJSONObject(i))
        } catch (e: Exception) { /* purani history kharab ho to ignore */ }
        put("history", JSONArray(list).toString())
    }

    fun historyText(): String {
        return try {
            val arr = JSONArray(str("history", "[]"))
            if (arr.length() == 0) return "Abhi koi history nahi."
            val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            val sb = StringBuilder()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                sb.append("[").append(df.format(Date(o.getLong("t")))).append("] ")
                    .append(o.getString("s").uppercase()).append(" - ").append(o.getString("c")).append("\n")
                if (o.getString("i").isNotEmpty()) sb.append("Unhon ne: ").append(o.getString("i")).append("\n")
                if (o.getString("o").isNotEmpty()) sb.append("Reply: ").append(o.getString("o")).append("\n")
                sb.append("\n")
            }
            sb.toString()
        } catch (e: Exception) { "History parh nahi saka." }
    }

    fun clearHistory() = sp.edit().remove("history").apply()

    companion object {
        const val PROVIDER_GROQ = "groq"
        const val PROVIDER_GEMINI = "gemini"
        // Purane llama-3.1-8b-instant ko Groq ne 16 Aug 2026 ko band kar diya; Gemini 2.0 bhi band.
        const val DEFAULT_GROQ_MODEL = "openai/gpt-oss-20b"
        const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash"
        const val DEFAULT_INTRO = "Main {NAME} ka AI assistant hun, {NAME} abhi busy hain."
        const val DEFAULT_PERSONA = """Tum {NAME} ke AI assistant ho aur SMS par unki taraf se jawab dete ho jab wo available nahi hote.
Zaban: natural Roman Urdu, bilkul waisi hi jaisi purani chat mein {NAME} ke messages ki style, spelling aur short chat tone hai.
Rules:
- Jawab chhota rakho: 1-2 jumle maximum.
- Koi paragraph, bullet point, markdown ya formal essay nahi.
- Tone chat history ke mutabiq rakho (casual, caring, friendly).
- Khud ko insaan zahir mat karo aur AI hone se inkar mat karo.
- Koi wada, paisay ka maamla ya private maloomat mat do. Zaroori ya serious baat ho to kaho ke {NAME} jald khud jawab denge."""
    }
}
