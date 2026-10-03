package com.musfira.smsai

import org.json.JSONObject

/** Provider se live model list laata hai, taake model band hone par 404 na aaye. */
object ModelCatalog {
    private val groqPreferred = listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b", "qwen/qwen3.6-27b")
    private val geminiPreferred = listOf("gemini-3.5-flash", "gemini-3.1-flash-lite", "gemini-3.6-flash")

    suspend fun list(prefs: Prefs, provider: String): List<String> {
        val key = prefs.keyFor(provider)
        if (key.isBlank()) throw IllegalStateException("Pehle is provider ki API key daalein")
        return if (provider == Prefs.PROVIDER_GEMINI) {
            val resp = ApiClient.getJson(
                "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200",
                null, mapOf("x-goog-api-key" to key)
            )
            val arr = JSONObject(resp).optJSONArray("models") ?: return emptyList()
            val out = mutableListOf<String>()
            val bad = listOf("image", "tts", "live", "embed", "robotics", "audio", "computer", "veo", "imagen")
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val id = m.optString("name").removePrefix("models/")
                val methods = m.optJSONArray("supportedGenerationMethods")
                var ok = false
                if (methods != null) for (j in 0 until methods.length()) if (methods.getString(j) == "generateContent") ok = true
                if (ok && id.startsWith("gemini") && bad.none { id.contains(it) }) out.add(id)
            }
            out.sorted()
        } else {
            val resp = ApiClient.getJson("https://api.groq.com/openai/v1/models", key)
            val arr = JSONObject(resp).optJSONArray("data") ?: return emptyList()
            val out = mutableListOf<String>()
            val bad = listOf("whisper", "tts", "orpheus", "guard", "embed", "compound")
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("id")
                if (id.isNotEmpty() && bad.none { id.contains(it) }) out.add(id)
            }
            out.sorted()
        }
    }

    suspend fun autoPick(prefs: Prefs, provider: String): String? {
        val l = try { list(prefs, provider) } catch (e: Exception) { return null }
        val pref = if (provider == Prefs.PROVIDER_GEMINI) geminiPreferred else groqPreferred
        return pref.firstOrNull { it in l } ?: l.firstOrNull()
    }
}
