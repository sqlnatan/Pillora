package com.pillora.pillora.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle // *** GARANTIR ESTE IMPORT ***
import androidx.navigation.NavController
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.model.Vaccine
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.repository.DataResult // *** USAR A CLASSE DO REPOSITÓRIO (ou mover para um arquivo comum) ***
import com.pillora.pillora.repository.VaccineRepository
import kotlinx.coroutines.launch

// *** IMPORTANTE: A classe DataResult DEVE estar definida em um local acessível. ***
// Se você a definiu dentro do VaccineRepository.kt, o import acima deve funcionar.
// Se não, defina-a em um arquivo separado (ex: utils/DataResult.kt) e importe de lá.
/* Exemplo de definição em arquivo separado:
   package com.pillora.pillora.utils // ou outro pacote

   sealed class DataResult<out T> {
       data object Loading : DataResult<Nothing>()
       data class Success<out T>(val data: T) : DataResult<T>()
       data class Error(val message: String?) : DataResult<Nothing>()
   }
*/

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccineListScreen(navController: NavController) {

    val context = LocalContext.current
    val application = context.applicationContext as PilloraApplication
    val isPremium by application.userPreferences.isPremium.collectAsState(initial = false)

    // Coletar o estado do Flow usando collectAsStateWithLifecycle
    // Especificar explicitamente o tipo <DataResult<List<Vaccine>>> pode ajudar o compilador
    val vaccinesState: DataResult<List<Vaccine>> by VaccineRepository.getAllVaccinesFlow()
        .collectAsStateWithLifecycle(initialValue = DataResult.Loading)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var vaccineToDelete by remember { mutableStateOf<Vaccine?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Diálogo de confirmação de exclusão
    if (showDeleteDialog && vaccineToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; vaccineToDelete = null },
            title = { Text("Confirmar exclusão") },
            text = { Text("Deseja realmente excluir o lembrete para ${vaccineToDelete?.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val currentVaccine = vaccineToDelete
                        if (currentVaccine != null && currentVaccine.id.isNotEmpty()) {
                            scope.launch {
                                VaccineRepository.deleteVaccine(
                                    vaccineId = currentVaccine.id,
                                    onSuccess = {
                                        Toast.makeText(context, "Lembrete excluído com sucesso", Toast.LENGTH_SHORT).show()
                                        // A lista atualiza automaticamente via Flow
                                    },
                                    onFailure = {
                                        Toast.makeText(context, "Erro ao excluir lembrete: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        } else {
                            Toast.makeText(context, "Erro: ID inválido para exclusão", Toast.LENGTH_SHORT).show()
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
                windowInsets = WindowInsets(0),
                title = {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            "Lembretes de Vacina",
                            style = MaterialTheme.typography.titleMedium
                        )

                        val count = when (vaccinesState) {
                            is DataResult.Success<*> -> {
                                val success = vaccinesState as? DataResult.Success<List<Vaccine>>
                                success?.data?.size ?: 0
                            }
                            else -> 0
                        }

                        Text(
                            text = "$count ${if (count == 1) "lembrete" else "lembretes"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = { navController.navigate(Screen.VaccineForm.route) }) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar Lembrete")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Usar o estado coletado do Flow
            when (vaccinesState) {
                is DataResult.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is DataResult.Error -> {
                    val errorResult = vaccinesState as DataResult.Error // Cast para acessar a mensagem
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorResult.message ?: "Ocorreu um erro desconhecido",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is DataResult.Success<*> -> {
                    // Cast seguro para DataResult.Success<List<Vaccine>>
                    val successResult = vaccinesState as? DataResult.Success<List<Vaccine>>
                    val vaccines = successResult?.data ?: emptyList() // Obter a lista ou uma lista vazia

                    if (vaccines.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "Nenhum lembrete de vacina encontrado.")
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(vaccines, key = { vaccine -> vaccine.id }) { vaccine ->
                                VaccineListItem(
                                    vaccine = vaccine,

                                    onEditClick = {
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
}

// Componente para exibir cada item da lista (sem alterações)
@Composable
fun VaccineListItem(
    vaccine: Vaccine,
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = vaccine.name,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (vaccine.patientName.isNotBlank()) {
                        Text(
                            text = "Paciente: ${vaccine.patientName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Excluir",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, contentDescription = "Data", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = vaccine.reminderDate.ifEmpty { "Data não informada" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (vaccine.reminderTime.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Filled.Alarm, contentDescription = "Hora", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = vaccine.reminderTime,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
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
                        style = MaterialTheme.typography.bodyMedium
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
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

