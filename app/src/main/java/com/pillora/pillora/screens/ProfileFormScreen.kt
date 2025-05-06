package com.pillora.pillora.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.viewmodel.ProfileDetailUiState
import com.pillora.pillora.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFormScreen(
    navController: NavController,
    profileId: String?,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val detailState by profileViewModel.profileDetailState.collectAsState()
    val formState = profileViewModel.profileUiState
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    val isEditingProfile = !profileId.isNullOrBlank()
    val screenTitle = if (isEditingProfile) "Editar Perfil" else "Adicionar Perfil"

    // Load profile details when editing
    LaunchedEffect(profileId) {
        // Only load if the state is Idle and profileId is present
        if (detailState is ProfileDetailUiState.Idle && isEditingProfile) {
            profileViewModel.loadProfileDetails(profileId)
        } else if (!isEditingProfile && detailState !is ProfileDetailUiState.Idle) {
            // If adding new, ensure form is reset if state wasn't Idle
            profileViewModel.loadProfileDetails(null)
        }
    }

    // Handle navigation after successful operation (save/update/delete)
    LaunchedEffect(detailState) {
        if (detailState is ProfileDetailUiState.OperationSuccess) {
            navController.popBackStack()
            profileViewModel.resetDetailState() // Reset state AFTER navigation
        }
    }

    // --- Delete Confirmation Dialog ---
    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text("Confirmar Exclusão") },
            text = { Text("Tem certeza que deseja excluir este perfil? Todos os dados associados (receitas, medicamentos, consultas, vacinas) serão permanentemente removidos. Esta ação não pode ser desfeita.") },
            confirmButton = {
                Button(
                    onClick = {
                        profileId?.let { profileViewModel.deleteProfileAndData(it) }
                        showDeleteConfirmationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteConfirmationDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // --- UI ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (isEditingProfile) {
                        IconButton(onClick = { showDeleteConfirmationDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Deletar Perfil", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) {
            paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Spacer(modifier = Modifier.height(0.dp))

            // Display loading indicator
            if (detailState is ProfileDetailUiState.Loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Display error message
            if (detailState is ProfileDetailUiState.Error) {
                Text(
                    text = (detailState as ProfileDetailUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            // Form Fields (Show only when not loading)
            if (detailState !is ProfileDetailUiState.Loading) {
                OutlinedTextField(
                    value = formState.name,
                    onValueChange = profileViewModel::updateName,
                    label = { Text("Nome do Perfil") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    isError = detailState is ProfileDetailUiState.Error && formState.name.isBlank(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = formState.relationship,
                    onValueChange = profileViewModel::updateRelationship,
                    label = { Text("Relação (ex: Filho, Mãe, Eu)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true
                )

                // Save/Update Button
                Button(
                    onClick = { profileViewModel.saveOrUpdateProfile() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                    // Button is enabled when not loading
                    enabled = true
                ) {
                    Text(if (isEditingProfile) "Atualizar Perfil" else "Salvar Perfil")
                }
            }
        }
    }
}

