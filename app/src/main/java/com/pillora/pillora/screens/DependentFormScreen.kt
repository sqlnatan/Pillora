package com.pillora.pillora.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Corrected import for ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.model.Dependent
import com.pillora.pillora.viewmodel.DependentViewModel

@OptIn(ExperimentalMaterial3Api::class) // Added for Material 3 components
@Composable
fun DependentFormScreen(
    navController: NavController,
    dependentViewModel: DependentViewModel = viewModel(),
    dependentId: String? = null
) {
    var name by remember { mutableStateOf("") }
    val isLoading by dependentViewModel.isLoading.collectAsState()
    val error by dependentViewModel.error.collectAsState()
    var isEditing by remember { mutableStateOf(false) }

    // Effect to load dependent data if dependentId is provided (for editing)
    LaunchedEffect(dependentId) {
        if (dependentId != null) {
            isEditing = true
            // Attempt to find the dependent in the current list
            // A more robust approach would be a specific function in ViewModel to fetch by ID
            val existingDependent = dependentViewModel.dependents.value.find { dep -> dep.id == dependentId }
            existingDependent?.let { dep ->
                name = dep.name
            }
            // If not found, you might want to trigger a specific load in ViewModel
            // dependentViewModel.loadDependentById(dependentId) // Example
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar Dependente" else "Adicionar Dependente") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") // Corrected Icon
                    }
                }
            )
        }
    ) { paddingValues -> // Changed 'it' to 'paddingValues' to avoid shadowing
        Column(
            modifier = Modifier
                .padding(paddingValues) // Use paddingValues from Scaffold
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do Dependente") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        if (isEditing && dependentId != null) {
                            // Ensure userId is passed if your model/repository expects it, though for update ID is key
                            dependentViewModel.updateDependent(Dependent(id = dependentId, name = name, userId = "")) // Assuming userId might be needed by model
                        } else {
                            dependentViewModel.addDependent(name)
                        }
                        // Consider observing a success state from ViewModel to navigate
                        // For now, popBackStack if no immediate error is shown from a previous op.
                        if (error == null) {
                            navController.popBackStack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (isEditing) "Salvar Alterações" else "Adicionar Dependente")
                }
            }

            error?.let { errorMessage -> // Changed 'it' to 'errorMessage'
                Text(
                    text = "Erro: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

