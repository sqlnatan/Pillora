package com.pillora.pillora.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.model.Dependent
import com.pillora.pillora.navigation.Screen // Certifique-se que esta importação está correta para seu projeto
import com.pillora.pillora.viewmodel.DependentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DependentListScreen(
    navController: NavController,
    dependentViewModel: DependentViewModel = viewModel()
) {
    val dependents by dependentViewModel.dependents.collectAsState()
    val isLoading by dependentViewModel.isLoading.collectAsState()
    val error by dependentViewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Dependentes") })
        },
        floatingActionButton = {
            // Corrigido para usar Screen.DependentForm.route
            FloatingActionButton(onClick = { navController.navigate(Screen.DependentForm.route) }) {
                Icon(Icons.Filled.Add, "Adicionar Dependente")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error?.let { errorMessage ->
                Text(
                    text = "Erro: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (dependents.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum dependente cadastrado.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(dependents, key = { it.id }) { dependent ->
                        DependentListItem(dependent = dependent, onDelete = {
                            dependentViewModel.deleteDependent(dependent.id)
                        }, onEdit = {
                            if (dependent.id.isNotEmpty()) {
                                // Corrigido para usar Screen.DependentForm.route
                                navController.navigate("${Screen.DependentForm.route}?dependentId=${dependent.id}")
                            } else {
                                // Handle error: show a toast or log, as ID is crucial
                            }
                        })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun DependentListItem(
    dependent: Dependent,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = dependent.name, style = MaterialTheme.typography.titleMedium)
        Row {
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar Dependente")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Excluir Dependente")
            }
        }
    }
}

