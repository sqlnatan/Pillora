package com.pillora.pillora.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange // Add import
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn // Add import
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.repository.ConsultationRepository
import com.pillora.pillora.navigation.Screen // Import Screen object
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultationListScreen(navController: NavController) {

    var consultations by remember { mutableStateOf<List<Consultation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var consultationToDelete by remember { mutableStateOf<Consultation?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Function to load consultations
    fun loadConsultations() {
        isLoading = true
        error = null
        ConsultationRepository.getAllConsultations(
            onSuccess = {
                consultations = it.sortedBy { consultation -> consultation.dateTime } // Sort by date/time
                isLoading = false
            },
            onFailure = {
                error = "Erro ao buscar consultas: ${it.message}"
                isLoading = false
            }
        )
    }

    // Fetch consultations when the screen is composed or refreshed
    LaunchedEffect(Unit) { // Consider adding a refresh mechanism later
        loadConsultations()
    }

    // Delete confirmation dialog
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
                                        loadConsultations() // Reload list after deletion
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
    ) {
            padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { loadConsultations() }) {
                            Text("Tentar novamente")
                        }
                    }
                }
                consultations.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Nenhuma consulta encontrada. Adicione uma nova consulta clicando no botão ",
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { navController.navigate(Screen.ConsultationForm.route) }) {
                            Text("Adicionar Consulta")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp) // Increased spacing
                    ) {
                        items(consultations, key = { it.id }) { consultation -> // Added key for performance
                            ConsultationListItem(
                                consultation = consultation,
                                onEditClick = {
                                    // Corrected navigation to use query parameter format
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

@Composable
fun ConsultationListItem(
    consultation: Consultation,
    onEditClick: () -> Unit, // Added lambda for edit
    onDeleteClick: () -> Unit // Added lambda for delete
) {
    Card(
        modifier = Modifier.fillMaxWidth(), // Removed clickable modifier from Card
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top // Align icons to the top
            ) {
                // Consultation Details Column
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
                // Action Icons Row
                Row {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp)) { // Smaller icons
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) { // Smaller icons
                        Icon(Icons.Default.Delete, contentDescription = "Excluir")
                    }
                }
            }

            // Rest of the details
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
                    maxLines = 2 // Limit observation lines in list view
                )
            }
        }
    }
}

