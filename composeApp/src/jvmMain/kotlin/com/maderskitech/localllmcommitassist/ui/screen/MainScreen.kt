package com.maderskitech.localllmcommitassist.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

            Button(onClick = onAddProject) {
                Text("Add Project")
            }
        }

        Text("Commit Generation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = uiState.additionalContextInput,
            onValueChange = onAdditionalContextChange,
            label = { Text("Additional Context (Optional)") },
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )

        Button(onClick = onGenerate) {
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
            Button(onClick = onCopyToClipboard) {
                Text("Copy to Clipboard")
            }
            Button(onClick = onCommitChanges) {
                Text("Commit Changes")
            }
            Button(onClick = onClearCommitText) {
                Text("Clear")
            }
        }

        uiState.generationStatus?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}
