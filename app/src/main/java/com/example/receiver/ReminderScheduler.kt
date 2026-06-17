package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.EcoActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"

    fun scheduleReminder(context: Context, activity: EcoActivity, minutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("activity_title", activity.title)
            putExtra("activity_id", activity.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            activity.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel previous if any
        alarmManager.cancel(pendingIntent)

        val eventTimeMs = parseActivityDateTime(activity.date)
        if (eventTimeMs == null) {
            Log.e(TAG, "No se pudo parsear la fecha del evento: ${activity.date}")
            return
        }

        var triggerTimeMs = eventTimeMs - (minutes * 60 * 1000)
        val nowMs = System.currentTimeMillis()

        if (triggerTimeMs <= nowMs) {
            if (eventTimeMs > nowMs) {
                // Event is in the future, but reminder time passed. Trigger in 5 seconds as catch-up.
                triggerTimeMs = nowMs + 5000
                Log.d(TAG, "La hora del recordatorio ya había pasado. Programando aviso de puesta al día en 5 segundos.")
            } else {
                Log.w(TAG, "La fecha del recordatorio ($triggerTimeMs) y el evento ($eventTimeMs) ya pasaron en relación con el tiempo actual ($nowMs). No se programará.")
                return
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            }
            Log.d(TAG, "Recordatorio programado con éxito para '${activity.title}' en epoch $triggerTimeMs ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error programando alarma, intentando programar alarma inexacta", e)
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Error al programar alarma alternativa", ex)
            }
        }
    }

    fun cancelReminder(context: Context, activity: EcoActivity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            activity.id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Recordatorio cancelado con éxito para '${activity.title}'")
        }
    }

    private fun parseActivityDateTime(dateStr: String): Long? {
        return com.example.util.TimezoneHelper.parseEcuadorDateTime(dateStr)
    }
}
