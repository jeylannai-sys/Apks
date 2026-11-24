package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.example.data.DiagramConnectionEntity
import com.example.data.DiagramEntity
import com.example.data.DiagramNodeEntity
import com.example.data.DiagramStyle
import com.example.data.NodeStatus
import com.example.data.NodeType
import com.example.data.TaskEntity
import com.example.ui.components.DiagramCanvas
import kotlin.math.roundToInt

/**
 * Screen providing customizable Flowchart and Diagram planning with draggable shapes,
 * titled connections, reset controls, 5 rendering styles, and node-to-task conversion.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagramPlanningScreen(
    diagrams: List<DiagramEntity>,
    currentDiagram: DiagramEntity?,
    nodes: List<DiagramNodeEntity>,
    connections: List<DiagramConnectionEntity>,
    onSelectDiagram: (DiagramEntity) -> Unit,
    onCreateDiagram: (title: String, style: DiagramStyle) -> Unit,
    onRenameDiagram: (DiagramEntity, newTitle: String, newDescription: String) -> Unit,
    onUpdateDiagramStyle: (DiagramEntity, DiagramStyle) -> Unit,
    onDeleteDiagram: (DiagramEntity) -> Unit,
    onSaveNode: (DiagramNodeEntity) -> Unit,
    onDeleteNode: (DiagramNodeEntity) -> Unit,
    onNodeMoved: (nodeId: String, newX: Float, newY: Float) -> Unit,
    onSaveConnection: (DiagramConnectionEntity) -> Unit,
    onDeleteConnection: (DiagramConnectionEntity) -> Unit,
    onAutoArrangeNodes: (diagramId: String) -> Unit,
    onResetDiagram: (diagramId: String, addStarterTopic: Boolean) -> Unit,
    onLoadTemplate: (templateName: String) -> Unit,
    onConvertToTask: (TaskEntity) -> Unit,
    onAddShapeWithConnection: ((DiagramNodeEntity, String?, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedNode by remember { mutableStateOf<DiagramNodeEntity?>(null) }
    var editingNode by remember { mutableStateOf<DiagramNodeEntity?>(null) }
    var showNewDiagramDialog by remember { mutableStateOf(false) }
    var showRenameDiagramDialog by remember { mutableStateOf(false) }
    var showNewNodeDialog by remember { mutableStateOf(false) }
    var draftNodeType by remember { mutableStateOf(NodeType.PROCESS) }
    var draftDropPos by remember { mutableStateOf<Offset?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var showConvertToTaskDialog by remember { mutableStateOf<DiagramNodeEntity?>(null) }
    var connectingSourceNode by remember { mutableStateOf<DiagramNodeEntity?>(null) }
    var pendingConnectionTarget by remember { mutableStateOf<DiagramNodeEntity?>(null) }
    var showDiagramPickerMenu by remember { mutableStateOf(false) }

    // Viewport pan & zoom controlled here so Reset View & shape drop-coordinates stay exact
    var panOffsetX by remember { mutableFloatStateOf(40f) }
    var panOffsetY by remember { mutableFloatStateOf(40f) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    // Root container & canvas bounds for dragging shape elements from palette onto canvas
    var rootOffsetInWindow by remember { mutableStateOf(Offset.Zero) }
    var canvasBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var draggingPaletteType by remember { mutableStateOf<NodeType?>(null) }
    var draggingPaletteRootPos by remember { mutableStateOf(Offset.Zero) }

    val activeStyle = remember(currentDiagram?.style) {
        try {
            DiagramStyle.valueOf(currentDiagram?.style ?: DiagramStyle.CLASSIC_FLOWCHART.name)
        } catch (_: Exception) {
            DiagramStyle.CLASSIC_FLOWCHART
        }
    }

    // Keep selectedNode refreshed when nodes list updates
    val resolvedSelectedNode = remember(selectedNode, nodes) {
        selectedNode?.let { sel -> nodes.find { it.id == sel.id } }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                rootOffsetInWindow = coords.positionInRoot()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. Top Header: Diagram Title / Topic, Switch Dropdown, Templates, New
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showDiagramPickerMenu = true }
                                .testTag("diagram_picker_dropdown")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AccountTree,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currentDiagram?.title ?: "Planning Flowchart",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("▼", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(
                                    text = "${nodes.size} shapes • ${connections.size} links",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (currentDiagram != null) {
                                IconButton(
                                    onClick = { showRenameDiagramDialog = true },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("edit_diagram_title_button")
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Diagram Title & Topic",
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showDiagramPickerMenu,
                                onDismissRequest = { showDiagramPickerMenu = false }
                            ) {
                                diagrams.forEach { d ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(d.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(d.style, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        onClick = {
                                            onSelectDiagram(d)
                                            showDiagramPickerMenu = false
                                        },
                                        trailingIcon = if (diagrams.size > 1) {
                                            {
                                                IconButton(
                                                    onClick = {
                                                        onDeleteDiagram(d)
                                                        showDiagramPickerMenu = false
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete Diagram",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        } else null
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { showTemplatesDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("diagram_templates_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Templates", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { showNewDiagramDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("new_diagram_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2. Visual Style Switcher Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Style,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Style:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(DiagramStyle.entries) { styleItem ->
                                val isSelected = activeStyle == styleItem
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (currentDiagram != null) {
                                            onUpdateDiagramStyle(currentDiagram, styleItem)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = styleItem.displayName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("style_chip_${styleItem.name}")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3. Hold-Before-Drag / Tappable Shape Elements Toolbox
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Shapes:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(end = 12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("shape_palette_row")
                            ) {
                                items(NodeType.entries) { shapeType ->
                                    DraggableShapePaletteChip(
                                        nodeType = shapeType,
                                        onTap = {
                                            draftNodeType = shapeType
                                            draftDropPos = null
                                            showNewNodeDialog = true
                                        },
                                        onDragStart = { startRootPos ->
                                            draggingPaletteType = shapeType
                                            draggingPaletteRootPos = startRootPos
                                        },
                                        onDragMove = { currentRootPos ->
                                            draggingPaletteRootPos = currentRootPos
                                        },
                                        onDragEnd = { endRootPos ->
                                            val droppedType = draggingPaletteType
                                            draggingPaletteType = null
                                            if (droppedType != null && endRootPos != Offset.Zero) {
                                                if (canvasBoundsInRoot.contains(endRootPos)) {
                                                    val localX = endRootPos.x - canvasBoundsInRoot.left
                                                    val localY = endRootPos.y - canvasBoundsInRoot.top
                                                    val canvasX = ((localX - panOffsetX) / zoomScale - 80f).coerceAtLeast(10f)
                                                    val canvasY = ((localY - panOffsetY) / zoomScale - 40f).coerceAtLeast(10f)
                                                    draftNodeType = droppedType
                                                    draftDropPos = Offset(canvasX, canvasY)
                                                    showNewNodeDialog = true
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Swipe ↔ for more shapes • Hold & drag to canvas, or tap",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, start = 46.dp)
                        )
                    }
                }
            }

            // 4. Main Diagram Canvas & Toolbar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(10.dp)
                    .onGloballyPositioned { coords ->
                        canvasBoundsInRoot = coords.boundsInRoot()
                    }
            ) {
                DiagramCanvas(
                    nodes = nodes,
                    connections = connections,
                    style = activeStyle,
                    selectedNode = resolvedSelectedNode,
                    panOffsetX = panOffsetX,
                    panOffsetY = panOffsetY,
                    zoomScale = zoomScale,
                    onTransformChange = { px, py, z ->
                        panOffsetX = px
                        panOffsetY = py
                        zoomScale = z
                    },
                    onNodeSelected = { node ->
                        if (connectingSourceNode != null && node != null && connectingSourceNode!!.id != node.id) {
                            // Open Connect with Title / Topic dialog
                            pendingConnectionTarget = node
                        } else {
                            selectedNode = node
                        }
                    },
                    onNodeMoved = onNodeMoved,
                    onEditNode = { node -> editingNode = node },
                    onStatusToggle = { node ->
                        val nextStatus = when (node.status) {
                            NodeStatus.TODO.name -> NodeStatus.IN_PROGRESS.name
                            NodeStatus.IN_PROGRESS.name -> NodeStatus.COMPLETED.name
                            NodeStatus.COMPLETED.name -> NodeStatus.BLOCKED.name
                            else -> NodeStatus.TODO.name
                        }
                        onSaveNode(node.copy(status = nextStatus))
                    },
                    onConvertToTask = { node -> showConvertToTaskDialog = node },
                    onStartConnecting = { node -> connectingSourceNode = node },
                    onDeleteNode = { node ->
                        onDeleteNode(node)
                        selectedNode = null
                    },
                    isConnectingMode = connectingSourceNode != null,
                    connectingSourceNode = connectingSourceNode,
                    modifier = Modifier.fillMaxSize()
                )

                // Top-right Quick Canvas Controls: Auto-Align + Reset Flowchart
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .clickable {
                                currentDiagram?.let { onAutoArrangeNodes(it.id) }
                            }
                            .testTag("auto_align_flowchart_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Auto Arrange", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Auto-Align", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .clickable { showResetDialog = true }
                            .testTag("reset_flowchart_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = "Reset Flowchart",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Reset",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Bottom-right Floating Add Node FAB
                FloatingActionButton(
                    onClick = {
                        draftNodeType = NodeType.PROCESS
                        draftDropPos = null
                        showNewNodeDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .testTag("add_diagram_node_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Flowchart Shape")
                }

                // Connecting mode indicator banner
                if (connectingSourceNode != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 44.dp, start = 12.dp, end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tap target shape to connect from '${connectingSourceNode?.title}'",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { connectingSourceNode = null },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Live Ghost Shape Preview while dragging a shape element from palette onto the canvas
        draggingPaletteType?.let { ghostType ->
            val localOffset = draggingPaletteRootPos - rootOffsetInWindow
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shadowElevation = 10.dp,
                modifier = Modifier.offset {
                    IntOffset(
                        (localOffset.x - 56f).roundToInt(),
                        (localOffset.y - 24f).roundToInt()
                    )
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniShapeIcon(nodeType = ghostType, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Drop ${ghostType.label}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }

    // DIALOG: Reset Flowchart Options
    if (showResetDialog && currentDiagram != null) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Flowchart", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose what to reset for \"${currentDiagram.title}\":",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ElevatedCard(
                        onClick = {
                            panOffsetX = 40f
                            panOffsetY = 40f
                            zoomScale = 1.0f
                            selectedNode = null
                            connectingSourceNode = null
                            showResetDialog = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_view_option")
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ZoomOutMap, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Reset View (100% Zoom)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Re-center canvas and restore default zoom", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    ElevatedCard(
                        onClick = {
                            onAutoArrangeNodes(currentDiagram.id)
                            panOffsetX = 40f
                            panOffsetY = 40f
                            zoomScale = 1.0f
                            showResetDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Reset Layout (Auto-Align)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Reorganize existing shapes into a neat grid", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    ElevatedCard(
                        onClick = {
                            onResetDiagram(currentDiagram.id, true)
                            selectedNode = null
                            connectingSourceNode = null
                            panOffsetX = 40f
                            panOffsetY = 40f
                            zoomScale = 1.0f
                            showResetDialog = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_to_starter_option")
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Color(0xFFF59E0B))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Reset to Fresh Topic", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Clear current diagram and start with 1 Main Topic node", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    ElevatedCard(
                        onClick = {
                            onResetDiagram(currentDiagram.id, false)
                            selectedNode = null
                            connectingSourceNode = null
                            panOffsetX = 40f
                            panOffsetY = 40f
                            zoomScale = 1.0f
                            showResetDialog = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_canvas_option")
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Clear All (Blank Canvas)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                                Text("Remove all shapes and links from this flowchart", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: Rename Current Diagram Title & Topic
    if (showRenameDiagramDialog && currentDiagram != null) {
        var titleText by remember(currentDiagram) { mutableStateOf(currentDiagram.title) }
        var descText by remember(currentDiagram) { mutableStateOf(currentDiagram.description) }
        AlertDialog(
            onDismissRequest = { showRenameDiagramDialog = false },
            title = { Text("Customize Diagram Title / Topic") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("Diagram Title / Topic") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = descText,
                        onValueChange = { descText = it },
                        label = { Text("Topic Summary (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (titleText.isNotBlank()) {
                        onRenameDiagram(currentDiagram, titleText, descText)
                        showRenameDiagramDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDiagramDialog = false }) { Text("Cancel") }
            }
        )
    }

    // DIALOG: Create New Diagram
    if (showNewDiagramDialog) {
        var title by remember { mutableStateOf("") }
        var selectedStyle by remember { mutableStateOf(DiagramStyle.CLASSIC_FLOWCHART) }

        AlertDialog(
            onDismissRequest = { showNewDiagramDialog = false },
            title = { Text("Create New Planning Diagram") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Diagram Title / Topic") },
                        placeholder = { Text("e.g. Project Workflow, Study Map") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Select Initial Visual Style:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    DiagramStyle.entries.forEach { style ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedStyle = style }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedStyle == style) MaterialTheme.colorScheme.primary else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(style.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(style.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onCreateDiagram(title, selectedStyle)
                            showNewDiagramDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewDiagramDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: Add Customizable Shape & Optional Connection with Title/Topic
    if (showNewNodeDialog) {
        var nodeTitle by remember { mutableStateOf("") }
        var nodeDesc by remember { mutableStateOf("") }
        var nodeType by remember(draftNodeType) { mutableStateOf(draftNodeType) }
        var selectedColor by remember { mutableStateOf("#3B82F6") }
        var connectFromNodeId by remember { mutableStateOf<String?>(selectedNode?.id) }
        var connectionTopic by remember { mutableStateOf("") }

        val palette = listOf("#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#14B8A6")

        AlertDialog(
            onDismissRequest = { showNewNodeDialog = false },
            title = { Text("Add Shape & Topic", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = nodeTitle,
                        onValueChange = { nodeTitle = it },
                        label = { Text("Shape Title / Topic") },
                        placeholder = { Text("e.g. Research Phase, User Login?") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_node_title_input")
                    )
                    OutlinedTextField(
                        value = nodeDesc,
                        onValueChange = { nodeDesc = it },
                        label = { Text("Subtitle / Notes (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Element Shape:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NodeType.entries.forEach { type ->
                            FilterChip(
                                selected = nodeType == type,
                                onClick = { nodeType = type },
                                leadingIcon = {
                                    MiniShapeIcon(
                                        nodeType = type,
                                        color = if (nodeType == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                label = { Text(type.label, fontSize = 10.sp) }
                            )
                        }
                    }

                    Text("Shape Accent Color:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        palette.forEach { hex ->
                            val c = try { Color(hex.toColorInt()) } catch (_: Exception) { Color.Blue }
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .clickable { selectedColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == hex) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    if (nodes.isNotEmpty()) {
                        Text("Connect From Existing Shape (Optional):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = connectFromNodeId == null,
                                    onClick = { connectFromNodeId = null },
                                    label = { Text("None", fontSize = 10.sp) }
                                )
                            }
                            items(nodes) { existing ->
                                FilterChip(
                                    selected = connectFromNodeId == existing.id,
                                    onClick = { connectFromNodeId = existing.id },
                                    label = { Text(existing.title, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                        if (connectFromNodeId != null) {
                            OutlinedTextField(
                                value = connectionTopic,
                                onValueChange = { connectionTopic = it },
                                label = { Text("Connection Title / Topic") },
                                placeholder = { Text("e.g. Next, Yes, Branch A") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalTitle = nodeTitle.trim().ifBlank { "${nodeType.label} #${nodes.size + 1}" }
                        val targetDiagramId = currentDiagram?.id ?: ""
                        val newId = java.util.UUID.randomUUID().toString()
                        val drop = draftDropPos
                        val newX = drop?.x ?: (100f + (nodes.size % 3) * 180f)
                        val newY = drop?.y ?: (80f + (nodes.size / 3) * 160f)
                        val createdNode = DiagramNodeEntity(
                            id = newId,
                            diagramId = targetDiagramId,
                            title = finalTitle,
                            description = nodeDesc.trim(),
                            type = nodeType.name,
                            posX = newX,
                            posY = newY,
                            colorHex = selectedColor,
                            orderIndex = nodes.size + 1
                        )
                        if (onAddShapeWithConnection != null) {
                            onAddShapeWithConnection(createdNode, connectFromNodeId, connectionTopic.trim())
                        } else {
                            onSaveNode(createdNode)
                            if (connectFromNodeId != null && targetDiagramId.isNotBlank()) {
                                onSaveConnection(
                                    DiagramConnectionEntity(
                                        diagramId = targetDiagramId,
                                        fromNodeId = connectFromNodeId!!,
                                        toNodeId = newId,
                                        label = connectionTopic.trim(),
                                        colorHex = selectedColor
                                    )
                                )
                            }
                        }
                        selectedNode = createdNode
                        showNewNodeDialog = false
                    },
                    modifier = Modifier.testTag("confirm_add_node_button")
                ) {
                    Text("Add Shape")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewNodeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: Edit Existing Shape & Manage Connections
    editingNode?.let { target ->
        var nodeTitle by remember(target.id) { mutableStateOf(target.title) }
        var nodeDesc by remember(target.id) { mutableStateOf(target.description) }
        var nodeType by remember(target.id) {
            mutableStateOf(try { NodeType.valueOf(target.type) } catch (_: Exception) { NodeType.PROCESS })
        }
        var selectedColor by remember(target.id) { mutableStateOf(target.colorHex) }
        val palette = listOf("#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#14B8A6")
        val nodeLinks = connections.filter { it.fromNodeId == target.id || it.toNodeId == target.id }

        AlertDialog(
            onDismissRequest = { editingNode = null },
            title = { Text("Customize Shape & Connections", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = nodeTitle,
                        onValueChange = { nodeTitle = it },
                        label = { Text("Shape Title / Topic") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nodeDesc,
                        onValueChange = { nodeDesc = it },
                        label = { Text("Subtitle / Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Shape Type:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NodeType.entries.forEach { type ->
                            FilterChip(
                                selected = nodeType == type,
                                onClick = { nodeType = type },
                                leadingIcon = {
                                    MiniShapeIcon(nodeType = type, color = MaterialTheme.colorScheme.primary)
                                },
                                label = { Text(type.label, fontSize = 10.sp) }
                            )
                        }
                    }

                    Text("Color:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        palette.forEach { hex ->
                            val c = try { Color(hex.toColorInt()) } catch (_: Exception) { Color.Blue }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .clickable { selectedColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == hex) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    if (nodeLinks.isNotEmpty()) {
                        Text("Connected Links (${nodeLinks.size}):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        nodeLinks.forEach { conn ->
                            val otherId = if (conn.fromNodeId == target.id) conn.toNodeId else conn.fromNodeId
                            val otherTitle = nodes.find { it.id == otherId }?.title ?: "Shape"
                            val direction = if (conn.fromNodeId == target.id) "→" else "←"
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("$direction $otherTitle", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = if (conn.label.isNotBlank()) "Topic: ${conn.label}" else "No connection title",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteConnection(conn) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove Connection", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (nodeTitle.isNotBlank()) {
                        onSaveNode(
                            target.copy(
                                title = nodeTitle.trim(),
                                description = nodeDesc.trim(),
                                type = nodeType.name,
                                colorHex = selectedColor
                            )
                        )
                        editingNode = null
                    }
                }) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingNode = null }) { Text("Cancel") }
            }
        )
    }

    // DIALOG: Connect Two Shapes with Title / Topic
    val srcNode = connectingSourceNode
    val dstNode = pendingConnectionTarget
    if (srcNode != null && dstNode != null && currentDiagram != null) {
        var linkLabel by remember(srcNode.id, dstNode.id) { mutableStateOf("") }
        var linkColor by remember { mutableStateOf("#3B82F6") }
        val presets = listOf("Next Step", "Yes", "No", "Leads to", "Depends on")
        val linkPalette = listOf("#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#64748B")

        AlertDialog(
            onDismissRequest = {
                pendingConnectionTarget = null
                connectingSourceNode = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NorthEast, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect with Title / Topic", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${srcNode.title}  ➜  ${dstNode.title}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    OutlinedTextField(
                        value = linkLabel,
                        onValueChange = { linkLabel = it },
                        label = { Text("Connection Title / Topic") },
                        placeholder = { Text("e.g. Approved, Phase 2, If Valid") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connection_label_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(presets) { preset ->
                            FilterChip(
                                selected = linkLabel == preset,
                                onClick = { linkLabel = preset },
                                label = { Text(preset, fontSize = 10.sp) }
                            )
                        }
                    }

                    Text("Connector Color:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        linkPalette.forEach { hex ->
                            val c = try { Color(hex.toColorInt()) } catch (_: Exception) { Color.Blue }
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .clickable { linkColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (linkColor == hex) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveConnection(
                            DiagramConnectionEntity(
                                diagramId = currentDiagram.id,
                                fromNodeId = srcNode.id,
                                toNodeId = dstNode.id,
                                label = linkLabel.trim(),
                                colorHex = linkColor
                            )
                        )
                        pendingConnectionTarget = null
                        connectingSourceNode = null
                    },
                    modifier = Modifier.testTag("confirm_connect_button")
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingConnectionTarget = null
                    connectingSourceNode = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: Convert Node to Task
    showConvertToTaskDialog?.let { targetNode ->
        var taskTitle by remember { mutableStateOf(targetNode.title) }
        var taskDesc by remember { mutableStateOf(targetNode.description) }
        var taskPriority by remember { mutableStateOf("HIGH") }

        AlertDialog(
            onDismissRequest = { showConvertToTaskDialog = null },
            title = { Text("Convert Node to Scheduled Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Turn this flowchart step into an actionable task with deadlines and alarm reminders in your Task Organizer.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it },
                        label = { Text("Task Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = taskDesc,
                        onValueChange = { taskDesc = it },
                        label = { Text("Task Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Priority Level:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("LOW", "MEDIUM", "HIGH").forEach { p ->
                            FilterChip(
                                selected = taskPriority == p,
                                onClick = { taskPriority = p },
                                label = { Text(p, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newTask = TaskEntity(
                            title = taskTitle,
                            description = taskDesc,
                            priority = taskPriority,
                            category = "Planning",
                            dueDateMillis = System.currentTimeMillis() + 86400000L * 2
                        )
                        onConvertToTask(newTask)
                        showConvertToTaskDialog = null
                    }
                ) {
                    Text("Add to Tasks")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConvertToTaskDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: Templates Presets
    if (showTemplatesDialog) {
        val templates = listOf(
            "Product Launch Roadmap" to "Concept -> MVP Core -> QA Test -> Marketing -> Public Release",
            "Decision Making Logic Tree" to "Problem Definition -> Cost/Benefit Check [Yes/No] -> Implementation",
            "Agile Sprint Pipeline" to "Sprint Backlog -> Active Development -> Code Review -> QA Verification",
            "Daily Morning Routine" to "6:00 AM Wake -> Hydrate -> 20m Workout -> Healthy Breakfast -> Deep Work",
            "Software Architecture Blueprint" to "Jetpack Compose UI -> ViewModel Flow -> Room Database & Planning Canvas"
        )

        AlertDialog(
            onDismissRequest = { showTemplatesDialog = false },
            title = { Text("Load Planning Template") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose an instant planning structure with interconnected nodes and decision logic:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    templates.forEach { (name, summary) ->
                        ElevatedCard(
                            onClick = {
                                onLoadTemplate(name)
                                showTemplatesDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(summary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTemplatesDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DraggableShapePaletteChip(
    nodeType: NodeType,
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var chipTopLeftInRoot by remember { mutableStateOf(Offset.Zero) }
    var currentPointerInRoot by remember { mutableStateOf(Offset.Zero) }
    var isHoldingForDrag by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isHoldingForDrag) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = if (isHoldingForDrag) 2.dp else 1.dp,
            color = if (isHoldingForDrag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .onGloballyPositioned { coords ->
                chipTopLeftInRoot = coords.positionInRoot()
            }
            .pointerInput(nodeType) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { localOffset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isHoldingForDrag = true
                        val startRoot = chipTopLeftInRoot + localOffset
                        currentPointerInRoot = startRoot
                        onDragStart(startRoot)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentPointerInRoot += dragAmount
                        onDragMove(currentPointerInRoot)
                    },
                    onDragEnd = {
                        isHoldingForDrag = false
                        onDragEnd(currentPointerInRoot)
                    },
                    onDragCancel = {
                        isHoldingForDrag = false
                        onDragEnd(Offset.Zero)
                    }
                )
            }
            .clickable(onClick = onTap)
            .testTag("palette_shape_${nodeType.name}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniShapeIcon(nodeType = nodeType, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = nodeType.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MiniShapeIcon(nodeType: NodeType, color: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.4f)
        when (nodeType) {
            NodeType.START_END -> {
                drawRoundRect(
                    color = color,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f),
                    style = stroke
                )
            }
            NodeType.PROCESS -> {
                drawRoundRect(
                    color = color,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                    style = stroke
                )
            }
            NodeType.DECISION -> {
                val p = Path().apply {
                    moveTo(w / 2f, 1f)
                    lineTo(w - 1f, h / 2f)
                    lineTo(w / 2f, h - 1f)
                    lineTo(1f, h / 2f)
                    close()
                }
                drawPath(p, color = color, style = stroke)
            }
            NodeType.CIRCLE -> {
                drawCircle(color = color, radius = (minOf(w, h) / 2f) - 1f, style = stroke)
            }
            NodeType.MILESTONE -> {
                val indent = w * 0.22f
                val p = Path().apply {
                    moveTo(indent, 1.5f)
                    lineTo(w - indent, 1.5f)
                    lineTo(w - 1f, h / 2f)
                    lineTo(w - indent, h - 1.5f)
                    lineTo(indent, h - 1.5f)
                    lineTo(1f, h / 2f)
                    close()
                }
                drawPath(p, color = color, style = stroke)
            }
            NodeType.NOTE -> {
                drawRect(color = color, style = stroke)
                drawLine(color, Offset(3f, h * 0.45f), Offset(w - 3f, h * 0.45f), strokeWidth = 1.6f)
                drawLine(color, Offset(3f, h * 0.72f), Offset(w - 5f, h * 0.72f), strokeWidth = 1.6f)
            }
        }
    }
}
