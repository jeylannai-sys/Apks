package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VaultFileCategory(val label: String) {
    IMAGE("Images"),
    VIDEO("Videos"),
    DOCUMENT("Documents"),
    AUDIO("Audio"),
    OTHER("Other")
}

@Entity(tableName = "vault_files")
data class VaultFileEntity(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val storedFileName: String,
    val mimeType: String,
    val fileCategory: String,
    val sizeBytes: Long,
    val importedAtMillis: Long = System.currentTimeMillis(),
    val originalRelativePath: String = "",
    val originalUriString: String = ""
)
