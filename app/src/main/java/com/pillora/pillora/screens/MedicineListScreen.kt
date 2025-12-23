package com.pillora.pillora.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.repository.MedicineRepository
import com.pillora.pillora.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Função auxiliar para calcular horários (simplificada para uso no MedicineItem)
// Mantida aqui conforme o código original do usuário
private fun calculateTimesForDisplay(startTime: String?, intervalHours: Int?): String {
    // Adicionado tratamento para startTime e intervalHours nulos
    if (startTime.isNullOrEmpty() || intervalHours == null || intervalHours <= 0) return startTime ?: ""

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
            // Parar se já passou de 24h (lógica original do usuário)
            // Adicionado verificação de segurança para evitar loop infinito se intervalHours for 24
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val startHour = format.parse(startTime)?.let {
                Calendar.getInstance().apply { time = it }.get(Calendar.HOUR_OF_DAY)
            } ?: 0
            if (currentHour == startHour && times.size > 0) break
            if (times.size >= (24 / intervalHours) + 1) break

            times.add(format.format(calendar.time))
        }
    } catch (e: Exception) {
        return startTime
    }

    return times.joinToString(", ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineListScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var medicineToDelete by remember { mutableStateOf<Medicine?>(null) }
    val context = LocalContext.current

    // Carregar medicamentos usando Flow
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null
        MedicineRepository.getAllMedicinesFlow()
            .catch { exception ->
                errorMessage = "Erro ao carregar medicamentos: ${exception.message}"
                isLoading = false
            }
            .collect { medicinesList ->
                medicines = medicinesList
                isLoading = false
            }
    }

    // Diálogo de confirmação para exclusão
    if (showDeleteDialog && medicineToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirmar exclusão") },
            text = { Text("Deseja realmente excluir o medicamento ${medicineToDelete?.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        medicineToDelete?.id?.let { id ->
                            scope.launch {
                                homeViewModel.deleteMedicine(
                                    context = context,
                                    medicineId = id,
                                    onSuccess = {
                                        Toast.makeText(
                                            context,
                                            "Medicamento excluído com sucesso!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onError = { exception ->
                                        errorMessage = "Erro ao excluir: ${exception.message}"
                                        Toast.makeText(
                                            context,
                                            "Erro ao excluir: ${exception.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                        } ?: run {
                            Toast.makeText(
                                context,
                                "Erro: ID do medicamento inválido",
                                Toast.LENGTH_LONG
                            ).show()
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
                windowInsets = WindowInsets(0),
                title = {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "Medicamentos",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${medicines.size} ${if (medicines.size == 1) "medicamento" else "medicamentos"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = { navController.navigate(Screen.MedicineForm.route) }
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Adicionar medicamento",
                )
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
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        medicines,
                        key = { it.id ?: it.hashCode() }
                    ) { medicine ->
                        MedicineItem(
                            medicine = medicine,
                            onEditClick = {
                                medicine.id?.let { id ->
                                    try {
                                        navController.navigate(
                                            Screen.MedicineForm.route + "?id=$id"
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Erro ao abrir tela de edição: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } ?: run {
                                    Toast.makeText(
                                        context,
                                        "Erro: ID do medicamento inválido para edição",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onDeleteClick = {
                                medicineToDelete = medicine
                                showDeleteDialog = true
                            },
                            onAlarmToggle = { isEnabled ->
                                medicine.id?.let { id ->
                                    scope.launch {
                                        homeViewModel.toggleMedicineAlarms(
                                            context = context,
                                            medicineId = id,
                                            alarmsEnabled = isEnabled,
                                            onSuccess = {
                                                Toast.makeText(
                                                    context,
                                                    if (isEnabled) "Alarmes ativados" else "Alarmes desativados",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onError = { exception ->
                                                Toast.makeText(
                                                    context,
                                                    "Erro ao atualizar alarmes: ${exception.message}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        )
                                    }
                                } ?: run {
                                    Toast.makeText(
                                        context,
                                        "Erro: ID do medicamento inválido",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MedicineItem(
    medicine: Medicine,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAlarmToggle: (Boolean) -> Unit
) {
    // Lógica para calcular a data final (mantida conforme original do usuário)
    val finalDateText = if (medicine.duration > 0 && medicine.startDate.isNotEmpty()) {
        try {
            val parseFormatDisplay = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            parseFormatDisplay.isLenient = false
            val startDateCalendar = Calendar.getInstance()

            try {
                startDateCalendar.time =
                    parseFormatDisplay.parse(medicine.startDate)
                        ?: throw IllegalArgumentException()
            } catch (e: Exception) {
                val parseFormatLegacy = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
                parseFormatLegacy.isLenient = false
                startDateCalendar.time =
                    parseFormatLegacy.parse(medicine.startDate)
                        ?: throw IllegalArgumentException()
            }

            startDateCalendar.add(Calendar.DAY_OF_YEAR, medicine.duration - 1)
            val displayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            "Último dia: ${displayFormat.format(startDateCalendar.time)}"
        } catch (e: Exception) {
            "Último dia indisponível"
        }
    } else if (medicine.duration == -1) {
        "Contínuo"
    } else {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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
                    )

                    if (medicine.recipientName.isNotBlank()) {
                        Text(
                            text = "Para: ${medicine.recipientName}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (medicine.frequencyType == "vezes_dia" && !medicine.horarios.isNullOrEmpty()) {
                        Text(
                            text = "Horários: ${medicine.horarios.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else if (medicine.frequencyType == "a_cada_x_horas") {
                        val timesDisplay =
                            calculateTimesForDisplay(
                                medicine.startTime,
                                medicine.intervalHours
                            )
                        if (timesDisplay.isNotEmpty()) {
                            Text(
                                text = "Horários: $timesDisplay",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = onEditClick) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Excluir",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Dose: ${medicine.dose}${medicine.doseUnit?.let { " $it" } ?: ""}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            val frequencyText = when (medicine.frequencyType) {
                "vezes_dia" -> "${medicine.timesPerDay ?: "?"}x ao dia"
                "a_cada_x_horas" -> "A cada ${medicine.intervalHours ?: "?"} horas"
                else -> "Frequência não definida"
            }

            Text(
                text = "Frequência: $frequencyText",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            val durationText =
                if (medicine.duration == -1) "Contínuo" else "${medicine.duration} dias"

            Text(
                text = "Duração: $durationText",
                style = MaterialTheme.typography.bodyMedium
            )

            if (finalDateText != null && finalDateText != "Contínuo") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = finalDateText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

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
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Alarmes",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (medicine.alarmsEnabled) "Ativados" else "Desativados",
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (medicine.alarmsEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = medicine.alarmsEnabled,
                    onCheckedChange = onAlarmToggle
                )
            }
        }
    }
}
