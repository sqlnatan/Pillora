package com.pillora.pillora.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.repository.MedicineRepository
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Importar a função calculateAllTimes do MedicineFormScreen em vez de duplicá-la
// A função será definida apenas no MedicineFormScreen.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineListScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var medicines by remember { mutableStateOf<List<Pair<String, Medicine>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var medicineToDelete by remember { mutableStateOf<Pair<String, Medicine>?>(null) }
    val context = LocalContext.current

    // Carregar medicamentos ao iniciar a tela
    LaunchedEffect(Unit) {
        loadMedicines(
            onSuccess = { medicinesList ->
                medicines = medicinesList
                isLoading = false
            },
            onError = { exception ->
                errorMessage = "Erro ao carregar medicamentos: ${exception.message}"
                isLoading = false
            }
        )
    }

    // Diálogo de confirmação para exclusão
    if (showDeleteDialog && medicineToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirmar exclusão") },
            text = { Text("Deseja realmente excluir o medicamento ${medicineToDelete?.second?.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        medicineToDelete?.let { (id, _) ->
                            scope.launch {
                                deleteMedicine(
                                    id,
                                    onSuccess = {
                                        // Recarregar a lista após excluir
                                        loadMedicines(
                                            onSuccess = { updatedList ->
                                                medicines = updatedList
                                            },
                                            onError = { exception ->
                                                errorMessage = "Erro ao recarregar: ${exception.message}"
                                            }
                                        )
                                    },
                                    onError = { exception ->
                                        errorMessage = "Erro ao excluir: ${exception.message}"
                                    }
                                )
                            }
                        }
                        showDeleteDialog = false
                        medicineToDelete = null
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
                title = {
                    Column {
                        Text("Medicamentos")
                        // Contador de medicamentos
                        Text(
                            text = "${medicines.size} ${if (medicines.size == 1) "medicamento" else "medicamentos"}",
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
                onClick = { navController.navigate(Screen.MedicineForm.route) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar medicamento")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(errorMessage ?: "Erro desconhecido")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        isLoading = true
                        errorMessage = null
                        loadMedicines(
                            onSuccess = { medicinesList ->
                                medicines = medicinesList
                                isLoading = false
                            },
                            onError = { exception ->
                                errorMessage = "Erro ao carregar medicamentos: ${exception.message}"
                                isLoading = false
                            }
                        )
                    }) {
                        Text("Tentar novamente")
                    }
                }
            } else if (medicines.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Nenhum medicamento cadastrado")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { navController.navigate(Screen.MedicineForm.route) }) {
                        Text("Adicionar medicamento")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(medicines) { (id, medicine) ->
                        MedicineItem(
                            medicine = medicine,
                            onEditClick = {
                                try {
                                    // Como sua rota é definida sem parâmetros, precisamos passar o ID como argumento de navegação
                                    navController.navigate(Screen.MedicineForm.route + "?id=$id")
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Erro ao abrir tela de edição: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onDeleteClick = {
                                medicineToDelete = Pair(id, medicine)
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }
    }
}

// Função auxiliar para calcular horários (simplificada para uso no MedicineItem)
private fun calculateTimesForDisplay(startTime: String, intervalHours: Int): String {
    if (startTime.isEmpty() || intervalHours <= 0) return startTime

    val times = mutableListOf<String>()
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())

    try {
        val date = format.parse(startTime) ?: return startTime
        val calendar = Calendar.getInstance()
        calendar.time = date

        // Adicionar o horário inicial
        times.add(format.format(calendar.time))

        // Calcular horários subsequentes em um período de 24 horas
        for (i in 1 until (24 / intervalHours) + 1) {
            calendar.add(Calendar.HOUR_OF_DAY, intervalHours)
            // Parar se já passou de 24h
            if (i > 1 && calendar.get(Calendar.HOUR_OF_DAY) < intervalHours) break
            times.add(format.format(calendar.time))
        }
    } catch (e: Exception) {
        return startTime
    }

    return times.joinToString(", ")
}

@Composable
fun MedicineItem(
    medicine: Medicine,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medicine.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Exibir horários logo abaixo do nome do medicamento
                    if (medicine.frequencyType == "vezes_dia" && !medicine.horarios.isNullOrEmpty()) {
                        // Exibir horários para frequência "vezes ao dia"
                        Text(
                            text = "Horários: ${medicine.horarios.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold // Negrito conforme solicitado
                        )
                    } else if (medicine.frequencyType == "a_cada_x_horas" &&
                        !medicine.startTime.isNullOrEmpty() &&
                        medicine.intervalHours != null &&
                        medicine.intervalHours > 0) {
                        // Calcular e exibir horários para frequência "a cada X horas"
                        val timesDisplay = calculateTimesForDisplay(medicine.startTime, medicine.intervalHours)
                        Text(
                            text = "Horários: $timesDisplay",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold // Negrito conforme solicitado
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Default.Delete, contentDescription = "Excluir")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Dose: ${medicine.dose} ${medicine.doseUnit ?: ""}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            val frequencyText = when (medicine.frequencyType) {
                "vezes_dia" -> "${medicine.timesPerDay ?: 0}x ao dia"
                else -> "A cada ${medicine.intervalHours ?: 0} horas"
            }

            Text(
                text = "Frequência: $frequencyText",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            val durationText = if (medicine.duration == -1) {
                "Contínuo (sem tempo definido)"
            } else {
                "${medicine.duration} dias"
            }

            Text(
                text = "Duração: $durationText",
                style = MaterialTheme.typography.bodyMedium
            )

            // Mostrar informações de estoque se estiver rastreando
            if (medicine.trackStock) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Estoque: ${medicine.stockQuantity} ${medicine.stockUnit}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (medicine.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Observações: ${medicine.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Função auxiliar para carregar medicamentos
private fun loadMedicines(
    onSuccess: (List<Pair<String, Medicine>>) -> Unit,
    onError: (Exception) -> Unit
) {
    MedicineRepository.getAllMedicines(
        onSuccess = onSuccess,
        onError = onError
    )
}

// Função auxiliar para excluir medicamento
private fun deleteMedicine(
    medicineId: String,
    onSuccess: () -> Unit,
    onError: (Exception) -> Unit
) {
    MedicineRepository.deleteMedicine(
        medicineId = medicineId,
        onSuccess = onSuccess,
        onError = onError
    )
}

@Preview(showBackground = true)
@Composable
fun MedicineListScreenPreview() {
    val navController = rememberNavController()
    MedicineListScreen(navController = navController)
}
