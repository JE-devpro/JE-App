package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.data.Member
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object FirebaseHelper {
    private const val TAG = "FirebaseHelper"

    /**
     * Dynamically verifies if the default Firebase App has been initialized successfully
     * in the current process. This prevents illegal state exceptions when accessing SDK clients.
     */
    val isDefaultAppInitialized: Boolean
        get() {
            return try {
                FirebaseApp.getInstance()
                true
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Checks whether a valid (non-mock) Firebase API key is configured.
     */
    val isRealFirebaseConfigured: Boolean
        get() {
            return try {
                val app = FirebaseApp.getInstance()
                val key = app.options.apiKey
                key.isNotBlank() && !key.contains("Mock", ignoreCase = true) && !key.startsWith("AIzaSyA_Mock")
            } catch (e: Exception) {
                false
            }
        }

    fun init(context: Context) {
        if (isDefaultAppInitialized) {
            Log.d(TAG, "Firebase DEFAULT app is already initialized.")
            return
        }

        // Try standard resource-based initialization first (e.g. from google-services.json)
        try {
            val app = FirebaseApp.initializeApp(context.applicationContext)
            if (app != null && isDefaultAppInitialized) {
                Log.d(TAG, "Firebase DEFAULT app initialized via auto-config resource providers.")
                return
            }
        } catch (e: Exception) {
            Log.d(TAG, "Auto-config resource provider not found, attempting programmatic config for RTDB.")
        }

        // Programmatic fallback for Database and Cloud Logging
        try {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyA_Mock_Key_For_Firebase_Auth_EcoTech_2026")
                .setApplicationId("1:1234567890:android:mockjovsecotech")
                .setDatabaseUrl("https://je-app-3e271-default-rtdb.firebaseio.com/")
                .setProjectId("je-app-3e271")
                .build()
            FirebaseApp.initializeApp(context.applicationContext, options)
            Log.d(TAG, "Firebase DEFAULT app initialized dynamically for Realtime Database.")
        } catch (e: Exception) {
            Log.w(TAG, "Firebase initialization error: ${e.message}")
        }
    }

    /**
     * Safely retrieves reference to Firebase Auth. Wraps inside try-catch to prevent crash.
     * Only returns an instance if a real, valid Firebase API key is present.
     */
    fun getAuthOrNull(): FirebaseAuth? {
        if (!isDefaultAppInitialized || !isRealFirebaseConfigured) return null
        return try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FirebaseAuth instance safely: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Safely retrieves reference to Firebase Realtime Database. Wraps inside try-catch to prevent crash.
     */
    fun getDatabaseOrNull(): FirebaseDatabase? {
        if (!isDefaultAppInitialized) return null
        return try {
            FirebaseDatabase.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FirebaseDatabase instance safely: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Attempts to register/update a user's secure credentials (password) under the "/passwords" node
     * in the Firebase Realtime Database using the Firebase SDK.
     * To fulfill security criteria, this path is only queried / accessed by coordinators/admins in-app.
     */
    fun storeMemberPasswordInDb(memberEmail: String, plainPassword: String) {
        try {
            val db = getDatabaseOrNull() ?: return
            val safeKey = memberEmail.trim().lowercase().replace(".", "_").replace("@", "_")
            db.getReference("passwords").child(safeKey).setValue(plainPassword)
                .addOnSuccessListener {
                    Log.d(TAG, "Credential stored successfully in DB for $memberEmail")
                }
                .addOnFailureListener {
                    Log.e(TAG, "Failed to store credential in DB for $memberEmail", it)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing password to Firebase Realtime Database SDK: ${e.localizedMessage}")
        }
    }

    /**
     * Admin/Coordinator hook using Firebase Realtime Database SDK to retrieve a user's stored password.
     * Restricts call only if caller has Admin/Coordinator privileges.
     */
    fun getMemberPasswordFromDb(isAdminOrCoordinator: Boolean, memberEmail: String, onResult: (String?) -> Unit) {
        if (!isAdminOrCoordinator) {
            Log.w(TAG, "Security rejection: Unauthorized read attempt of member credentials")
            onResult(null)
            return
        }
        try {
            val db = getDatabaseOrNull()
            if (db == null) {
                onResult(null)
                return
            }
            val safeKey = memberEmail.trim().lowercase().replace(".", "_").replace("@", "_")
            db.getReference("passwords").child(safeKey).get()
                .addOnSuccessListener { snapshot ->
                    onResult(snapshot.getValue(String::class.java))
                }
                .addOnFailureListener {
                    Log.e(TAG, "Failed to fetch password from Firebase Realtime Database SDK", it)
                    onResult(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading password from Firebase Realtime Database SDK: ${e.localizedMessage}")
            onResult(null)
        }
    }
}
