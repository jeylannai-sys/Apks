package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueDateMillis: Long? = null,
    val priority: String = "MEDIUM",
    val category: String = "General",
    val isCompleted: Boolean = false,
    val hasAlarm: Boolean = false,
    val alarmRingtone: String = "DIGITAL_CHIME",
    val repeatDaysBitmask: Int = 0, // 0 = No repeat, 1=Sun, 2=Mon, 4=Tue, 8=Wed, 16=Thu, 32=Fri, 64=Sat
    val repeatMode: String = "NONE", // NONE, DAILY, WEEKDAYS, WEEKENDS, CUSTOM_DAYS
    val createdAt: Long = System.currentTimeMillis()
) {
    fun isRepeating(): Boolean = repeatDaysBitmask > 0 || repeatMode != "NONE"

    fun getRepeatLabel(): String {
        return when {
            repeatMode == "DAILY" || repeatDaysBitmask == 127 -> "Daily"
            repeatMode == "WEEKDAYS" || repeatDaysBitmask == 62 -> "Mon–Fri"
            repeatMode == "WEEKENDS" || repeatDaysBitmask == 65 -> "Weekends"
            repeatDaysBitmask > 0 -> {
                val days = mutableListOf<String>()
                if ((repeatDaysBitmask and 2) != 0) days.add("Mon")
                if ((repeatDaysBitmask and 4) != 0) days.add("Tue")
                if ((repeatDaysBitmask and 8) != 0) days.add("Wed")
                if ((repeatDaysBitmask and 16) != 0) days.add("Thu")
                if ((repeatDaysBitmask and 32) != 0) days.add("Fri")
                if ((repeatDaysBitmask and 64) != 0) days.add("Sat")
                if ((repeatDaysBitmask and 1) != 0) days.add("Sun")
                "Every " + days.joinToString(", ")
            }
            else -> "Once"
        }
    }

    fun calculateNextDueDate(fromMillis: Long = System.currentTimeMillis()): Long? {
        if (!isRepeating()) return dueDateMillis

        val cal = Calendar.getInstance()
        val originalTimeCal = Calendar.getInstance().apply {
            if (dueDateMillis != null) timeInMillis = dueDateMillis
        }
        val targetHour = originalTimeCal.get(Calendar.HOUR_OF_DAY)
        val targetMinute = originalTimeCal.get(Calendar.MINUTE)

        cal.timeInMillis = fromMillis
        cal.set(Calendar.HOUR_OF_DAY, targetHour)
        cal.set(Calendar.MINUTE, targetMinute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // Find the next day that matches bitmask
        val mask = if (repeatDaysBitmask > 0) repeatDaysBitmask else when (repeatMode) {
            "DAILY" -> 127
            "WEEKDAYS" -> 62
            "WEEKENDS" -> 65
            else -> 0
        }

        if (mask == 0) return dueDateMillis

        for (offset in 1..7) {
            val checkCal = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val dayOfWeek = checkCal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon... 7=Sat
            val dayBit = 1 shl (dayOfWeek - 1)
            if ((mask and dayBit) != 0) {
                return checkCal.timeInMillis
            }
        }
        return cal.timeInMillis + 86400000L
    }
}
