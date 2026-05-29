package com.example.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val sharedPrefs = application.getSharedPreferences("je_app_prefs", Context.MODE_PRIVATE)

    private val defaultUrl = "https://jsonblob.com/api/jsonBlob/019e7504-9660-7646-8a0a-837710498432"

    private val _cloudSyncUrl = MutableStateFlow(
        run {
            val saved = sharedPrefs.getString("cloud_sync_url", null)
            val oldUrls = setOf(
                "https://je-ecosistemas-cloud-sync-default-rtdb.firebaseio.com/",
                "https://kvdb.io/je-eco-nvyqzw",
                "https://jsonblob.com/api/jsonBlob/019e680e-91f0-7e6e-b236-4ae22640dccb"
            )
            if (saved == null || saved in oldUrls || saved.isBlank()) {
                defaultUrl
            } else {
                saved
            }
        }
    )
    val cloudSyncUrl: StateFlow<String> = _cloudSyncUrl.asStateFlow()

    fun updateCloudSyncUrl(newUrl: String) {
        val cleanUrl = newUrl.trim()
        val oldUrls = setOf(
            "https://je-ecosistemas-cloud-sync-default-rtdb.firebaseio.com/",
            "https://kvdb.io/je-eco-nvyqzw",
            "https://jsonblob.com/api/jsonBlob/019e680e-91f0-7e6e-b236-4ae22640dccb"
        )
        val urlToSave = if (cleanUrl in oldUrls || cleanUrl.isBlank()) defaultUrl else cleanUrl
        sharedPrefs.edit().putString("cloud_sync_url", urlToSave).apply()
        _cloudSyncUrl.value = urlToSave
    }

    private val _prefNotifActividades = MutableStateFlow(sharedPrefs.getBoolean("pref_notif_actividades", true))
    val prefNotifActividades = _prefNotifActividades.asStateFlow()

    private val _prefNotifNovedades = MutableStateFlow(sharedPrefs.getBoolean("pref_notif_novedades", true))
    val prefNotifNovedades = _prefNotifNovedades.asStateFlow()

    private val _prefNotifNube = MutableStateFlow(sharedPrefs.getBoolean("pref_notif_nube", true))
    val prefNotifNube = _prefNotifNube.asStateFlow()

    private val _prefNotifSistema = MutableStateFlow(sharedPrefs.getBoolean("pref_notif_sistema", true))
    val prefNotifSistema = _prefNotifSistema.asStateFlow()

    fun updatePrefNotifActividades(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("pref_notif_actividades", enabled).apply()
        _prefNotifActividades.value = enabled
    }

    fun updatePrefNotifNovedades(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("pref_notif_novedades", enabled).apply()
        _prefNotifNovedades.value = enabled
    }

    fun updatePrefNotifNube(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("pref_notif_nube", enabled).apply()
        _prefNotifNube.value = enabled
    }

    fun updatePrefNotifSistema(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("pref_notif_sistema", enabled).apply()
        _prefNotifSistema.value = enabled
    }

    fun triggerSystemNotification(title: String, message: String) {
        if (title == "Inscripción en Actividad 🌿") {
            val user = _loggedInMember.value
            val isUserAdmin = user?.isAdmin == true || user?.email == "coordinador@je.org"
            if (!isUserAdmin) return
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
        CloudSyncClient.onNewUrlGenerated = { newUrl ->
            updateCloudSyncUrl(newUrl)
        }
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
        }
    }

    // Login logic
    fun login(email: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val member = repository.getMemberByEmailDirect(email.trim().lowercase())
            if (member != null && member.password == password) {
                _loggedInMember.value = member
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    // Logout
    fun logout() {
        _loggedInMember.value = null
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
                repository.insertMember(newMember)
                
                // Track creation in status alerts
                val notif = EcoNotification(
                    title = "Miembro Creado 📋",
                    message = "Admin creó cuenta para: $fullName",
                    timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                )
                repository.insertNotification(notif)
                if (_prefNotifSistema.value) {
                    triggerSystemNotification("Miembro Creado 📋", "Admin creó cuenta para: $fullName")
                }
                viewModelScope.launch(Dispatchers.Main) { onResult(true) }
            }
        }
    }

    // Modify custom member card by coordinator from console
    fun modifyMemberProfile(member: Member) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateMember(member)
            // Update active session if they modified themselves
            if (_loggedInMember.value?.email == member.email) {
                _loggedInMember.value = member
            }

            // Log update
            val notif = EcoNotification(
                title = "Perfil Actualizado ✍️",
                message = "Se editó el carné de ${member.fullName}",
                timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            )
            repository.insertNotification(notif)
            if (_prefNotifSistema.value) {
                triggerSystemNotification("Perfil Actualizado ✍️", "Se editó el carné de ${member.fullName}")
            }
        }
    }

    // Suppress a member account
    fun removeMember(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMemberByEmail(email)
            if (_loggedInMember.value?.email == email) {
                _loggedInMember.value = null
            }
            // Alert coordinator
            val notif = EcoNotification(
                title = "Miembro Removido 🗑️",
                message = "Se eliminó el acceso de: $email",
                timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            )
            repository.insertNotification(notif)
            if (_prefNotifSistema.value) {
                triggerSystemNotification("Miembro Removido 🗑️", "Se eliminó el acceso de: $email")
            }
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
        location: String,
        country: String,
        category: String,
        organizer: String,
        eventType: String = "Voluntariado"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val activity = EcoActivity(
                title = title,
                description = description,
                date = date,
                location = location,
                country = country,
                category = category,
                organizer = organizer,
                eventType = eventType
            )
            repository.insertActivity(activity)

            // Alert notification that activity was programmed
            val calendarPrefix = if (googleCalendarLinked.value) "Sincronizado con Google Calendar 📅: " else ""
            val notif = EcoNotification(
                title = "Actividad Creada 🌾",
                message = "${calendarPrefix}Se programó '$title' para el $date de junio",
                timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            )
            repository.insertNotification(notif)
            if (_prefNotifActividades.value) {
                triggerSystemNotification("Nueva Actividad 🌾", "${calendarPrefix}Se programó '$title' para el $date de junio")
            }
        }
    }

    fun deleteActivity(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteActivity(id)
        }
    }

    // Refactored enrollment mechanics supporting multiple users
    fun toggleEnrollment(activity: EcoActivity, member: Member) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getEnrollmentDirect(activity.id, member.email)
            if (existing != null) {
                repository.deleteEnrollment(activity.id, member.email)
                // Deduct points from member
                val updatedMember = member.copy(points = (member.points - 15).coerceAtLeast(0))
                repository.updateMember(updatedMember)
                // If the logged in member was this member, update our active session state
                if (_loggedInMember.value?.email == member.email) {
                    _loggedInMember.value = updatedMember
                }
            } else {
                val enrollment = EcoEnrollment(
                    activityId = activity.id,
                    activityTitle = activity.title,
                    memberEmail = member.email,
                    memberName = member.fullName
                )
                repository.insertEnrollment(enrollment)
                // Add 15 points to member
                val updatedMember = member.copy(points = member.points + 15)
                repository.updateMember(updatedMember)
                // If the logged in member was this member, update our active session state
                if (_loggedInMember.value?.email == member.email) {
                    _loggedInMember.value = updatedMember
                }

                // Generate coordinator notification
                val timeStamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                val notification = EcoNotification(
                    title = "Inscripción en Actividad 🌿",
                    message = "${member.fullName} (${member.role}) se inscribió a '${activity.title}'",
                    timestamp = timeStamp
                )
                repository.insertNotification(notification)
                if (_prefNotifActividades.value) {
                    triggerSystemNotification("Inscripción en Actividad 🌿", "${member.fullName} (${member.role}) se inscribió a '${activity.title}'")
                }
            }
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
        }
    }

    fun updateProfilePhoto(photoUri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val active = _loggedInMember.value
            if (active != null) {
                val updated = active.copy(photoUri = photoUri)
                repository.updateMember(updated)
                _loggedInMember.value = updated
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
        }
    }

    fun publishLocalArticle(title: String, content: String, category: String, region: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newArticle = EcoArticle(
                title = title,
                content = content,
                category = category,
                region = region,
                publishDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                isAiGenerated = false
            )
            repository.insertArticle(newArticle)

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
            val notif = EcoNotification(
                title = "Artículo Publicado 📰",
                message = "Se publicó '$title' en la sección de Novedades.",
                timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            )
            repository.insertNotification(notif)
            if (_prefNotifNovedades.value) {
                triggerSystemNotification("Artículos y Novedades 📰", "Se publicó el artículo: $title")
            }
        }
    }

    fun deleteArticle(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteArticle(id)
        }
    }

    fun clearNotification(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteNotificationById(id)
        }
    }

    fun markNotificationsRead() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markAllNotificationsAsRead()
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
                val notif = EcoNotification(
                    title = "Asesoría AI Recibida 🧠",
                    message = "Se generó: $parsedTitle.",
                    timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                )
                repository.insertNotification(notif)
                if (_prefNotifNovedades.value) {
                    triggerSystemNotification("Asesoría AI Recibida 🧠", "Se generó: $parsedTitle")
                }

            } catch (e: Exception) {
                _aiState.value = AiState.Error(e.localizedMessage ?: "Error de comunicación con el consultor AI")
            }
        }
    }

    fun resetAiState() {
        _aiState.value = AiState.None
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
                val member = _loggedInMember.value
                val isCoordinator = member?.email == "coordinador@je.org" || member?.isAdmin == true

                val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    if (isCoordinator) {
                        // --- COORD / ADMIN MODE: Push state, pull remote joins ---
                        val localActivities = repository.allActivities.first()
                        val localMembers = repository.allMembers.first()
                        val localNotifications = repository.allNotifications.first()
                        val localEnrollments = repository.allEnrollments.first()

                        val remoteEnrollments = CloudSyncClient.fetchEnrollments(url)
                        val mergedEnrollments = (localEnrollments + remoteEnrollments).distinctBy { "${it.activityId}_${it.memberEmail}" }

                        val actSuccess = CloudSyncClient.uploadActivities(url, localActivities)
                        val memSuccess = CloudSyncClient.uploadMembers(url, localMembers)
                        val notSuccess = CloudSyncClient.uploadNotifications(url, localNotifications)
                        val enrolSuccess = CloudSyncClient.uploadEnrollments(url, mergedEnrollments)

                        if (actSuccess && memSuccess && notSuccess && enrolSuccess) {
                            repository.overwriteEnrollments(mergedEnrollments)
                            "Consola Publicada ✓ ${localActivities.size} actividades y ${localMembers.size} miembros actualizados en la nube."
                        } else {
                            throw IllegalStateException("Fallo de red al publicar Consola.")
                        }
                    } else {
                        // --- REGULAR MEMBER MODE: Pull state, merge/push own joins ---
                        val remoteActivities = CloudSyncClient.fetchActivities(url)
                        val remoteMembers = CloudSyncClient.fetchMembers(url)
                        val remoteNotifications = CloudSyncClient.fetchNotifications(url)
                        val remoteEnrollments = CloudSyncClient.fetchEnrollments(url)

                        if (remoteActivities.isEmpty() && remoteMembers.isEmpty()) {
                            throw IllegalStateException("La base de datos en la nube está vacía.\n\nEl Coordinador del proyecto debe ingresar con su cuenta (coordinador@je.org o administrador) y presionar 'Sincronizar Consola Ahora' desde el Panel de Administración primero para inicializar la base de datos.")
                        }

                        val localEnrollments = repository.allEnrollments.first()
                        val userEmail = member?.email ?: ""
                        val myEnrollments = localEnrollments.filter { it.memberEmail == userEmail }
                        val mergedEnrollments = (remoteEnrollments + myEnrollments).distinctBy { "${it.activityId}_${it.memberEmail}" }

                        if (myEnrollments.isNotEmpty()) {
                            CloudSyncClient.uploadEnrollments(url, mergedEnrollments)
                        }

                        repository.overwriteActivities(remoteActivities)
                        repository.overwriteMembers(remoteMembers)
                        repository.overwriteNotifications(remoteNotifications)
                        repository.overwriteEnrollments(mergedEnrollments)

                        val freshSelf = remoteMembers.find { it.email == userEmail }
                        if (freshSelf != null) {
                            _loggedInMember.value = freshSelf
                        }

                        "Sincronizado ✓ Cargados ${remoteActivities.size} eventos y ${remoteMembers.size} perfiles."
                    }
                }

                _syncState.value = SyncState.Success(result)
                val notif = EcoNotification(
                    title = "Nube Sincronizada ☁️",
                    message = result,
                    timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                )
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.insertNotification(notif)
                }
                if (_prefNotifNube.value) {
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
}
