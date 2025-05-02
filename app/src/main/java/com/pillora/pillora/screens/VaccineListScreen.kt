package com.pillora.pillora.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm // Icon for reminder time
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes // Icon for notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pillora.pillora.model.Vaccine
import com.pillora.pillora.navigation.Screen // Assuming Screen object exists
import com.pillora.pillora.repository.VaccineRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccineListScreen(navController: NavController) {

    var vaccines by remember { mutableStateOf<List<Vaccine>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var vaccineToDelete by remember { mutableStateOf<Vaccine?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Function to load vaccine reminders (using callback pattern like ConsultationListScreen)
    fun loadVaccines() {
        isLoading = true
        error = null
        VaccineRepository.getAllVaccines(
            onSuccess = {
                // Assuming getAllVaccines already sorts by reminderDate
                vaccines = it
                isLoading = false
            },
            onFailure = {
                error = "Erro ao buscar lembretes: ${it.message}"
                isLoading = false
            }
        )
    }

    // Fetch vaccine reminders when the screen is composed
    LaunchedEffect(Unit) {
        loadVaccines()
    }

    // Delete confirmation dialog (similar to ConsultationListScreen)
    if (showDeleteDialog && vaccineToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirmar exclusão") },
            text = { Text("Deseja realmente excluir o lembrete para ${vaccineToDelete?.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vaccineToDelete?.id?.let { id ->
                            scope.launch {
                                VaccineRepository.deleteVaccine(
                                    vaccineId = id,
                                    onSuccess = {
                                        Toast.makeText(context, "Lembrete excluído com sucesso", Toast.LENGTH_SHORT).show()
                                        loadVaccines() // Reload list after deletion
                                    },
                                    onFailure = {
                                        Toast.makeText(context, "Erro ao excluir lembrete: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                        showDeleteDialog = false
                        vaccineToDelete = null
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
                title = { Text("Lembretes de Vacina") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.VaccineForm.route) }) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar Lembrete")
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
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { loadVaccines() }) {
                            Text("Tentar novamente")
                        }
                    }
                }
                vaccines.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Nenhum lembrete de vacina encontrado.")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { navController.navigate(Screen.VaccineForm.route) }) {
                            Text("Adicionar Lembrete")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(vaccines, key = { it.id }) { vaccine ->
                            VaccineListItem(
                                vaccine = vaccine,
                                onEditClick = {
                                    navController.navigate("${Screen.VaccineForm.route}?id=${vaccine.id}")
                                },
                                onDeleteClick = {
                                    vaccineToDelete = vaccine
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
fun VaccineListItem(
    vaccine: Vaccine,
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
                // Vaccine Details Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vaccine.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Action Icons Row
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

            // Reminder Date and Time
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, contentDescription = "Data", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = vaccine.reminderDate.ifEmpty { "Data não informada" },
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(16.dp)) // Space between date and time
                Icon(Icons.Filled.Alarm, contentDescription = "Hora", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = vaccine.reminderTime.ifEmpty { "Hora não informada" },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Location
            if (vaccine.location.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = "Local", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = vaccine.location,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Notes
            if (vaccine.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) { // Align icon top for multi-line text
                    Icon(Icons.Filled.Notes, contentDescription = "Observações", modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = vaccine.notes,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

