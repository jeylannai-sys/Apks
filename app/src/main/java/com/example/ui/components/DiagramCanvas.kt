package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.example.data.DiagramConnectionEntity
import com.example.data.DiagramNodeEntity
import com.example.data.DiagramStyle
import com.example.data.NodeStatus
import com.example.data.NodeType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Interactive canvas that renders planning flowcharts & diagrams
 * with pan, zoom, zero-lag high-FPS draggable shapes, geometric nodes, and labeled links.
 */
@Composable
fun DiagramCanvas(
    nodes: List<DiagramNodeEntity>,
    connections: List<DiagramConnectionEntity>,
    style: DiagramStyle,
    selectedNode: DiagramNodeEntity?,
    panOffsetX: Float,
    panOffsetY: Float,
    zoomScale: Float,
    onTransformChange: (newPanX: Float, newPanY: Float, newZoom: Float) -> Unit,
    onNodeSelected: (DiagramNodeEntity?) -> Unit,
    onNodeMoved: (nodeId: String, newX: Float, newY: Float) -> Unit,
    onEditNode: (DiagramNodeEntity) -> Unit,
    onStatusToggle: (DiagramNodeEntity) -> Unit,
    onConvertToTask: (DiagramNodeEntity) -> Unit,
    onStartConnecting: (DiagramNodeEntity) -> Unit,
    onDeleteNode: (DiagramNodeEntity) -> Unit,
    isConnectingMode: Boolean = false,
    connectingSourceNode: DiagramNodeEntity? = null,
    modifier: Modifier = Modifier
) {
    // Live in-flight drag offsets keep node dragging synced to max device FPS without DB roundtrip lag
    val liveDragPositions = remember { mutableStateMapOf<String, Offset>() }
    var activeDraggingNodeId by remember { mutableStateOf<String?>(null) }

    val latestPanX by rememberUpdatedState(panOffsetX)
    val latestPanY by rememberUpdatedState(panOffsetY)
    val latestZoom by rememberUpdatedState(zoomScale)

    // Clear in-flight override once DB state catches up and user is no longer actively dragging that node
    LaunchedEffect(nodes, activeDraggingNodeId) {
        val nodeById = nodes.associateBy { it.id }
        val keysToRemove = liveDragPositions.keys.filter { id ->
            if (id == activeDraggingNodeId) return@filter false
            val dbNode = nodeById[id] ?: return@filter true
            val live = liveDragPositions[id] ?: return@filter true
            abs(dbNode.posX - live.x) < 1f && abs(dbNode.posY - live.y) < 1f
        }
        keysToRemove.forEach { liveDragPositions.remove(it) }
    }

    // Approximate center offset of a node card so connection arrows link neatly to centers
    fun nodeCenterOffset(typeStr: String): Offset {
        return when (typeStr) {
            NodeType.DECISION.name -> Offset(82f, 62f)
            NodeType.CIRCLE.name -> Offset(68f, 68f)
            else -> Offset(88f, 44f)
        }
    }

    val nodeCenters = nodes.associate { node ->
        val pos = liveDragPositions[node.id] ?: Offset(node.posX, node.posY)
        val centerShift = nodeCenterOffset(node.type)
        node.id to Offset(pos.x + centerShift.x, pos.y + centerShift.y)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(getCanvasBackgroundColor(style))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextZoom = (latestZoom * zoom).coerceIn(0.4f, 2.5f)
                    onTransformChange(latestPanX + pan.x, latestPanY + pan.y, nextZoom)
                }
            }
            .testTag("diagram_canvas_box")
    ) {
        // 1. Canvas Layer: Grid Background and Directed Connection Lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDiagramGrid(style, panOffsetX, panOffsetY, zoomScale)

            connections.forEach { conn ->
                val fromCenter = nodeCenters[conn.fromNodeId]
                val toCenter = nodeCenters[conn.toNodeId]
                if (fromCenter != null && toCenter != null) {
                    val p1 = Offset(
                        fromCenter.x * zoomScale + panOffsetX,
                        fromCenter.y * zoomScale + panOffsetY
                    )
                    val p2 = Offset(
                        toCenter.x * zoomScale + panOffsetX,
                        toCenter.y * zoomScale + panOffsetY
                    )

                    drawConnectionLine(
                        p1 = p1,
                        p2 = p2,
                        style = style,
                        label = conn.label,
                        color = parseHexColor(conn.colorHex, Color(0xFF64748B))
                    )
                }
            }

            if (isConnectingMode && connectingSourceNode != null) {
                val srcCenter = nodeCenters[connectingSourceNode.id]
                if (srcCenter != null) {
                    val p1 = Offset(
                        srcCenter.x * zoomScale + panOffsetX,
                        srcCenter.y * zoomScale + panOffsetY
                    )
                    drawCircle(
                        color = Color(0xFF3B82F6),
                        radius = 48f * zoomScale,
                        center = p1,
                        style = Stroke(
                            width = 3f * zoomScale,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                        )
                    )
                }
            }
        }

        // Empty canvas helper prompt
        if (nodes.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Blank Flowchart Canvas",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (style == DiagramStyle.CYBERPUNK_NEON) Color(0xFFE2E8F0) else Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Drag or tap a shape above to place elements, customize titles/topics, and connect them.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = if (style == DiagramStyle.CYBERPUNK_NEON) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            }
        }

        // 2. Nodes Layer: Interactive Geometric Elements placed at coordinates
        nodes.forEach { node ->
            val currentPos = liveDragPositions[node.id] ?: Offset(node.posX, node.posY)
            val screenX = currentPos.x * zoomScale + panOffsetX
            val screenY = currentPos.y * zoomScale + panOffsetY
            val isSelected = selectedNode?.id == node.id
            val isSource = connectingSourceNode?.id == node.id
            val latestNode by rememberUpdatedState(node)

            Box(
                modifier = Modifier
                    .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
                    .pointerInput(node.id) {
                        detectDragGestures(
                            onDragStart = {
                                activeDraggingNodeId = latestNode.id
                                if (!liveDragPositions.containsKey(latestNode.id)) {
                                    liveDragPositions[latestNode.id] = Offset(latestNode.posX, latestNode.posY)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val prev = liveDragPositions[latestNode.id] ?: Offset(latestNode.posX, latestNode.posY)
                                val next = Offset(
                                    x = prev.x + dragAmount.x / latestZoom,
                                    y = prev.y + dragAmount.y / latestZoom
                                )
                                liveDragPositions[latestNode.id] = next
                            },
                            onDragEnd = {
                                val finalPos = liveDragPositions[latestNode.id]
                                activeDraggingNodeId = null
                                if (finalPos != null) {
                                    onNodeMoved(latestNode.id, finalPos.x, finalPos.y)
                                }
                            },
                            onDragCancel = {
                                activeDraggingNodeId = null
                                liveDragPositions.remove(latestNode.id)
                            }
                        )
                    }
                    .clickable { onNodeSelected(node) }
                    .testTag("diagram_node_${node.id}")
            ) {
                DiagramNodeView(
                    node = node,
                    style = style,
                    isSelected = isSelected,
                    isConnectingSource = isSource,
                    zoomScale = zoomScale
                )
            }
        }

        // 3. Floating Node Quick Action Bar (when a node is tapped)
        if (selectedNode != null && !isConnectingMode) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp, start = 8.dp, end = 8.dp)
                    .testTag("node_quick_action_bar")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Edit Title / Shape / Connections
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .clickable { onEditNode(selectedNode) }
                            .testTag("edit_selected_node_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Node",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Edit",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Connect To Another Node
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clickable { onStartConnecting(selectedNode) }
                            .testTag("connect_selected_node_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.NorthEast,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Connect",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Status Toggle
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = getStatusColor(selectedNode.status).copy(alpha = 0.15f),
                        modifier = Modifier.clickable { onStatusToggle(selectedNode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selectedNode.status == NodeStatus.COMPLETED.name) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                contentDescription = null,
                                tint = getStatusColor(selectedNode.status),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (selectedNode.status) {
                                    NodeStatus.IN_PROGRESS.name -> "Active"
                                    NodeStatus.COMPLETED.name -> "Done"
                                    NodeStatus.BLOCKED.name -> "Blocked"
                                    else -> "To Do"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = getStatusColor(selectedNode.status)
                            )
                        }
                    }

                    // Convert to Task
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                        modifier = Modifier.clickable { onConvertToTask(selectedNode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PostAdd, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Task", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                        }
                    }

                    // Delete Node
                    IconButton(
                        onClick = { onDeleteNode(selectedNode) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Node", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }

                    // Close action bar
                    IconButton(
                        onClick = { onNodeSelected(null) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Node card rendered according to the selected DiagramStyle & geometric NodeType shape.
 */
@Composable
fun DiagramNodeView(
    node: DiagramNodeEntity,
    style: DiagramStyle,
    isSelected: Boolean,
    isConnectingSource: Boolean,
    zoomScale: Float
) {
    val nodeType = try { NodeType.valueOf(node.type) } catch (_: Exception) { NodeType.PROCESS }
    val nodeColor = parseHexColor(node.colorHex, Color(0xFF3B82F6))

    // Special geometric shapes (Diamond Decision, Circle Hub, Hexagon Goal) render consistently across styles
    when (nodeType) {
        NodeType.DECISION -> {
            DiamondDecisionNode(node, nodeColor, style, isSelected, isConnectingSource)
            return
        }
        NodeType.CIRCLE -> {
            CircleHubNode(node, nodeColor, style, isSelected, isConnectingSource)
            return
        }
        NodeType.MILESTONE -> {
            HexagonGoalNode(node, nodeColor, style, isSelected, isConnectingSource)
            return
        }
        else -> Unit
    }

    when (style) {
        DiagramStyle.CLASSIC_FLOWCHART -> ClassicFlowchartNode(node, nodeType, nodeColor, isSelected, isConnectingSource)
        DiagramStyle.MIND_MAP -> MindMapNode(node, nodeType, nodeColor, isSelected, isConnectingSource)
        DiagramStyle.AGILE_ROADMAP -> AgileRoadmapNode(node, nodeType, nodeColor, isSelected, isConnectingSource)
        DiagramStyle.CYBERPUNK_NEON -> CyberpunkNeonNode(node, nodeType, nodeColor, isSelected, isConnectingSource)
        DiagramStyle.STICKY_NOTES -> StickyNotesNode(node, nodeType, nodeColor, isSelected, isConnectingSource)
    }
}

@Composable
private fun DiamondDecisionNode(
    node: DiagramNodeEntity,
    color: Color,
    style: DiagramStyle,
    isSelected: Boolean,
    isConnectingSource: Boolean
) {
    val isDark = style == DiagramStyle.CYBERPUNK_NEON
    val fillColor = if (isDark) Color(0xFF0F172A) else MaterialTheme.colorScheme.surface
    val borderColor = if (isConnectingSource) Color(0xFF3B82F6) else if (isSelected) MaterialTheme.colorScheme.primary else color
    val textColor = if (isDark) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier.size(width = 164.dp, height = 124.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val diamond = Path().apply {
                moveTo(w / 2f, 2f)
                lineTo(w - 2f, h / 2f)
                lineTo(w / 2f, h - 2f)
                lineTo(2f, h / 2f)
                close()
            }
            drawPath(diamond, color = fillColor)
            drawPath(diamond, color = color.copy(alpha = 0.14f))
            drawPath(
                diamond,
                color = borderColor,
                style = Stroke(width = if (isSelected || isConnectingSource) 6f else 3.5f)
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DECISION",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = node.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CircleHubNode(
    node: DiagramNodeEntity,
    color: Color,
    style: DiagramStyle,
    isSelected: Boolean,
    isConnectingSource: Boolean
) {
    val isDark = style == DiagramStyle.CYBERPUNK_NEON
    val bgColor = if (isDark) Color(0xFF0F172A) else color.copy(alpha = 0.16f)
    val borderColor = if (isConnectingSource) Color(0xFF3B82F6) else if (isSelected) MaterialTheme.colorScheme.primary else color
    val textColor = if (isDark) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(if (isSelected || isConnectingSource) 3.dp else 2.dp, borderColor),
        shadowElevation = if (isSelected) 8.dp else 3.dp,
        modifier = Modifier.size(136.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = node.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (node.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = node.description,
                    fontSize = 9.sp,
                    color = if (isDark) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HexagonGoalNode(
    node: DiagramNodeEntity,
    color: Color,
    style: DiagramStyle,
    isSelected: Boolean,
    isConnectingSource: Boolean
) {
    val isDark = style == DiagramStyle.CYBERPUNK_NEON
    val fillColor = if (isDark) Color(0xFF0F172A) else MaterialTheme.colorScheme.surface
    val borderColor = if (isConnectingSource) Color(0xFF3B82F6) else if (isSelected) MaterialTheme.colorScheme.primary else color
    val textColor = if (isDark) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier.size(width = 176.dp, height = 88.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val indent = w * 0.14f
            val hex = Path().apply {
                moveTo(indent, 2f)
                lineTo(w - indent, 2f)
                lineTo(w - 2f, h / 2f)
                lineTo(w - indent, h - 2f)
                lineTo(indent, h - 2f)
                lineTo(2f, h / 2f)
                close()
            }
            drawPath(hex, color = fillColor)
            drawPath(hex, color = color.copy(alpha = 0.12f))
            drawPath(
                hex,
                color = borderColor,
                style = Stroke(width = if (isSelected || isConnectingSource) 6f else 3.5f)
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("GOAL", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Text(
                text = node.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// 1. CLASSIC FLOWCHART STYLE
@Composable
private fun ClassicFlowchartNode(
    node: DiagramNodeEntity,
    type: NodeType,
    color: Color,
    isSelected: Boolean,
    isConnectingSource: Boolean
) {
    val shape = when (type) {
        NodeType.START_END -> RoundedCornerShape(28.dp)
        NodeType.NOTE -> RoundedCornerShape(4.dp)
        else -> RoundedCornerShape(10.dp)
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected || isConnectingSource) 3.dp else 1.5.dp,
            color = if (isConnectingSource) Color(0xFF3B82F6) else if (isSelected) MaterialTheme.colorScheme.primary else color
        ),
        shadowElevation = if (isSelected) 8.dp else 3.dp,
        modifier = Modifier.widthIn(min = 140.dp, max = 210.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = type.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(modifier = Modifier.weight(1f))
                if (node.status == NodeStatus.COMPLETED.name) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = node.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            if (node.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = node.description,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

// 2. MIND MAP STYLE
@Composable
private fun MindMapNode(
    node: DiagramNodeEntity,
    type: NodeType,
    color: Color,
    isSelected: Boolean,
    isConnectingSource: Boolean
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(
            width = if (isSelected || isConnectingSource) 3.dp else 2.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else color
        ),
        shadowElevation = if (isSelected) 6.dp else 2.dp,
        modifier = Modifier.widthIn(min = 130.dp, max = 190.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (type) {
                        NodeType.START_END -> Icons.Default.PlayArrow
                        NodeType.DECISION -> Icons.Default.PriorityHigh
                        NodeType.MILESTONE -> Icons.Default.Star
                        NodeType.NOTE -> Icons.Default.Lightbulb
                        else -> Icons.Default.Check
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = node.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                if (node.description.isNotEmpty()) {
                    Text(
                        text = node.description,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// 3. AGILE ROADMAP STYLE
@Composable
private fun AgileRoadmapNode(
    node: DiagramNodeEntity,
    type: NodeType,
    color: Color,
    isSelected: Boolean,
    isConnectingSource: Boolean
) {
    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(if (isSelected) 6.dp else 2.dp),
        modifier = Modifier
            .widthIn(min = 150.dp, max = 220.dp)
            .border(
                width = if (isSelected || isConnectingSource) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else color.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = color.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = type.label.uppercase(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = getStatusColor(node.status).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = node.status,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = getStatusColor(node.status),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = node.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            if (node.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = node.description,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

// 4. CYBERPUNK NEON STYLE
@Composable
private fun CyberpunkNeonNode(
    node: DiagramNodeEntity,
    type: NodeType,
    color: Color,
    isSelected: Boolean,
    isConnectingSource: Boolean
) {
    val neonGlow = if (isSelected || isConnectingSource) Color(0xFF00FFFF) else color
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(
            width = if (isSelected || isConnectingSource) 2.5.dp else 1.5.dp,
            color = neonGlow
        ),
        shadowElevation = if (isSelected) 10.dp else 4.dp,
        modifier = Modifier.widthIn(min = 140.dp, max = 210.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ">_${type.name}",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = neonGlow
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (node.status == NodeStatus.COMPLETED.name) Color(0xFF10B981) else Color(0xFFFF007F))
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = node.title,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE2E8F0),
                maxLines = 2
            )
            if (node.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = node.description,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF94A3B8),
                    maxLines = 2
                )
            }
        }
    }
}

// 5. STICKY NOTES STYLE
@Composable
private fun StickyNotesNode(
    node: DiagramNodeEntity,
    type: NodeType,
    color: Color,
    isSelected: Boolean,
    isConnectingSource: Boolean
) {
    val pastelColor = when (node.orderIndex % 4) {
        0 -> Color(0xFFFEF08A)
        1 -> Color(0xFFBAE6FD)
        2 -> Color(0xFFBBF7D0)
        else -> Color(0xFFFED7AA)
    }

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = pastelColor,
        shadowElevation = if (isSelected) 8.dp else 4.dp,
        border = if (isSelected || isConnectingSource) BorderStroke(2.dp, Color(0xFF1E293B)) else null,
        modifier = Modifier
            .widthIn(min = 130.dp, max = 180.dp)
            .rotate(if (node.orderIndex % 2 == 0) -1.5f else 1.5f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDC2626))
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = node.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                maxLines = 3
            )
            if (node.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = node.description,
                    fontSize = 10.sp,
                    color = Color(0xFF475569),
                    maxLines = 2
                )
            }
        }
    }
}

private fun DrawScope.drawDiagramGrid(
    style: DiagramStyle,
    panX: Float,
    panY: Float,
    zoom: Float
) {
    val step = (40f * zoom).coerceAtLeast(15f)
    val gridColor = when (style) {
        DiagramStyle.CYBERPUNK_NEON -> Color(0xFF1E293B).copy(alpha = 0.5f)
        DiagramStyle.STICKY_NOTES -> Color(0xFFD6D3D1).copy(alpha = 0.4f)
        else -> Color(0xFF94A3B8).copy(alpha = 0.2f)
    }

    var x = panX % step
    while (x < size.width) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }

    var y = panY % step
    while (y < size.height) {
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

private fun DrawScope.drawConnectionLine(
    p1: Offset,
    p2: Offset,
    style: DiagramStyle,
    label: String,
    color: Color
) {
    val strokeWidth = when (style) {
        DiagramStyle.CYBERPUNK_NEON -> 3.5f
        DiagramStyle.MIND_MAP -> 4f
        else -> 2.8f
    }

    val path = Path()
    when (style) {
        DiagramStyle.MIND_MAP -> {
            val midX = (p1.x + p2.x) / 2
            path.moveTo(p1.x, p1.y)
            path.cubicTo(midX, p1.y, midX, p2.y, p2.x, p2.y)
        }
        else -> {
            path.moveTo(p1.x, p1.y)
            path.lineTo(p2.x, p2.y)
        }
    }

    val effect = if (style == DiagramStyle.STICKY_NOTES) {
        PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    } else null

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, pathEffect = effect)
    )

    // Draw midpoint arrowhead so arrows remain clearly visible even outside card centers
    val midX = (p1.x + p2.x) / 2f
    val midY = (p1.y + p2.y) / 2f
    val angle = atan2(p2.y - p1.y, p2.x - p1.x)
    val tipX = (p1.x + p2.x) * 0.5f + cos(angle) * 28f
    val tipY = (p1.y + p2.y) * 0.5f + sin(angle) * 28f
    val arrowSize = 18f
    val arrowP1 = Offset(
        tipX - arrowSize * cos(angle - Math.PI / 6).toFloat(),
        tipY - arrowSize * sin(angle - Math.PI / 6).toFloat()
    )
    val arrowP2 = Offset(
        tipX - arrowSize * cos(angle + Math.PI / 6).toFloat(),
        tipY - arrowSize * sin(angle + Math.PI / 6).toFloat()
    )

    val arrowPath = Path().apply {
        moveTo(tipX, tipY)
        lineTo(arrowP1.x, arrowP1.y)
        lineTo(arrowP2.x, arrowP2.y)
        close()
    }
    drawPath(arrowPath, color = color)

    if (label.isNotEmpty()) {
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                this.color = android.graphics.Color.WHITE
                this.textSize = 24f
                this.isAntiAlias = true
                this.typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val bgPaint = android.graphics.Paint().apply {
                this.color = "#334155".toColorInt()
                this.isAntiAlias = true
            }
            val textWidth = paint.measureText(label)
            drawRoundRect(
                midX - textWidth / 2 - 10f,
                midY - 18f,
                midX + textWidth / 2 + 10f,
                midY + 12f,
                10f,
                10f,
                bgPaint
            )
            drawText(label, midX - textWidth / 2, midY + 4f, paint)
        }
    }
}

private fun getCanvasBackgroundColor(style: DiagramStyle): Color {
    return when (style) {
        DiagramStyle.CYBERPUNK_NEON -> Color(0xFF030712)
        DiagramStyle.STICKY_NOTES -> Color(0xFFE7E5E4)
        DiagramStyle.MIND_MAP -> Color(0xFFF8FAFC)
        DiagramStyle.AGILE_ROADMAP -> Color(0xFFF1F5F9)
        DiagramStyle.CLASSIC_FLOWCHART -> Color(0xFFF8FAFC)
    }
}

private fun getStatusColor(status: String): Color {
    return when (status) {
        NodeStatus.COMPLETED.name -> Color(0xFF10B981)
        NodeStatus.IN_PROGRESS.name -> Color(0xFF3B82F6)
        NodeStatus.BLOCKED.name -> Color(0xFFEF4444)
        else -> Color(0xFF64748B)
    }
}

private fun parseHexColor(hex: String, default: Color): Color {
    return try {
        Color(hex.toColorInt())
    } catch (_: Exception) {
        default
    }
}
