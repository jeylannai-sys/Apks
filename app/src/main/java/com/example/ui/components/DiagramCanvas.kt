package com.example.ui.components

import androidx.core.graphics.toColorInt
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DiagramConnectionEntity
import com.example.data.DiagramNodeEntity
import com.example.data.DiagramStyle
import com.example.data.NodeStatus
import com.example.data.NodeType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Interactive canvas that renders planning flowcharts & diagrams
 * with support for pan, zoom, draggable nodes, and 5 distinct styles.
 */
@Composable
fun DiagramCanvas(
    nodes: List<DiagramNodeEntity>,
    connections: List<DiagramConnectionEntity>,
    style: DiagramStyle,
    selectedNode: DiagramNodeEntity?,
    onNodeSelected: (DiagramNodeEntity?) -> Unit,
    onNodeMoved: (nodeId: String, newX: Float, newY: Float) -> Unit,
    onStatusToggle: (DiagramNodeEntity) -> Unit,
    onConvertToTask: (DiagramNodeEntity) -> Unit,
    onStartConnecting: (DiagramNodeEntity) -> Unit,
    onDeleteNode: (DiagramNodeEntity) -> Unit,
    isConnectingMode: Boolean = false,
    connectingSourceNode: DiagramNodeEntity? = null,
    modifier: Modifier = Modifier
) {
    var panOffsetX by remember { mutableFloatStateOf(60f) }
    var panOffsetY by remember { mutableFloatStateOf(60f) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    // Map of nodeId -> current position for fast lookup during connection drawing
    val nodePositions = remember(nodes) {
        nodes.associate { it.id to Offset(it.posX, it.posY) }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(getCanvasBackgroundColor(style))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(0.4f, 2.5f)
                    panOffsetX += pan.x
                    panOffsetY += pan.y
                }
            }
            .testTag("diagram_canvas_box")
    ) {
        // 1. Canvas Layer: Grid Background and Directed Connection Lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDiagramGrid(style, panOffsetX, panOffsetY, zoomScale)

            // Draw all connections between nodes
            connections.forEach { conn ->
                val fromPos = nodePositions[conn.fromNodeId]
                val toPos = nodePositions[conn.toNodeId]
                if (fromPos != null && toPos != null) {
                    val p1 = Offset(
                        fromPos.x * zoomScale + panOffsetX,
                        fromPos.y * zoomScale + panOffsetY
                    )
                    val p2 = Offset(
                        toPos.x * zoomScale + panOffsetX,
                        toPos.y * zoomScale + panOffsetY
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

            // Draw active connecting line preview if user is linking two nodes
            if (isConnectingMode && connectingSourceNode != null) {
                val srcPos = nodePositions[connectingSourceNode.id]
                if (srcPos != null) {
                    val p1 = Offset(
                        srcPos.x * zoomScale + panOffsetX,
                        srcPos.y * zoomScale + panOffsetY
                    )
                    drawCircle(
                        color = Color(0xFF3B82F6),
                        radius = 24f * zoomScale,
                        center = p1,
                        style = Stroke(width = 3f * zoomScale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                    )
                }
            }
        }

        // 2. Nodes Layer: Interactive Compose Cards placed at coordinates
        nodes.forEach { node ->
            val screenX = node.posX * zoomScale + panOffsetX
            val screenY = node.posY * zoomScale + panOffsetY
            val isSelected = selectedNode?.id == node.id
            val isSource = connectingSourceNode?.id == node.id

            Box(
                modifier = Modifier
                    .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
                    .pointerInput(node.id) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newX = node.posX + dragAmount.x / zoomScale
                                val newY = node.posY + dragAmount.y / zoomScale
                                onNodeMoved(node.id, newX, newY)
                            }
                        )
                    }
                    .clickable {
                        onNodeSelected(node)
                    }
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
                    .padding(bottom = 16.dp)
                    .testTag("node_quick_action_bar")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = getStatusColor(selectedNode.status).copy(alpha = 0.15f),
                        modifier = Modifier.clickable { onStatusToggle(selectedNode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selectedNode.status == NodeStatus.COMPLETED.name) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                contentDescription = null,
                                tint = getStatusColor(selectedNode.status),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedNode.status,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = getStatusColor(selectedNode.status)
                            )
                        }
                    }

                    // Connect To Another Node
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable { onStartConnecting(selectedNode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.NorthEast, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    // Convert to Task in Task Manager
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                        modifier = Modifier.clickable { onConvertToTask(selectedNode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PostAdd, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("To Task", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                        }
                    }

                    // Delete Node
                    IconButton(
                        onClick = { onDeleteNode(selectedNode) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Node", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }

                    // Close action bar
                    IconButton(
                        onClick = { onNodeSelected(null) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * Node card rendered according to the selected DiagramStyle.
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

    when (style) {
        DiagramStyle.CLASSIC_FLOWCHART -> {
            ClassicFlowchartNode(node, nodeType, nodeColor, isSelected, isConnectingSource)
        }
        DiagramStyle.MIND_MAP -> {
            MindMapNode(node, nodeType, nodeColor, isSelected, isConnectingSource)
        }
        DiagramStyle.AGILE_ROADMAP -> {
            AgileRoadmapNode(node, nodeType, nodeColor, isSelected, isConnectingSource)
        }
        DiagramStyle.CYBERPUNK_NEON -> {
            CyberpunkNeonNode(node, nodeType, nodeColor, isSelected, isConnectingSource)
        }
        DiagramStyle.STICKY_NOTES -> {
            StickyNotesNode(node, nodeType, nodeColor, isSelected, isConnectingSource)
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
        NodeType.START_END -> RoundedCornerShape(28.dp) // Capsule
        NodeType.DECISION -> RoundedCornerShape(8.dp) // Diamond-like compact
        NodeType.MILESTONE -> RoundedCornerShape(12.dp)
        else -> RoundedCornerShape(10.dp) // Process rectangle
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
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

// 2. MIND MAP STYLE (Curved radiating bubbles)
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
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 3.dp else 2.dp,
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

// 3. AGILE ROADMAP STYLE (Sprint and phase cards)
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
                width = if (isSelected) 2.dp else 1.dp,
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
                        text = "ORDER #${node.orderIndex}",
                        fontSize = 9.sp,
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
                        fontSize = 9.sp,
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

// 4. CYBERPUNK NEON STYLE (Glowing borders and dark tech monospace)
@Composable
private fun CyberpunkNeonNode(
    node: DiagramNodeEntity,
    type: NodeType,
    color: Color,
    isSelected: Boolean,
    isConnectingSource: Boolean
) {
    val neonGlow = if (isSelected) Color(0xFF00FFFF) else color
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF0F172A),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.5.dp else 1.5.dp,
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

// 5. STICKY NOTES STYLE (Pastel pinboard notes)
@Composable
private fun StickyNotesNode(
    node: DiagramNodeEntity,
    type: NodeType,
    color: Color,
    isSelected: Boolean,
    isConnectingSource: Boolean
) {
    val pastelColor = when (node.orderIndex % 4) {
        0 -> Color(0xFFFEF08A) // Soft Yellow
        1 -> Color(0xFFBAE6FD) // Soft Cyan
        2 -> Color(0xFFBBF7D0) // Soft Mint
        else -> Color(0xFFFED7AA) // Soft Orange
    }

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = pastelColor,
        shadowElevation = if (isSelected) 8.dp else 4.dp,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1E293B)) else null,
        modifier = Modifier
            .widthIn(min = 130.dp, max = 180.dp)
            .rotate(if (node.orderIndex % 2 == 0) -2f else 2f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Push pin indicator
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

/**
 * Draws background grid pattern based on diagram style.
 */
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

/**
 * Draws connection paths and arrowheads between nodes.
 */
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
        else -> 2.5f
    }

    val path = Path()
    when (style) {
        DiagramStyle.MIND_MAP -> {
            // Smooth organic cubic bezier
            val midX = (p1.x + p2.x) / 2
            path.moveTo(p1.x, p1.y)
            path.cubicTo(midX, p1.y, midX, p2.y, p2.x, p2.y)
        }
        DiagramStyle.STICKY_NOTES -> {
            // Dotted pin-to-pin string
            path.moveTo(p1.x, p1.y)
            path.lineTo(p2.x, p2.y)
        }
        else -> {
            // Direct / orthogonal connection
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

    // Draw Arrowhead at p2
    val angle = atan2(p2.y - p1.y, p2.x - p1.x)
    val arrowSize = 16f
    val arrowP1 = Offset(
        p2.x - arrowSize * cos(angle - Math.PI / 6).toFloat(),
        p2.y - arrowSize * sin(angle - Math.PI / 6).toFloat()
    )
    val arrowP2 = Offset(
        p2.x - arrowSize * cos(angle + Math.PI / 6).toFloat(),
        p2.y - arrowSize * sin(angle + Math.PI / 6).toFloat()
    )

    val arrowPath = Path().apply {
        moveTo(p2.x, p2.y)
        lineTo(arrowP1.x, arrowP1.y)
        lineTo(arrowP2.x, arrowP2.y)
        close()
    }
    drawPath(arrowPath, color = color)

    // Draw label text along connection line if provided
    if (label.isNotEmpty()) {
        val midX = (p1.x + p2.x) / 2
        val midY = (p1.y + p2.y) / 2
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
                midX - textWidth / 2 - 8f,
                midY - 18f,
                midX + textWidth / 2 + 8f,
                midY + 12f,
                8f,
                8f,
                bgPaint
            )
            drawText(label, midX - textWidth / 2, midY + 4f, paint)
        }
    }
}

private fun getCanvasBackgroundColor(style: DiagramStyle): Color {
    return when (style) {
        DiagramStyle.CYBERPUNK_NEON -> Color(0xFF030712)
        DiagramStyle.STICKY_NOTES -> Color(0xFFE7E5E4) // Corkboard cream
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
