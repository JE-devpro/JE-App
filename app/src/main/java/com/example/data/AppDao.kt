package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Upcoming Activities
    @Query("SELECT * FROM activities ORDER BY date ASC")
    fun getAllActivities(): Flow<List<EcoActivity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: EcoActivity)

    @Update
    suspend fun updateActivity(activity: EcoActivity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<EcoActivity>)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteActivityById(id: Int)

    // Digital Carnet Profile
    @Query("SELECT * FROM carnet_profile WHERE id = 1 LIMIT 1")
    fun getProfileFlow(): Flow<CarnetProfile?>

    @Query("SELECT * FROM carnet_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileDirect(): CarnetProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: CarnetProfile)

    // Ecosystem Articles / Notice Feed
    @Query("SELECT * FROM articles ORDER BY id DESC")
    fun getAllArticles(): Flow<List<EcoArticle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: EcoArticle)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<EcoArticle>)

    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteArticleById(id: Int)

    @Query("DELETE FROM articles")
    suspend fun clearAllArticles()

    // Members accounts (registered by Admin only)
    @Query("SELECT * FROM members ORDER BY fullName ASC")
    fun getAllMembers(): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE email = :email LIMIT 1")
    suspend fun getMemberByEmailDirect(email: String): Member?

    @Query("SELECT * FROM members WHERE email = :email LIMIT 1")
    fun getMemberByEmailFlow(email: String): Flow<Member?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: Member)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<Member>)

    @Update
    suspend fun updateMember(member: Member)

    @Query("DELETE FROM members WHERE email = :email")
    suspend fun deleteMemberByEmail(email: String)

    // Enrollments to Activities
    @Query("SELECT * FROM enrollments ORDER BY enrolledAt DESC")
    fun getAllEnrollments(): Flow<List<EcoEnrollment>>

    @Query("SELECT * FROM enrollments WHERE activityId = :activityId")
    fun getEnrollmentsByActivityFlow(activityId: Int): Flow<List<EcoEnrollment>>

    @Query("SELECT * FROM enrollments WHERE activityId = :activityId AND LOWER(TRIM(memberEmail)) = LOWER(TRIM(:memberEmail)) LIMIT 1")
    suspend fun getEnrollmentDirect(activityId: Int, memberEmail: String): EcoEnrollment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnrollment(enrollment: EcoEnrollment)

    @Query("DELETE FROM enrollments WHERE activityId = :activityId AND LOWER(TRIM(memberEmail)) = LOWER(TRIM(:memberEmail))")
    suspend fun deleteEnrollment(activityId: Int, memberEmail: String)

    // Administration Notifications / Alerts
    @Query("SELECT * FROM je_notifications ORDER BY id DESC")
    fun getAllNotifications(): Flow<List<EcoNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: EcoNotification)

    @Query("DELETE FROM je_notifications WHERE id = :id")
    suspend fun deleteNotificationById(id: Int)

    @Query("DELETE FROM je_notifications WHERE isGlobalAlert = 1 AND expiryMillis > 0 AND expiryMillis < :currentTime")
    suspend fun deleteExpiredNotifications(currentTime: Long)

    @Query("UPDATE je_notifications SET isRead = 1")
    suspend fun markAllNotificationsAsRead()

    @Query("DELETE FROM activities")
    suspend fun clearAllActivities()

    @Query("DELETE FROM members")
    suspend fun clearAllMembers()

    @Query("DELETE FROM je_notifications")
    suspend fun clearAllNotifications()

    @Query("DELETE FROM je_notifications WHERE isGlobalAlert = 0")
    suspend fun clearNonGlobalNotifications()

    @Query("DELETE FROM enrollments")
    suspend fun clearAllEnrollments()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnrollments(enrollments: List<EcoEnrollment>)
}
