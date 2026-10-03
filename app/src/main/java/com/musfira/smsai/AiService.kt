package com.musfira.smsai

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object AiService {

    /** Primary provider -> model band ho to auto naya model -> phir doosra provider (agar key ho). */
    suspend fun reply(prefs: Prefs, history: List<ChatMsg>, incoming: String): String {
        val turns = buildTurns(history, incoming)
        val other = if (prefs.provider == Prefs.PROVIDER_GROQ) Prefs.PROVIDER_GEMINI else Prefs.PROVIDER_GROQ
        val order = if (prefs.fallbackEnabled) listOf(prefs.provider, other) else listOf(prefs.provider)
        var last: Exception? = null
        for (p in order) {
            if (prefs.keyFor(p).isBlank()) continue
            try {
                return attempt(prefs, p, prefs.modelFor(p), turns)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiHttpException) {
                last = e
                if (e.code == 400 || e.code == 404) {
                    val alt = ModelCatalog.autoPick(prefs, p)
                    if (alt != null && alt != prefs.modelFor(p)) {
                        try {
                            val r = attempt(prefs, p, alt, turns)
                            prefs.setModelFor(p, alt)
                            return r
                        } catch (e2: CancellationException) {
                            throw e2
                        } catch (e2: Exception) {
                            last = e2
                        }
                    }
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("API key set nahi hai")
    }

    private suspend fun attempt(prefs: Prefs, provider: String, model: String, turns: List<ChatMsg>): String {
        val raw = if (provider == Prefs.PROVIDER_GEMINI) callGemini(prefs, model, turns) else callGroq(prefs, model, turns)
        val r = clean(raw)
        if (r.isBlank()) throw IOException("AI ka jawab khaali aaya")
        return r
    }

    /** History + naya message; musalsal same-role messages merge. */
    private fun buildTurns(history: List<ChatMsg>, incoming: String): List<ChatMsg> {
        val list = history.toMutableList()
        val seen = list.takeLast(3).any { !it.fromMe && it.text.trim() == incoming.trim() }
        if (!seen) list.add(ChatMsg(false, incoming))
        val merged = mutableListOf<ChatMsg>()
        for (m in list) {
            val p = merged.lastOrNull()
            if (p != null && p.fromMe == m.fromMe) {
                merged[merged.size - 1] = ChatMsg(m.fromMe, p.text + "\n" + m.text)
            } else merged.add(m)
        }
        while (merged.isNotEmpty() && merged.first().fromMe) merged.removeAt(0)
        return merged
    }

    private suspend fun callGroq(prefs: Prefs, model: String, turns: List<ChatMsg>): String {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", prefs.resolvedPersona()))
        for (t in turns) {
            msgs.put(JSONObject().put("role", if (t.fromMe) "assistant" else "user").put("content", t.text))
        }
        val body = JSONObject().put("model", model).put("messages", msgs).put("temperature", 0.7)
        if (model.contains("gpt-oss")) {
            // Reasoning model: sochne mein tokens na khatam hon
            body.put("reasoning_effort", "low").put("max_completion_tokens", 500)
        } else {
            body.put("max_tokens", 160)
        }
        val resp = ApiClient.postJson("https://api.groq.com/openai/v1/chat/completions", body.toString(), prefs.groqKey)
        return JSONObject(resp).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content", "")
    }

    private suspend fun callGemini(prefs: Prefs, model: String, turns: List<ChatMsg>): String {
        val contents = JSONArray()
        for (t in turns) {
            contents.put(
                JSONObject().put("role", if (t.fromMe) "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", t.text)))
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prefs.resolvedPersona()))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 600))
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val resp = ApiClient.postJson(url, body.toString(), null, mapOf("x-goog-api-key" to prefs.geminiKey))
        val parts = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (!p.optBoolean("thought", false)) sb.append(p.optString("text", ""))
        }
        return sb.toString()
    }

    private fun clean(s: String): String =
        s.replace(Regex("[*#`_>~]"), "")
            .replace(Regex("^\\s*[-•]\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("\\s*\\n+\\s*"), " ")
            .trim()
            .trim('"')
            .take(320)
}
