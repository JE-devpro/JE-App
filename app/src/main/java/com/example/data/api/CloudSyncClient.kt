package com.example.data.api

import android.util.Log
import com.example.data.EcoActivity
import com.example.data.EcoEnrollment
import com.example.data.EcoNotification
import com.example.data.EcoArticle
import com.example.data.Member
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class CloudDatabase(
    val activities: List<EcoActivity> = emptyList(),
    val members: List<Member> = emptyList(),
    val notifications: List<EcoNotification> = emptyList(),
    val enrollments: List<EcoEnrollment> = emptyList(),
    val articles: List<EcoArticle> = emptyList(),
    val preferences: com.example.data.CloudPreferences? = null,
    val userPreferences: Map<String, com.example.data.CloudPreferences>? = emptyMap(),
    val tombstones: List<String> = emptyList()
)

object CloudSyncClient {
    private const val TAG = "CloudSyncClient"

    var cloudSecurityKey: String = "JE-Organizacion-EcoTech-2026"

    var onNewUrlGenerated: ((String) -> Unit)? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Adapters for parsing lists/maps
    private val activitiesAdapter = moshi.adapter<List<EcoActivity>>(
        Types.newParameterizedType(List::class.java, EcoActivity::class.java)
    )
    private val membersMapAdapter = moshi.adapter<Map<String, Member>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Member::class.java)
    )
    private val notificationsAdapter = moshi.adapter<List<EcoNotification>>(
        Types.newParameterizedType(List::class.java, EcoNotification::class.java)
    )
    private val enrollmentsAdapter = moshi.adapter<List<EcoEnrollment>>(
        Types.newParameterizedType(List::class.java, EcoEnrollment::class.java)
    )
    private val articlesGroupAdapter = moshi.adapter<List<EcoArticle>>(
        Types.newParameterizedType(List::class.java, EcoArticle::class.java)
    )
    private val preferencesAdapter = moshi.adapter(com.example.data.CloudPreferences::class.java)
    private val memberAdapter = moshi.adapter(Member::class.java)
    private val userPreferencesMapAdapter = moshi.adapter<Map<String, com.example.data.CloudPreferences>>(
        Types.newParameterizedType(Map::class.java, String::class.java, com.example.data.CloudPreferences::class.java)
    )
    private val tombstonesAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    private fun isJsonBlob(url: String): Boolean {
        return url.contains("jsonblob.com", ignoreCase = true)
    }

    private fun throwNetworkError(code: Int, action: String): Nothing {
        val message = when (code) {
            401, 403 -> "Fallo de Permisos (HTTP $code) al $action.\n\nAsegúrate de configurar las Reglas de la Base de Datos para permitir lectura y escritura pública."
            404 -> "Base de datos no encontrada (HTTP 404) al $action.\n\nVerifica que la URL del panel de Nube sea válida y exista."
            400 -> "Petición no válida (HTTP 400) al $action.\n\nVerifica la estructura de tus datos o URL."
            else -> "Error del Servidor Nube (HTTP $code) al $action."
        }
        throw java.io.IOException(message)
    }

    private fun wrapException(action: String, e: Exception): Nothing {
        val msg = e.localizedMessage ?: "Fallo de red desconocido"
        if (e is java.net.UnknownHostException) {
            throw java.io.IOException("Error de Conexión: No se pudo resolver el host ($msg). Verifica la URL de la base de datos o si tu dispositivo tiene acceso a internet.\n\nURL configurada: $msg")
        } else if (e is java.net.SocketTimeoutException) {
            throw java.io.IOException("Error de Conexión: Tiempo de espera agotado al $action. Verifica tu conexión de red e inténtalo de nuevo.")
        } else if (e is java.io.IOException) {
            throw e
        } else {
            throw java.io.IOException("Error al $action: $msg")
        }
    }

    private fun parseDatabase(json: String): CloudDatabase {
        return try {
            val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val mapAdapter = moshi.adapter<Map<String, Any>>(type)
            val root = mapAdapter.fromJson(json) ?: return CloudDatabase()
            
            val activitiesJson = moshi.adapter(Any::class.java).toJson(root["activities"])
            val activities = parseActivities(activitiesJson)
            
            val membersJson = moshi.adapter(Any::class.java).toJson(root["members"])
            val members = parseMembers(membersJson)
            
            val notificationsJson = moshi.adapter(Any::class.java).toJson(root["notifications"])
            val notifications = parseNotifications(notificationsJson)
            
            val enrollmentsJson = moshi.adapter(Any::class.java).toJson(root["enrollments"])
            val enrollments = parseEnrollments(enrollmentsJson)

            val articlesJson = if (root.containsKey("articles")) moshi.adapter(Any::class.java).toJson(root["articles"]) else "[]"
            val articles = parseArticlesGroup(articlesJson)

            val preferencesJson = if (root.containsKey("preferences")) moshi.adapter(Any::class.java).toJson(root["preferences"]) else null
            val preferences = if (preferencesJson != null) parsePreferences(preferencesJson) else null
            
            val userPrefsJson = if (root.containsKey("userPreferences")) moshi.adapter(Any::class.java).toJson(root["userPreferences"]) else null
            val userPreferences = mutableMapOf<String, com.example.data.CloudPreferences>()
            if (userPrefsJson != null) {
                try {
                    userPreferencesMapAdapter.fromJson(userPrefsJson)?.let {
                        userPreferences.putAll(it)
                    }
                } catch (e: Exception) {
                    // Ignore error and continue
                }
            }
            root.forEach { (key, value) ->
                if (key.startsWith("preferences_")) {
                    val hash = key.substringAfter("preferences_")
                    if (hash.isNotEmpty()) {
                        try {
                            val singleJson = moshi.adapter(Any::class.java).toJson(value)
                            val prefObj = preferencesAdapter.fromJson(singleJson)
                            if (prefObj != null) {
                                userPreferences[hash] = prefObj
                            }
                        } catch (e: Exception) {
                            // Non-blocking catch
                        }
                    }
                }
            }
            
            CloudDatabase(activities, members, notifications, enrollments, articles, preferences, userPreferences)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding database from jsonblob JSON", e)
            CloudDatabase()
        }
    }

    private fun serializeDatabase(db: CloudDatabase): String {
        val encryptedMembers = db.members.map { com.example.data.SecurityUtils.encryptMember(it, cloudSecurityKey) }
        val encryptedEnrollments = db.enrollments.map { com.example.data.SecurityUtils.encryptEnrollment(it, cloudSecurityKey) }
        val encryptedNotifications = db.notifications.map { com.example.data.SecurityUtils.encryptNotification(it, cloudSecurityKey) }

        val map = mutableMapOf<String, Any>(
            "activities" to db.activities,
            "members" to encryptedMembers,
            "notifications" to encryptedNotifications,
            "enrollments" to encryptedEnrollments,
            "articles" to db.articles
        )
        db.preferences?.let {
            map["preferences"] = it
        }
        db.userPreferences?.let {
            map["userPreferences"] = it
        }
        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        return moshi.adapter<Map<String, Any>>(type).toJson(map)
    }

    fun fetchFullDatabase(baseUrl: String): CloudDatabase {
        var trimmed = baseUrl.trim().removeSuffix("/")
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        val urlToFetch = if (isJsonBlob(trimmed)) {
            trimmed
        } else if (trimmed.contains("kvdb.io", ignoreCase = true)) {
            trimmed
        } else {
            if (trimmed.endsWith(".json")) trimmed else "$trimmed/.json"
        }
        Log.d(TAG, "Fetching full database from $urlToFetch")
        val request = Request.Builder().url(urlToFetch).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) {
                        Log.w(TAG, "Cloud database returned 404. Returning empty database configuration.")
                        return CloudDatabase()
                    }
                    throwNetworkError(response.code, "obtener base de datos")
                }
                val bodyString = response.body?.string() ?: return CloudDatabase()
                if (bodyString == "null") return CloudDatabase()
                return parseDatabase(bodyString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetchFullDatabase", e)
            wrapException("obtener base de datos", e)
        }
    }

    private fun uploadFullDatabase(baseUrl: String, db: CloudDatabase): Boolean {
        var trimmed = baseUrl.trim().removeSuffix("/")
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        val urlToUpload = if (isJsonBlob(trimmed)) {
            trimmed
        } else if (trimmed.contains("kvdb.io", ignoreCase = true)) {
            trimmed
        } else {
            if (trimmed.endsWith(".json")) trimmed else "$trimmed/.json"
        }
        Log.d(TAG, "Uploading full database to $urlToUpload")
        try {
            val json = serializeDatabase(db)
            val request = Request.Builder()
                .url(urlToUpload)
                .put(json.toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throwNetworkError(response.code, "actualizar base de datos")
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploadFullDatabase", e)
            wrapException("actualizar base de datos", e)
        }
    }

    fun fetchActivities(baseUrl: String): List<EcoActivity> {
        if (isJsonBlob(baseUrl)) {
            return fetchFullDatabase(baseUrl).activities
        }
        val url = formatUrl(baseUrl, "activities")
        Log.d(TAG, "Fetching activities from $url")
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) return emptyList()
                    throwNetworkError(response.code, "obtener actividades")
                }
                val bodyString = response.body?.string() ?: return emptyList()
                if (bodyString == "null") return emptyList()
                return parseActivities(bodyString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetchActivities", e)
            wrapException("obtener actividades", e)
        }
    }

    fun uploadActivities(baseUrl: String, list: List<EcoActivity>): Boolean {
        if (isJsonBlob(baseUrl)) {
            val db = fetchFullDatabase(baseUrl)
            val updated = db.copy(activities = list)
            return uploadFullDatabase(baseUrl, updated)
        }
        val url = formatUrl(baseUrl, "activities")
        Log.d(TAG, "Uploading ${list.size} activities to $url")
        try {
            val json = activitiesAdapter.toJson(list)
            val request = Request.Builder().url(url).put(json.toRequestBody(jsonMediaType)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throwNetworkError(response.code, "subir actividades")
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploadActivities", e)
            wrapException("subir actividades", e)
        }
    }

    fun fetchMembers(baseUrl: String): List<Member> {
        if (isJsonBlob(baseUrl)) {
            return fetchFullDatabase(baseUrl).members
        }
        val url = formatUrl(baseUrl, "members")
        Log.d(TAG, "Fetching members from $url")
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) return emptyList()
                    throwNetworkError(response.code, "obtener perfiles de miembros")
                }
                val bodyString = response.body?.string() ?: return emptyList()
                if (bodyString == "null") return emptyList()
                return parseMembers(bodyString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetchMembers", e)
            wrapException("obtener perfiles de miembros", e)
        }
    }

    fun fetchSingleMember(baseUrl: String, email: String): Member? {
        if (isJsonBlob(baseUrl)) {
            val db = fetchFullDatabase(baseUrl)
            return db.members.find { it.email.trim().lowercase() == email.trim().lowercase() }
        }
        val safeKey = com.example.data.SecurityUtils.hashPasswordSha256(email.trim().lowercase()).take(16)
        val url = formatUrl(baseUrl, "members/$safeKey")
        Log.d(TAG, "Fetching single member from $url")
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) return null
                    throwNetworkError(response.code, "obtener perfil de miembro")
                }
                val bodyString = response.body?.string() ?: return null
                if (bodyString == "null") return null
                val encMember = memberAdapter.fromJson(bodyString) ?: return null
                return com.example.data.SecurityUtils.decryptMember(encMember, cloudSecurityKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetchSingleMember", e)
            wrapException("obtener perfil de miembro", e)
        }
    }

    fun uploadMembers(baseUrl: String, members: List<Member>): Boolean {
        val cleanMembers = members.map { it.copy(email = it.email.trim().lowercase()) }
        if (isJsonBlob(baseUrl)) {
            val db = fetchFullDatabase(baseUrl)
            val updated = db.copy(members = cleanMembers)
            return uploadFullDatabase(baseUrl, updated)
        }
        val url = formatUrl(baseUrl, "members")
        Log.d(TAG, "Uploading ${cleanMembers.size} members to $url")
        try {
            val map = cleanMembers.associate { m ->
                val safeKey = com.example.data.SecurityUtils.hashPasswordSha256(m.email).take(16)
                val encrypted = com.example.data.SecurityUtils.encryptMember(m, cloudSecurityKey)
                safeKey to encrypted
            }
            val json = membersMapAdapter.toJson(map)
            val request = Request.Builder().url(url).put(json.toRequestBody(jsonMediaType)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throwNetworkError(response.code, "subir perfiles de miembros")
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploadMembers", e)
            wrapException("subir perfiles de miembros", e)
        }
    }

    fun uploadSingleMember(baseUrl: String, member: Member): Boolean {
        val cleanEmail = member.email.trim().lowercase()
        val cleanMember = member.copy(email = cleanEmail)
        if (isJsonBlob(baseUrl)) {
            val db = fetchFullDatabase(baseUrl)
            val updatedMembers = db.members.toMutableList()
            val index = updatedMembers.indexOfFirst { it.email.trim().lowercase() == cleanEmail }
            if (index != -1) {
                updatedMembers[index] = cleanMember
            } else {
                updatedMembers.add(cleanMember)
            }
            val updated = db.copy(members = updatedMembers)
            return uploadFullDatabase(baseUrl, updated)
        }
        val safeKey = com.example.data.SecurityUtils.hashPasswordSha256(cleanEmail).take(16)
        val url = formatUrl(baseUrl, "members/$safeKey")
        Log.d(TAG, "Uploading single member to $url")
        try {
            val encryptedMember = com.example.data.SecurityUtils.encryptMember(cleanMember, cloudSecurityKey)
            val json = memberAdapter.toJson(encryptedMember)
            val request = Request.Builder()
                .url(url)
                .put(json.toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throwNetworkError(response.code, "subir perfil de miembro individual")
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploadSingleMember", e)
            wrapException("subir perfil de miembro individual", e)
        }
    }

    fun deleteSingleMember(baseUrl: String, email: String): Boolean {
        if (isJsonBlob(baseUrl)) {
            val db = fetchFullDatabase(baseUrl)
            val updatedMembers = db.members.filterNot { it.email.trim().lowercase() == email.trim().lowercase() }
            val updated = db.copy(members = updatedMembers)
            return uploadFullDatabase(baseUrl, updated)
        }
        val safeKey = com.example.data.SecurityUtils.hashPasswordSha256(email.trim().lowercase()).take(16)
        val url = formatUrl(baseUrl, "members/$safeKey")
        Log.d(TAG, "Deleting single member from $url")
        try {
            val request = Request.Builder()
                .url(url)
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code != 404) {
                        throwNetworkError(response.code, "eliminar perfil de miembro individual")
                    }
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception deleteSingleMember", e)
            wrapException("eliminar perfil de miembro individual", e)
        }
    }

    fun fetchNotifications(baseUrl: String): List<EcoNotification> {
        if (isJsonBlob(baseUrl)) {
            return fetchFullDatabase(baseUrl).notifications
        }
        val url = formatUrl(baseUrl, "notifications")
        Log.d(TAG, "Fetching notifications from $url")
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) return emptyList()
                    throwNetworkError(response.code, "obtener notificaciones")
                }
                val bodyString = response.body?.string() ?: return emptyList()
                if (bodyString == "null") return emptyList()
                return parseNotifications(bodyString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetchNotifications", e)
            wrapException("obtener notificaciones", e)
        }
    }

    fun uploadNotifications(baseUrl: String, list: List<EcoNotification>): Boolean {
        if (isJsonBlob(baseUrl)) {
            val db = fetchFullDatabase(baseUrl)
            val updated = db.copy(notifications = list)
            return uploadFullDatabase(baseUrl, updated)
        }
        val url = formatUrl(baseUrl, "notifications")
        Log.d(TAG, "Uploading ${list.size} notifications to $url")
        try {
            val encryptedNotifications = list.map { com.example.data.SecurityUtils.encryptNotification(it, cloudSecurityKey) }
            val json = notificationsAdapter.toJson(encryptedNotifications)
            val request = Request.Builder().url(url).put(json.toRequestBody(jsonMediaType)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throwNetworkError(response.code, "subir notificaciones")
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploadNotifications", e)
            wrapException("subir notificaciones", e)
        }
    }

    fun fetchEnrollments(baseUrl: String): List<EcoEnrollment> {
        if (isJsonBlob(baseUrl)) {
            return fetchFullDatabase(baseUrl).enrollments
        }
        val url = formatUrl(baseUrl, "enrollments")
        Log.d(TAG, "Fetching enrollments from $url")
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) return emptyList()
                    throwNetworkError(response.code, "obtener inscripciones")
                }
                val bodyString = response.body?.string() ?: return emptyList()
                if (bodyString == "null") return emptyList()
                return parseEnrollments(bodyString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetchEnrollments", e)
            wrapException("obtener inscripciones", e)
        }
    }

    fun uploadEnrollments(baseUrl: String, list: List<EcoEnrollment>): Boolean {
        if (isJsonBlob(baseUrl)) {
            val db = fetchFullDatabase(baseUrl)
            val updated = db.copy(enrollments = list)
            return uploadFullDatabase(baseUrl, updated)
        }
        val url = formatUrl(baseUrl, "enrollments")
        Log.d(TAG, "Uploading ${list.size} enrollments to $url")
        try {
            val encryptedEnrollments = list.map { com.example.data.SecurityUtils.encryptEnrollment(it, cloudSecurityKey) }
            val json = enrollmentsAdapter.toJson(encryptedEnrollments)
            val request = Request.Builder().url(url).put(json.toRequestBody(jsonMediaType)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throwNetworkError(response.code, "subir inscripciones")
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploadEnrollments", e)
            wrapException("subir inscripciones", e)
        }
    }

    private fun formatUrl(base: String, path: String): String {
        var trimmed = base.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        trimmed = trimmed.removeSuffix("/")
        
        val parts = trimmed.split("?", limit = 2)
        val basePath = parts[0].removeSuffix("/")
        val queryString = if (parts.size > 1) "?" + parts[1] else ""

        return if (basePath.contains("kvdb.io", ignoreCase = true)) {
            "$basePath/$path$queryString"
        } else {
            "$basePath/$path.json$queryString"
        }
    }

    private fun parseActivities(json: String): List<EcoActivity> {
        return try {
            activitiesAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            try {
                val type = Types.newParameterizedType(Map::class.java, String::class.java, EcoActivity::class.java)
                val mapAdapter = moshi.adapter<Map<String, EcoActivity>>(type)
                val map = mapAdapter.fromJson(json)
                map?.values?.toList() ?: emptyList()
            } catch (ex: Exception) {
                emptyList()
            }
        }
    }

    private fun parseMembers(json: String): List<Member> {
        val raw = try {
            val map = membersMapAdapter.fromJson(json)
            map?.values?.toList() ?: emptyList()
        } catch (e: Exception) {
            try {
                val listAdapter = moshi.adapter<List<Member>>(Types.newParameterizedType(List::class.java, Member::class.java))
                listAdapter.fromJson(json) ?: emptyList()
            } catch (ex: Exception) {
                emptyList()
            }
        }
        return raw.map { com.example.data.SecurityUtils.decryptMember(it, cloudSecurityKey) }
    }

    private fun parseNotifications(json: String): List<EcoNotification> {
        val raw = try {
            notificationsAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            try {
                val type = Types.newParameterizedType(Map::class.java, String::class.java, EcoNotification::class.java)
                val mapAdapter = moshi.adapter<Map<String, EcoNotification>>(type)
                mapAdapter.fromJson(json)?.values?.toList() ?: emptyList()
            } catch (ex: Exception) {
                emptyList()
            }
        }
        return raw.map { com.example.data.SecurityUtils.decryptNotification(it, cloudSecurityKey) }
    }

    private fun parseEnrollments(json: String): List<EcoEnrollment> {
        val raw = try {
            enrollmentsAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            try {
                val type = Types.newParameterizedType(Map::class.java, String::class.java, EcoEnrollment::class.java)
                val mapAdapter = moshi.adapter<Map<String, EcoEnrollment>>(type)
                mapAdapter.fromJson(json)?.values?.toList() ?: emptyList()
            } catch (ex: Exception) {
                emptyList()
            }
        }
        return raw.map { com.example.data.SecurityUtils.decryptEnrollment(it, cloudSecurityKey) }
    }

    private fun parseArticlesGroup(json: String): List<EcoArticle> {
        return try {
            articlesGroupAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            try {
                val type = Types.newParameterizedType(Map::class.java, String::class.java, EcoArticle::class.java)
                val mapAdapter = moshi.adapter<Map<String, EcoArticle>>(type)
                mapAdapter.fromJson(json)?.values?.toList() ?: emptyList()
            } catch (ex: Exception) {
                emptyList()
            }
        }
    }

    private fun parsePreferences(json: String): com.example.data.CloudPreferences? {
        return try {
            preferencesAdapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun fetchArticles(baseUrl: String): List<EcoArticle> {
        if (isJsonBlob(baseUrl)) {
            return fetchFullDatabase(baseUrl).articles
        }
        val url = formatUrl(baseUrl, "articles")
        Log.d(TAG, "Fetching articles from $url")
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) return emptyList()
                    throwNetworkError(response.code, "obtener artículos")
                }
                val bodyString = response.body?.string() ?: return emptyList()
                if (bodyString == "null") return emptyList()
                return parseArticlesGroup(bodyString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetchArticles", e)
            wrapException("obtener artículos", e)
        }
    }

    fun uploadArticles(baseUrl: String, list: List<EcoArticle>): Boolean {
        if (isJsonBlob(baseUrl)) {
            val db = fetchFullDatabase(baseUrl)
            val updated = db.copy(articles = list)
            return uploadFullDatabase(baseUrl, updated)
        }
        val url = formatUrl(baseUrl, "articles")
        Log.d(TAG, "Uploading ${list.size} articles to $url")
        try {
            val json = articlesGroupAdapter.toJson(list)
            val request = Request.Builder().url(url).put(json.toRequestBody(jsonMediaType)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throwNetworkError(response.code, "subir artículos")
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploadArticles", e)
            wrapException("subir artículos", e)
        }
    }

    fun fetchPreferences(baseUrl: String, email: String?): com.example.data.CloudPreferences? {
        val safeKey = if (!email.isNullOrBlank()) com.example.data.SecurityUtils.hashPasswordSha256(email.trim().lowercase()).take(16) else ""
        if (isJsonBlob(baseUrl)) {
            val db = fetchFullDatabase(baseUrl)
            return if (safeKey.isNotEmpty() && db.userPreferences?.containsKey(safeKey) == true) {
                db.userPreferences[safeKey]
            } else {
                db.preferences
            }
        }
        val path = if (safeKey.isNotEmpty()) "preferences_$safeKey" else "preferences"
        val url = formatUrl(baseUrl, path)
        Log.d(TAG, "Fetching preferences from $url")
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) return null
                    throwNetworkError(response.code, "obtener preferencias")
                }
                val bodyString = response.body?.string() ?: return null
                if (bodyString == "null") return null
                return preferencesAdapter.fromJson(bodyString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetchPreferences", e)
            wrapException("obtener preferencias", e)
        }
    }

    fun uploadPreferences(baseUrl: String, prefs: com.example.data.CloudPreferences, email: String?): Boolean {
        val safeKey = if (!email.isNullOrBlank()) com.example.data.SecurityUtils.hashPasswordSha256(email.trim().lowercase()).take(16) else ""
        if (isJsonBlob(baseUrl)) {
            val db = fetchFullDatabase(baseUrl)
            val updatedUserPrefs = (db.userPreferences ?: emptyMap()).toMutableMap()
            if (safeKey.isNotEmpty()) {
                updatedUserPrefs[safeKey] = prefs
            }
            val updated = if (safeKey.isNotEmpty()) {
                db.copy(userPreferences = updatedUserPrefs)
            } else {
                db.copy(preferences = prefs)
            }
            return uploadFullDatabase(baseUrl, updated)
        }
        val path = if (safeKey.isNotEmpty()) "preferences_$safeKey" else "preferences"
        val url = formatUrl(baseUrl, path)
        Log.d(TAG, "Uploading preferences to $url")
        try {
            val json = preferencesAdapter.toJson(prefs)
            val request = Request.Builder().url(url).put(json.toRequestBody(jsonMediaType)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throwNetworkError(response.code, "subir preferencias")
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploadPreferences", e)
            wrapException("subir preferencias", e)
        }
    }

    fun fetchTombstones(baseUrl: String): List<String> {
        if (isJsonBlob(baseUrl)) {
            return fetchFullDatabase(baseUrl).tombstones
        }
        val url = formatUrl(baseUrl, "tombstones")
        Log.d(TAG, "Fetching tombstones from $url")
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) return emptyList()
                    throwNetworkError(response.code, "obtener borrados")
                }
                val bodyString = response.body?.string() ?: return emptyList()
                if (bodyString == "null") return emptyList()
                return try {
                    tombstonesAdapter.fromJson(bodyString) ?: emptyList()
                } catch (e: Exception) {
                    try {
                        val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
                        val mapAdapter = moshi.adapter<Map<String, String>>(type)
                        val map = mapAdapter.fromJson(bodyString)
                        map?.values?.toList() ?: emptyList()
                    } catch (ex: Exception) {
                        emptyList()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetchTombstones", e)
            wrapException("obtener borrados", e)
        }
    }

    fun uploadTombstones(baseUrl: String, list: List<String>): Boolean {
        if (isJsonBlob(baseUrl)) {
            val db = fetchFullDatabase(baseUrl)
            val updated = db.copy(tombstones = list)
            return uploadFullDatabase(baseUrl, updated)
        }
        val url = formatUrl(baseUrl, "tombstones")
        Log.d(TAG, "Uploading ${list.size} tombstones to $url")
        try {
            val json = tombstonesAdapter.toJson(list)
            val request = Request.Builder().url(url).put(json.toRequestBody(jsonMediaType)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throwNetworkError(response.code, "subir borrados")
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploadTombstones", e)
            wrapException("subir borrados", e)
        }
    }
}
