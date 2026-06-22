package com.example.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.BuildConfig
import com.example.data.*
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.api.CloudSyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository

    val allActivities: StateFlow<List<EcoActivity>>
    val carnetProfile: StateFlow<CarnetProfile?>
    val allArticles: StateFlow<List<EcoArticle>>
    val allMembers: StateFlow<List<Member>>
    val allEnrollments: StateFlow<List<EcoEnrollment>>
    val allNotifications: StateFlow<List<EcoNotification>>

    // Current Session State
    private val _loggedInMember = MutableStateFlow<Member?>(null)
    val loggedInMember: StateFlow<Member?> = _loggedInMember.asStateFlow()

    private val _adminMembersList = MutableStateFlow<List<Member>>(emptyList())
    val adminMembersList: StateFlow<List<Member>> = _adminMembersList.asStateFlow()

    private val _isOverrideCoordinator = MutableStateFlow(false)
    val isOverrideCoordinator: StateFlow<Boolean> = _isOverrideCoordinator.asStateFlow()

    fun setOverrideCoordinator(enabled: Boolean) {
        _isOverrideCoordinator.value = enabled
    }

    private fun checkIfAdmin(): Boolean {
        val member = _loggedInMember.value ?: return _isOverrideCoordinator.value
        return member.isAdmin || 
               member.email.trim().lowercase() == "coordinador@je.org" || 
               _isOverrideCoordinator.value
    }

    // Google Calendar Sync State (Tied dynamically to the active logged-in member)
    val googleCalendarLinked: StateFlow<Boolean> = _loggedInMember
        .map { it?.isGoogleCalendarLinked ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val googleCalendarAccount: StateFlow<String> = _loggedInMember
        .map { it?.googleCalendarEmail ?: it?.email ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // Cloud Synchronization State
    sealed interface SyncState {
        object Idle : SyncState
        object Syncing : SyncState
        data class Success(val message: String) : SyncState
        data class Error(val message: String) : SyncState
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Login UI State Interface
    sealed interface LoginUiState {
        object Idle : LoginUiState
        object Loading : LoginUiState
        data class Success(val message: String) : LoginUiState
        data class Error(val message: String) : LoginUiState
    }

    private val _loginUiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    fun resetLoginUiState() {
        _loginUiState.value = LoginUiState.Idle
    }

    private val sharedPrefs = application.getSharedPreferences("je_app_prefs", Context.MODE_PRIVATE)

    private val defaultUrl = "https://je-app-3e271-default-rtdb.firebaseio.com/"

    private val _cloudSyncUrl = MutableStateFlow(defaultUrl)
    val cloudSyncUrl: StateFlow<String> = _cloudSyncUrl.asStateFlow()

    private val _cloudSecurityKey = MutableStateFlow("JE-Organizacion-EcoTech-2026")
    val cloudSecurityKey: StateFlow<String> = _cloudSecurityKey.asStateFlow()

    fun loadUserPreferences(email: String?) {
        val suffix = if (email.isNullOrBlank()) "" else "_${email.trim().lowercase()}"
        _cloudSyncUrl.value = sharedPrefs.getString("pref_cloud_sync_url", defaultUrl) ?: defaultUrl
        _cloudSecurityKey.value = sharedPrefs.getString("pref_cloud_security_key", "JE-Organizacion-EcoTech-2026") ?: "JE-Organizacion-EcoTech-2026"
        com.example.data.api.CloudSyncClient.cloudSecurityKey = _cloudSecurityKey.value

        _prefNotifActividades.value = sharedPrefs.getBoolean("pref_notif_actividades$suffix", sharedPrefs.getBoolean("pref_notif_actividades", true))
        _prefNotifNovedades.value = sharedPrefs.getBoolean("pref_notif_novedades$suffix", sharedPrefs.getBoolean("pref_notif_novedades", true))
        _prefNotifNube.value = sharedPrefs.getBoolean("pref_notif_nube$suffix", sharedPrefs.getBoolean("pref_notif_nube", true))
        _prefNotifSistema.value = sharedPrefs.getBoolean("pref_notif_sistema$suffix", sharedPrefs.getBoolean("pref_notif_sistema", true))
        _prefTheme.value = sharedPrefs.getString("pref_theme$suffix", "light") ?: "light"
    }

    fun updateCloudSyncUrl(newUrl: String) {
        sharedPrefs.edit().putString("pref_cloud_sync_url", newUrl).apply()
        _cloudSyncUrl.value = newUrl
    }

    fun updateCloudSecurityKey(newKey: String) {
        sharedPrefs.edit().putString("pref_cloud_security_key", newKey).apply()
        _cloudSecurityKey.value = newKey
        com.example.data.api.CloudSyncClient.cloudSecurityKey = newKey
    }

    private fun getTombstones(email: String? = _loggedInMember.value?.email): MutableSet<String> {
        val suffix = if (email.isNullOrBlank()) "" else "_${email.trim().lowercase()}"
        return sharedPrefs.getStringSet("sync_tombstones$suffix", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    }

    private fun addTombstone(key: String, email: String? = _loggedInMember.value?.email) {
        val current = getTombstones(email)
        current.add(key)
        val suffix = if (email.isNullOrBlank()) "" else "_${email.trim().lowercase()}"
        sharedPrefs.edit().putStringSet("sync_tombstones$suffix", current).apply()
    }

    private fun removeTombstone(key: String, email: String? = _loggedInMember.value?.email) {
        val current = getTombstones(email)
        if (current.remove(key)) {
            val suffix = if (email.isNullOrBlank()) "" else "_${email.trim().lowercase()}"
            sharedPrefs.edit().putStringSet("sync_tombstones$suffix", current).apply()
        }
    }

    private val _prefTheme = MutableStateFlow("light")
    val prefTheme = _prefTheme.asStateFlow()

    private val _prefNotifActividades = MutableStateFlow(true)
    val prefNotifActividades = _prefNotifActividades.asStateFlow()

    private val _prefNotifNovedades = MutableStateFlow(true)
    val prefNotifNovedades = _prefNotifNovedades.asStateFlow()

    private val _prefNotifNube = MutableStateFlow(true)
    val prefNotifNube = _prefNotifNube.asStateFlow()

    private val _prefNotifSistema = MutableStateFlow(true)
    val prefNotifSistema = _prefNotifSistema.asStateFlow()

    fun getLocalPreferences(): com.example.data.CloudPreferences {
        val email = _loggedInMember.value?.email
        val suffix = if (email.isNullOrBlank()) "" else "_${email.trim().lowercase()}"
        val lastUpdated = sharedPrefs.getLong("pref_last_updated$suffix", 0L)
        return com.example.data.CloudPreferences(
            prefTheme = _prefTheme.value,
            prefNotifActividades = _prefNotifActividades.value,
            prefNotifNovedades = _prefNotifNovedades.value,
            prefNotifNube = _prefNotifNube.value,
            prefNotifSistema = _prefNotifSistema.value,
            lastUpdated = lastUpdated
        )
    }

    fun applyCloudPreferences(prefs: com.example.data.CloudPreferences) {
        val email = _loggedInMember.value?.email
        val suffix = if (email.isNullOrBlank()) "" else "_${email.trim().lowercase()}"
        sharedPrefs.edit().apply {
            putString("pref_theme$suffix", prefs.prefTheme)
            putBoolean("pref_notif_actividades$suffix", prefs.prefNotifActividades)
            putBoolean("pref_notif_novedades$suffix", prefs.prefNotifNovedades)
            putBoolean("pref_notif_nube$suffix", prefs.prefNotifNube)
            putBoolean("pref_notif_sistema$suffix", prefs.prefNotifSistema)
            putLong("pref_last_updated$suffix", prefs.lastUpdated)
        }.apply()
        _prefTheme.value = prefs.prefTheme
        _prefNotifActividades.value = prefs.prefNotifActividades
        _prefNotifNovedades.value = prefs.prefNotifNovedades
        _prefNotifNube.value = prefs.prefNotifNube
        _prefNotifSistema.value = prefs.prefNotifSistema
    }

    fun uploadPrefsToCloud() {
        val url = _cloudSyncUrl.value
        val email = _loggedInMember.value?.email
        if (url.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    com.example.data.api.CloudSyncClient.uploadPreferences(url, getLocalPreferences(), email)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to upload preferences to cloud: ${e.localizedMessage}")
                }
            }
        }
    }

    fun updatePrefTheme(theme: String) {
        val email = _loggedInMember.value?.email
        val suffix = if (email.isNullOrBlank()) "" else "_${email.trim().lowercase()}"
        val now = System.currentTimeMillis()
        sharedPrefs.edit().apply {
            putString("pref_theme$suffix", theme)
            putLong("pref_last_updated$suffix", now)
        }.apply()
        _prefTheme.value = theme
        uploadPrefsToCloud()
    }

    fun updatePrefNotifActividades(enabled: Boolean) {
        val email = _loggedInMember.value?.email
        val suffix = if (email.isNullOrBlank()) "" else "_${email.trim().lowercase()}"
        val now = System.currentTimeMillis()
        sharedPrefs.edit().apply {
            putBoolean("pref_notif_actividades$suffix", enabled)
            putLong("pref_last_updated$suffix", now)
        }.apply()
        _prefNotifActividades.value = enabled
        uploadPrefsToCloud()
    }

    fun updatePrefNotifNovedades(enabled: Boolean) {
        val email = _loggedInMember.value?.email
        val suffix = if (email.isNullOrBlank()) "" else "_${email.trim().lowercase()}"
        val now = System.currentTimeMillis()
        sharedPrefs.edit().apply {
            putBoolean("pref_notif_novedades$suffix", enabled)
            putLong("pref_last_updated$suffix", now)
        }.apply()
        _prefNotifNovedades.value = enabled
        uploadPrefsToCloud()
    }

    fun updatePrefNotifNube(enabled: Boolean) {
        val email = _loggedInMember.value?.email
        val suffix = if (email.isNullOrBlank()) "" else "_${email.trim().lowercase()}"
        val now = System.currentTimeMillis()
        sharedPrefs.edit().apply {
            putBoolean("pref_notif_nube$suffix", enabled)
            putLong("pref_last_updated$suffix", now)
        }.apply()
        _prefNotifNube.value = enabled
        uploadPrefsToCloud()
    }

    fun updatePrefNotifSistema(enabled: Boolean) {
        val email = _loggedInMember.value?.email
        val suffix = if (email.isNullOrBlank()) "" else "_${email.trim().lowercase()}"
        val now = System.currentTimeMillis()
        sharedPrefs.edit().apply {
            putBoolean("pref_notif_sistema$suffix", enabled)
            putLong("pref_last_updated$suffix", now)
        }.apply()
        _prefNotifSistema.value = enabled
        uploadPrefsToCloud()
    }

    fun triggerSystemNotification(title: String, message: String) {
        if (title == "Inscripción en Actividad 🌿") {
            if (!checkIfAdmin()) return
        }
        val context = getApplication<Application>()
        val channelId = "je_app_notifications"
        val channelName = "Notificaciones Jóvenes y Ecosistemas"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal para notificaciones de JE-App"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Crear PendingIntent para abrir MainActivity al hacer clic
        val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.example.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val _aiState = MutableStateFlow<AiState>(AiState.None)
    val aiState: StateFlow<AiState> = _aiState.asStateFlow()

    sealed interface AiState {
        object None : AiState
        object Generating : AiState
        data class Success(val generatedText: String) : AiState
        data class Error(val message: String) : AiState
    }

    init {
        val database = AppDatabase.getDatabase(application)
        val dao = database.appDao()
        repository = AppRepository(dao)

        allActivities = repository.allActivities
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        carnetProfile = repository.carnetProfile
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        allArticles = repository.allArticles
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allMembers = repository.allMembers
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allEnrollments = repository.allEnrollments
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allNotifications = repository.allNotifications
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        viewModelScope.launch(Dispatchers.IO) {
            repository.checkAndSeedData()
            try {
                repository.deleteExpiredNotifications(System.currentTimeMillis())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        viewModelScope.launch {
            _loggedInMember.collect { member ->
                loadUserPreferences(member?.email)
            }
        }

        val rem = sharedPrefs.getBoolean("session_remember_me", false)
        val sMail = sharedPrefs.getString("session_saved_email", null)
        val sPass = sharedPrefs.getString("session_saved_password", null)
        if (rem && !sMail.isNullOrBlank() && !sPass.isNullOrBlank()) {
            viewModelScope.launch {
                login(sMail, sPass, true) { success ->
                    Log.d("AppViewModel", "Auto-login on app launch success: $success")
                }
            }
        }
    }

    fun getSavedRememberMe(): Boolean {
        return sharedPrefs.getBoolean("session_remember_me", false)
    }

    fun getSavedEmail(): String {
        return sharedPrefs.getString("session_saved_email", "") ?: ""
    }

    fun getSavedPassword(): String {
        return sharedPrefs.getString("session_saved_password", "") ?: ""
    }

    // Login logic
    fun login(email: String, password: String, rememberMe: Boolean = false, onResult: (Boolean) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            _loginUiState.value = LoginUiState.Error("Por favor complete todos los campos.")
            onResult(false)
            return
        }

        _loginUiState.value = LoginUiState.Loading

        val trimmedEmail = email.trim().lowercase()
        val hashedInput = SecurityUtils.hashPasswordSha256(password)

        // Try authenticating through Firebase Auth safely
        val auth = com.example.data.api.FirebaseHelper.getAuthOrNull()
        if (auth != null) {
            try {
                auth.signInWithEmailAndPassword(trimmedEmail, password)
                    .addOnCompleteListener { authSignInTask ->
                        if (authSignInTask.isSuccessful) {
                            Log.d("AppViewModel", "Firebase Auth authentication successful for $trimmedEmail")
                            // Continue standard database login and syncing
                            proceedLocalAndCloudLogin(trimmedEmail, password, rememberMe, onResult)
                        } else {
                            // SignIn failed, maybe user is registered locally/cloud database but not yet in FirebaseAuth!
                            // Let's check local/cloud databases.
                            viewModelScope.launch(Dispatchers.IO) {
                                var member = repository.getMemberByEmailDirect(trimmedEmail)
                                val url = _cloudSyncUrl.value
                                var cloudException: Exception? = null
                                if (member == null) {
                                    if (url.isNotBlank()) {
                                        try {
                                            member = CloudSyncClient.fetchSingleMember(url, trimmedEmail)
                                        } catch (e: Exception) {
                                            cloudException = e
                                            Log.e("AppViewModel", "Failed to check member in cloud: ${e.localizedMessage}")
                                        }
                                    }
                                }
                                
                                val matches = member != null && (member.password == password || member.password == hashedInput)
                                if (matches) {
                                    // Password matches! Register on FirebaseAuth immediately (Sync migration)
                                    try {
                                        auth.createUserWithEmailAndPassword(trimmedEmail, password)
                                            .addOnCompleteListener { registerTask ->
                                                if (registerTask.isSuccessful) {
                                                    Log.d("AppViewModel", "Auto-migrated member to Firebase Auth: $trimmedEmail")
                                                    com.example.data.api.FirebaseHelper.storeMemberPasswordInDb(trimmedEmail, password)
                                                }
                                                proceedLocalAndCloudLogin(trimmedEmail, password, rememberMe, onResult)
                                            }
                                    } catch (e: Exception) {
                                        Log.e("AppViewModel", "Failed to createUserWithEmailAndPassword safely", e)
                                        proceedLocalAndCloudLogin(trimmedEmail, password, rememberMe, onResult)
                                    }
                                } else {
                                    viewModelScope.launch(Dispatchers.Main) {
                                        val errMsg = when {
                                            member == null && cloudException != null -> 
                                                "Error de conexión con la base de datos en la nube. Verifique su acceso a internet."
                                            member == null -> 
                                                "El correo electrónico ingresado no coincide con ningún miembro registrado."
                                            else -> 
                                                "La contraseña introducida es incorrecta. Por favor verifique sus datos."
                                        }
                                        _loginUiState.value = LoginUiState.Error(errMsg)
                                        onResult(false)
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w("AppViewModel", "Firebase Auth signIn failed with exception", e)
                        proceedLocalAndCloudLogin(trimmedEmail, password, rememberMe, onResult)
                    }
            } catch (e: Exception) {
                Log.e("AppViewModel", "FirebaseAuth signInWithEmailAndPassword exception", e)
                proceedLocalAndCloudLogin(trimmedEmail, password, rememberMe, onResult)
            }
        } else {
            // Firebase Auth not initialized or not available, fallback seamlessly
            proceedLocalAndCloudLogin(trimmedEmail, password, rememberMe, onResult)
        }
    }

    private fun proceedLocalAndCloudLogin(email: String, password: String, rememberMe: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val url = _cloudSyncUrl.value
            var cloudFetchSuccess = true
            var cloudFetchError: String? = null
            if (url.isNotBlank()) {
                try {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        val remoteMember = CloudSyncClient.fetchSingleMember(url, email.trim().lowercase())
                        val isUserAdmin = email.trim().lowercase() == "coordinador@je.org" || (remoteMember?.isAdmin == true) || (remoteMember?.role == "Director General")
                        
                        if (remoteMember != null) {
                            val localMembers = repository.allMembers.first()
                            val mergedMembers = smartMergeMembers(localMembers, listOf(remoteMember), isFirstSync = true)
                            // Clean local database to ONLY contain this specific logged-in user's profile
                            val filteredMembers = mergedMembers.filter { it.email.trim().lowercase() == email.trim().lowercase() }
                            repository.overwriteMembers(filteredMembers)
                        }
                        
                        // If it is coordinator or admin, only pre-fetch and populate _adminMembersList in-memory
                        if (isUserAdmin) {
                            val allRemote = CloudSyncClient.fetchMembers(url)
                            if (allRemote.isNotEmpty()) {
                                _adminMembersList.value = allRemote
                            }
                        }
                    }
                } catch (e: Exception) {
                    cloudFetchSuccess = false
                    cloudFetchError = e.localizedMessage
                    Log.e("AppViewModel", "Failed to pre-fetch member from cloud: ${e.localizedMessage}")
                }
            }

            val member = repository.getMemberByEmailDirect(email.trim().lowercase())
            val hashedInput = SecurityUtils.hashPasswordSha256(password)
            if (member != null && (member.password == password || member.password == hashedInput)) {
                _loggedInMember.value = member
                
                // Back up credential securely in Firebase RTDB
                com.example.data.api.FirebaseHelper.storeMemberPasswordInDb(email.trim().lowercase(), password)

                // Save or clear Remember Me session preferences
                if (rememberMe) {
                    sharedPrefs.edit().apply {
                        putBoolean("session_remember_me", true)
                        putString("session_saved_email", email.trim().lowercase())
                        putString("session_saved_password", password)
                    }.apply()
                } else {
                    sharedPrefs.edit().apply {
                        putBoolean("session_remember_me", false)
                        putString("session_saved_email", null)
                        putString("session_saved_password", null)
                    }.apply()
                }

                syncWithCloud { success, message ->
                    Log.d("AppViewModel", "Immediate login sync completed. Success: $success, Message: $message")
                }
                _loginUiState.value = LoginUiState.Success("¡Bienvenido de nuevo!")
                onResult(true)
            } else {
                val errMsg = when {
                    member == null && !cloudFetchSuccess ->
                        "Error de red al consultar el perfil en la nube. Verifique su conexión."
                    member == null ->
                        "El usuario especificado no está registrado en el sistema local ni en la nube."
                    else ->
                        "La contraseña introducida es incorrecta. Por favor intente de nuevo."
                }
                _loginUiState.value = LoginUiState.Error(errMsg)
                onResult(false)
            }
        }
    }

    // Bottom / Logout
    fun logout() {
        _loggedInMember.value = null
        _isOverrideCoordinator.value = false
        _adminMembersList.value = emptyList()
        // Clear saved session strictly on explicit logout
        sharedPrefs.edit().apply {
            putBoolean("session_remember_me", false)
            putString("session_saved_email", null)
            putString("session_saved_password", null)
        }.apply()
    }

    // Register a member account (Coordinator console action only)
    fun registerNewMember(
        email: String,
        password: String,
        fullName: String,
        role: String,
        country: String,
        emojiAvatar: String,
        isAdmin: Boolean = false,
        photoUri: String? = null,
        qrUri: String? = null,
        customCara1Uri: String? = null,
        customCara2Uri: String? = null,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmedEmail = email.trim().lowercase()
            val existing = repository.getMemberByEmailDirect(trimmedEmail)
            if (existing != null) {
                viewModelScope.launch(Dispatchers.Main) { onResult(false) }
            } else {
                val newMember = Member(
                    email = trimmedEmail,
                    password = password,
                    fullName = fullName,
                    role = role,
                    country = country,
                    emojiAvatar = emojiAvatar,
                    points = 50,
                    credentialId = "LA-ECO-${(10000..99999).random()}",
                    joinDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                    isAdmin = isAdmin,
                    photoUri = photoUri,
                    qrUri = qrUri,
                    customCara1Uri = customCara1Uri,
                    customCara2Uri = customCara2Uri
                )
                // No local SQLite persistence for other profiles
                if (trimmedEmail == _loggedInMember.value?.email?.trim()?.lowercase()) {
                    repository.insertMember(newMember)
                }
                
                // Register details in Firebase Authentication safely
                val auth = com.example.data.api.FirebaseHelper.getAuthOrNull()
                if (auth != null) {
                    try {
                        auth.createUserWithEmailAndPassword(trimmedEmail, password)
                            .addOnCompleteListener { authTask ->
                                if (authTask.isSuccessful) {
                                    Log.d("AppViewModel", "Successfully created member user in Firebase Auth: $trimmedEmail")
                                } else {
                                    Log.w("AppViewModel", "Firebase Auth creation failed: ${authTask.exception?.message}")
                                }
                            }
                    } catch (e: Exception) {
                        Log.e("AppViewModel", "Failed to invoke createUserWithEmailAndPassword safely", e)
                    }
                }

                // Register credential safely in Firebase database cloud
                com.example.data.api.FirebaseHelper.storeMemberPasswordInDb(trimmedEmail, password)

                // Update in-memory lists for UI
                _adminMembersList.value = _adminMembersList.value.filterNot { it.email.trim().lowercase() == trimmedEmail } + newMember

                val url = _cloudSyncUrl.value
                if (url.isNotBlank()) {
                    try {
                        CloudSyncClient.uploadSingleMember(url, newMember)
                    } catch (e: Exception) {
                        Log.e("AppViewModel", "Failed to upload registered member to cloud: ${e.localizedMessage}")
                    }
                }
                
                // Track creation in status alerts
                if (_prefNotifSistema.value) {
                    val notif = EcoNotification(
                        title = "Miembro Creado 📋",
                        message = "Admin creó cuenta para: $fullName",
                        timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                    )
                    repository.insertNotification(notif)
                    triggerSystemNotification("Miembro Creado 📋", "Admin creó cuenta para: $fullName")
                }
                viewModelScope.launch(Dispatchers.Main) { onResult(true) }
                autoSync()
            }
        }
    }

    // Modify custom member card by coordinator from console
    fun modifyMemberProfile(member: Member) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanEmail = member.email.trim().lowercase()
            val cleanMember = member.copy(email = cleanEmail)
            val isSelf = cleanEmail == _loggedInMember.value?.email?.trim()?.lowercase()
            
            // Backup credential securely in Firebase RTDB cloud
            val plainPassword = SecurityUtils.resolveDehashedPassword(cleanMember.password)
            com.example.data.api.FirebaseHelper.storeMemberPasswordInDb(cleanEmail, plainPassword)

            // Logically save on SQLite only if it is the user's self profile to prevent storing other members locally
            if (isSelf) {
                repository.insertMember(cleanMember)
                _loggedInMember.value = cleanMember
            }

            // Update in-memory lists for UI
            _adminMembersList.value = _adminMembersList.value.map {
                if (it.email.trim().lowercase() == cleanEmail) cleanMember else it
            }

            val url = _cloudSyncUrl.value
            if (url.isNotBlank()) {
                try {
                    CloudSyncClient.uploadSingleMember(url, cleanMember)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to upload updated member profile to cloud: ${e.localizedMessage}")
                }
            }

            // Log update
            if (_prefNotifSistema.value) {
                val notif = EcoNotification(
                    title = "Perfil Actualizado ✍️",
                    message = "Se editó el carné de ${cleanMember.fullName}",
                    timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                )
                repository.insertNotification(notif)
                triggerSystemNotification("Perfil Actualizado ✍️", "Se editó el carné de ${cleanMember.fullName}")
            }
            autoSync()
        }
    }

    // Refresh member list in memory from the cloud (coordinator console only)
    fun refreshAdminMembers() {
        if (!checkIfAdmin()) return
        viewModelScope.launch(Dispatchers.IO) {
            val url = _cloudSyncUrl.value
            if (url.isNotBlank()) {
                try {
                    val allRemote = CloudSyncClient.fetchMembers(url).distinctBy { it.email.trim().lowercase() }
                    if (allRemote.isNotEmpty()) {
                        _adminMembersList.value = allRemote
                    }
                } catch (e: java.lang.Exception) {
                    android.util.Log.e("AppViewModel", "Failed to refresh admin members: ${e.localizedMessage}")
                }
            }
        }
    }

    // Suppress a member account
    fun removeMember(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val key = "member_${email.trim().lowercase()}"
            addTombstone(key)
            repository.deleteMemberByEmail(email)
            
            // Delete from in-memory admin representation
            _adminMembersList.value = _adminMembersList.value.filterNot { it.email.trim().lowercase() == email.trim().lowercase() }

            val url = _cloudSyncUrl.value
            if (url.isNotBlank()) {
                try {
                    CloudSyncClient.deleteSingleMember(url, email)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to delete removed member from cloud: ${e.localizedMessage}")
                }
            }
            if (_loggedInMember.value?.email == email) {
                _loggedInMember.value = null
            }
            // Alert coordinator
            if (_prefNotifSistema.value) {
                val notif = EcoNotification(
                    title = "Miembro Removido 🗑️",
                    message = "Se eliminó el acceso de: $email",
                    timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                )
                repository.insertNotification(notif)
                triggerSystemNotification("Miembro Removido 🗑️", "Se eliminó el acceso de: $email")
            }
            autoSync()
        }
    }

    // Link/Unlink Google Calendar (Tied to the database configuration of the logged in administrator/member)
    fun toggleGoogleCalendarLinked(email: String? = null) {
        val current = _loggedInMember.value ?: return
        val newLinked = !current.isGoogleCalendarLinked
        val newEmail = if (newLinked) {
            email?.trim()?.takeIf { it.isNotBlank() } ?: current.googleCalendarEmail ?: current.email
        } else {
            current.googleCalendarEmail
        }
        val updated = current.copy(
            isGoogleCalendarLinked = newLinked,
            googleCalendarEmail = newEmail
        )
        _loggedInMember.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateMember(updated)
        }
    }

    // Create a new activity in the DB (supports Google Calendar simulation)
    fun addActivity(
        title: String,
        description: String,
        date: String,
        endDate: String = "",
        location: String,
        country: String,
        category: String,
        organizer: String,
        eventType: String = "Voluntariado",
        isMandatory: Boolean = false,
        registrationDeadline: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val activity = EcoActivity(
                title = title,
                description = description,
                date = date,
                endDate = endDate,
                location = location,
                country = country,
                category = category,
                organizer = organizer,
                eventType = eventType,
                isMandatory = isMandatory,
                registrationDeadline = registrationDeadline
            )
            repository.insertActivity(activity)
            removeTombstone("activity_${title.trim().lowercase()}_${date.trim()}")

            // Alert notification that activity was programmed
            if (_prefNotifActividades.value) {
                val calendarPrefix = if (googleCalendarLinked.value) "Sincronizado con Google Calendar 📅: " else ""
                val notif = EcoNotification(
                    title = "Actividad Creada 🌾",
                    message = "${calendarPrefix}Se programó '$title' para el $date de junio",
                    timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                )
                repository.insertNotification(notif)
                triggerSystemNotification("Nueva Actividad 🌾", "${calendarPrefix}Se programó '$title' para el $date de junio")
            }
            autoSync()
        }
    }

    fun deleteActivity(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.allActivities.first()
            val act = list.find { it.id == id }
            if (act != null) {
                val key = "activity_${act.title.trim().lowercase()}_${act.date.trim()}"
                addTombstone(key)
            }
            repository.deleteActivity(id)
            repository.deleteEnrollmentsByActivityId(id)
            val url = _cloudSyncUrl.value
            if (url.isNotBlank() && checkIfAdmin()) {
                try {
                    val remainingAct = repository.allActivities.first()
                    CloudSyncClient.uploadActivities(url, remainingAct)
                    val remainingEnrollments = repository.allEnrollments.first()
                    CloudSyncClient.uploadEnrollments(url, remainingEnrollments)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Immediate cloud upload of remaining activities and enrollments failed: ${e.localizedMessage}")
                }
            }
            autoSync()
        }
    }

    // Refactored enrollment mechanics supporting multiple users
    fun toggleEnrollment(activity: EcoActivity, member: Member) {
        viewModelScope.launch(Dispatchers.IO) {
            val emailClean = member.email.trim().lowercase()
            val existing = repository.getEnrollmentDirect(activity.id, emailClean)
            if (existing != null) {
                repository.deleteEnrollment(activity.id, emailClean)
                // Deduct points from member
                val updatedMember = member.copy(points = (member.points - 15).coerceAtLeast(0))
                repository.updateMember(updatedMember)
                // If the logged in member was this member, update our active session state
                if (_loggedInMember.value?.email?.trim()?.lowercase() == emailClean) {
                    _loggedInMember.value = updatedMember
                }
            } else {
                val ectSdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                ectSdf.timeZone = java.util.TimeZone.getTimeZone("America/Guayaquil")
                val ecuadorTimeStr = ectSdf.format(Date()) + " (Ec)"
                val enrollment = EcoEnrollment(
                    activityId = activity.id,
                    activityTitle = activity.title,
                    memberEmail = emailClean,
                    memberName = member.fullName,
                    enrolledAt = ecuadorTimeStr,
                    reminderMinutes = -1
                )
                repository.insertEnrollment(enrollment)
                // Add 15 points to member
                val updatedMember = member.copy(points = member.points + 15)
                repository.updateMember(updatedMember)
                // If the logged in member was this member, update our active session state
                if (_loggedInMember.value?.email?.trim()?.lowercase() == emailClean) {
                    _loggedInMember.value = updatedMember
                }

                // Generate coordinator notification
                if (_prefNotifActividades.value) {
                    val timeStamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                    val notification = EcoNotification(
                        title = "Inscripción en Actividad 🌿",
                        message = "${member.fullName} (${member.role}) se inscribió a '${activity.title}'",
                        timestamp = timeStamp
                    )
                    repository.insertNotification(notification)
                    triggerSystemNotification("Inscripción en Actividad 🌿", "${member.fullName} (${member.role}) se inscribió a '${activity.title}'")
                }
            }
            autoSync()
        }
    }

    fun setEventReminder(activity: EcoActivity, member: Member, isActive: Boolean, minutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val emailClean = member.email.trim().lowercase()
            val existing = repository.getEnrollmentDirect(activity.id, emailClean)
            val ectSdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            ectSdf.timeZone = java.util.TimeZone.getTimeZone("America/Guayaquil")
            val ecuadorTimeStr = ectSdf.format(Date()) + " (Ec)"
            
            if (existing != null) {
                val updated = existing.copy(reminderMinutes = if (isActive) minutes else -1)
                repository.insertEnrollment(updated)
            } else {
                val enrollment = EcoEnrollment(
                    activityId = activity.id,
                    activityTitle = activity.title,
                    memberEmail = emailClean,
                    memberName = member.fullName,
                    enrolledAt = ecuadorTimeStr,
                    reminderMinutes = if (isActive) minutes else -1
                )
                repository.insertEnrollment(enrollment)
            }

            val context = getApplication<android.app.Application>()
            if (isActive) {
                com.example.receiver.ReminderScheduler.scheduleReminder(context, activity, minutes)
            } else {
                com.example.receiver.ReminderScheduler.cancelReminder(context, activity)
            }

            autoSync()
        }
    }

    // Deprecated old toggling fallback for backwards compatibility
    fun toggleActivityRegistration(activity: EcoActivity) {
        viewModelScope.launch(Dispatchers.IO) {
            val active = _loggedInMember.value
            if (active != null) {
                toggleEnrollment(activity, active)
            } else {
                val updated = activity.copy(isUserRegistered = !activity.isUserRegistered)
                repository.updateActivity(updated)
            }
        }
    }

    fun updateActivity(activity: EcoActivity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateActivity(activity)
            val url = _cloudSyncUrl.value
            if (url.isNotBlank() && checkIfAdmin()) {
                try {
                    val currentActs = repository.allActivities.first()
                    CloudSyncClient.uploadActivities(url, currentActs)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Immediate cloud upload of updated activities failed: ${e.localizedMessage}")
                }
            }
            autoSync()
        }
    }

    // Coordinator can edit any user's profile and points
    fun saveProfile(
        fullName: String,
        association: String,
        role: String,
        country: String,
        emojiAvatar: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val active = _loggedInMember.value
            if (active != null) {
                val updated = active.copy(
                    fullName = fullName,
                    association = association,
                    role = role,
                    country = country,
                    emojiAvatar = emojiAvatar
                )
                repository.updateMember(updated)
                _loggedInMember.value = updated
            } else {
                val current = repository.carnetProfile.firstOrNull()
                val points = current?.points ?: 50
                val credId = current?.credentialId ?: "LA-ECO-${(10000..99999).random()}"
                val joinD = current?.joinDate ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

                val profile = CarnetProfile(
                    fullName = fullName,
                    association = association,
                    role = role,
                    country = country,
                    emojiAvatar = emojiAvatar,
                    points = points,
                    credentialId = credId,
                    joinDate = joinD
                )
                repository.updateProfile(profile)
            }
            autoSync()
        }
    }

    fun updateProfilePhoto(photoUri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val active = _loggedInMember.value
            if (active != null) {
                val updated = active.copy(photoUri = photoUri)
                repository.updateMember(updated)
                _loggedInMember.value = updated
                autoSync()
            }
        }
    }

    fun updateProfileCustomCara1(cara1Uri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val active = _loggedInMember.value
            if (active != null) {
                val updated = active.copy(customCara1Uri = cara1Uri)
                repository.updateMember(updated)
                _loggedInMember.value = updated
            } else {
                val current = repository.carnetProfile.firstOrNull()
                if (current != null) {
                    val updatedProfile = current.copy(customCara1Uri = cara1Uri)
                    repository.updateProfile(updatedProfile)
                }
            }
            autoSync()
        }
    }

    fun updateProfileCustomCara2(cara2Uri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val active = _loggedInMember.value
            if (active != null) {
                val updated = active.copy(customCara2Uri = cara2Uri)
                repository.updateMember(updated)
                _loggedInMember.value = updated
            } else {
                val current = repository.carnetProfile.firstOrNull()
                if (current != null) {
                    val updatedProfile = current.copy(customCara2Uri = cara2Uri)
                    repository.updateProfile(updatedProfile)
                }
            }
            autoSync()
        }
    }

    fun updateLocalArticle(article: EcoArticle) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertArticle(article)
            removeTombstone("article_${article.title.trim().lowercase()}_${article.publishDate.trim()}")
            val url = _cloudSyncUrl.value
            if (url.isNotBlank() && checkIfAdmin()) {
                try {
                    val currentArticles = repository.allArticles.first()
                    CloudSyncClient.uploadArticles(url, currentArticles)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Immediate cloud upload of updated articles failed: ${e.localizedMessage}")
                }
            }
            autoSync()
        }
    }

    fun publishLocalArticle(title: String, content: String, category: String, region: String, photoUri: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val newArticle = EcoArticle(
                title = title,
                content = content,
                category = category,
                region = region,
                publishDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                isAiGenerated = false,
                photoUri = photoUri
            )
            repository.insertArticle(newArticle)
            removeTombstone("article_${newArticle.title.trim().lowercase()}_${newArticle.publishDate.trim()}")

            val url = _cloudSyncUrl.value
            if (url.isNotBlank() && checkIfAdmin()) {
                try {
                    val currentArticles = repository.allArticles.first()
                    CloudSyncClient.uploadArticles(url, currentArticles)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Immediate cloud upload of newly published article failed: ${e.localizedMessage}")
                }
            }

            // Add points for contributing knowledge!
            val active = _loggedInMember.value
            if (active != null) {
                val updated = active.copy(points = active.points + 25)
                repository.updateMember(updated)
                _loggedInMember.value = updated
            } else {
                val profile = repository.carnetProfile.firstOrNull()
                if (profile != null) {
                    val updatedProfile = profile.copy(points = profile.points + 25)
                    repository.updateProfile(updatedProfile)
                }
            }

            // News / Article publication notification
            if (_prefNotifNovedades.value) {
                val notif = EcoNotification(
                    title = "Artículo Publicado 📰",
                    message = "Se publicó '$title' en la sección de Novedades.",
                    timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                )
                repository.insertNotification(notif)
                triggerSystemNotification("Artículos y Novedades 📰", "Se publicó el artículo: $title")
            }
            autoSync()
        }
    }

    fun deleteArticle(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.allArticles.first()
            val art = list.find { it.id == id }
            if (art != null) {
                val key = "article_${art.title.trim().lowercase()}_${art.publishDate.trim()}"
                addTombstone(key)
            }
            repository.deleteArticle(id)
            val url = _cloudSyncUrl.value
            if (url.isNotBlank() && checkIfAdmin()) {
                try {
                    val remainingArticles = repository.allArticles.first()
                    CloudSyncClient.uploadArticles(url, remainingArticles)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Immediate cloud upload of remaining articles failed: ${e.localizedMessage}")
                }
            }
            autoSync()
        }
    }

    fun clearNotification(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.allNotifications.first()
            val notif = list.find { it.id == id }
            if (notif != null) {
                val key = "notif_${notif.title.trim().lowercase()}_${notif.timestamp.trim()}"
                addTombstone(key)
            }
            repository.deleteNotificationById(id)
            autoSync()
        }
    }

    fun toggleNotificationReadStatus(notification: EcoNotification) {
        viewModelScope.launch {
            val updated = notification.copy(isRead = !notification.isRead)
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                repository.insertNotification(updated)
            }
            autoSync()
        }
    }

    fun markNotificationsRead() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markAllNotificationsAsRead()
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.allNotifications.first()
            for (notif in list) {
                if (!notif.isGlobalAlert) {
                    val key = "notif_${notif.title.trim().lowercase()}_${notif.timestamp.trim()}"
                    addTombstone(key)
                }
            }
            repository.clearNonGlobalNotifications()
            autoSync()
        }
    }

    fun clearAllCloudNotifications(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val url = _cloudSyncUrl.value
        if (url.isBlank()) {
            onComplete(false, "La URL de sincronización con la nube está vacía.")
            return
        }
        viewModelScope.launch {
            try {
                val success = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val currentNotifs = repository.allNotifications.first()
                    val globalNotifs = currentNotifs.filter { it.isGlobalAlert }
                    CloudSyncClient.uploadNotifications(url, globalNotifs)
                }
                if (success) {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        repository.clearNonGlobalNotifications()
                    }
                    scanCloudDatabase()
                    onComplete(true, "Las notificaciones han sido limpiadas, conservando los mensajes de difusión.")
                } else {
                    onComplete(false, "No se pudo actualizar la base de datos de la nube.")
                }
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage ?: "Error de conexión o red.")
            }
        }
    }

    fun consultEcoAi(prompt: String, category: String = "Consejo AI", region: String = "América Latina") {
        viewModelScope.launch {
            _aiState.value = AiState.Generating
            try {
                val key = BuildConfig.GEMINI_API_KEY
                if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
                    throw IllegalStateException("Falta la Clave de API. Por favor configure GEMINI_API_KEY en el panel de secretos de AI Studio.")
                }

                val systemInstructionText = """
                    Eres un Asesor de 'Una Sola Salud' (One Health) de Jóvenes y Ecosistemas Latinoamérica.
                    Responde al usuario en ESPAÑOL, de forma estructurada, clara e inspiradora. Usa listas y subtítulos.
                    Enfoca tu respuesta en la interacción indisoluble entre ambiente, salud humana y salud animal.
                    Habla apasionadamente sobre Resistencia a antimicrobianos (JE-RAM), salud mental en el activismo (JE-Mental), podcasts educativos (JE-Podcast), restauración biológica (JE-Ambiente) y diseño/mensajes de mercadotecnia creativa (JE-Visual).
                    Tu respuesta debe empezar directamente con un título amigable en la primera línea precedido de 'TÍTULO: ' y luego el contenido del artículo. No agregues etiquetas markdown h1 en esa línea.
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(temperature = 0.7f),
                    systemInstruction = Content(parts = listOf(Part(text = systemInstructionText)))
                )

                val response = RetrofitClient.service.generateContent(key, request)
                val fullResponseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "No se obtuvo respuesta del asesor."

                var parsedTitle = "Estudio: $prompt"
                var parsedContent = fullResponseText

                if (fullResponseText.contains("TÍTULO:")) {
                    val lines = fullResponseText.split("\n")
                    val titleLine = lines.firstOrNull { it.startsWith("TÍTULO:") }
                    if (titleLine != null) {
                        parsedTitle = titleLine.replace("TÍTULO:", "").trim()
                        parsedContent = lines.filter { !it.startsWith("TÍTULO:") }.joinToString("\n").trim()
                    }
                }

                val aiArticle = EcoArticle(
                    title = parsedTitle,
                    content = parsedContent,
                    category = category,
                    region = region,
                    publishDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                    isAiGenerated = true
                )
                repository.insertArticle(aiArticle)

                _aiState.value = AiState.Success(parsedContent)

                // Add points for consulting AI
                val active = _loggedInMember.value
                if (active != null) {
                    val updated = active.copy(points = active.points + 10)
                    repository.updateMember(updated)
                    _loggedInMember.value = updated
                } else {
                    val profile = repository.carnetProfile.firstOrNull()
                    if (profile != null) {
                        val updatedProfile = profile.copy(points = profile.points + 10)
                        repository.updateProfile(updatedProfile)
                    }
                }

                // AI Article notification
                if (_prefNotifNovedades.value) {
                    val notif = EcoNotification(
                        title = "Asesoría AI Recibida 🧠",
                        message = "Se generó: $parsedTitle.",
                        timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                    )
                    repository.insertNotification(notif)
                    triggerSystemNotification("Asesoría AI Recibida 🧠", "Se generó: $parsedTitle")
                }

            } catch (e: Exception) {
                _aiState.value = AiState.Error(e.localizedMessage ?: "Error de comunicación con el consultor AI")
            }
        }
    }

    private fun smartMergeMembers(localMembers: List<Member>, remoteMembers: List<Member>, isFirstSync: Boolean = false): List<Member> {
        val mergedMembers = mutableListOf<Member>()
        val remoteMap = remoteMembers.associateBy { it.email.trim().lowercase() }
        val localMap = localMembers.associateBy { it.email.trim().lowercase() }
        
        val allEmails = (localMap.keys + remoteMap.keys).distinct()
        val isAdminSelf = checkIfAdmin()
        val userEmail = _loggedInMember.value?.email ?: ""
        val tombstones = getTombstones()

        for (email in allEmails) {
            val cleanEmail = email.trim().lowercase()
            val key = "member_$cleanEmail"
            if (tombstones.contains(key)) {
                continue
            }
            
            val localMember = localMap[cleanEmail]
            val remoteMember = remoteMap[cleanEmail]
            if (localMember != null && remoteMember != null) {
                val isSelf = cleanEmail == userEmail.trim().lowercase()
                val useLocal = (isSelf || isAdminSelf) && !isFirstSync
                
                // Keep password/admin/profile updates from local database (where they are edited) ONLY if it's our self-profile, otherwise use the cloud database as authoritative
                val finalPassword = if (useLocal) localMember.password else remoteMember.password
                val finalRole = if (useLocal) localMember.role else remoteMember.role
                val finalIsAdmin = if (useLocal) localMember.isAdmin else remoteMember.isAdmin
                val finalFullName = if (useLocal) localMember.fullName else remoteMember.fullName
                val finalAssociation = if (useLocal) localMember.association else remoteMember.association
                val finalCountry = if (useLocal) localMember.country else remoteMember.country
                val finalCustomCara1 = if (useLocal) {
                    localMember.customCara1Uri ?: remoteMember.customCara1Uri
                } else {
                    remoteMember.customCara1Uri ?: localMember.customCara1Uri
                }
                val finalCustomCara2 = if (useLocal) {
                    localMember.customCara2Uri ?: remoteMember.customCara2Uri
                } else {
                    remoteMember.customCara2Uri ?: localMember.customCara2Uri
                }
                val finalPhotoUri = if (useLocal) {
                    localMember.photoUri ?: remoteMember.photoUri
                } else {
                    remoteMember.photoUri ?: localMember.photoUri
                }
                val finalQrUri = if (useLocal) {
                    localMember.qrUri ?: remoteMember.qrUri
                } else {
                    remoteMember.qrUri ?: localMember.qrUri
                }

                // Create merged member
                mergedMembers.add(
                    localMember.copy(
                        email = cleanEmail,
                        customCara1Uri = finalCustomCara1,
                        customCara2Uri = finalCustomCara2,
                        photoUri = finalPhotoUri,
                        qrUri = finalQrUri,
                        isAdmin = finalIsAdmin,
                        fullName = finalFullName,
                        role = finalRole,
                        association = finalAssociation,
                        country = finalCountry,
                        password = finalPassword,
                        points = maxOf(localMember.points, remoteMember.points)
                    )
                )
            } else if (localMember != null) {
                mergedMembers.add(localMember.copy(email = cleanEmail))
            } else {
                remoteMember?.let { mergedMembers.add(it.copy(email = cleanEmail)) }
            }
        }
        return mergedMembers
    }

    private fun smartMergeActivities(
        localActivities: List<EcoActivity>, 
        remoteActivities: List<EcoActivity>,
        isFirstSync: Boolean,
        isAdmin: Boolean
    ): List<EcoActivity> {
        val tombstones = getTombstones()
        val mergedMap = mutableMapOf<Int, EcoActivity>()
        val mergedListNoId = mutableListOf<EcoActivity>()

        // 1. Match by ID (for non-zero IDs)
        val localWithId = localActivities.filter { it.id != 0 }.associateBy { it.id }
        val remoteWithId = remoteActivities.filter { it.id != 0 }.associateBy { it.id }
        val allIds = (localWithId.keys + remoteWithId.keys).distinct()

        for (id in allIds) {
            val localAct = localWithId[id]
            val remoteAct = remoteWithId[id]

            if (localAct != null && remoteAct != null) {
                val useLocal = !isFirstSync
                
                // Tombstone check for either old or new key to support deletion propagation
                val compositeKeyLocal = "${localAct.title.trim().lowercase()}_${localAct.date.trim()}"
                val compositeKeyRemote = "${remoteAct.title.trim().lowercase()}_${remoteAct.date.trim()}"
                if (tombstones.contains("activity_$compositeKeyLocal") || tombstones.contains("activity_$compositeKeyRemote")) {
                    continue
                }

                val finalMandatory = if (useLocal) localAct.isMandatory else remoteAct.isMandatory
                val finalTitle = if (useLocal) localAct.title else remoteAct.title
                val finalDesc = if (useLocal) localAct.description else remoteAct.description
                val finalDate = if (useLocal) localAct.date else remoteAct.date
                val finalEndDate = if (useLocal) localAct.endDate else remoteAct.endDate
                val finalLoc = if (useLocal) localAct.location else remoteAct.location
                val finalCountry = if (useLocal) localAct.country else remoteAct.country
                val finalCategory = if (useLocal) localAct.category else remoteAct.category
                val finalOrganizer = if (useLocal) localAct.organizer else remoteAct.organizer
                val finalEventType = if (useLocal) localAct.eventType else remoteAct.eventType
                val finalRegistrationDeadline = if (useLocal) localAct.registrationDeadline else remoteAct.registrationDeadline
                
                val finalRegistered = localAct.isUserRegistered

                mergedMap[id] = localAct.copy(
                    title = finalTitle,
                    description = finalDesc,
                    date = finalDate,
                    endDate = finalEndDate,
                    location = finalLoc,
                    country = finalCountry,
                    category = finalCategory,
                    organizer = finalOrganizer,
                    eventType = finalEventType,
                    isMandatory = finalMandatory,
                    isUserRegistered = finalRegistered,
                    registrationDeadline = finalRegistrationDeadline
                )
            } else if (localAct != null) {
                val compositeKey = "${localAct.title.trim().lowercase()}_${localAct.date.trim()}"
                if (!tombstones.contains("activity_$compositeKey")) {
                    if (isAdmin) {
                        mergedMap[id] = localAct
                    }
                }
            } else {
                remoteAct?.let {
                    val compositeKey = "${it.title.trim().lowercase()}_${it.date.trim()}"
                    if (!tombstones.contains("activity_$compositeKey")) {
                        mergedMap[id] = it
                    }
                }
            }
        }

        // 2. Match by composite key for items where ID == 0 (if any)
        val localNoId = localActivities.filter { it.id == 0 }
        val remoteNoId = remoteActivities.filter { it.id == 0 }
        val localNoIdMap = localNoId.associateBy { "${it.title.trim().lowercase()}_${it.date.trim()}" }
        val remoteNoIdMap = remoteNoId.associateBy { "${it.title.trim().lowercase()}_${it.date.trim()}" }
        val allNoIdKeys = (localNoIdMap.keys + remoteNoIdMap.keys).distinct()

        for (key in allNoIdKeys) {
            if (tombstones.contains("activity_$key")) {
                continue
            }
            val localAct = localNoIdMap[key]
            val remoteAct = remoteNoIdMap[key]

            if (localAct != null && remoteAct != null) {
                val useLocal = !isFirstSync
                val finalAct = if (useLocal) localAct else remoteAct
                mergedListNoId.add(finalAct)
            } else if (localAct != null) {
                if (isAdmin) {
                    mergedListNoId.add(localAct)
                }
            } else {
                remoteAct?.let { mergedListNoId.add(it) }
            }
        }

        return mergedMap.values.toList() + mergedListNoId
    }

    private fun smartMergeArticles(
        localArticles: List<EcoArticle>, 
        remoteArticles: List<EcoArticle>,
        isFirstSync: Boolean,
        isAdmin: Boolean
    ): List<EcoArticle> {
        val merged = mutableListOf<EcoArticle>()
        val localMap = localArticles.associateBy { "${it.title.trim().lowercase()}_${it.publishDate.trim()}" }
        val remoteMap = remoteArticles.associateBy { "${it.title.trim().lowercase()}_${it.publishDate.trim()}" }
        
        val allKeys = (localMap.keys + remoteMap.keys).distinct()
        val tombstones = getTombstones()

        for (key in allKeys) {
            val tombstoneKey = "article_$key"
            if (tombstones.contains(tombstoneKey)) {
                continue
            }

            val localArt = localMap[key]
            val remoteArt = remoteMap[key]
            if (localArt != null && remoteArt != null) {
                // In a cooperative bidirectional sync, we allow local updates to propagate stably to the cloud.
                val useLocal = !isFirstSync
                
                val finalTitle = if (useLocal) localArt.title else remoteArt.title
                val finalContent = if (useLocal) localArt.content else remoteArt.content
                val finalCategory = if (useLocal) localArt.category else remoteArt.category
                val finalRegion = if (useLocal) localArt.region else remoteArt.region
                val finalPublishDate = if (useLocal) localArt.publishDate else remoteArt.publishDate
                val finalFeatured = if (useLocal) localArt.isFeatured else remoteArt.isFeatured
                val finalAiGenerated = if (useLocal) localArt.isAiGenerated else remoteArt.isAiGenerated
                val finalPhotoUri = if (useLocal) {
                    localArt.photoUri ?: remoteArt.photoUri
                } else {
                    remoteArt.photoUri ?: localArt.photoUri
                }

                merged.add(
                    localArt.copy(
                        title = finalTitle,
                        content = finalContent,
                        category = finalCategory,
                        region = finalRegion,
                        publishDate = finalPublishDate,
                        isFeatured = finalFeatured,
                        isAiGenerated = finalAiGenerated,
                        photoUri = finalPhotoUri
                    )
                )
            } else if (localArt != null) {
                if (isAdmin) {
                    merged.add(localArt)
                }
            } else {
                remoteArt?.let { merged.add(it) }
            }
        }
        return merged
    }

    private fun smartMergeNotifications(
        localNotifications: List<EcoNotification>,
        remoteNotifications: List<EcoNotification>,
        isFirstSync: Boolean,
        filterByTombstones: Boolean = true,
        isAdmin: Boolean = false
    ): List<EcoNotification> {
        val merged = mutableListOf<EcoNotification>()
        val localMap = localNotifications.associateBy { "${it.title.trim().lowercase()}_${it.timestamp.trim()}" }
        val remoteMap = remoteNotifications.associateBy { "${it.title.trim().lowercase()}_${it.timestamp.trim()}" }

        val allKeys = (localMap.keys + remoteMap.keys).distinct()
        val tombstones = getTombstones()

        for (key in allKeys) {
            val tombstoneKey = "notif_$key"
            // If the key is in tombstones:
            // 1. If filterByTombstones is true, we always skip.
            // 2. If the user is admin, we also skip so that the deleted status gets propagated to the cloud!
            if (tombstones.contains(tombstoneKey)) {
                if (filterByTombstones || isAdmin) {
                    continue
                }
            }

            val localNotif = localMap[key]
            val remoteNotif = remoteMap[key]
            if (localNotif != null && remoteNotif != null) {
                val finalRead = localNotif.isRead || remoteNotif.isRead
                merged.add(localNotif.copy(isRead = finalRead))
            } else if (localNotif != null) {
                if (localNotif.isGlobalAlert) {
                    if (isAdmin) {
                        // Admin keeping locally-created not-yet-synced notification
                        merged.add(localNotif)
                    } else {
                        // Standard member: if it's a global alert but not on the cloud (deleted by admin), discard it!
                        // This ensures that once the admin deletes it from the cloud, it vanishes from members' devices!
                    }
                } else {
                    merged.add(localNotif)
                }
            } else {
                remoteNotif?.let { merged.add(it) }
            }
        }
        return merged
    }

    fun resetAiState() {
        _aiState.value = AiState.None
    }

    private suspend fun performCloudMerge(url: String): String {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val member = _loggedInMember.value
            val userEmail = member?.email ?: ""
            val emailKey = if (userEmail.isNotBlank()) userEmail.trim().lowercase() else "anonymous"
            val hasSyncedKey = "first_sync_done_$emailKey"

            val isAdmin = checkIfAdmin()

            // 1. Fetch remote data from cloud (nullable to distinguish fetch failures from empty list)
            val remoteActivities = try { CloudSyncClient.fetchActivities(url) } catch (e: Exception) { Log.e("AppViewModel", "Failed to fetch activities", e); null }
            val isUserAdmin = userEmail.trim().lowercase() == "coordinador@je.org" || isAdmin
            val remoteMembers = try {
                if (userEmail.isNotBlank()) {
                    val single = CloudSyncClient.fetchSingleMember(url, userEmail)
                    if (single != null) listOf(single) else emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Failed to fetch single member profile", e)
                null
            }
            
            // If the user is coordinator or admin, fetch and populate _adminMembersList in-memory directly from cloud
            if (isUserAdmin) {
                try {
                    val allRemote = CloudSyncClient.fetchMembers(url).distinctBy { it.email.trim().lowercase() }
                    if (allRemote.isNotEmpty()) {
                        _adminMembersList.value = allRemote
                    }
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to load in-memory members: ${e.localizedMessage}")
                }
            }
            val remoteNotifications = try { CloudSyncClient.fetchNotifications(url) } catch (e: Exception) { Log.e("AppViewModel", "Failed to fetch notifications", e); null }
            val remoteEnrollments = try { CloudSyncClient.fetchEnrollments(url) } catch (e: Exception) { Log.e("AppViewModel", "Failed to fetch enrollments", e); null }
            val remoteArticles = try { CloudSyncClient.fetchArticles(url) } catch (e: Exception) { Log.e("AppViewModel", "Failed to fetch articles", e); null }
            val remoteTombstonesList = try { CloudSyncClient.fetchTombstones(url) } catch (e: Exception) { Log.e("AppViewModel", "Failed to fetch tombstones", e); null }

            val localTombstones = getTombstones(userEmail)
            val mergedTombstones = if (remoteTombstonesList != null) {
                (localTombstones + remoteTombstonesList).toSet()
            } else {
                localTombstones
            }

            if (mergedTombstones != localTombstones) {
                val suffix = if (userEmail.isBlank()) "" else "_${userEmail.trim().lowercase()}"
                sharedPrefs.edit().putStringSet("sync_tombstones$suffix", mergedTombstones.toMutableSet()).apply()
            }

            if (remoteTombstonesList != null && mergedTombstones.size > remoteTombstonesList.size && isUserAdmin) {
                try {
                    CloudSyncClient.uploadTombstones(url, mergedTombstones.toList())
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to upload merged tombstones to cloud", e)
                }
            }

            val isFirstSync = !sharedPrefs.getBoolean(hasSyncedKey, false) && (
                (remoteActivities != null && remoteActivities.isNotEmpty()) ||
                (remoteMembers != null && remoteMembers.isNotEmpty()) ||
                (remoteArticles != null && remoteArticles.isNotEmpty())
            )

            // 2. Fetch local data from Room
            val localActivities = repository.allActivities.first()
            val localMembers = repository.allMembers.first()
            val localNotifications = repository.allNotifications.first()
            val localEnrollments = repository.allEnrollments.first()
            val localArticles = repository.allArticles.first()

            // 3. Bidirectional merge (with defensive non-wipe fallback if downloads failed)
            // Merging Activities with smart overwrite prevention
            val mergedActivities = if (remoteActivities != null) {
                smartMergeActivities(localActivities, remoteActivities, isFirstSync, isUserAdmin)
            } else {
                localActivities
            }

            // Merging Members securely to preserve Admin-assigned carnet images and key updates Bidirectionally
            val mergedMembers = if (remoteMembers != null) {
                smartMergeMembers(localMembers, remoteMembers, isFirstSync)
            } else {
                localMembers
            }

            // Merging Enrollments
            val mergedEnrollments = if (remoteEnrollments != null) {
                if (isFirstSync) {
                    // Device switch / First sync: Retrieve all active registrations from the cloud to restore user's session
                    (localEnrollments + remoteEnrollments).distinctBy { 
                        "${it.activityId}_${it.memberEmail.trim().lowercase()}" 
                    }
                } else {
                    // Bidirectional sync with Authority Rules:
                    // 1. For the logged-in user (self), the local database is the authority (allows adding, deleting, and editing reminders locally).
                    // 2. For other users, the cloud database is the authority (inherits updates/deletions from others, prevents overwriting with stale local copies).
                    val selfEnrollments = localEnrollments.filter { 
                        it.memberEmail.trim().lowercase() == userEmail.trim().lowercase() 
                    }
                    val otherRemoteEnrollments = remoteEnrollments.filter { 
                        it.memberEmail.trim().lowercase() != userEmail.trim().lowercase() 
                    }
                    
                    // If the logged-in user deleted their local enrollment, it is omitted from selfEnrollments, and thus omitted from the cloud.
                    // If it is present, it takes precedence over the cloud version of themselves.
                    (selfEnrollments + otherRemoteEnrollments).distinctBy { 
                        "${it.activityId}_${it.memberEmail.trim().lowercase()}" 
                    }
                }
            } else {
                localEnrollments
            }

            // Clean enrollments so they don't contain references to deleted activities
            val activeActivityIds = mergedActivities.map { it.id }.toSet()
            val cleanEnrollments = mergedEnrollments.filter { it.activityId in activeActivityIds }

            // Merging Articles/Reports with smart overwrite prevention
            val mergedArticles = if (remoteArticles != null) {
                smartMergeArticles(localArticles, remoteArticles, isFirstSync, isUserAdmin)
            } else {
                localArticles
            }

            // Merging Notifications (separate local filtered vs. cloud persisted lists)
            val localMergedNotifications = if (remoteNotifications != null) {
                smartMergeNotifications(localNotifications, remoteNotifications, isFirstSync, filterByTombstones = true, isAdmin = isUserAdmin)
            } else {
                localNotifications
            }
            val cloudMergedNotifications = if (remoteNotifications != null) {
                smartMergeNotifications(localNotifications, remoteNotifications, isFirstSync, filterByTombstones = false, isAdmin = isUserAdmin)
            } else {
                null
            }

            // 4. Upload merged data back to Cloud (Cooperative Bidirectional Synchronization)
            // To ensure all data created or altered locally is fully published and synchronized with the cloud backend,
            // we perform a complete, robust push of all tables bidirectionally for all roles.
            // But we ONLY upload if we successfully fetched the remote state (to prevent corrupting cloud data with old state in case of fetch failure).
            val uploadFailedModules = mutableListOf<String>()

            if (isUserAdmin && remoteActivities != null) {
                try {
                    CloudSyncClient.uploadActivities(url, mergedActivities)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed uploading activities back to cloud", e)
                    uploadFailedModules.add("actividades")
                }
            }
            if (userEmail.isNotBlank() && remoteMembers != null) {
                // All users (including coordinator/admin) only sync and upload their own profile in background sync
                val selfProfile = mergedMembers.find { it.email.trim().lowercase() == userEmail.trim().lowercase() }
                if (selfProfile != null) {
                    try {
                        CloudSyncClient.uploadSingleMember(url, selfProfile)
                    } catch (e: Exception) {
                        Log.e("AppViewModel", "Failed uploading user profile back to cloud", e)
                        uploadFailedModules.add("perfil")
                    }
                }
            }
            if (isUserAdmin) {
                if (remoteNotifications != null && cloudMergedNotifications != null) {
                    try {
                        CloudSyncClient.uploadNotifications(url, cloudMergedNotifications)
                    } catch (e: Exception) {
                        Log.e("AppViewModel", "Failed uploading merged notifications back to cloud", e)
                        uploadFailedModules.add("notificaciones")
                    }
                }
                if (remoteArticles != null) {
                    try {
                        CloudSyncClient.uploadArticles(url, mergedArticles)
                    } catch (e: Exception) {
                        Log.e("AppViewModel", "Failed uploading merged articles back to cloud", e)
                        uploadFailedModules.add("artículos")
                    }
                }
            }
            if (remoteEnrollments != null) {
                try {
                    CloudSyncClient.uploadEnrollments(url, cleanEnrollments)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed uploading clean enrollments back to cloud", e)
                    uploadFailedModules.add("inscripciones")
                }
            }
            
            // Sync preferences bidirectionally using timestamps
            val remotePrefs = try { CloudSyncClient.fetchPreferences(url, userEmail) } catch (e: Exception) { null }
            val localPrefs = getLocalPreferences()
            if (remotePrefs != null) {
                if (localPrefs.lastUpdated > remotePrefs.lastUpdated) {
                    // Local is newer, upload local to cloud
                    try {
                        CloudSyncClient.uploadPreferences(url, localPrefs, userEmail)
                    } catch (e: Exception) {
                        Log.e("AppViewModel", "Failed to upload newer local preferences: ${e.localizedMessage}")
                    }
                } else if (remotePrefs.lastUpdated > localPrefs.lastUpdated) {
                    // Cloud is newer, apply cloud to local
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        applyCloudPreferences(remotePrefs)
                    }
                } else {
                    // Timestamps are equal, make sure local is synced or keep remote
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        applyCloudPreferences(remotePrefs)
                    }
                }
            } else {
                try {
                    CloudSyncClient.uploadPreferences(url, localPrefs, userEmail)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to upload preferences: ${e.localizedMessage}")
                }
            }

            // 5. Overwrite local Room database with the merged data
            repository.overwriteActivities(mergedActivities)
            
            // Clean local database to ALWAYS ONLY contain this specific logged-in user's profile on their device
            val filteredMembers = if (userEmail.isNotBlank()) {
                mergedMembers.filter { it.email.trim().lowercase() == userEmail.trim().lowercase() }
            } else {
                mergedMembers
            }
            repository.overwriteMembers(filteredMembers)
            
            repository.overwriteNotifications(localMergedNotifications)
            repository.overwriteEnrollments(cleanEnrollments)
            repository.overwriteArticles(mergedArticles)

            // Save first sync completion flag
            sharedPrefs.edit().putBoolean(hasSyncedKey, true).apply()

            // Update active session profile if userEmail is set and profile exists
            if (userEmail.isNotBlank()) {
                val freshSelf = mergedMembers.find { it.email.trim().lowercase() == userEmail.trim().lowercase() }
                if (freshSelf != null) {
                    _loggedInMember.value = freshSelf
                }
            }

            val statusSuffix = if (uploadFailedModules.isNotEmpty()) {
                " (Sincronización hacia la nube incompleta en: ${uploadFailedModules.joinToString(", ")})"
            } else {
                ""
            }

            "Sincronización Completa ✓ Sincronizados ${mergedArticles.size} artículos, ${mergedActivities.size} eventos, ${mergedMembers.size} perfiles y ${mergedEnrollments.size} inscripciones.$statusSuffix"
        }
    }

    fun syncWithCloud(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val url = _cloudSyncUrl.value
        if (url.isBlank()) {
            _syncState.value = SyncState.Error("La URL de sincronización con la nube está vacía.")
            onComplete(false, "URL vacía")
            return
        }

        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                val result = performCloudMerge(url)
                _syncState.value = SyncState.Success(result)
                if (_prefNotifNube.value) {
                    val notif = EcoNotification(
                        title = "Nube Sincronizada ☁️",
                        message = result,
                        timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                    )
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        repository.insertNotification(notif)
                    }
                    triggerSystemNotification("Nube Sincronizada ☁️", result)
                }
                onComplete(true, result)
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Error de red o conexión fallida."
                _syncState.value = SyncState.Error(errorMsg)
                onComplete(false, errorMsg)
            }
        }
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    sealed interface CloudDiagnosisState {
        object Idle : CloudDiagnosisState
        object Scanning : CloudDiagnosisState
        data class Success(val report: CloudDiagnosisReport) : CloudDiagnosisState
        data class Error(val message: String) : CloudDiagnosisState
    }

    data class CloudDiagnosisReport(
        val urlReachable: Boolean = false,
        val latencyMs: Long = 0L,
        val formatReadable: Boolean = false,
        val localActivities: Int = 0,
        val localMembers: Int = 0,
        val localNotifications: Int = 0,
        val localEnrollments: Int = 0,
        val localArticles: Int = 0,
        val localReminders: Int = 0,
        val remoteActivities: Int = 0,
        val remoteMembers: Int = 0,
        val remoteNotifications: Int = 0,
        val remoteEnrollments: Int = 0,
        val remoteArticles: Int = 0,
        val remoteReminders: Int = 0,
        val localPrefsCount: Int = 0,
        val remotePrefsCount: Int = 0,
        val decryptSuccessMembersCount: Int = 0,
        val decryptFailureMembersCount: Int = 0,
        val recommendations: List<String> = emptyList(),
        val statusMessage: String = "",
        val firebaseAuthConnected: Boolean = false,
        val firebaseRtdbConnected: Boolean = false,
        val activeSessionSynced: Boolean = true,
        val activeSessionDiscrepancy: String? = null
    )

    private val _cloudDiagnosis = MutableStateFlow<CloudDiagnosisState>(CloudDiagnosisState.Idle)
    val cloudDiagnosis: StateFlow<CloudDiagnosisState> = _cloudDiagnosis.asStateFlow()

    fun resetCloudDiagnosis() {
        _cloudDiagnosis.value = CloudDiagnosisState.Idle
    }

    fun scanCloudDatabase() {
        val url = _cloudSyncUrl.value
        if (url.isBlank()) {
            _cloudDiagnosis.value = CloudDiagnosisState.Error("La URL de la base de datos está vacía. Configure una URL válida primero.")
            return
        }

        _cloudDiagnosis.value = CloudDiagnosisState.Scanning

        viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                
                // Fetch local counters
                val localAct = repository.allActivities.first().size
                val localMem = repository.allMembers.first().size
                val localNot = repository.allNotifications.first().size
                val localRawEnrollments = repository.allEnrollments.first()
                val localEnr = localRawEnrollments.size
                val localArt = repository.allArticles.first().size
                
                val localReminders = localRawEnrollments.count { it.reminderMinutes >= 0 }
                val localPrefsCount = if (_loggedInMember.value != null) 1 else 0

                // Request remote
                val remoteDb = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    CloudSyncClient.fetchFullDatabase(url)
                }
                
                val latency = System.currentTimeMillis() - startTime

                // Check security decryption rate
                var decryptSuccess = 0
                var decryptFailure = 0
                val customKey = _cloudSecurityKey.value

                for (m in remoteDb.members) {
                    val isFail = SecurityUtils.isEncrypted(m.email) || SecurityUtils.isEncrypted(m.fullName)
                    if (isFail) {
                        decryptFailure++
                    } else {
                        decryptSuccess++
                    }
                }

                // Check active session status and look for discrepancies
                val activeMember = _loggedInMember.value
                var activeSessionSynced = true
                var activeSessionDiscrepancy: String? = null

                if (activeMember != null) {
                    val remoteMem = remoteDb.members.find { it.email.trim().lowercase() == activeMember.email.trim().lowercase() }
                    if (remoteMem == null) {
                        activeSessionSynced = false
                        activeSessionDiscrepancy = "La sesión activa (${activeMember.email}) no se ha respaldado o propagado a la nube."
                    } else {
                        val diffPoints = activeMember.points != remoteMem.points
                        val diffRole = activeMember.role != remoteMem.role
                        val diffName = activeMember.fullName != remoteMem.fullName

                        if (diffPoints || diffRole || diffName) {
                            activeSessionSynced = false
                            val details = mutableListOf<String>()
                            if (diffPoints) details.add("Puntos (${activeMember.points} locales vs ${remoteMem.points} en nube)")
                            if (diffRole) details.add("Rol (${activeMember.role} local vs ${remoteMem.role} en nube)")
                            if (diffName) details.add("Nombre (${activeMember.fullName} local vs ${remoteMem.fullName} en nube)")
                            activeSessionDiscrepancy = "Datos desfasados: " + details.joinToString(", ")
                        }
                    }
                }

                // Verify Firebase Core & Auth Connectivity
                val firebaseAuthConnected = try {
                    com.example.data.api.FirebaseHelper.getAuthOrNull() != null
                } catch (e: Exception) {
                    false
                }

                val firebaseRtdbConnected = try {
                    com.example.data.api.FirebaseHelper.getDatabaseOrNull() != null
                } catch (e: Exception) {
                    false
                }

                val recs = mutableListOf<String>()
                
                // Conectividad general y Firebase/Firestore
                if (firebaseAuthConnected && firebaseRtdbConnected) {
                    recs.add("🔥 Firebase / Realtime DB: Coherencia y canal de autenticación activo con la nube.")
                } else {
                    recs.add("⚠️ Canal Firebase inactivo: El SDK local utiliza configuraciones fuera de línea o parciales. Siga instucciones de entorno.")
                }

                // Inconsistencias de Sesiones Activas
                if (activeMember != null) {
                    if (activeSessionSynced) {
                        recs.add("✅ Sesión Activa Sincronizada: El perfil del facilitador/miembro actual (${activeMember.email}) coincide con la base remota.")
                    } else {
                        recs.add("⚠️ Alerta de Inconsistencia: ${activeSessionDiscrepancy ?: ""}. Presione sincronización manual para corregir el desfase.")
                    }
                } else {
                    recs.add("ℹ️ Sin Sesión Activa: Inicie sesión para evaluar desajustes en el perfil de su usuario actual contra el servidor.")
                }

                // Generate specific advice
                if (decryptFailure > 0) {
                    recs.add("⚠️ Clave de seguridad desincronizada: Se encontraron a nivel de nube $decryptFailure perfiles ilegibles con la clave de descifrado actual ($customKey). Verifique que tenga la clave idéntica del administrador.")
                } else if (remoteDb.members.isNotEmpty()) {
                    recs.add("✅ Criptografía robusta: El 100% de los perfiles en la nube ($decryptSuccess/$decryptSuccess) se descifraron correctamente con la clave de seguridad activa de la app.")
                }

                val remoteReminders = remoteDb.enrollments.count { it.reminderMinutes >= 0 }
                val remotePrefsCount = remoteDb.userPreferences?.size ?: (if (remoteDb.preferences != null) 1 else 0)

                if (localReminders != remoteReminders) {
                    val diffRem = localReminders - remoteReminders
                    if (diffRem > 0) {
                        recs.add("⏰ Recordatorios locales: Hay $diffRem recordatorios activos en la base local que no se han propagado a la nube. Sincronice para guardarlos.")
                    } else {
                        recs.add("⏰ Recordatorios remotos: Hay ${-diffRem} alertas de eventos guardadas en la nube. Se asimilarán automáticamente.")
                    }
                } else if (localReminders > 0) {
                    recs.add("✅ Coherencia de recordatorios: Todos los recordatorios activos ($localReminders) están perfectamente sincronizados con la nube.")
                }

                if (localPrefsCount != remotePrefsCount) {
                    recs.add("⚙️ Sincronización de preferencias: Las preferencias individuales de usuario se sincronizaron (Local: $localPrefsCount vs Nube: $remotePrefsCount perfiles).")
                }

                val diffAct = localAct - remoteDb.activities.size
                if (diffAct > 0) {
                    recs.add("💡 Desviación local: Existen $diffAct actividades locales nuevas no sincronizadas a nivel de nube. Pruebe subir la consola para actualizar la nube.")
                } else if (diffAct < 0) {
                    recs.add("💡 Desviación remota: Hay ${-diffAct} actividades en la nube pendientes de asimilar de forma local. Se actualizarán en la próxima carga automática.")
                }

                if (remoteDb.activities.isEmpty() && remoteDb.members.isEmpty()) {
                    recs.add("ℹ️ Servidor sin registros: No hay información hospedada en esta base de datos remota para Jóvenes y Ecosistemas.")
                }

                recs.add("🌐 Conectividad estable: Servidor respondido de forma correcta con latencia de diagnóstico HTTP de ${latency}ms.")

                val report = CloudDiagnosisReport(
                    urlReachable = true,
                    latencyMs = latency,
                    formatReadable = true,
                    localActivities = localAct,
                    localMembers = localMem,
                    localNotifications = localNot,
                    localEnrollments = localEnr,
                    localArticles = localArt,
                    localReminders = localReminders,
                    remoteActivities = remoteDb.activities.size,
                    remoteMembers = remoteDb.members.size,
                    remoteNotifications = remoteDb.notifications.size,
                    remoteEnrollments = remoteDb.enrollments.size,
                    remoteArticles = remoteDb.articles.size,
                    remoteReminders = remoteReminders,
                    localPrefsCount = localPrefsCount,
                    remotePrefsCount = remotePrefsCount,
                    decryptSuccessMembersCount = decryptSuccess,
                    decryptFailureMembersCount = decryptFailure,
                    recommendations = recs,
                    statusMessage = "Escaneo completado exitosamente.",
                    firebaseAuthConnected = firebaseAuthConnected,
                    firebaseRtdbConnected = firebaseRtdbConnected,
                    activeSessionSynced = activeSessionSynced,
                    activeSessionDiscrepancy = activeSessionDiscrepancy
                )

                _cloudDiagnosis.value = CloudDiagnosisState.Success(report)

            } catch (e: Exception) {
                _cloudDiagnosis.value = CloudDiagnosisState.Error(
                    "Fallo de conexión o escaneo de nube:\n" + (e.localizedMessage ?: "Conexión de red interrumpida o host inalcanzable.")
                )
            }
        }
    }

    fun autoSync() {
        val url = _cloudSyncUrl.value
        if (url.isBlank()) return
        viewModelScope.launch {
            try {
                performCloudMerge(url)
            } catch (e: Exception) {
                Log.e("AppViewModel", "AutoSync background failed silently: ${e.localizedMessage}")
            }
        }
    }

    private fun convertImageToBase64(filePath: String): String? {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists()) return null
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(filePath, options)
            val targetSize = 600
            var scale = 1
            while (options.outWidth / scale / 2 >= targetSize && options.outHeight / scale / 2 >= targetSize) {
                scale *= 2
            }
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            val bitmap = android.graphics.BitmapFactory.decodeFile(filePath, decodeOptions) ?: return null
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun sendBugReportDirectly(
        comment: String,
        photoPath: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val member = _loggedInMember.value
                val id = UUID.randomUUID().toString().take(8)
                val timestampStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                
                // Construct a detailed and clean message for the Administrator
                val fullMessageBuilder = java.lang.StringBuilder()
                fullMessageBuilder.append("🐞 REPORTE DE ERROR / ALERTA\n\n")
                fullMessageBuilder.append("• Reportero/a: ${member?.fullName ?: "Invitado"}\n")
                fullMessageBuilder.append("• Correo: ${member?.email ?: "No disponible"}\n")
                fullMessageBuilder.append("• Rol: ${member?.role ?: "No disponible"}\n")
                fullMessageBuilder.append("• País: ${member?.country ?: "No disponible"}\n\n")
                fullMessageBuilder.append("--- DETALLE DEL BUG ---\n")
                fullMessageBuilder.append(comment)

                val newNotification = EcoNotification(
                    title = "🐛 Bug: ${if (comment.length > 25) comment.take(22).trim() + "..." else comment}",
                    message = fullMessageBuilder.toString(),
                    timestamp = timestampStr,
                    isRead = false,
                    photoUri = photoPath // This is already the Base64 representation!
                )

                // 1. Insert locally to database
                repository.insertNotification(newNotification)

                // 2. Perform background autoSync to update the cloud database (kvdb / jsonblob / cloud database)
                autoSync()

                launch(Dispatchers.Main) {
                    onSuccess()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Error al procesar el reporte de bug en las alertas")
                }
            }
        }
    }

    fun insertGlobalAlert(
        title: String,
        message: String,
        buttonText: String,
        photoPath: String? = null,
        broadcastIcon: String? = null,
        expiryMillis: Long = 0L,
        actionUrl: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val timestampStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                val base64Photo = photoPath?.let { convertImageToBase64(it) } ?: photoPath
                val newNotification = EcoNotification(
                    title = title,
                    message = message,
                    timestamp = timestampStr,
                    isRead = false,
                    photoUri = base64Photo,
                    isGlobalAlert = true,
                    alertButtonText = buttonText.ifBlank { "Aceptar" },
                    broadcastIcon = broadcastIcon,
                    expiryMillis = expiryMillis,
                    actionUrl = actionUrl
                )
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.insertNotification(newNotification)
                }
                autoSync()
                launch(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Error al guardar el anuncio global")
                }
            }
        }
    }
}
