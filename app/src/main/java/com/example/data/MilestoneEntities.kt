package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "milestones")
data class MilestoneEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val category: String, // "HABIT", "FINANCE", "FITNESS", "PRODUCTIVITY"
    val tier: String = "BRONZE", // "BRONZE", "SILVER", "GOLD", "PLATINUM"
    val iconName: String = "EmojiEvents",
    val badgeColorHex: String = "#F59E0B",
    val isUnlocked: Boolean = false,
    val unlockedAtMillis: Long? = null,
    val currentProgress: Int = 0,
    val targetProgress: Int = 1
)
