package com.maderskitech.localllmcommitassist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    projects: List<Project>,
    selectedProjectName: String,
    dropdownExpanded: Boolean,
    additionalContextInput: String,
    generatedSummary: String,
    generatedDescription: String,
    generationStatus: String?,
    onToggleDropdown: () -> Unit,
    onDismissDropdown: () -> Unit,
    onSelectProject: (Project) -> Unit,
    onAddProject: () -> Unit,
    onAdditionalContextChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onCopyToClipboard: () -> Unit,
    onCommitChanges: () -> Unit,
    onSummaryChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Projects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        Button(onClick = onAddProject) {
            Text("Add Project")
        }

        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { onToggleDropdown() }
        ) {
            OutlinedTextField(
                value = selectedProjectName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Selected Project") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
            )

            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = onDismissDropdown
            ) {
                projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(text = project.name) },
                        onClick = { onSelectProject(project) }
                    )
                }
            }
        }

        Text("Commit Generation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = additionalContextInput,
            onValueChange = onAdditionalContextChange,
            label = { Text("Additional Context (Optional)") },
            modifier = Modifier.fillMaxWidth().height(180.dp)
        )

        Button(onClick = onGenerate) {
            Text("Generate Commit Summary + Description")
        }

        OutlinedTextField(
            value = generatedSummary,
            onValueChange = onSummaryChange,
            label = { Text("Summary") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = generatedDescription,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCopyToClipboard) {
                Text("Copy to Clipboard")
            }
            Button(onClick = onCommitChanges) {
                Text("Commit Changes")
            }
        }

        generationStatus?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}
