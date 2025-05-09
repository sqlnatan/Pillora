package com.pillora.pillora.screens

// Imports moved to the top
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
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
import androidx.compose.ui.text.style.TextAlign // Import TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pillora.pillora.model.Vaccine
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.repository.VaccineRepository
import kotlinx.coroutines.launch
// import java.util.UUID // Import moved to top

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

    // Function to load vaccine reminders
    fun loadVaccines() {
        isLoading = true
        error = null
        VaccineRepository.getAllVaccines(
            onSuccess = {
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

    // Delete confirmation dialog
    if (showDeleteDialog && vaccineToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; vaccineToDelete = null },
            title = { Text("Confirmar exclusão") },
            text = { Text("Deseja realmente excluir o lembrete para ${vaccineToDelete?.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vaccineToDelete?.id?.let { id ->
                            if (id.isNotEmpty()) { // Ensure ID is not empty before deleting
                                scope.launch {
                                    VaccineRepository.deleteVaccine(
                                        vaccineId = id,
                                        onSuccess = {
                                            Toast.makeText(context, "Lembrete excluído com sucesso", Toast.LENGTH_SHORT).show()
                                            loadVaccines() // Reload list
                                        },
                                        onFailure = {
                                            Toast.makeText(context, "Erro ao excluir lembrete: ${it.message}", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            } else {
                                Toast.makeText(context, "Erro: ID inválido para exclusão", Toast.LENGTH_SHORT).show()
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
                TextButton(onClick = { showDeleteDialog = false; vaccineToDelete = null }) {
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
                        // Use safe call ?.let or provide a default value for error
                        Text(text = error ?: "Ocorreu um erro desconhecido", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
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
                        // Use vaccine.id directly as key, it's non-nullable String
                        items(vaccines, key = { it.id }) { vaccine ->
                            VaccineListItem(
                                vaccine = vaccine,
                                onEditClick = {
                                    // Ensure ID is not empty before navigating
                                    if (vaccine.id.isNotEmpty()) {
                                        navController.navigate("${Screen.VaccineForm.route}?id=${vaccine.id}")
                                    } else {
                                        Toast.makeText(context, "Erro: ID do lembrete inválido", Toast.LENGTH_SHORT).show()
                                    }
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
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = vaccine.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (vaccine.patientName.isNotBlank()) {
                        Text(
                            text = "Paciente: ${vaccine.patientName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Excluir", modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, contentDescription = "Data", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = vaccine.reminderDate.ifEmpty { "Data não informada" },
                    style = MaterialTheme.typography.bodySmall
                )
                if (vaccine.reminderTime.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Filled.Alarm, contentDescription = "Hora", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = vaccine.reminderTime,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

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

            if (vaccine.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.AutoMirrored.Filled.Notes,
                        contentDescription = "Observações",
                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                    )
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

