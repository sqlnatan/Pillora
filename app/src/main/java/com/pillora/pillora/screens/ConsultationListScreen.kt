package com.pillora.pillora.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.repository.ConsultationRepository
import com.pillora.pillora.navigation.Screen
import kotlinx.coroutines.flow.map // Import map operator
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

// Define states for the screen based on Flow emission
sealed interface ConsultationListUiState {
    data object Loading : ConsultationListUiState
    data class Success(val consultations: List<Consultation>) : ConsultationListUiState
    data class Error(val message: String) : ConsultationListUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultationListScreen(navController: NavController) {

    // Collect the flow, map success/error to UI states
    val uiState by ConsultationRepository.getAllConsultationsFlow()
        .map<List<Consultation>, ConsultationListUiState> { consultations -> // Map successful list emission to Success state
            ConsultationListUiState.Success(consultations)
        }
        .catch { exception -> // Catch exceptions from the flow or the map operator
            // Emit Error state. This is now valid as the flow type is Flow<ConsultationListUiState>
            emit(ConsultationListUiState.Error("Erro ao carregar consultas: ${exception.message}"))
        }
        // Start with Loading state, collectAsStateWithLifecycle handles the initial value.
        .collectAsStateWithLifecycle(initialValue = ConsultationListUiState.Loading)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var consultationToDelete by remember { mutableStateOf<Consultation?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Delete confirmation dialog (remains the same)
    if (showDeleteDialog && consultationToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirmar exclusão") },
            text = { Text("Deseja realmente excluir a consulta de ${consultationToDelete?.specialty}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        consultationToDelete?.id?.let { id ->
                            scope.launch {
                                ConsultationRepository.deleteConsultation(
                                    consultationId = id,
                                    onSuccess = {
                                        Toast.makeText(context, "Consulta excluída com sucesso", Toast.LENGTH_SHORT).show()
                                        // Flow updates the list automatically
                                    },
                                    onFailure = {
                                        Toast.makeText(context, "Erro ao excluir consulta: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                        showDeleteDialog = false
                        consultationToDelete = null
                    }
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Consultas Médicas") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.ConsultationForm.route) }) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar Consulta")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Adapt the UI based on the collected uiState (remains the same)
            when (val state = uiState) {
                is ConsultationListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ConsultationListUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        // Consider adding a retry button
                    }
                }
                is ConsultationListUiState.Success -> {
                    val consultations = state.consultations.sortedBy { it.dateTime }
                    if (consultations.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Nenhuma consulta encontrada. Adicione uma nova consulta clicando no botão +",
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(consultations, key = { it.id }) { consultation ->
                                ConsultationListItem(
                                    consultation = consultation,
                                    onEditClick = {
                                        navController.navigate("${Screen.ConsultationForm.route}?id=${consultation.id}")
                                    },
                                    onDeleteClick = {
                                        consultationToDelete = consultation
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ConsultationListItem remains the same
@Composable
fun ConsultationListItem(
    consultation: Consultation,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = consultation.specialty,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dr(a). ${consultation.doctorName.ifEmpty { "Não informado" }}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Excluir")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, contentDescription = "Data e Hora", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = consultation.dateTime.ifEmpty { "Data/Hora não informada" },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (consultation.location.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = "Local", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = consultation.location,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (consultation.observations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Obs: ${consultation.observations}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
        }
    }
}

