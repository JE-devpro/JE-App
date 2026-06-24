package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "activities")
@JsonClass(generateAdapter = true)
data class EcoActivity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val date: String, // Represents start date and time
    val endDate: String = "", // Represents end date and time
    val location: String,
    val country: String,
    val category: String,
    val organizer: String,
    val isUserRegistered: Boolean = false,
    val eventType: String = "Voluntariado", // Event modes: "Talleres", "Voluntariado", "Charlas"
    val isMandatory: Boolean = false,
    val registrationDeadline: String = ""
)

@Entity(tableName = "carnet_profile")
@JsonClass(generateAdapter = true)
data class CarnetProfile(
    @PrimaryKey val id: Int = 1,
    val fullName: String,
    val association: String,
    val role: String,
    val country: String,
    val emojiAvatar: String,
    val points: Int = 50,
    val credentialId: String = "LA-ECO-88741",
    val joinDate: String = "23/05/2026",
    val photoUri: String? = null,
    val qrUri: String? = null,
    val customCara1Uri: String? = null,
    val customCara2Uri: String? = null
)

@Entity(tableName = "members")
@JsonClass(generateAdapter = true)
data class Member(
    @PrimaryKey val email: String,
    val password: String,
    val fullName: String,
    val association: String = "Jóvenes y Ecosistemas Latinoamérica",
    val role: String = "Miembro Activo",
    val country: String = "América Latina",
    val emojiAvatar: String = "🐸",
    val points: Int = 50,
    val credentialId: String,
    val joinDate: String = "23/05/2026",
    val isAdmin: Boolean = false,
    val googleCalendarEmail: String? = null,
    val isGoogleCalendarLinked: Boolean = false,
    val photoUri: String? = null,
    val qrUri: String? = null,
    val customCara1Uri: String? = null,
    val customCara2Uri: String? = null
)

@Entity(tableName = "enrollments")
@JsonClass(generateAdapter = true)
data class EcoEnrollment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val activityId: Int,
    val activityTitle: String,
    val memberEmail: String,
    val memberName: String,
    val enrolledAt: String = "23/05/2026",
    val reminderMinutes: Int = -1
)

@Entity(tableName = "je_notifications")
@JsonClass(generateAdapter = true)
data class EcoNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val message: String,
    val timestamp: String,
    val isRead: Boolean = false,
    val photoUri: String? = null,
    val isGlobalAlert: Boolean = false,
    val alertButtonText: String? = null,
    val broadcastIcon: String? = null,
    val expiryMillis: Long = 0L,
    val actionUrl: String? = null
)

@Entity(tableName = "articles")
@JsonClass(generateAdapter = true)
data class EcoArticle(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val category: String,
    val region: String,
    val publishDate: String,
    val isFeatured: Boolean = false,
    val isAiGenerated: Boolean = false,
    val photoUri: String? = null
)

@JsonClass(generateAdapter = true)
data class CloudPreferences(
    val prefTheme: String = "system",
    val prefNotifActividades: Boolean = true,
    val prefNotifNovedades: Boolean = true,
    val prefNotifNube: Boolean = true,
    val prefNotifSistema: Boolean = true,
    val lastUpdated: Long = 0L
)

@JsonClass(generateAdapter = true)
data class AppUpdate(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val minRequiredVersionCode: Int = 0
)


