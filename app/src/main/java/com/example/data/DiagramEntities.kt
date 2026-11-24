package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Visual styles for rendering planning flowcharts and diagrams.
 */
enum class DiagramStyle(val displayName: String, val description: String) {
    CLASSIC_FLOWCHART("Classic Flowchart", "BPMN symbols with Start/End, Action rects, Decision diamonds"),
    MIND_MAP("Mind Map", "Radial organic curved branches and brainstorming idea bubbles"),
    AGILE_ROADMAP("Agile Roadmap", "Sprint columns, phase milestones, and progress badges"),
    CYBERPUNK_NEON("Cyberpunk Neon", "Dark circuit grid with glowing cyan, magenta & wireframe nodes"),
    STICKY_NOTES("Sticky Notes", "Creative pinboard canvas with pastel notes and push pins")
}

/**
 * Flowchart and diagram node shapes & roles.
 */
enum class NodeType(val label: String) {
    START_END("Start / End"),
    PROCESS("Process / Box"),
    DECISION("Decision"),
    CIRCLE("Circle / Hub"),
    MILESTONE("Goal / Hex"),
    NOTE("Sticky Note")
}

/**
 * Status tracking for planning nodes.
 */
enum class NodeStatus(val label: String) {
    TODO("To Do"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    BLOCKED("Blocked")
}

@Entity(tableName = "diagrams")
data class DiagramEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val style: String = DiagramStyle.CLASSIC_FLOWCHART.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "diagram_nodes",
    foreignKeys = [
        ForeignKey(
            entity = DiagramEntity::class,
            parentColumns = ["id"],
            childColumns = ["diagramId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["diagramId"])]
)
data class DiagramNodeEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val diagramId: String,
    val title: String,
    val description: String = "",
    val type: String = NodeType.PROCESS.name,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val colorHex: String = "#4285F4",
    val status: String = NodeStatus.TODO.name,
    val orderIndex: Int = 0
)

@Entity(
    tableName = "diagram_connections",
    foreignKeys = [
        ForeignKey(
            entity = DiagramEntity::class,
            parentColumns = ["id"],
            childColumns = ["diagramId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["diagramId"]), Index(value = ["fromNodeId"]), Index(value = ["toNodeId"])]
)
data class DiagramConnectionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val diagramId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val label: String = "",
    val colorHex: String = "#64748B"
)
