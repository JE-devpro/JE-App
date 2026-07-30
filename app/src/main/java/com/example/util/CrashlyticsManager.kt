package com.example.util

import android.os.Build
import android.util.Log
import com.example.data.api.FirebaseHelper
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CrashLogEntry(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String = "INFO",
    val message: String = "",
    val exceptionType: String? = null,
    val stackTraceSnippet: String? = null,
    val userId: String? = null,
    val deviceModel: String? = "${Build.MANUFACTURER} ${Build.MODEL}"
)

object CrashlyticsManager {
    private const val TAG = "FirebaseCrashlytics"

    private val _capturedLogs = MutableStateFlow<List<CrashLogEntry>>(emptyList())
    val capturedLogs: StateFlow<List<CrashLogEntry>> = _capturedLogs.asStateFlow()

    private val _isCollectionEnabled = MutableStateFlow(true)
    val isCollectionEnabled: StateFlow<Boolean> = _isCollectionEnabled.asStateFlow()

    private var isCloudListenerAttached = false
    private var isUncaughtHandlerConfigured = false
    private var currentUserId: String = "Usuario"

    /**
     * Initializes cloud sync with Firebase Realtime Database and attaches uncaught exception handler
     * so crash logs from all users automatically upload and sync in real-time across devices.
     */
    fun initCloudSync() {
        setupUncaughtExceptionHandler()

        if (isCloudListenerAttached) return
        try {
            val db = FirebaseHelper.getDatabaseOrNull() ?: return
            val logsRef = db.getReference("crash_logs")
            logsRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val logs = mutableListOf<CrashLogEntry>()
                    for (child in snapshot.children) {
                        try {
                            val id = child.key ?: ""
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                            val tag = child.child("tag").getValue(String::class.java) ?: "INFO"
                            val message = child.child("message").getValue(String::class.java) ?: ""
                            val exceptionType = child.child("exceptionType").getValue(String::class.java)
                            val stackTraceSnippet = child.child("stackTraceSnippet").getValue(String::class.java)
                            val userId = child.child("userId").getValue(String::class.java)
                            val deviceModel = child.child("deviceModel").getValue(String::class.java)

                            logs.add(
                                CrashLogEntry(
                                    id = id,
                                    timestamp = timestamp,
                                    tag = tag,
                                    message = message,
                                    exceptionType = exceptionType,
                                    stackTraceSnippet = stackTraceSnippet,
                                    userId = userId,
                                    deviceModel = deviceModel
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error deserializing cloud crash entry: ${e.message}")
                        }
                    }
                    // Sort newest first
                    logs.sortByDescending { it.timestamp }
                    _capturedLogs.value = logs
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Cloud crash listener cancelled: ${error.message}")
                }
            })
            isCloudListenerAttached = true
            Log.d(TAG, "Firebase Crashlytics Realtime Cloud Synchronization active.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Crashlytics cloud synchronization: ${e.message}")
        }
    }

    private fun setupUncaughtExceptionHandler() {
        if (isUncaughtHandlerConfigured) return
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordException(throwable, "Cierre no controlado automáticamente detectado en '${thread.name}'")
            } catch (e: Exception) {
                Log.e(TAG, "Error in uncaught exception handler: ${e.message}")
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
        isUncaughtHandlerConfigured = true
        Log.d(TAG, "Uncaught Exception Handler configured for automatic telemetry collection.")
    }

    fun log(message: String) {
        Log.d(TAG, message)
        try {
            FirebaseCrashlytics.getInstance().log(message)
        } catch (e: Throwable) {
            // Firebase app missing or not initialized in preview
        }
        val entry = CrashLogEntry(
            tag = "INFO",
            message = message,
            userId = currentUserId
        )
        addLocalLog(entry)
        syncLogToCloud(entry)
    }

    fun recordException(throwable: Throwable, extraMessage: String? = null) {
        val msg = extraMessage ?: throwable.localizedMessage ?: "Excepción no manejada"
        Log.e(TAG, "RecordException: $msg", throwable)
        
        try {
            FirebaseCrashlytics.getInstance().apply {
                if (extraMessage != null) {
                    log(extraMessage)
                }
                recordException(throwable)
            }
        } catch (e: Throwable) {
            // Firebase exception fallback
        }

        val snippet = throwable.stackTrace.take(5).joinToString("\n") { "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
        val entry = CrashLogEntry(
            tag = "FATAL/NON-FATAL",
            message = msg,
            exceptionType = throwable.javaClass.simpleName,
            stackTraceSnippet = snippet,
            userId = currentUserId
        )
        addLocalLog(entry)
        syncLogToCloud(entry)
    }

    fun setUserId(userId: String) {
        currentUserId = userId
        try {
            FirebaseCrashlytics.getInstance().setUserId(userId)
        } catch (e: Throwable) {
        }
    }

    fun setCustomKey(key: String, value: String) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (e: Throwable) {
        }
    }

    fun setCollectionEnabled(enabled: Boolean) {
        _isCollectionEnabled.value = enabled
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        } catch (e: Throwable) {
        }
        log("Crashlytics recopilación ${if (enabled) "activada" else "desactivada"}")
    }

    fun triggerTestCrash(): String {
        val testMessage = "Fallo de prueba manual de Crashlytics generado desde el panel de administración"
        val ex = RuntimeException(testMessage)
        recordException(ex, "Prueba de Diagnóstico Firebase Crashlytics")
        return "Prueba de error enviada a Firebase Crashlytics y sincronizada con la nube exitosamente."
    }

    fun clearLocalLogs() {
        _capturedLogs.value = emptyList()
        try {
            val db = FirebaseHelper.getDatabaseOrNull()
            db?.getReference("crash_logs")?.removeValue()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cloud logs: ${e.message}")
        }
    }

    private fun addLocalLog(entry: CrashLogEntry) {
        val current = _capturedLogs.value.toMutableList()
        current.add(0, entry) // Newest first
        if (current.size > 100) {
            current.removeAt(current.size - 1)
        }
        _capturedLogs.value = current
    }

    private fun syncLogToCloud(entry: CrashLogEntry) {
        if (!_isCollectionEnabled.value) return
        try {
            val db = FirebaseHelper.getDatabaseOrNull() ?: return
            val ref = db.getReference("crash_logs").push()
            val entryWithKey = entry.copy(id = ref.key ?: "")
            ref.setValue(entryWithKey)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push crash entry to RTDB: ${e.message}")
        }
    }
}

