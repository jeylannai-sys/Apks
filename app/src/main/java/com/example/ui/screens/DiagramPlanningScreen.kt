package com.example.ui.screens

import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DiagramConnectionEntity
import com.example.data.DiagramEntity
import com.example.data.DiagramNodeEntity
import com.example.data.DiagramStyle
import com.example.data.NodeStatus
import com.example.data.NodeType
import com.example.data.TaskEntity
import com.example.ui.components.DiagramCanvas
import java.util.UUID

/**
 * Screen providing comprehensive Flowchart and Diagram planning capabilities
 * with 5 distinct rendering styles, node-to-task conversion, and auto-layout.
 */
@Composable
fun DiagramPlanningScreen(
    diagrams: List<DiagramEntity>,
    currentDiagram: DiagramEntity?,
    nodes: List<DiagramNodeEntity>,
    connections: List<DiagramConnectionEntity>,
    onSelectDiagram: (DiagramEntity) -> Unit,
    onCreateDiagram: (title: String, style: DiagramStyle) -> Unit,
    onUpdateDiagramStyle: (DiagramEntity, DiagramStyle) -> Unit,
    onDeleteDiagram: (DiagramEntity) -> Unit,
    onSaveNode: (DiagramNodeEntity) -> Unit,
    onDeleteNode: (DiagramNodeEntity) -> Unit,
    onNodeMoved: (nodeId: String, newX: Float, newY: Float) -> Unit,
    onSaveConnection: (DiagramConnectionEntity) -> Unit,
    onDeleteConnection: (DiagramConnectionEntity) -> Unit,
    onAutoArrangeNodes: (diagramId: String) -> Unit,
    onLoadTemplate: (templateName: String) -> Unit,
    onConvertToTask: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedNode by remember { mutableStateOf<DiagramNodeEntity?>(null) }
    var showNewDiagramDialog by remember { mutableStateOf(false) }
    var showNewNodeDialog by remember { mutableStateOf(false) }
    var showConnectDialog by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var showConvertToTaskDialog by remember { mutableStateOf<DiagramNodeEntity?>(null) }
    var connectingSourceNode by remember { mutableStateOf<DiagramNodeEntity?>(null) }
    var showDiagramPickerMenu by remember { mutableStateOf(false) }

    val activeStyle = remember(currentDiagram?.style) {
        try {
            DiagramStyle.valueOf(currentDiagram?.style ?: DiagramStyle.CLASSIC_FLOWCHART.name)
        } catch (_: Exception) {
            DiagramStyle.CLASSIC_FLOWCHART
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Top Header: Diagram Title, Switch Diagram Dropdown, and New Diagram Button
        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
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
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentDiagram?.title ?: "Planning Flowchart",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("▼", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                text = "${nodes.size} nodes • ${connections.size} links",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                                    }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Templates Preset Button
                        OutlinedButton(
                            onClick = { showTemplatesDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("diagram_templates_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Templates", fontSize = 11.sp)
                        }

                        // New Diagram
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

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Visual Style Switcher Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Style,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Visual Style:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(DiagramStyle.values()) { styleItem ->
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
            }
        }

        // 3. Main Diagram Canvas & Toolbar
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            DiagramCanvas(
                nodes = nodes,
                connections = connections,
                style = activeStyle,
                selectedNode = selectedNode,
                onNodeSelected = { node ->
                    if (connectingSourceNode != null && node != null && connectingSourceNode!!.id != node.id) {
                        // Complete connection linking
                        onSaveConnection(
                            DiagramConnectionEntity(
                                diagramId = currentDiagram?.id ?: "",
                                fromNodeId = connectingSourceNode!!.id,
                                toNodeId = node.id,
                                label = "Next Step"
                            )
                        )
                        connectingSourceNode = null
                    } else {
                        selectedNode = node
                    }
                },
                onNodeMoved = onNodeMoved,
                onStatusToggle = { node ->
                    val nextStatus = when (node.status) {
                        NodeStatus.TODO.name -> NodeStatus.IN_PROGRESS.name
                        NodeStatus.IN_PROGRESS.name -> NodeStatus.COMPLETED.name
                        NodeStatus.COMPLETED.name -> NodeStatus.BLOCKED.name
                        else -> NodeStatus.TODO.name
                    }
                    onSaveNode(node.copy(status = nextStatus))
                },
                onConvertToTask = { node ->
                    showConvertToTaskDialog = node
                },
                onStartConnecting = { node ->
                    connectingSourceNode = node
                },
                onDeleteNode = { node ->
                    onDeleteNode(node)
                    selectedNode = null
                },
                isConnectingMode = connectingSourceNode != null,
                connectingSourceNode = connectingSourceNode,
                modifier = Modifier.fillMaxSize()
            )

            // Top-right Quick Canvas Controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Auto Arrange Nodes Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.clickable {
                        currentDiagram?.let { onAutoArrangeNodes(it.id) }
                    }
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
            }

            // Bottom-right Floating Add Node FAB
            FloatingActionButton(
                onClick = { showNewNodeDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_diagram_node_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Flowchart Node")
            }

            // Connecting indicator banner
            if (connectingSourceNode != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tap target node to connect from '${connectingSourceNode?.title}'",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { connectingSourceNode = null },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // DIALOG: Create New Diagram
    if (showNewDiagramDialog) {
        var title by remember { mutableStateOf("") }
        var selectedStyle by remember { mutableStateOf(DiagramStyle.CLASSIC_FLOWCHART) }

        AlertDialog(
            onDismissRequest = { showNewDiagramDialog = false },
            title = { Text("Create New Planning Diagram") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Diagram Name") },
                        placeholder = { Text("e.g. Q4 Sprint, Feature Pipeline, Habit Flow") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Select Initial Visual Style:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    DiagramStyle.values().forEach { style ->
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

    // DIALOG: Add Node
    if (showNewNodeDialog) {
        var nodeTitle by remember { mutableStateOf("") }
        var nodeDesc by remember { mutableStateOf("") }
        var nodeType by remember { mutableStateOf(NodeType.PROCESS) }
        var selectedColor by remember { mutableStateOf("#3B82F6") }

        val palette = listOf("#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#14B8A6")

        AlertDialog(
            onDismissRequest = { showNewNodeDialog = false },
            title = { Text("Add Flowchart / Diagram Node") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nodeTitle,
                        onValueChange = { nodeTitle = it },
                        label = { Text("Node Title") },
                        placeholder = { Text("e.g. 1. Draft Specification") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nodeDesc,
                        onValueChange = { nodeDesc = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Node Role / Shape:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NodeType.values().forEach { type ->
                            FilterChip(
                                selected = nodeType == type,
                                onClick = { nodeType = type },
                                label = { Text(type.label, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("Accent Color:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        palette.forEach { hex ->
                            val c = try { Color(hex.toColorInt()) } catch (_: Exception) { Color.Blue }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nodeTitle.isNotBlank() && currentDiagram != null) {
                            val newX = 150f + (nodes.size % 3) * 180f
                            val newY = 100f + (nodes.size / 3) * 160f
                            onSaveNode(
                                DiagramNodeEntity(
                                    diagramId = currentDiagram.id,
                                    title = nodeTitle,
                                    description = nodeDesc,
                                    type = nodeType.name,
                                    posX = newX,
                                    posY = newY,
                                    colorHex = selectedColor,
                                    orderIndex = nodes.size + 1
                                )
                            )
                            showNewNodeDialog = false
                        }
                    }
                ) {
                    Text("Add Node")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewNodeDialog = false }) {
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
                            dueDateMillis = System.currentTimeMillis() + 86400000L * 2 // in 2 days
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
            "Software Architecture Blueprint" to "Jetpack Compose UI -> ViewModel Flow -> Room Database & Map Tile CDN"
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
