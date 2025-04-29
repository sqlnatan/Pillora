package com.pillora.pillora.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember // Adicionado
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// import androidx.compose.ui.graphics.vector.ImageVector // Removido, não mais necessário por HomeMenuItem
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.repository.AuthRepository
import com.pillora.pillora.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel() // Injetar ViewModel
) {
    // Observar os estados do ViewModel
    val medicinesToday by viewModel.medicinesToday.collectAsState()
    val stockAlerts by viewModel.stockAlerts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Mostrar Snackbar em caso de erro
    LaunchedEffect(error) {
        error?.let {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = it,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pillora") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                    IconButton(onClick = {
                        AuthRepository.signOut()
                        navController.navigate("auth") {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Bem-vindo ao Pillora",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Card "Medicamentos de Hoje"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Linha do Título com Botões
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Medicamentos de Hoje", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.weight(1f)) // Empurra os botões para a direita
                            IconButton(onClick = { navController.navigate(Screen.MedicineForm.route) }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Cadastrar Medicamento")
                            }
                            IconButton(onClick = { navController.navigate(Screen.MedicineList.route) }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Ver Lista de Medicamentos")
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        if (medicinesToday.isNotEmpty()) {
                            medicinesToday.forEach { med ->
                                // TODO: Criar um Composable melhor para exibir o item
                                Text(
                                    text = "${med.name} - ${med.dose} ${med.doseUnit ?: ""} - ${med.horarios?.joinToString() ?: med.startTime ?: "N/A"}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        } else {
                            Text("Nenhum medicamento agendado para hoje.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Card "Consultas Médicas"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Consultas Médicas", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { navController.navigate(Screen.ConsultationForm.route) }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Adicionar Consulta")
                            }
                            IconButton(onClick = { navController.navigate(Screen.ConsultationList.route) }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Ver Lista de Consultas")
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        // TODO: Optionally display next upcoming consultation here (requires HomeViewModel update)
                        Text("Acesse a lista para ver ou adicionar consultas.", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Card "Alertas"
                if (stockAlerts.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Alertas de Estoque",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f))
                            stockAlerts.forEach { med ->
                                Text(
                                    text = "${med.name}: Estoque baixo (${med.stockQuantity} ${med.stockUnit})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }

                // Card "Acesso Rápido" REMOVIDO

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Indicador de carregamento centralizado
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

// HomeMenuItem REMOVIDO

