package com.maderskitech.localllmcommitassist.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maderskitech.localllmcommitassist.data.AttachmentConfig
import com.maderskitech.localllmcommitassist.viewmodel.MainViewModel
import java.awt.Desktop
import java.awt.Component
import java.awt.Container
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File
import java.util.UUID
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.RootPaneContainer
import javax.swing.filechooser.FileNameExtensionFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    window: java.awt.Window,
) {
    val state by viewModel.uiState.collectAsState()
    var dropdownExpanded by remember { mutableStateOf(false) }
    var branchDropdownExpanded by remember { mutableStateOf(false) }
    var currentBranchDropdownExpanded by remember { mutableStateOf(false) }
    var showAddBranchDialog by remember { mutableStateOf(false) }
    var addBranchName by remember { mutableStateOf("") }
    var showDeleteBranchDialog by remember { mutableStateOf(false) }
    var pushAfterCommit by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var isDragHovering by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Drag-and-drop support for PR attachments
    DisposableEffect(window, selectedTab) {
        if (selectedTab == 1) {
            val dropTargetListener = object : DropTargetAdapter() {
                override fun dragEnter(dtde: DropTargetDragEvent) {
                    if (dtde.isFileDrag()) {
                        isDragHovering = true
                        dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    } else {
                        dtde.rejectDrag()
                    }
                }

                override fun dragOver(dtde: DropTargetDragEvent) {
                    if (dtde.isFileDrag()) {
                        isDragHovering = true
                        dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    } else {
                        isDragHovering = false
                        dtde.rejectDrag()
                    }
                }

                override fun dragExit(dte: DropTargetEvent) {
                    isDragHovering = false
                }

                override fun drop(dtde: DropTargetDropEvent) {
                    isDragHovering = false
                    val transferable = dtde.transferable
                    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.rejectDrop()
                        dtde.dropComplete(false)
                        return
                    }
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    runCatching {
                        @Suppress("UNCHECKED_CAST")
                        transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                    }.onSuccess { files ->
                        viewModel.addAttachments(files)
                        dtde.dropComplete(true)
                    }.onFailure {
                        dtde.dropComplete(false)
                    }
                }
            }
            val dropTargets = installFileDropTargets(window, dropTargetListener)
            onDispose {
                dropTargets.forEach { (component, previousDropTarget) ->
                    component.dropTarget = previousDropTarget
                }
                isDragHovering = false
            }
        } else {
            isDragHovering = false
            onDispose { }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Local LLM Commit Assist",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Project selector card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Project",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = if (state.repoPath.isNotBlank()) File(state.repoPath).name else "",
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("Select or add a project") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                        ) {
                            if (state.savedProjects.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No projects added yet", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { dropdownExpanded = false },
                                    enabled = false,
                                )
                            }
                            state.savedProjects.forEach { project ->
                                DropdownMenuItem(
                                    text = { Text(File(project).name) },
                                    onClick = {
                                        viewModel.selectProject(project)
                                        dropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Add Project") } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = {
                                val chooser = JFileChooser().apply {
                                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                    dialogTitle = "Select Git Repository"
                                }
                                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    viewModel.addProject(chooser.selectedFile.absolutePath)
                                }
                            },
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Project", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                    }

                    if (state.repoPath.isNotBlank()) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text("Open in Finder") } },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(onClick = { viewModel.openFinder() }) {
                                Icon(Icons.Default.Folder, contentDescription = "Open in Finder", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text("Open in Terminal") } },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(onClick = { viewModel.openTerminal() }) {
                                Icon(Icons.Default.Terminal, contentDescription = "Open in Terminal", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text("Remove Project") } },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(onClick = { viewModel.removeProject(state.repoPath) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Project", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }


                }

                if (state.currentBranch.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        viewModel.refreshBranches()
                                        currentBranchDropdownExpanded = true
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "⎇",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    state.currentBranch,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch branch",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = currentBranchDropdownExpanded,
                                onDismissRequest = { currentBranchDropdownExpanded = false },
                            ) {
                                if (state.availableBranches.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No branches found") },
                                        onClick = { currentBranchDropdownExpanded = false },
                                        enabled = false,
                                    )
                                } else {
                                    state.availableBranches.forEach { branch ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    branch,
                                                    fontWeight = if (branch == state.currentBranch)
                                                        androidx.compose.ui.text.font.FontWeight.Bold
                                                    else null,
                                                )
                                            },
                                            onClick = {
                                                currentBranchDropdownExpanded = false
                                                viewModel.switchBranch(branch)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text("Add Branch") } },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(
                                onClick = {
                                    addBranchName = ""
                                    showAddBranchDialog = true
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Branch", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                            }
                        }

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = {
                                PlainTooltip {
                                    Text(if (state.isCurrentBranchPublished) "Fetch" else "Publish Branch")
                                }
                            },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(
                                onClick = {
                                    if (state.isCurrentBranchPublished) {
                                        viewModel.fetchBranch()
                                    } else {
                                        viewModel.publishBranch()
                                    }
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    if (state.isCurrentBranchPublished) Icons.Default.CloudDownload else Icons.Default.CloudUpload,
                                    contentDescription = if (state.isCurrentBranchPublished) "Fetch" else "Publish Branch",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text("Delete Branch") } },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(
                                onClick = { showDeleteBranchDialog = true },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Branch", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Commit") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Create PR") },
            )
        }

        // Tab content
        when (selectedTab) {
            0 -> {
                val selectedCount = state.changedFiles.count { it.isSelected }
                val hasSelectedFiles = selectedCount > 0
                val canGenerate = !state.isLoading && state.repoPath.isNotBlank() && hasSelectedFiles
                var showDiff by remember { mutableStateOf(false) }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                ) {
                    // LEFT PANEL — File selection (Step 1)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                        ) {
                            // Header row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Changed Files (${state.changedFiles.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                IconButton(
                                    onClick = { viewModel.loadChangedFiles() },
                                    enabled = !state.isLoading && state.repoPath.isNotBlank(),
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            if (state.changedFiles.isEmpty()) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "No changed files detected. Click Refresh to scan.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                // Select All row with count
                                val allSelected = state.changedFiles.all { it.isSelected }
                                val noneSelected = state.changedFiles.none { it.isSelected }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    TriStateCheckbox(
                                        state = when {
                                            allSelected -> ToggleableState.On
                                            noneSelected -> ToggleableState.Off
                                            else -> ToggleableState.Indeterminate
                                        },
                                        onClick = {
                                            if (allSelected) viewModel.unselectAllFiles() else viewModel.selectAllFiles()
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary,
                                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    )
                                    Text(
                                        "Select All ($selectedCount/${state.changedFiles.size} selected)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                // File list — fills available space
                                val fileListScrollState = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(fileListScrollState),
                                ) {
                                    state.changedFiles.forEach { file ->
                                        FileListItem(
                                            filePath = file.path,
                                            status = file.status,
                                            isSelected = file.isSelected,
                                            onToggle = { viewModel.toggleFileSelection(file.path) },
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Generate button at bottom of left panel
                            Button(
                                onClick = { viewModel.generateCommitMessage() },
                                enabled = canGenerate,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                ),
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Generating...", style = MaterialTheme.typography.labelLarge)
                                } else {
                                    Text("Generate Commit Message", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            if (!canGenerate && !state.isLoading && !hasSelectedFiles && state.changedFiles.isNotEmpty()) {
                                Text(
                                    "Select files to generate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally),
                                )
                            }
                        }
                    }

                    // RIGHT PANEL — Review & Commit (Step 2)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Loading bar at top
                            if (state.isLoading) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                            }

                            // Header
                            Text(
                                "Review & Commit",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            // Diff preview
                            DiffPreview(
                                diff = state.fullDiff,
                                expanded = showDiff,
                                onToggle = { showDiff = !showDiff },
                            )

                            if (state.commitSummary.isBlank() && state.commitDescription.isBlank() && !state.isLoading) {
                                // Empty state placeholder
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "Generate a commit message",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                // Summary field
                                OutlinedTextField(
                                    value = state.commitSummary,
                                    onValueChange = { viewModel.updateCommitSummary(it) },
                                    label = { Text("Summary") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                // Description field — grows to fill
                                OutlinedTextField(
                                    value = state.commitDescription,
                                    onValueChange = { viewModel.updateCommitDescription(it) },
                                    label = { Text("Description") },
                                    minLines = 3,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            // Action buttons
                            CommitActions(
                                commitSummary = state.commitSummary,
                                commitDescription = state.commitDescription,
                                isLoading = state.isLoading,
                                pushAfterCommit = pushAfterCommit,
                                onPushToggle = { pushAfterCommit = it },
                                onCommit = { viewModel.commit(andPush = pushAfterCommit) },
                            )
                        }
                    }
                }
            }
            1 -> {
                val platformName = if (state.prPlatform == "azure_devops") "Azure DevOps" else "GitHub"
                val platformLimit = AttachmentConfig.maxSizeLabelForPlatform(state.prPlatform)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Pull Request card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "Pull Request",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            ExposedDropdownMenuBox(
                                expanded = branchDropdownExpanded,
                                onExpandedChange = { branchDropdownExpanded = it },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedTextField(
                                    value = state.prTargetBranch.ifBlank { "main" },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Merge Into") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchDropdownExpanded) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth(),
                                )
                                ExposedDropdownMenu(
                                    expanded = branchDropdownExpanded,
                                    onDismissRequest = { branchDropdownExpanded = false },
                                ) {
                                    if (state.availableBranches.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No branches found", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                            onClick = { branchDropdownExpanded = false },
                                            enabled = false,
                                        )
                                    }
                                    state.availableBranches.forEach { branch ->
                                        DropdownMenuItem(
                                            text = { Text(branch) },
                                            onClick = {
                                                viewModel.updatePrTargetBranch(branch)
                                                branchDropdownExpanded = false
                                            },
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { viewModel.generatePrDescription() },
                                enabled = !state.isLoading && state.repoPath.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                ),
                            ) {
                                Text("Generate PR Description", style = MaterialTheme.typography.labelLarge)
                            }

                            OutlinedTextField(
                                value = state.prTitle,
                                onValueChange = { viewModel.updatePrTitle(it) },
                                label = { Text("PR Title") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            "Attachments (${state.prAttachments.size})",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            "Drag images or videos here, or use the attach button. Supported types: ${AttachmentConfig.allowedExtensions.sorted().joinToString(", ")}. Current limit for $platformName: $platformLimit.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TooltipBox(
                                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                            tooltip = { PlainTooltip { Text("Paste Image") } },
                                            state = rememberTooltipState(),
                                        ) {
                                            IconButton(
                                                onClick = { pasteClipboardAttachment(viewModel) },
                                            ) {
                                                Icon(
                                                    Icons.Default.ContentPaste,
                                                    contentDescription = "Paste Image",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }

                                        TooltipBox(
                                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                            tooltip = { PlainTooltip { Text("Attach Files") } },
                                            state = rememberTooltipState(),
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    val chooser = JFileChooser().apply {
                                                        fileSelectionMode = JFileChooser.FILES_ONLY
                                                        isMultiSelectionEnabled = true
                                                        dialogTitle = "Select Images or Videos"
                                                        fileFilter = FileNameExtensionFilter(
                                                            "Images & Videos",
                                                            *AttachmentConfig.allowedExtensions.toTypedArray(),
                                                        )
                                                    }
                                                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                                        viewModel.addAttachments(chooser.selectedFiles.toList())
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    Icons.Default.AttachFile,
                                                    contentDescription = "Attach Files",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            BorderStroke(
                                                if (isDragHovering) 2.dp else 1.dp,
                                                if (isDragHovering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            ),
                                            RoundedCornerShape(12.dp),
                                        )
                                        .background(
                                            if (isDragHovering) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                            },
                                            RoundedCornerShape(12.dp),
                                        ),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (isDragHovering) {
                                            Text(
                                                "Drop files to attach them to this pull request",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 4.dp),
                                            )
                                        }

                                        if (state.prAttachments.isEmpty()) {
                                            Text(
                                                "No attachments added yet.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp),
                                            )
                                        } else {
                                            Text(
                                                "${state.prAttachments.size} attachment${if (state.prAttachments.size == 1) "" else "s"} ready to upload with this PR.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 4.dp),
                                            )

                                            @OptIn(ExperimentalLayoutApi::class)
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                state.prAttachments.forEach { attachment ->
                                                    val sizeMb = "%.1f".format(attachment.sizeBytes.toDouble() / (1024 * 1024))
                                                    InputChip(
                                                        selected = false,
                                                        onClick = {},
                                                        label = { Text("${attachment.name} ($sizeMb MB)", style = MaterialTheme.typography.bodySmall) },
                                                        trailingIcon = {
                                                            IconButton(
                                                                onClick = { viewModel.removeAttachment(attachment.id) },
                                                                modifier = Modifier.size(18.dp),
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Close,
                                                                    contentDescription = "Remove ${attachment.name}",
                                                                    modifier = Modifier.size(14.dp),
                                                                )
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = state.prBody,
                                            onValueChange = { viewModel.updatePrBody(it) },
                                            label = { Text("PR Description") },
                                            minLines = 3,
                                            maxLines = 6,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = { viewModel.createPullRequest() },
                                    enabled = !state.isLoading && state.prTitle.isNotBlank(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    ),
                                ) {
                                    Text("Create PR")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        val text = buildString {
                                            append(state.prTitle)
                                            if (state.prBody.isNotBlank()) {
                                                append("\n\n")
                                                append(state.prBody)
                                            }
                                        }
                                        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                        clipboard.setContents(StringSelection(text), null)
                                    },
                                    enabled = state.prTitle.isNotBlank(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                ) {
                                    Text("Copy to Clipboard")
                                }
                            }

                            if (state.prUrl.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable {
                                            runCatching {
                                                Desktop.getDesktop().browse(URI(state.prUrl))
                                            }
                                        }
                                        .padding(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudUpload,
                                        contentDescription = "Pull Request",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = if (state.prNumber.isNotBlank()) "PR ${state.prNumber}" else "Open Pull Request",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = state.prUrl,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Status bar
        if (state.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
        // Add Branch dialog
        if (showAddBranchDialog) {
            AlertDialog(
                onDismissRequest = { showAddBranchDialog = false },
                title = { Text("New Branch") },
                text = {
                    OutlinedTextField(
                        value = addBranchName,
                        onValueChange = { addBranchName = it },
                        label = { Text("Branch name") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAddBranchDialog = false
                            viewModel.createBranch(addBranchName.trim())
                        },
                        enabled = addBranchName.isNotBlank(),
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddBranchDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        // Delete Branch dialog
        if (showDeleteBranchDialog) {
            var deleteFromRemote by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showDeleteBranchDialog = false },
                title = { Text("Delete Branch") },
                text = {
                    Column {
                        Text("Are you sure you want to delete '${state.currentBranch}'?")
                        if (state.isCurrentBranchPublished) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = deleteFromRemote,
                                    onCheckedChange = { deleteFromRemote = it },
                                )
                                Text("Also delete from remote")
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteBranchDialog = false
                            viewModel.deleteCurrentBranch(deleteFromRemote)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteBranchDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        // Branch switch with uncommitted changes dialog
        if (state.showBranchSwitchDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBranchSwitchDialog() },
                title = { Text("Uncommitted Changes") },
                text = { Text("You have uncommitted changes. What would you like to do?") },
                confirmButton = {
                    Button(
                        onClick = { viewModel.onBranchSwitchBringChanges() },
                    ) {
                        Text("Bring Changes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onBranchSwitchLeaveChanges() }) {
                        Text("Leave Changes")
                    }
                },
            )
        }

        // Attachment validation dialog
        if (state.showAttachmentValidationDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissAttachmentValidationDialog() },
                title = { Text(state.attachmentValidationTitle) },
                text = { Text(state.attachmentValidationMessage) },
                confirmButton = {
                    Button(onClick = { viewModel.dismissAttachmentValidationDialog() }) {
                        Text("OK")
                    }
                },
            )
        }

        // Publish branch dialog
        if (state.showPublishBranchDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissPublishBranchDialog() },
                title = { Text("Publish Branch?") },
                text = { Text("The branch \"${state.currentBranch}\" has not been published to the remote yet. Would you like to publish it and push your changes?") },
                confirmButton = {
                    Button(onClick = { viewModel.confirmPublishAndPush() }) {
                        Text("Publish & Push")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissPublishBranchDialog() }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (state.statusMessage.isNotBlank()) {
            val bgColor = if (state.isError)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.primaryContainer
            val textColor = if (state.isError)
                MaterialTheme.colorScheme.onErrorContainer
            else
                MaterialTheme.colorScheme.onPrimaryContainer

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = state.statusMessage,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.isError) {
                    IconButton(
                        onClick = {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(StringSelection(state.statusMessage), null)
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy error to clipboard",
                            tint = textColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun installFileDropTargets(window: java.awt.Window, listener: DropTargetAdapter): List<Pair<Component, DropTarget?>> {
    val components = linkedSetOf<Component>()
    components += window
    if (window is RootPaneContainer) {
        components += window.rootPane
        components += window.layeredPane
        components += window.glassPane
        components += window.contentPane
        collectChildComponents(window.contentPane, components)
    } else if (window is Container) {
        collectChildComponents(window, components)
    }

    return components.map { component ->
        val previousDropTarget = component.dropTarget
        component.dropTarget = DropTarget(component, DnDConstants.ACTION_COPY, listener, true)
        component to previousDropTarget
    }
}

private fun collectChildComponents(container: Container, components: MutableSet<Component>) {
    container.components.forEach { component ->
        components += component
        if (component is Container) {
            collectChildComponents(component, components)
        }
    }
}

private fun DropTargetDragEvent.isFileDrag(): Boolean =
    transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)

private fun pasteClipboardAttachment(viewModel: MainViewModel) {
    val clipboard = runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull()
    if (clipboard == null) {
        viewModel.showAttachmentStatus("Clipboard is not available.", isError = true)
        return
    }

    val transferable = runCatching { clipboard.getContents(null) }.getOrNull()
    if (transferable == null) {
        viewModel.showAttachmentStatus("Clipboard is empty.", isError = true)
        return
    }

    when {
        transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
            runCatching {
                @Suppress("UNCHECKED_CAST")
                transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
            }.onSuccess { files ->
                viewModel.addAttachments(files)
            }.onFailure {
                viewModel.showAttachmentStatus("Could not read files from the clipboard.", isError = true)
            }
        }

        transferable.isDataFlavorSupported(DataFlavor.imageFlavor) -> {
            runCatching {
                val image = transferable.getTransferData(DataFlavor.imageFlavor) as? Image
                    ?: error("Clipboard image format is not supported")
                createClipboardImageTempFile(image)
            }.onSuccess { file ->
                viewModel.addAttachments(listOf(file), isTempFile = true)
            }.onFailure {
                viewModel.showAttachmentStatus("Clipboard does not contain a supported image.", isError = true)
            }
        }

        else -> viewModel.showAttachmentStatus("Clipboard does not contain an image or file attachment.", isError = true)
    }
}

private fun createClipboardImageTempFile(image: Image): File {
    val tempFile = File(System.getProperty("java.io.tmpdir"), "pr-clipboard-${UUID.randomUUID()}.png")
    ImageIO.write(image.toBufferedImage(), "png", tempFile)
    return tempFile
}

private fun Image.toBufferedImage(): BufferedImage {
    if (this is BufferedImage) return this

    val width = getWidth(null)
    val height = getHeight(null)
    require(width > 0 && height > 0) { "Clipboard image has invalid dimensions." }

    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { buffered ->
        val graphics = buffered.createGraphics()
        try {
            graphics.drawImage(this, 0, 0, null)
        } finally {
            graphics.dispose()
        }
    }
}

@Composable
private fun FileStatusIndicator(status: String) {
    val color = when (status) {
        "added", "untracked" -> Color(0xFF4CAF50) // green
        "modified" -> Color(0xFF2196F3) // blue
        "deleted" -> Color(0xFFF44336) // red
        else -> Color.Gray
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape),
    )
}

@Composable
private fun FileListItem(
    filePath: String,
    status: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 2.dp),
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        FileStatusIndicator(status)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                filePath,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = when (status) {
                    "added", "untracked" -> MaterialTheme.colorScheme.tertiary
                    "deleted" -> MaterialTheme.colorScheme.error
                    "modified" -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun DiffPreview(
    diff: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column {
        TextButton(
            onClick = onToggle,
            enabled = diff.isNotBlank(),
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(if (expanded) "Hide Diff" else "Show Diff")
        }

        AnimatedVisibility(
            visible = expanded && diff.isNotBlank(),
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            val annotatedDiff = remember(diff) {
                buildAnnotatedString {
                    diff.lineSequence().forEach { line ->
                        val style = when {
                            line.startsWith("@@") -> SpanStyle(color = Color(0xFF64B5F6)) // blue
                            line.startsWith("+") -> SpanStyle(color = Color(0xFF81C784)) // green
                            line.startsWith("-") -> SpanStyle(color = Color(0xFFE57373)) // red
                            else -> SpanStyle()
                        }
                        withStyle(style) { append(line) }
                        append("\n")
                    }
                }
            }
            val diffScrollState = rememberScrollState()
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    annotatedDiff,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(diffScrollState)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun CommitActions(
    commitSummary: String,
    commitDescription: String,
    isLoading: Boolean,
    pushAfterCommit: Boolean,
    onPushToggle: (Boolean) -> Unit,
    onCommit: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onCommit,
            enabled = !isLoading && commitSummary.isNotBlank(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
        ) {
            Text(if (pushAfterCommit) "Commit & Push" else "Commit")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = pushAfterCommit,
                onCheckedChange = onPushToggle,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.tertiary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Text(
                "Push",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = {
                val text = if (commitDescription.isBlank()) {
                    commitSummary
                } else {
                    "$commitSummary\n\n$commitDescription"
                }
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(text), null)
            },
            enabled = commitSummary.isNotBlank(),
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy commit message",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
