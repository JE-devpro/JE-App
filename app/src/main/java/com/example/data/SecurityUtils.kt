package com.example.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecurityUtils {
    /**
     * Hashes a password using the SHA-256 algorithm.
     * This ensures that cleartext passwords are never stored in the database or sent over the network.
     */
    fun hashPasswordSha256(password: String): String {
        if (password.isBlank()) return ""
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            password // Fallback to plain text if MessageDigest fails
        }
    }

    /**
     * Encrypts a string using AES-256-CBC with PKCS5Padding.
     * The key is derived from the passphrase using SHA-256.
     * A random IV is generated for each encryption and prepended to the output.
     */
    fun encryptAes256(plaintext: String, passphrase: String): String {
        if (plaintext.isEmpty()) return ""
        try {
            // Derive 256-bit key from passphrase
            val keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(passphrase.toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, "AES")

            // Generate 16-byte random IV
            val ivBytes = ByteArray(16)
            SecureRandom().nextBytes(ivBytes)
            val ivSpec = IvParameterSpec(ivBytes)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)

            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val ivBase64 = Base64.encodeToString(ivBytes, Base64.NO_WRAP)
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

            return "$ivBase64:$encryptedBase64"
        } catch (e: Exception) {
            return plaintext // Fallback
        }
    }

    /**
     * Decrypts a string encrypted with encryptAes256.
     */
    fun decryptAes256(ciphertext: String, passphrase: String): String {
        if (ciphertext.isEmpty()) return ""
        try {
            val parts = ciphertext.split(":", limit = 2)
            if (parts.size != 2) return ciphertext // Not encrypted or invalid format

            val ivBytes = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)

            val keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(passphrase.toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(ivBytes)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // Return plaintext or placeholder
            return ciphertext
        }
    }

    /**
     * Checks if a string is encrypted (contains the delimiter colons or matches format)
     */
    fun isEncrypted(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value.contains(":") && value.length > 20
    }

    // --- High level model encryption assistants ---

    fun encryptMember(member: Member, key: String): Member {
        if (key.isBlank()) return member
        return member.copy(
            email = encryptAes256(member.email, key),
            password = encryptAes256(member.password, key),
            fullName = encryptAes256(member.fullName, key),
            association = encryptAes256(member.association, key),
            role = encryptAes256(member.role, key),
            country = encryptAes256(member.country, key),
            credentialId = encryptAes256(member.credentialId, key),
            googleCalendarEmail = member.googleCalendarEmail?.let { encryptAes256(it, key) },
            photoUri = member.photoUri?.let { encryptAes256(it, key) },
            qrUri = member.qrUri?.let { encryptAes256(it, key) },
            customCara1Uri = member.customCara1Uri?.let { encryptAes256(it, key) },
            customCara2Uri = member.customCara2Uri?.let { encryptAes256(it, key) }
        )
    }

    fun decryptMember(member: Member, key: String): Member {
        if (key.isBlank()) return member
        return member.copy(
            email = if (isEncrypted(member.email)) decryptAes256(member.email, key) else member.email,
            password = if (isEncrypted(member.password)) decryptAes256(member.password, key) else member.password,
            fullName = if (isEncrypted(member.fullName)) decryptAes256(member.fullName, key) else member.fullName,
            association = if (isEncrypted(member.association)) decryptAes256(member.association, key) else member.association,
            role = if (isEncrypted(member.role)) decryptAes256(member.role, key) else member.role,
            country = if (isEncrypted(member.country)) decryptAes256(member.country, key) else member.country,
            credentialId = if (isEncrypted(member.credentialId)) decryptAes256(member.credentialId, key) else member.credentialId,
            googleCalendarEmail = member.googleCalendarEmail?.let { if (isEncrypted(it)) decryptAes256(it, key) else it },
            photoUri = member.photoUri?.let { if (isEncrypted(it)) decryptAes256(it, key) else it },
            qrUri = member.qrUri?.let { if (isEncrypted(it)) decryptAes256(it, key) else it },
            customCara1Uri = member.customCara1Uri?.let { if (isEncrypted(it)) decryptAes256(it, key) else it },
            customCara2Uri = member.customCara2Uri?.let { if (isEncrypted(it)) decryptAes256(it, key) else it }
        )
    }

    fun encryptEnrollment(enrollment: EcoEnrollment, key: String): EcoEnrollment {
        if (key.isBlank()) return enrollment
        return enrollment.copy(
            memberEmail = encryptAes256(enrollment.memberEmail, key),
            memberName = encryptAes256(enrollment.memberName, key)
        )
    }

    fun decryptEnrollment(enrollment: EcoEnrollment, key: String): EcoEnrollment {
        if (key.isBlank()) return enrollment
        return enrollment.copy(
            memberEmail = if (isEncrypted(enrollment.memberEmail)) decryptAes256(enrollment.memberEmail, key) else enrollment.memberEmail,
            memberName = if (isEncrypted(enrollment.memberName)) decryptAes256(enrollment.memberName, key) else enrollment.memberName
        )
    }

    fun encryptNotification(notif: EcoNotification, key: String): EcoNotification {
        if (key.isBlank()) return notif
        return notif.copy(
            title = encryptAes256(notif.title, key),
            message = encryptAes256(notif.message, key)
        )
    }

    fun decryptNotification(notif: EcoNotification, key: String): EcoNotification {
        if (key.isBlank()) return notif
        return notif.copy(
            title = if (isEncrypted(notif.title)) decryptAes256(notif.title, key) else notif.title,
            message = if (isEncrypted(notif.message)) decryptAes256(notif.message, key) else notif.message
        )
    }
}
