package com.maderskitech.localllmcommitassist.data

import java.io.File
import java.util.UUID

data class PrAttachment(
    val id: String = UUID.randomUUID().toString(),
    val file: File,
    val name: String = file.name,
    val sizeBytes: Long = file.length(),
    val mimeType: String = mimeTypeFromExtension(file.extension),
    val isVideo: Boolean = file.extension.lowercase() in videoExtensions,
    val isTempFile: Boolean = false,
)

object AttachmentConfig {
    val allowedExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "mp4", "mov")
    const val GITHUB_MAX_SIZE_BYTES = 10L * 1024 * 1024       // 10 MB
    const val AZURE_DEVOPS_MAX_SIZE_BYTES = 25L * 1024 * 1024  // 25 MB

    fun maxSizeForPlatform(platform: String): Long = when (platform) {
        "azure_devops" -> AZURE_DEVOPS_MAX_SIZE_BYTES
        else -> GITHUB_MAX_SIZE_BYTES
    }

    fun maxSizeLabelForPlatform(platform: String): String = when (platform) {
        "azure_devops" -> "25 MB"
        else -> "10 MB"
    }
}

private val videoExtensions = setOf("mp4", "mov")

private fun mimeTypeFromExtension(ext: String): String = when (ext.lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    else -> "application/octet-stream"
}
