package com.maderskitech.localllmcommitassist.data

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object TokenEncryption {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    fun generateKey(): String {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(256)
        val secretKey = keyGen.generateKey()
        return Base64.getEncoder().encodeToString(secretKey.encoded)
    }

    fun encrypt(plaintext: String, base64Key: String): String {
        if (plaintext.isEmpty()) return ""

        val keyBytes = Base64.getDecoder().decode(base64Key)
        val secretKey = SecretKeySpec(keyBytes, ALGORITHM)

        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encrypted: String, base64Key: String): String {
        if (encrypted.isEmpty()) return ""

        val keyBytes = Base64.getDecoder().decode(base64Key)
        val secretKey = SecretKeySpec(keyBytes, ALGORITHM)

        val combined = Base64.getDecoder().decode(encrypted)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
