package com.pillora.pillora.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person // Icon for profile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.model.Profile
import com.pillora.pillora.navigation.PROFILE_FORM_ROUTE
import com.pillora.pillora.viewmodel.ProfileListUiState
import com.pillora.pillora.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val listState by profileViewModel.profileListState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gerenciar Perfis") },
                navigationIcon = {
                    // Assuming this screen is reachable from a settings/main menu
                    // If it's a top-level destination, remove this or adjust as needed
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                profileViewModel.loadProfileDetails(null) // Reset form for adding new
                navController.navigate(PROFILE_FORM_ROUTE)
            }) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Perfil")
            }
        }
    ) {
            paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = listState) {
                is ProfileListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ProfileListUiState.Success -> {
                    if (state.profiles.isEmpty()) {
                        Text(
                            text = "Nenhum perfil encontrado. Adicione um perfil usando o botão '+'",
                            modifier = Modifier.align(Alignment.Center).padding(16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.profiles) { profile ->
                                ProfileListItem(profile = profile) {
                                    // Navigate to form screen for editing this profile
                                    navController.navigate("$PROFILE_FORM_ROUTE?id=${profile.id}")
                                }
                            }
                        }
                    }
                }
                is ProfileListUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileListItem(
    profile: Profile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.Person, contentDescription = "Perfil", tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (profile.relationship.isNotBlank()) {
                    Text(profile.relationship, style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Add edit icon or similar if desired, though clicking the card navigates
            // Icon(Icons.Default.Edit, contentDescription = "Editar")
        }
    }
}

