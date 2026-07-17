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
import java.util.Locale
import java.util.TimeZone
import timber.log.Timber

object NotificationScheduler {

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleSessionAlarm(context: Context, session: Session, groupName: String) {
        val sessionCal = parseSessionDateTime(session.date, session.time)
        if (sessionCal == null) {
            Timber.e("Failed to parse session date/time for alarm scheduling: ${session.date} ${session.time}")
            return
        }

        val alarmCal = sessionCal.clone() as Calendar
        alarmCal.add(Calendar.MINUTE, -15)

        val now = Calendar.getInstance() // Defaults to device timezone
        if (alarmCal.before(now)) {
            if (sessionCal.after(now)) {
                // Session is in the future but less than 15 minutes from now. Fire warning alarm in 2 seconds.
                alarmCal.timeInMillis = now.timeInMillis + 2000
                Timber.i("Alarm time was in the past, but session is in the future. Scheduling in 2 seconds.")
            } else {
                // Alarm time is already in the past, don't schedule
                Timber.i("Alarm time ${alarmCal.time} is in the past, skipping scheduling.")
                return
            }
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

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleTestAlarm(context: Context, groupName: String, delaySeconds: Int = 3) {
        val now = Calendar.getInstance()
        val alarmCal = (now.clone() as Calendar).apply {
            add(Calendar.SECOND, delaySeconds)
        }

        Timber.i("Scheduling TEST alarm for Group: $groupName in $delaySeconds seconds")

        val intent = Intent(context, SessionAlarmReceiver::class.java).apply {
            putExtra("session_id", 9999)
            putExtra("group_id", 9999)
            putExtra("group_name", groupName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9999,
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
        } catch (e: Exception) {
            Timber.e(e, "Error scheduling test alarm: ${e.message}")
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

    fun getAlarmCalendar(dateStr: String, timeStr: String): Calendar? {
        val sessionCal = parseSessionDateTime(dateStr, timeStr) ?: return null
        
        // Subtract 15 minutes for the warning alarm
        val alarmCal = sessionCal.clone() as Calendar
        alarmCal.add(Calendar.MINUTE, -15)
        return alarmCal
    }

    private fun normalizeArabicDigits(str: String): String {
        var result = str
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        for (i in 0..9) {
            result = result.replace(arabicDigits[i], ('0' + i))
        }
        return result
    }

    private fun parseSessionDateTime(dateStr: String, timeStr: String): Calendar? {
        try {
            val cleanDate = dateStr.replace("/", "-").trim()
            val cleanTime = normalizeArabicDigits(timeStr).lowercase(Locale.ENGLISH).trim()

            var isPm: Boolean? = null
            if (cleanTime.contains("pm") || cleanTime.contains("p.m.") || cleanTime.contains("م") || cleanTime.contains("مساء")) {
                isPm = true
            } else if (cleanTime.contains("am") || cleanTime.contains("a.m.") || cleanTime.contains("ص") || cleanTime.contains("صباح")) {
                isPm = false
            }

            val timePattern = Regex("(\\d{1,2})\\s*:\\s*(\\d{2})")
            val matchResult = timePattern.find(cleanTime) ?: return null
            val originalHour = matchResult.groupValues[1].toIntOrNull() ?: return null
            val minute = matchResult.groupValues[2].toIntOrNull() ?: return null

            val calendar = Calendar.getInstance() // Defaults to device local timezone
            val dateParts = cleanDate.split("-")
            if (dateParts.size < 3) return null
            
            val year: Int
            val month: Int
            val day: Int
            if (dateParts[0].length == 4) {
                // yyyy-MM-dd
                year = dateParts[0].toInt()
                month = dateParts[1].toInt() - 1
                day = dateParts[2].toInt()
            } else {
                // dd-MM-yyyy
                day = dateParts[0].toInt()
                month = dateParts[1].toInt() - 1
                year = dateParts[2].toInt()
            }

            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)

            var hourOfDay = originalHour
            if (isPm != null) {
                if (isPm) {
                    if (originalHour < 12) hourOfDay += 12
                } else {
                    if (originalHour == 12) hourOfDay = 0
                }
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
