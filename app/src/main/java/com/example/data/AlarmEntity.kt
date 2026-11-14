package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String,
    val hour: Int,
    val minute: Int,
    val daysBitmask: Int = 127, // 127 = Sun(1)+Mon(2)+Tue(4)+Wed(8)+Thu(16)+Fri(32)+Sat(64) -> All days
    val isEnabled: Boolean = true,
    val ringtone: String = "RADAR_PULSE",
    val vibrate: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
