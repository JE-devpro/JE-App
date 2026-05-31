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
    val preferences: com.example.data.CloudPreferences? = null
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
            
            CloudDatabase(activities, members, notifications, enrollments, articles, preferences)
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
        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        return moshi.adapter<Map<String, Any>>(type).toJson(map)
    }

    private fun fetchFullDatabase(baseUrl: String): CloudDatabase {
        val trimmed = baseUrl.trim().removeSuffix("/")
        Log.d(TAG, "Fetching full database from $trimmed")
        val request = Request.Builder().url(trimmed).get().build()
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
        val trimmed = baseUrl.trim().removeSuffix("/")
        Log.d(TAG, "Uploading full database to $trimmed")
        try {
            val json = serializeDatabase(db)
            val request = Request.Builder()
                .url(trimmed)
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

    fun uploadMembers(baseUrl: String, members: List<Member>): Boolean {
        if (isJsonBlob(baseUrl)) {
            val db = fetchFullDatabase(baseUrl)
            val updated = db.copy(members = members)
            return uploadFullDatabase(baseUrl, updated)
        }
        val url = formatUrl(baseUrl, "members")
        Log.d(TAG, "Uploading ${members.size} members to $url")
        try {
            val encryptedMembers = members.map { com.example.data.SecurityUtils.encryptMember(it, cloudSecurityKey) }
            val map = encryptedMembers.associateBy { com.example.data.SecurityUtils.hashPasswordSha256(it.email).take(16) }
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

    fun fetchPreferences(baseUrl: String): com.example.data.CloudPreferences? {
        if (isJsonBlob(baseUrl)) {
            return fetchFullDatabase(baseUrl).preferences
        }
        val url = formatUrl(baseUrl, "preferences")
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

    fun uploadPreferences(baseUrl: String, prefs: com.example.data.CloudPreferences): Boolean {
        if (isJsonBlob(baseUrl)) {
            val db = fetchFullDatabase(baseUrl)
            val updated = db.copy(preferences = prefs)
            return uploadFullDatabase(baseUrl, updated)
        }
        val url = formatUrl(baseUrl, "preferences")
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
}
