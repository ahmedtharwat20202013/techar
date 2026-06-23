package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.utils.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val dao = db.appDao()
                    
                    val allSessions = dao.getAllSessions().first()
                    Timber.i("BootReceiver loaded ${allSessions.size} sessions from database to reschedule.")
                    
                    for (session in allSessions) {
                        val group = dao.getGroupById(session.groupId)
                        if (group != null) {
                            NotificationScheduler.scheduleSessionAlarm(context, session, group.name)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error rescheduling alarms on boot")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
