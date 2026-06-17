package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ReminderReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "onReceive intent action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val appDao = db.appDao()
                    val activeEnrollments = appDao.getActiveReminderEnrollmentsDirect()
                    Log.d(TAG, "Reinicio detectado. Reprogramando ${activeEnrollments.size} recordatorios...")
                    
                    for (enrollment in activeEnrollments) {
                        try {
                            val activity = appDao.getActivityByIdDirect(enrollment.activityId)
                            if (activity != null) {
                                ReminderScheduler.scheduleReminder(context, activity, enrollment.reminderMinutes)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reprogramando recordatorio individual para id ${enrollment.activityId}", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error general reprogramando alarmas en boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // Standard notification trigger
        val activityTitle = intent.getStringExtra("activity_title") ?: "Actividad de Ecosistemas"
        val activityId = intent.getIntExtra("activity_id", -1)

        val channelId = "je_app_notifications_elegant"
        val channelName = "Recordatorios de Actividades (Elegante)"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Buscar si existe un sonido elegante personalizado en res/raw/elegant_chime
        val soundResId = context.resources.getIdentifier("elegant_chime", "raw", context.packageName)
        val soundUri = if (soundResId != 0) {
            android.net.Uri.parse("android.resource://${context.packageName}/$soundResId")
        } else {
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal para notificaciones con sonido elegante de JE-App"
                enableLights(true)
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            activityId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.example.R.mipmap.ic_launcher)
            .setContentTitle("Recordatorio de Evento 🌿")
            .setContentText("¡Es hora de tu actividad!: $activityTitle")
            .setStyle(NotificationCompat.BigTextStyle().bigText("¡Es hora de tu actividad!: $activityTitle. Revisa los detalles en la aplicación."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)

        try {
            notificationManager.notify(activityId + 10000, builder.build())
            Log.d(TAG, "Notificación de recordatorio enviada para '$activityTitle'")
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al disparar notificación de recordatorio", e)
        }
    }
}
