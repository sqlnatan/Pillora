package com.pillora.pillora.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete // Import Delete icon
import androidx.compose.material.icons.filled.Edit // Import Edit icon
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf // Import mutableStateOf
import androidx.compose.runtime.remember // Import remember
import androidx.compose.runtime.setValue // Import setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.model.Recipe
import com.pillora.pillora.navigation.RECIPE_FORM_ROUTE // Import route constant
import com.pillora.pillora.viewmodel.RecipeListUiState
import com.pillora.pillora.viewmodel.RecipeViewModel
import java.util.UUID

@Composable
fun RecipeListScreen(
    navController: NavController,
    recipeViewModel: RecipeViewModel = viewModel()
) {
    val uiState by recipeViewModel.recipeListState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var recipeToDelete by remember { mutableStateOf<Recipe?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Reset detail state before navigating to add new recipe
                    recipeViewModel.resetDetailState() // <<< ADDED: Reset state for adding new
                    // Navigate to Add Recipe Screen (pass empty ID or specific marker)
                    navController.navigate("$RECIPE_FORM_ROUTE?id=")
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Receita")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp) // Apply horizontal padding here
        ) {
            Text(
                "Minhas Receitas",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp) // Add vertical padding
            )

            when (val state = uiState) {
                is RecipeListUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is RecipeListUiState.Success -> {
                    if (state.recipes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nenhuma receita encontrada.")
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { // Increased spacing
                            items(state.recipes, key = { it.id ?: UUID.randomUUID().toString() }) { recipe ->
                                RecipeListItem(
                                    recipe = recipe,
                                    onEditClick = {
                                        // <<< ADDED: Reset detail state before navigating to edit
                                        recipeViewModel.resetDetailState()
                                        // Navigate to Recipe Detail/Edit Screen
                                        navController.navigate("$RECIPE_FORM_ROUTE?id=${recipe.id}")
                                    },
                                    onDeleteClick = {
                                        recipeToDelete = recipe
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
                is RecipeListUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Erro: ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Confirmation Dialog for Deletion
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Confirmar Exclusão") },
                text = { Text("Tem certeza que deseja excluir a receita para '${recipeToDelete?.patientName}'?") },
                confirmButton = {
                    Button(
                        onClick = {
                            recipeToDelete?.id?.let { recipeViewModel.deleteRecipe(it) }
                            showDeleteDialog = false
                            recipeToDelete = null // Clear selection
                        }
                    ) {
                        Text("Excluir")
                    }
                },
                dismissButton = {
                    Button(onClick = { showDeleteDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun RecipeListItem(
    recipe: Recipe,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row( // Use Row to place content and buttons side-by-side
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp), // Adjust padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) { // Content takes available space
                if (recipe.patientName.isNotBlank()) {
                    Text(
                        text = "Paciente: ${recipe.patientName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Médico: ${recipe.doctorName}", style = MaterialTheme.typography.bodyMedium)
                Text("Data: ${recipe.prescriptionDate}", style = MaterialTheme.typography.bodyMedium)
                if (recipe.prescribedMedications.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Medicamentos: ${recipe.prescribedMedications.joinToString { it.name }}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, // Limit lines if needed
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis // Add ellipsis
                    )
                }
            }
            // Buttons Column
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar Receita")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir Receita", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

