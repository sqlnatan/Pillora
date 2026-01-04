package com.pillora.pillora.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete // Import Delete icon
import androidx.compose.material.icons.filled.Edit // Import Edit icon
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf // Import mutableStateOf
import androidx.compose.runtime.remember // Import remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue // Import setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext // *** CORRIGIDO: Import LocalContext ***
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.data.local.AppDatabase // *** CORRIGIDO: Import AppDatabase ***
import com.pillora.pillora.model.Recipe
import com.pillora.pillora.navigation.RECIPE_FORM_ROUTE // Import route constant
import com.pillora.pillora.viewmodel.RecipeListUiState
import com.pillora.pillora.viewmodel.RecipeViewModel
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    navController: NavController,
    recipeViewModel: RecipeViewModel = viewModel()
) {
    val context = LocalContext.current // *** CORRIGIDO: Obter Context ***
    val application = context.applicationContext as PilloraApplication
    val isPremium by application.userPreferences.isPremium.collectAsState(initial = false)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lembreteDao = remember { AppDatabase.getDatabase(context).lembreteDao() } // *** CORRIGIDO: Obter LembreteDao ***

    val uiState by recipeViewModel.recipeListState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var recipeToDelete by remember { mutableStateOf<Recipe?>(null) }

    // Verificar se é premium
    LaunchedEffect(isPremium) {
        if (!isPremium) {
            navController.navigate("subscription") {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            "Minhas Receitas",
                            style = MaterialTheme.typography.titleMedium
                        )

                        val count = when (uiState) {
                            is RecipeListUiState.Success ->
                                (uiState as RecipeListUiState.Success).recipes.size
                            else -> 0
                        }

                        Text(
                            text = "$count ${if (count == 1) "receita" else "receitas"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    FilledIconButton(
                        onClick = {
                            recipeViewModel.resetDetailState()
                            navController.navigate("$RECIPE_FORM_ROUTE?id=")
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Adicionar Receita"
                        )
                    }
                }
            )

        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .padding(horizontal = 16.dp) // Apply horizontal padding here
        ) {

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
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
                        ) { // Increased spacing
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
                            // *** CORRIGIDO: Passar context e lembreteDao ***
                            recipeToDelete?.id?.let { recipeViewModel.deleteRecipe(context, lembreteDao, it) }
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Médico: ${recipe.doctorName}", style = MaterialTheme.typography.bodyMedium)
                Text("Data: ${recipe.prescriptionDate}", style = MaterialTheme.typography.bodyMedium)
                if (recipe.prescribedMedications.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Medicamentos: ${recipe.prescribedMedications.joinToString { it.name }}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, // Limit lines if needed
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis // Add ellipsis
                    )
                }
            }
            // Buttons Column
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onEditClick) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar Receita",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Excluir Receita",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

        }
    }
}
