package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class SessionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra("session_id", 0)
        val groupId = intent.getIntExtra("group_id", 0)
        val groupName = intent.getStringExtra("group_name") ?: "المجموعة"

        val channelId = "session_alerts_channel_v2" // Using v2 to force update sound configuration on device
        val notificationId = sessionId xor groupId xor 98765

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val channel = NotificationChannel(
                channelId,
                "تنبيهات الحصص",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "قناة لإرسال إشعارات وتنبيهات قبل بدء الحصص"
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent to open MainActivity (الدخول إلى البرنامج)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("session_id", sessionId)
            putExtra("group_id", groupId)
        }
        
        val pendingOpenIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Notification Builder
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // System alarm icon
            .setContentTitle("تنبيه بدء الحصة الرسمية")
            .setContentText("أهلاً أستاذنا العزيز، سوف تبدأ حصة بعد ربع ساعة من الآن لمجموعة $groupName")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("أهلاً أستاذنا العزيز، سوف تبدأ حصة بعد ربع ساعة من الآن لمجموعة $groupName")
            )
            .setSound(soundUri)
            .setVibrate(longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingOpenIntent)
            .addAction(
                android.R.drawable.ic_menu_directions,
                "الدخول إلى البرنامج",
                pendingOpenIntent
            )

        notificationManager.notify(notificationId, builder.build())
    }
}
