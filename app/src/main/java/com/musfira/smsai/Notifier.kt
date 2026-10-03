package com.musfira.smsai

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    private const val CHANNEL = "alerts"
    const val ACTION_SEND = "com.musfira.smsai.ACTION_SEND_DRAFT"
    const val ACTION_DISMISS = "com.musfira.smsai.ACTION_DISMISS_DRAFT"

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensure(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Girlfriend Automate alerts", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    @SuppressLint("MissingPermission")
    fun info(ctx: Context, title: String, text: String) {
        if (!canPost(ctx)) return
        ensure(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt(), n)
        } catch (e: SecurityException) { /* ignore */ }
    }

    @SuppressLint("MissingPermission")
    fun draft(ctx: Context, label: String, incoming: String, draft: String, to: String, sub: Int) {
        ensure(ctx)
        val id = (System.currentTimeMillis() % 100000).toInt()
        fun pi(action: String, rc: Int) = PendingIntent.getBroadcast(
            ctx, rc,
            Intent(ctx, ActionReceiver::class.java).setAction(action)
                .putExtra("nid", id).putExtra("to", to).putExtra("text", draft)
                .putExtra("sub", sub).putExtra("label", label).putExtra("incoming", incoming),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = "Unhon ne: $incoming\n\nDraft: $draft"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Reply draft: $label")
            .setContentText(draft)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .addAction(0, "Send", pi(ACTION_SEND, id * 2))
            .addAction(0, "Ignore", pi(ACTION_DISMISS, id * 2 + 1))
            .setAutoCancel(false)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) { /* ignore */ }
    }
}
