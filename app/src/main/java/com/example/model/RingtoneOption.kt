package com.example.model

enum class RingtoneOption(
    val id: String,
    val displayName: String,
    val description: String,
    val durationSeconds: Int = 3
) {
    RADAR_PULSE("RADAR_PULSE", "Radar Pulse", "High-tempo dual pulse alert", 3),
    DIGITAL_CHIME("DIGITAL_CHIME", "Digital Chime", "Ascending modern digital melody", 4),
    MORNING_BELLS("MORNING_BELLS", "Morning Bells", "Soothing acoustic harmonized chimes", 4),
    ENERGY_SYNTH("ENERGY_SYNTH", "Energy Synth", "Energetic electronic wake-up rhythm", 4),
    ZEN_GONG("ZEN_GONG", "Zen Gong", "Deep calming resonating tones", 4),
    CLASSIC_ALARM("CLASSIC_ALARM", "Classic Alarm", "Sharp classic twin-bell alert", 3);

    companion object {
        fun fromId(id: String?): RingtoneOption {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DIGITAL_CHIME
        }
    }
}
