package com.maderskitech.localllmcommitassist.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
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
            val dropTarget = DropTarget(window, DnDConstants.ACTION_COPY, object : DropTargetAdapter() {
                override fun dragEnter(dtde: DropTargetDragEvent) {
                    isDragHovering = true
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                }
                override fun dragExit(dte: DropTargetEvent) {
                    isDragHovering = false
                }
                override fun drop(dtde: DropTargetDropEvent) {
                    isDragHovering = false
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    val transferable = dtde.transferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        viewModel.addAttachments(files)
                    }
                    dtde.dropComplete(true)
                }
            })
            window.dropTarget = dropTarget
            onDispose {
                window.dropTarget = null
                isDragHovering = false
            }
        } else {
            window.dropTarget = null
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

                            // PR Description with drop zone indicator
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f).then(
                                        if (isDragHovering) Modifier
                                            .border(
                                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                                RoundedCornerShape(8.dp),
                                            )
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                                RoundedCornerShape(8.dp),
                                            )
                                        else Modifier
                                    ),
                                ) {
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

                                // Attach Files button
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

                            // Attachment chips
                            if (state.prAttachments.isNotEmpty()) {
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
                                Text(
                                    text = state.prUrl,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(12.dp)
                                        .clickable {
                                            runCatching {
                                                Desktop.getDesktop().browse(URI(state.prUrl))
                                            }
                                        },
                                )
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

        // File size error dialog
        if (state.showFileSizeErrorDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissFileSizeErrorDialog() },
                title = { Text("File Too Large") },
                text = { Text(state.fileSizeErrorMessage) },
                confirmButton = {
                    Button(onClick = { viewModel.dismissFileSizeErrorDialog() }) {
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
