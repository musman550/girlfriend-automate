package com.musfira.smsai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

class ReplyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val sender = inputData.getString("sender") ?: return Result.failure()
        val body = inputData.getString("body") ?: return Result.failure()
        val sub = inputData.getInt("sub", -1)
        if (!prefs.isActive()) return Result.success()

        val other = if (prefs.provider == Prefs.PROVIDER_GROQ) Prefs.PROVIDER_GEMINI else Prefs.PROVIDER_GROQ
        val hasKey = prefs.keyFor(prefs.provider).isNotBlank() || (prefs.fallbackEnabled && prefs.keyFor(other).isNotBlank())
        val label = ChatRepository.lookupName(applicationContext, sender) ?: sender
        if (!hasKey) {
            log(prefs, "API key set nahi hai")
            prefs.addHistory(label, body, "", "error: API key nahi")
            return Result.failure()
        }
        if (prefs.approveMode && !Notifier.canPost(applicationContext)) {
            log(prefs, "Approve mode ke liye notification permission chahiye")
            prefs.addHistory(label, body, "", "error: notification permission nahi")
            return Result.failure()
        }
        return try {
            if (runAttemptCount == 0) {
                val d = prefs.randomDelaySec()
                if (d > 0) delay(d * 1000L)
                if (!prefs.isActive()) return Result.success()
            }
            val intros = listOf(
                SmsManagerHelper.effectiveIntro(prefs.resolvedIntro()),
                SmsManagerHelper.effectiveIntro(Prefs.DEFAULT_INTRO.replace("{NAME}", prefs.nameOrDefault()))
            )
            val history = ChatRepository.history(applicationContext, sender, 20).map { m ->
                if (!m.fromMe) m else {
                    var t = m.text
                    for (i in intros) if (t.startsWith(i)) { t = t.removePrefix(i).trim(); break }
                    ChatMsg(true, t)
                }
            }.filter { it.text.isNotBlank() }

            val reply = AiService.reply(prefs, history, body)
            val finalText = SmsManagerHelper.compose(prefs.resolvedIntro(), reply)

            if (prefs.approveMode) {
                Notifier.draft(applicationContext, label, body, finalText, sender, sub)
                prefs.addHistory(label, body, finalText, "draft (aap ke approve ka intezar)")
                log(prefs, "Draft tayyar -> $label (notification dekhein)")
            } else {
                SmsManagerHelper.send(applicationContext, sender, finalText, sub)
                prefs.sentCount = prefs.sentCount + 1
                prefs.addHistory(label, body, finalText, "sent")
                log(prefs, "Reply bheja -> $label\n$finalText")
            }
            Result.success()
        } catch (e: ApiHttpException) {
            log(prefs, "API error: ${e.message}")
            if (e.code in 400..499 && e.code != 429) {
                prefs.addHistory(label, body, "", "error: ${e.message}")
                Result.failure()
            } else if (runAttemptCount < 3) Result.retry() else {
                prefs.addHistory(label, body, "", "error: ${e.message}")
                Result.failure()
            }
        } catch (e: Exception) {
            log(prefs, "Error (${e.javaClass.simpleName}): ${e.message}")
            if (runAttemptCount < 3) Result.retry() else {
                prefs.addHistory(label, body, "", "error: ${e.message}")
                Result.failure()
            }
        }
    }

    private fun log(prefs: Prefs, msg: String) {
        val t = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())
        prefs.lastLog = "[$t] $msg"
    }
}
