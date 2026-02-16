package com.maderskitech.localllmcommitassist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maderskitech.localllmcommitassist.viewmodel.MainViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var dropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Local LLM Commit Assist", style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(onClick = onNavigateToSettings) {
                Text("Settings")
            }
        }

        HorizontalDivider()

        // Project selector
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
                    label = { Text("Project") },
                    placeholder = { Text("Select or add a project") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    singleLine = true,
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

            Button(onClick = {
                val chooser = JFileChooser().apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Select Git Repository"
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    viewModel.addProject(chooser.selectedFile.absolutePath)
                }
            }) {
                Text("Add Project")
            }

            if (state.repoPath.isNotBlank()) {
                OutlinedButton(onClick = {
                    viewModel.removeProject(state.repoPath)
                }) {
                    Text("Remove")
                }
            }

            Button(
                onClick = { viewModel.loadDiff() },
                enabled = !state.isLoading && state.repoPath.isNotBlank(),
            ) {
                Text("Load Diff")
            }
        }

        // Diff area
        OutlinedTextField(
            value = state.diffText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Staged Diff") },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        // Generate button
        Button(
            onClick = { viewModel.generateCommitMessage() },
            enabled = !state.isLoading && state.diffText.isNotBlank(),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Generate Commit Message")
        }

        // Commit message fields
        OutlinedTextField(
            value = state.commitSummary,
            onValueChange = { viewModel.updateCommitSummary(it) },
            label = { Text("Commit Summary") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.commitDescription,
            onValueChange = { viewModel.updateCommitDescription(it) },
            label = { Text("Commit Description") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { viewModel.commit() },
                enabled = !state.isLoading && state.commitSummary.isNotBlank(),
            ) {
                Text("Commit")
            }
            OutlinedButton(
                onClick = {
                    val text = if (state.commitDescription.isBlank()) {
                        state.commitSummary
                    } else {
                        "${state.commitSummary}\n\n${state.commitDescription}"
                    }
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(StringSelection(text), null)
                },
                enabled = state.commitSummary.isNotBlank(),
            ) {
                Text("Copy to Clipboard")
            }
        }

        // Status / loading
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (state.statusMessage.isNotBlank()) {
            Text(
                text = state.statusMessage,
                color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
