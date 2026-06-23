package com.example.utils

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.Session
import com.example.receiver.SessionAlarmReceiver
import java.util.Calendar
import java.util.TimeZone
import timber.log.Timber

object NotificationScheduler {

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleSessionAlarm(context: Context, session: Session, groupName: String) {
        val sessionCal = parseSessionDateTime(session.date, session.time)
        if (sessionCal == null) {
            Timber.e("Failed to parse session date/time: ${session.date} ${session.time}")
            return
        }

        // Subtract 15 minutes
        val alarmCal = (sessionCal.clone() as Calendar).apply {
            add(Calendar.MINUTE, -15)
        }

        val now = Calendar.getInstance(TimeZone.getTimeZone("Africa/Cairo"))
        if (alarmCal.before(now)) {
            // Alarm time is already in the past, don't schedule
            Timber.i("Alarm time ${alarmCal.time} is in the past, skipping scheduling.")
            return
        }

        Timber.i("Scheduling alarm for session ${session.id} (Group: $groupName) at ${alarmCal.time}")

        val intent = Intent(context, SessionAlarmReceiver::class.java).apply {
            putExtra("session_id", session.id)
            putExtra("group_id", session.groupId)
            putExtra("group_name", groupName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            session.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmCal.timeInMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmCal.timeInMillis, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmCal.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, alarmCal.timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException while scheduling exact alarm, falling back to inexact.")
            alarmManager.set(AlarmManager.RTC_WAKEUP, alarmCal.timeInMillis, pendingIntent)
        } catch (e: Exception) {
            Timber.e(e, "Error scheduling alarm.")
        }
    }

    fun cancelSessionAlarm(context: Context, sessionId: Int) {
        val intent = Intent(context, SessionAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_NO_CREATE or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Timber.i("Cancelled alarm for session $sessionId")
        }
    }

    private fun parseSessionDateTime(dateStr: String, timeStr: String): Calendar? {
        try {
            val cleanDate = dateStr.replace("/", "-").trim()
            val parts = timeStr.trim().split(" ")
            if (parts.size < 2) return null
            val hm = parts[0].split(":")
            if (hm.size < 2) return null
            val originalHour = hm[0].toIntOrNull() ?: return null
            val minute = hm[1].toIntOrNull() ?: return null
            val amPmMarker = parts[1].trim()

            var isPm = false
            if (amPmMarker.contains("PM", ignoreCase = true) || amPmMarker.contains("م") || amPmMarker.contains("مساءً")) {
                isPm = true
            } else if (amPmMarker.contains("AM", ignoreCase = true) || amPmMarker.contains("ص") || amPmMarker.contains("صباحاً")) {
                isPm = false
            }

            val calendar = Calendar.getInstance(TimeZone.getTimeZone("Africa/Cairo"))
            val dateParts = cleanDate.split("-")
            if (dateParts.size < 3) return null
            val year = dateParts[0].toInt()
            val month = dateParts[1].toInt() - 1 // Calendar months are 0-11
            val day = dateParts[2].toInt()

            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)

            var hourOfDay = originalHour
            if (isPm) {
                if (originalHour < 12) hourOfDay += 12
            } else {
                if (originalHour == 12) hourOfDay = 0
            }

            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
