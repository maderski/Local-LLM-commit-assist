package com.maderskitech.localllmcommitassist.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maderskitech.localllmcommitassist.ui.state.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: AppUiState,
    onToggleDropdown: () -> Unit,
    onDismissDropdown: () -> Unit,
    onSelectProjectAtPath: (String) -> Unit,
    onAddProject: () -> Unit,
    onAdditionalContextChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onCopyToClipboard: () -> Unit,
    onCommitChanges: () -> Unit,
    onClearCommitText: () -> Unit,
    onSummaryChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Projects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = uiState.dropdownExpanded,
                        onExpandedChange = { onToggleDropdown() },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = uiState.selectedProject?.name.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Selected Project") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = uiState.dropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                                .fillMaxWidth(),
                        )

                        DropdownMenu(
                            expanded = uiState.dropdownExpanded,
                            onDismissRequest = onDismissDropdown,
                        ) {
                            uiState.projects.forEach { project ->
                                DropdownMenuItem(
                                    text = { Text(text = project.name) },
                                    onClick = { onSelectProjectAtPath(project.path) },
                                )
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = onAddProject,
                        modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    ) {
                        Text("Add Project")
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Commit Message", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = uiState.additionalContextInput,
                    onValueChange = onAdditionalContextChange,
                    label = { Text("Additional Context (Optional)") },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )

                Button(
                    onClick = onGenerate,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                ) {
                    Text("Generate Commit Summary + Description")
                }

                OutlinedTextField(
                    value = uiState.generatedSummary,
                    onValueChange = onSummaryChange,
                    label = { Text("Summary") },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = uiState.generatedDescription,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onCopyToClipboard) {
                        Text("Copy")
                    }
                    Button(
                        onClick = onCommitChanges,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        )
                    ) {
                        Text("Commit Changes")
                    }
                    FilledTonalButton(onClick = onClearCommitText) {
                        Text("Clear")
                    }
                }
            }
        }

        uiState.generationStatus?.let {
            val isError = it.contains("failed", ignoreCase = true) || it.contains("required", ignoreCase = true)
            val background = if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            }
            val contentColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
            Surface(
                color = background,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = it,
                    color = contentColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
