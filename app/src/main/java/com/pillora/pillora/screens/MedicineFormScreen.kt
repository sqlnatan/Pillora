package com.pillora.pillora.screens

import android.app.TimePickerDialog
import android.util.Log
import android.widget.TimePicker
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.pillora.pillora.data.local.AppDatabase
import com.pillora.pillora.model.Lembrete
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.repository.MedicineRepository
import com.pillora.pillora.ui.components.DateTextField
import com.pillora.pillora.utils.AlarmScheduler
import com.pillora.pillora.utils.DateTimeUtils
import com.pillora.pillora.utils.DateValidator
import com.pillora.pillora.utils.FreeLimits
import com.pillora.pillora.PilloraApplication
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)


@Composable
fun MedicineFormScreen(navController: NavController, medicineId: String? = null) {
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf("") }

    var dose by remember { mutableStateOf("") }
    var doseError by remember { mutableStateOf("") }

    var doseUnit by remember { mutableStateOf("Cápsula") }
    val doseOptions = listOf("Cápsula", "ml", "Outros")
    var expanded by remember { mutableStateOf(false) }

    val frequencyType = remember { mutableStateOf("vezes_dia") }

    var startDate by remember { mutableStateOf("") }
    var startDateError by remember { mutableStateOf("") }

    var duration by remember { mutableStateOf("") }
    var durationError by remember { mutableStateOf("") }
    var isContinuousMedication by remember { mutableStateOf(false) }

    // Novos estados para rastreamento de estoque
    var trackStock by remember { mutableStateOf(false) }
    var stockQuantity by remember { mutableStateOf("") }
    var stockQuantityError by remember { mutableStateOf("") }
    var stockUnit by remember { mutableStateOf("Unidades") }
    val stockUnitOptions = listOf("Unidades", "ml")
    var stockUnitExpanded by remember { mutableStateOf(false) }

    var notes by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Verificação de limite do plano Free
    val application = context.applicationContext as PilloraApplication
    val isPremium by application.userPreferences.isPremium.collectAsState(initial = false)
    var showFreeLimitDialog by remember { mutableStateOf(false) }
    var currentMedicineCount by remember { mutableStateOf(0) }
    val horarios = remember { mutableStateListOf<String>().apply {
        if (medicineId == null) { // Se for novo medicamento, adiciona um horário inicial
            add("00:00")
        }
    } }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    var timesPerDay by remember { mutableStateOf("1") }
    var timesPerDayError by remember { mutableStateOf("") }

    var intervalHours by remember { mutableStateOf("") }
    var intervalHoursError by remember { mutableStateOf("") }

    var startTime by remember { mutableStateOf("") }
    var startTimeError by remember { mutableStateOf("") }

    var showFutureDateDialog by remember { mutableStateOf(false) }
    var medicineToSave by remember { mutableStateOf<Medicine?>(null) }
    var isEditing by remember { mutableStateOf(!medicineId.isNullOrBlank()) }
    var isLoading by remember { mutableStateOf(medicineId != null) }
    // Adicionar estado para controlar o carregamento durante o salvamento
    var isSaving by remember { mutableStateOf(false) }
    // Carregar dados do medicamento se estiver editando
    // Carregar dados do medicamento se estiver editando
    var recipientName by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val lembreteDao = remember { AppDatabase.getDatabase(context).lembreteDao() }

    // Contar medicamentos existentes para verificação de limite Free
    LaunchedEffect(Unit) {
        if (!isPremium && medicineId.isNullOrBlank()) {
            // Só verifica limite se for novo medicamento e usuário Free
            MedicineRepository.getAllMedicinesFlow().collect { medicines ->
                currentMedicineCount = medicines.size
                if (currentMedicineCount >= FreeLimits.MAX_MEDICINES_FREE) {
                    showFreeLimitDialog = true
                }
            }
        }
    }

    LaunchedEffect(medicineId) {
        if (!medicineId.isNullOrBlank()) {
            isEditing = true
            isLoading = true // Inicia o carregamento
            try {
                MedicineRepository.getMedicineById(
                    medicineId = medicineId,
                    onSuccess = { medicine ->
                        if (medicine != null) {
                            name = medicine.name
                            recipientName = medicine.recipientName
                            dose = medicine.dose
                            doseUnit = medicine.doseUnit ?: "Cápsula"
                            frequencyType.value = medicine.frequencyType
                            startDate = medicine.startDate
                            isContinuousMedication = medicine.duration == -1
                            duration = if (medicine.duration == -1) "" else medicine.duration.toString()
                            notes = medicine.notes

                            // Carregar dados de rastreamento de estoque
                            trackStock = medicine.trackStock
                            stockQuantity = if (medicine.stockQuantity > 0) medicine.stockQuantity.toString() else ""
                            stockUnit = medicine.stockUnit

                            if (medicine.frequencyType == "vezes_dia") {
                                timesPerDay = medicine.timesPerDay.toString()
                                horarios.clear()
                                medicine.horarios?.let { schedules ->
                                    horarios.addAll(schedules)
                                }
                                val count = timesPerDay.toIntOrNull() ?: 0
                                if (horarios.size < count) {
                                    repeat(count - horarios.size) { horarios.add("00:00") }
                                }
                            } else {
                                intervalHours = medicine.intervalHours.toString()
                                startTime = medicine.startTime ?: ""
                            }
                        } else {
                            // Medicamento não encontrado
                            Toast.makeText(context, "Medicamento não encontrado", Toast.LENGTH_LONG).show()
                            navController.popBackStack() // Voltar para a tela anterior
                        }
                        isLoading = false
                    },
                    onError = { exception ->
                        // Tratar o erro
                        Toast.makeText(context, "Erro ao carregar medicamento: ${exception.message}", Toast.LENGTH_LONG).show()
                        navController.popBackStack() // Voltar para a tela anterior
                        isLoading = false
                    }
                )
            } catch (e: Exception) {
                // Capturar qualquer exceção não tratada
                Toast.makeText(context, "Erro inesperado: ${e.message}", Toast.LENGTH_LONG).show()
                navController.popBackStack() // Voltar para a tela anterior
                isLoading = false
            }
        }
    }

    // Dialog de limite Free atingido
    if (showFreeLimitDialog) {
        AlertDialog(
            onDismissRequest = {
                showFreeLimitDialog = false
                navController.popBackStack()
            },
            title = { Text("Limite do Plano Free") },
            text = {
                Column {
                    Text(
                        "Você atingiu o limite de ${FreeLimits.MAX_MEDICINES_FREE} medicamentos do plano Free."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Para adicionar mais medicamentos, faça upgrade para o plano Premium."
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFreeLimitDialog = false
                        navController.navigate("subscription") {
                            popUpTo(navController.graph.startDestinationId)
                        }
                    }
                ) {
                    Text("Ver Planos")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showFreeLimitDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text("Voltar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(if (isEditing) "Editar Medicamento" else "Cadastro de Medicamento") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        // Usamos um Box para garantir que o conteúdo de carregamento seja centralizado corretamente
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                // Usamos um Column com scroll para garantir que todo o conteúdo seja acessível
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Adicionamos um espaçamento no topo para melhorar a aparência
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError =
                                if (it.isBlank()) "Nome do medicamento é obrigatório" else ""
                        },
                        label = { Text("Nome do medicamento") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = nameError.isNotEmpty(),
                        supportingText = {
                            if (nameError.isNotEmpty()) {
                                Text(
                                    text = nameError,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = dose,
                            onValueChange = { newValue ->
                                val filteredValue = if (doseUnit == "Cápsula" || doseUnit == "ml") {
                                    // Allow digits, one decimal point (either '.' or ',')
                                    val decimalFiltered =
                                        newValue.filter { it.isDigit() || it == '.' || it == ',' }
                                    val standardized =
                                        decimalFiltered.replace(',', '.') // Use '.' as standard
                                    val parts = standardized.split('.')
                                    if (parts.size <= 2) { // Allow 0 or 1 decimal point
                                        standardized
                                    } else {
                                        // If more than one '.', keep only the first one
                                        parts[0] + "." + parts.drop(1).joinToString("")
                                    }
                                } else {
                                    newValue // Allow any text for "Outros"
                                }
                                dose = filteredValue
                                doseError =
                                    if (filteredValue.isBlank()) "Dose é obrigatória" else ""
                            },
                            label = { Text("Dose") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (doseUnit == "Cápsula" || doseUnit == "ml") KeyboardType.Decimal else KeyboardType.Text
                            ),
                            isError = doseError.isNotEmpty(),
                            supportingText = {
                                if (doseError.isNotEmpty()) {
                                    Text(
                                        text = doseError,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (doseUnit == "Cápsula") { // Mostrar instrução apenas para Cápsula
                                    Text(
                                        text = "Para frações, use números decimais (ex: 0.5 para meio, 0.25 para 1/4).",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        )

                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.align(Alignment.BottomStart)
                            ) {
                                Text(doseUnit)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }) {
                                doseOptions.forEach { option ->
                                    DropdownMenuItem(text = { Text(option) }, onClick = {
                                        doseUnit = option
                                        expanded = false
                                    })
                                }
                            }
                        }
                    }

                    val selectedColor = MaterialTheme.colorScheme.primary
                    val unselectedColor = MaterialTheme.colorScheme.surfaceVariant

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                frequencyType.value = "vezes_dia"
                                intervalHours = ""
                                intervalHoursError = ""
                                startTime = ""
                                startTimeError = ""
                                // Correção: Garante que a lista de horários seja preenchida se estiver vazia
                                val currentTimes = timesPerDay.toIntOrNull() ?: 1
                                if (horarios.isEmpty() && currentTimes >= 1) {
                                    // Adiciona horários padrão até atingir a contagem de timesPerDay
                                    horarios.clear() // Limpa antes de adicionar para garantir a contagem correta
                                    repeat(currentTimes) { horarios.add("00:00") }
                                    // Garante que o índice da aba selecionada seja válido
                                    if (selectedTabIndex >= currentTimes) {
                                        selectedTabIndex = currentTimes - 1
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (frequencyType.value == "vezes_dia") selectedColor else unselectedColor
                            ),
                            modifier = Modifier.weight(1f)
                        ) { Text("Vezes ao dia") }

                        Button(
                            onClick = {
                                frequencyType.value = "a_cada_x_horas"
                                timesPerDay = "1"
                                timesPerDayError = ""
                                horarios.clear()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (frequencyType.value == "a_cada_x_horas") selectedColor else unselectedColor
                            ),
                            modifier = Modifier.weight(1f)
                        ) { Text("A cada X horas") }
                    }
                    if (frequencyType.value == "vezes_dia") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Vezes ao dia:",
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                val currentValue = timesPerDay.toIntOrNull() ?: 1
                                                if (currentValue > 1) {
                                                    timesPerDay = (currentValue - 1).toString()
                                                    val count = timesPerDay.toIntOrNull() ?: 0
                                                    if (horarios.size > count) {
                                                        repeat(horarios.size - count) {
                                                            horarios.removeAt(
                                                                horarios.lastIndex
                                                            )
                                                        }
                                                    }
                                                    if (selectedTabIndex >= count) {
                                                        selectedTabIndex = count - 1
                                                    }
                                                    timesPerDayError = ""
                                                }
                                            },
                                            modifier = Modifier.size(40.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("-", style = MaterialTheme.typography.titleLarge)
                                        }

                                        Text(
                                            text = timesPerDay,
                                            modifier = Modifier.width(40.dp),
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.titleLarge
                                        )

                                        Button(
                                            onClick = {
                                                val currentValue = timesPerDay.toIntOrNull() ?: 0
                                                timesPerDay = (currentValue + 1).toString()
                                                val count = timesPerDay.toIntOrNull() ?: 0
                                                if (horarios.size < count) {
                                                    repeat(count - horarios.size) { horarios.add("00:00") }
                                                }
                                                timesPerDayError = ""
                                            },
                                            modifier = Modifier.size(40.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("+", style = MaterialTheme.typography.titleLarge)
                                        }
                                    }
                                }
                                if (timesPerDayError.isNotEmpty()) {
                                    Text(
                                        text = timesPerDayError,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                if (horarios.isNotEmpty()) {
                                    Text(
                                        "Defina os horários:",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    TabRow(selectedTabIndex = selectedTabIndex) {
                                        horarios.forEachIndexed { index, _ ->
                                            Tab(
                                                selected = selectedTabIndex == index,
                                                onClick = { selectedTabIndex = index },
                                                text = { Text("Horário ${index + 1}") }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Horário ${selectedTabIndex + 1}:",
                                            style = MaterialTheme.typography.bodyLarge
                                        )

                                        OutlinedButton(
                                            onClick = {
                                                val cal = Calendar.getInstance()
                                                val hour = cal.get(Calendar.HOUR_OF_DAY)
                                                val minute = cal.get(Calendar.MINUTE)
                                                TimePickerDialog(
                                                    context,
                                                    { _: TimePicker, selectedHour: Int, selectedMinute: Int ->
                                                        val time = String.format(
                                                            Locale.getDefault(),
                                                            "%02d:%02d",
                                                            selectedHour,
                                                            selectedMinute
                                                        )
                                                        horarios[selectedTabIndex] = time
                                                    },
                                                    hour, minute, true
                                                ).show()
                                            }
                                        ) {
                                            Text(horarios[selectedTabIndex])
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        "Resumo dos horários: ${horarios.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = intervalHours,
                                onValueChange = {
                                    intervalHours = it.filter { c -> c.isDigit() }
                                    intervalHoursError =
                                        if (intervalHours.isBlank()) "Intervalo é obrigatório" else ""
                                },
                                label = { Text("Intervalo (em horas)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                isError = intervalHoursError.isNotEmpty(),
                                supportingText = {
                                    if (intervalHoursError.isNotEmpty()) {
                                        Text(
                                            text = intervalHoursError,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            )
                        }

                        Text("Horário inicial: ${startTime.ifEmpty { "--:--" }}")
                        Button(
                            onClick = {
                                val cal = Calendar.getInstance()
                                val hour = cal.get(Calendar.HOUR_OF_DAY)
                                val minute = cal.get(Calendar.MINUTE)
                                TimePickerDialog(
                                    context,
                                    { _: TimePicker, selectedHour: Int, selectedMinute: Int ->
                                        startTime = String.format(
                                            Locale.getDefault(),
                                            "%02d:%02d",
                                            selectedHour,
                                            selectedMinute
                                        )
                                        startTimeError = ""
                                    },
                                    hour, minute, true
                                ).show()
                            }
                        ) {
                            Text("Selecionar horário inicial")
                        }
                        if (startTimeError.isNotEmpty()) {
                            Text(
                                text = startTimeError,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Exibir horários calculados
                    val interval = intervalHours.toIntOrNull() ?: 0
                    if (startTime.isNotEmpty() && interval > 0) {
                        // Chame a função calculateAllTimes que você adicionou ao arquivo
                        val calculatedTimes = calculateAllTimes(startTime, interval)
                        if (calculatedTimes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Horários calculados:",
                                style = MaterialTheme.typography.titleMedium
                            )
                            // Exibe cada horário calculado em uma nova linha
                            calculatedTimes.forEach { time ->
                                Text(
                                    text = time,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(
                                        start = 8.dp,
                                        top = 4.dp
                                    ) // Adiciona um leve recuo
                                )
                            }
                        }
                    }

                    // Campo de data com a nova implementação
                    DateTextField(
                        value = startDate,
                        onValueChange = { newDate ->
                            startDate = newDate
                            startDateError = ""
                        },
                        label = "Data de Início (DD/MM/AAAA)",
                        isError = startDateError.isNotEmpty(),
                        errorMessage = startDateError,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = isContinuousMedication,
                            onCheckedChange = { checked ->
                                isContinuousMedication = checked
                                if (checked) {
                                    duration = ""
                                    durationError = ""
                                }
                            }
                        )
                        Text("Medicamento contínuo (sem tempo definido)")
                    }

                    if (!isContinuousMedication) {
                        OutlinedTextField(
                            value = duration,
                            onValueChange = {
                                duration = it.filter { c -> c.isDigit() }
                                durationError =
                                    if (duration.isBlank()) "Duração é obrigatória" else ""
                            },
                            label = { Text("Duração (em dias)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            isError = durationError.isNotEmpty(),
                            supportingText = {
                                if (durationError.isNotEmpty()) {
                                    Text(
                                        text = durationError,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                    } else {
                        Text(
                            text = "Duração: --",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    // Seção de rastreamento de estoque
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = trackStock,
                            onCheckedChange = { checked ->
                                trackStock = checked
                                if (!checked) {
                                    stockQuantity = ""
                                    stockQuantityError = ""
                                }
                            }
                        )
                        Text("Avisar quando estiver acabando")
                    }

                    if (trackStock) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Informações de estoque",
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = stockQuantity,
                                        onValueChange = {
                                            // Aceitar apenas números e ponto decimal
                                            stockQuantity =
                                                it.filter { c -> c.isDigit() || c == '.' }
                                            stockQuantityError =
                                                if (stockQuantity.isBlank()) "Quantidade é obrigatória" else ""
                                        },
                                        label = { Text("Quantidade em estoque") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        isError = stockQuantityError.isNotEmpty(),
                                        supportingText = {
                                            if (stockQuantityError.isNotEmpty()) {
                                                Text(
                                                    text = stockQuantityError,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    )
                                    Box {
                                        OutlinedButton(onClick = { stockUnitExpanded = true }) {
                                            Text(stockUnit)
                                        }
                                        DropdownMenu(
                                            expanded = stockUnitExpanded,
                                            onDismissRequest = { stockUnitExpanded = false }) {
                                            stockUnitOptions.forEach { option ->
                                                DropdownMenuItem(
                                                    text = { Text(option) },
                                                    onClick = {
                                                        stockUnit = option
                                                        stockUnitExpanded = false
                                                    })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Observações (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = recipientName,
                        onValueChange = { recipientName = it },
                        label = { Text("Para quem é este medicamento? (opcional)") },
                        placeholder = { Text("Deixe em branco se for para você") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )


                    if (showFutureDateDialog && medicineToSave != null) {
                        AlertDialog(
                            onDismissRequest = { showFutureDateDialog = false },
                            title = { Text("Data futura selecionada") },
                            text = {
                                Column {
                                    Text(
                                        "Você selecionou uma data no futuro (${
                                            DateValidator.formatDateString(
                                                startDate
                                            )
                                        })."
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Deseja continuar com esta data?")
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showFutureDateDialog = false
                                        // Definir isSaving como true antes de salvar
                                        isSaving = true
                                        if (isEditing && medicineId != null) {
                                            MedicineRepository.updateMedicine(
                                                medicineId = medicineId,
                                                medicine = medicineToSave!!,
                                                onSuccess = {
                                                    isSaving = false // Finaliza o carregamento
                                                    Toast.makeText(
                                                        context,
                                                        "Medicamento atualizado com sucesso!",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    navController.popBackStack()
                                                },
                                                onError = { exception ->
                                                    isSaving =
                                                        false // Finaliza o carregamento mesmo em caso de erro
                                                    Toast.makeText(
                                                        context,
                                                        "Erro ao atualizar: ${exception.message}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            )
                                        } else {
                                            MedicineRepository.saveMedicine(
                                                medicine = medicineToSave!!,
                                                onSuccess = {
                                                    isSaving = false // Finaliza o carregamento
                                                    Toast.makeText(
                                                        context,
                                                        "Medicamento salvo com sucesso!",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    navController.popBackStack()
                                                },
                                                onError = { exception ->
                                                    isSaving =
                                                        false // Finaliza o carregamento mesmo em caso de erro
                                                    Toast.makeText(
                                                        context,
                                                        "Erro ao salvar: ${exception.message}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            )
                                        }
                                    }
                                ) {
                                    Text("Continuar")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showFutureDateDialog = false }
                                ) {
                                    Text("Cancelar")
                                }
                            }
                        )
                    }


                    // Botão de salvar
                    Button(
                        onClick = {
                            var isValid = true

                            // Validações básicas (nome, dose)
                            if (name.isBlank()) {
                                nameError = "Nome do medicamento é obrigatório"
                                isValid = false
                            } else {
                                nameError = ""
                            }

                            if (dose.isBlank()) {
                                doseError = "Dose é obrigatória"
                                isValid = false
                            } else {
                                doseError = ""
                            }

                            // Validações de frequência
                            if (frequencyType.value == "vezes_dia") {
                                val timesValue = timesPerDay.toIntOrNull()
                                if (timesValue == null || timesValue <= 0) {
                                    timesPerDayError = "Número de vezes inválido"
                                    isValid = false
                                } else {
                                    timesPerDayError = ""
                                }
                            } else if (frequencyType.value == "intervalo" || frequencyType.value == "a_cada_x_horas"){
                                if (intervalHours.isBlank() || intervalHours.toIntOrNull() == null || (intervalHours.toIntOrNull() ?: 0) <= 0) {
                                    intervalHoursError = "Intervalo é obrigatório e deve ser maior que zero"
                                    isValid = false
                                } else {
                                    intervalHoursError = ""
                                }

                                if (startTime.isBlank()) {
                                    startTimeError = "Horário inicial é obrigatório"
                                    isValid = false
                                } else {
                                    startTimeError = ""
                                }
                            }

                            // Validação de data de início (MODIFICADA PARA AJUSTAR À DateValidator)
                            // val finalStartDateForMedicineObject = startDate // REMOVIDA - INLINED

                            if (startDate.isBlank()) {
                                startDateError = "Data de início é obrigatória"
                                isValid = false
                            } else {
                                var dateStringToValidate = startDate
                                // Se a data do TextField já tem barras (ex: "13/05/2021"), removemos para enviar ao DateValidator
                                if (startDate.length == 10 && startDate.count { it == '/' } == 2) {
                                    dateStringToValidate = startDate.replace("/", "") // Transforma em "13052021"
                                } else if (startDate.length != 8 || !startDate.all { it.isDigit() }) {
                                    // Se não tem 10 chars com barras, E não tem 8 dígitos, então está em formato inesperado
                                    startDateError = "Formato de data inválido. Use DD/MM/AAAA."
                                    isValid = false
                                    dateStringToValidate = "" // Evita que DateValidator seja chamado com lixo
                                }
                                // Se dateStringToValidate for vazio aqui, a validação acima já falhou.
                                // Se isValid ainda for true, prosseguimos para DateValidator.

                                if (isValid && dateStringToValidate.isNotEmpty()) {
                                    val (isValidDate, message) = DateValidator.validateDate(dateStringToValidate)
                                    if (!isValidDate) {
                                        // A mensagem do DateValidator pode ser "Formato de data inválido (esperado 8 dígitos)."
                                        // ou "Data inválida. Verifique dia, mês e ano."
                                        startDateError = message ?: "Data inválida"
                                        isValid = false
                                    } else {
                                        startDateError = "" // Limpa erro se passou
                                        // Se DateValidator retornou true, mas com mensagem (data futura), lidamos com isso abaixo.
                                    }
                                } else if (isValid && dateStringToValidate.isEmpty()) {
                                    // Este caso não deveria ocorrer se a lógica acima estiver correta,
                                    // mas é uma salvaguarda.
                                    startDateError = "Data de início inválida."
                                    isValid = false
                                }
                            }

                            // Validação de duração
                            if (!isContinuousMedication && (duration.isBlank() || duration.toIntOrNull() == null || (duration.toIntOrNull() ?: 0) <= 0) ) {
                                durationError = "Duração é obrigatória e deve ser maior que zero"
                                isValid = false
                            } else {
                                durationError = ""
                            }

                            // Validação de estoque
                            if (trackStock && stockQuantity.isBlank()) {
                                stockQuantityError = "Quantidade em estoque é obrigatória"
                                isValid = false
                            } else {
                                stockQuantityError = ""
                            }

                            if (isValid) {
                                val currentUserId = Firebase.auth.currentUser?.uid ?: ""
                                // Usamos startDate (que mantém o formato com barras "dd/MM/yyyy")
                                // para o objeto Medicine, pois o resto do sistema pode esperar isso.
                                val medicine = Medicine(
                                    id = if (isEditing) medicineId else null,
                                    userId = currentUserId,
                                    name = name,
                                    recipientName = recipientName.trim(),
                                    dose = dose,
                                    doseUnit = doseUnit,
                                    frequencyType = frequencyType.value,
                                    startDate = startDate, // USANDO startDate DIRETAMENTE
                                    duration = if (isContinuousMedication) -1 else duration.toIntOrNull() ?: 0,
                                    timesPerDay = if (frequencyType.value == "vezes_dia") timesPerDay.toIntOrNull() ?: 1 else 0,
                                    horarios = if (frequencyType.value == "vezes_dia") horarios.toList() else null,
                                    intervalHours = if (frequencyType.value == "intervalo" || frequencyType.value == "a_cada_x_horas") intervalHours.toIntOrNull() ?: 0 else 0,
                                    startTime = if (frequencyType.value == "intervalo" || frequencyType.value == "a_cada_x_horas") startTime else null,
                                    notes = notes,
                                    trackStock = trackStock,
                                    stockQuantity = if (trackStock) (stockQuantity.toDoubleOrNull() ?: 0.0) else 0.0,
                                    stockUnit = stockUnit
                                )

                                // Verifica se a data é futura usando o DateValidator novamente, passando a string de 8 dígitos.
                                // DateValidator retorna uma mensagem se for futura, mesmo que isValidDate seja true.
                                var dateForFutureCheck = startDate // USANDO startDate DIRETAMENTE
                                if (startDate.length == 10 && startDate.count { it == '/' } == 2) {
                                    dateForFutureCheck = startDate.replace("/", "")
                                }
                                val (_, validationMessageFromValidator) = DateValidator.validateDate(dateForFutureCheck)

                                if (validationMessageFromValidator != null && validationMessageFromValidator.contains("futuro")) {
                                    medicineToSave = medicine
                                    showFutureDateDialog = true
                                } else {
                                    // Se DateValidator não retornou mensagem de futuro, e a data é válida (isValid = true)
                                    // então podemos prosseguir com o salvamento.
                                    isSaving = true
                                    if (isEditing && medicineId != null) {
                                        MedicineRepository.updateMedicine(
                                            medicineId = medicineId,
                                            medicine = medicine,
                                            onSuccess = {
                                                coroutineScope.launch {
                                                    try {
                                                        var lembretesProcessadosComSucesso = true
                                                        Log.d("MedicineFormScreen", "Iniciando atualização de lembretes para medId: $medicineId")

                                                        val lembretesAntigos = lembreteDao.getLembretesByMedicamentoId(medicineId)
                                                        if (lembretesAntigos.isNotEmpty()) {
                                                            lembretesAntigos.forEach { lembreteAntigo ->
                                                                AlarmScheduler.cancelAlarm(context, lembreteAntigo.id)
                                                                Log.d("MedicineFormScreen", "Alarme cancelado para lembrete antigo ID: ${lembreteAntigo.id}")
                                                            }
                                                            lembreteDao.deleteLembretesByMedicamentoId(medicineId)
                                                            Log.d("MedicineFormScreen", "Lembretes antigos deletados do Room para medId: $medicineId")
                                                        } else {
                                                            Log.d("MedicineFormScreen", "Nenhum lembrete antigo encontrado para medId: $medicineId")
                                                        }

                                                        if (frequencyType.value == "vezes_dia") {
                                                            if (horarios.isEmpty()) {
                                                                Log.w("MedicineFormScreen", "Nenhum horário definido para lembretes 'vezes_dia' na atualização.")
                                                            }
                                                            horarios.forEach { horarioStr ->
                                                                val parts = horarioStr.split(":")
                                                                if (parts.size == 2) {
                                                                    val hora = parts[0].toIntOrNull()
                                                                    val minuto = parts[1].toIntOrNull()

                                                                    if (hora != null && minuto != null) {
                                                                        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                                                                        val dataInicioCalendar = Calendar.getInstance()
                                                                        try {
                                                                            val parsedDate: Date? = sdfDate.parse(startDate) // USANDO startDate DIRETAMENTE
                                                                            dataInicioCalendar.time = parsedDate ?: Date()
                                                                        } catch (e: Exception) {
                                                                            Log.e("MedicineFormScreen", "Erro ao parsear startDate ($startDate) para lembrete 'vezes_dia'. Usando data atual.", e)
                                                                            dataInicioCalendar.time = Date()
                                                                        }

                                                                        val lembreteCalendar = Calendar.getInstance().apply {
                                                                            time = dataInicioCalendar.time
                                                                            set(Calendar.HOUR_OF_DAY, hora)
                                                                            set(Calendar.MINUTE, minuto)
                                                                            set(Calendar.SECOND, 0)
                                                                            set(Calendar.MILLISECOND, 0)
                                                                        }

                                                                        while (lembreteCalendar.timeInMillis < System.currentTimeMillis()) {
                                                                            lembreteCalendar.add(Calendar.DAY_OF_MONTH, 1)
                                                                        }
                                                                        val proximaOcorrenciaMillis = lembreteCalendar.timeInMillis

                                                                        // MODIFICADO: Formatação da dose e inclusão do recipientName
                                                                        val doseFormatada = "$dose $doseUnit".trim()

                                                                        val novoLembrete = Lembrete(
                                                                            id = 0,
                                                                            medicamentoId = medicineId,
                                                                            nomeMedicamento = name,
                                                                            recipientName = medicine.recipientName, // ADICIONADO: Nome da pessoa
                                                                            hora = hora,
                                                                            minuto = minuto,
                                                                            dose = doseFormatada, // MODIFICADO: Usa a dose formatada
                                                                            observacao = notes,
                                                                            proximaOcorrenciaMillis = proximaOcorrenciaMillis,
                                                                            ativo = true
                                                                        )
                                                                        try {
                                                                            val idLembreteSalvo = lembreteDao.insertLembrete(novoLembrete)
                                                                            // Só agendar alarme se alarmsEnabled for true
                                                                            if (medicine.alarmsEnabled) {
                                                                                AlarmScheduler.scheduleAlarm(context, novoLembrete.copy(id = idLembreteSalvo))
                                                                                Log.d("MedicineFormScreen", "Lembrete (vezes_dia) atualizado/agendado para medId: $medicineId, lembreteId: $idLembreteSalvo")
                                                                            } else {
                                                                                Log.d("MedicineFormScreen", "Lembrete (vezes_dia) salvo mas alarme não agendado (alarmsEnabled=false) para medId: $medicineId, lembreteId: $idLembreteSalvo")
                                                                            }
                                                                        } catch (e: Exception) {
                                                                            Log.e("MedicineFormScreen", "Erro ao salvar/agendar lembrete (vezes_dia) para medId: $medicineId", e)
                                                                            lembretesProcessadosComSucesso = false
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        } else if (frequencyType.value == "intervalo" || frequencyType.value == "a_cada_x_horas") {
                                                            // MODIFICADO: Lógica para "a cada X horas" usando DateTimeUtils
                                                            val intervalH = medicine.intervalHours
                                                            val startT = medicine.startTime
                                                            if (intervalH != null && intervalH > 0 && startT != null) {
                                                                val durationD = if (medicine.duration == -1) -1 else medicine.duration

                                                                // Usar DateTimeUtils para calcular todas as ocorrências futuras
                                                                val timestamps = DateTimeUtils.calcularProximasOcorrenciasIntervalo(
                                                                    startDateString = medicine.startDate,
                                                                    startTimeString = startT,
                                                                    intervalHours = intervalH,
                                                                    durationDays = durationD
                                                                )

                                                                // MODIFICADO: Formatação da dose
                                                                val doseFormatada = "$dose $doseUnit".trim()

                                                                // Criar um lembrete individual para cada timestamp
                                                                timestamps.forEach { timestamp ->
                                                                    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                                                                    val novoLembrete = Lembrete(
                                                                        id = 0,
                                                                        medicamentoId = medicineId,
                                                                        nomeMedicamento = name,
                                                                        recipientName = medicine.recipientName, // ADICIONADO: Nome da pessoa
                                                                        hora = cal.get(Calendar.HOUR_OF_DAY),
                                                                        minuto = cal.get(Calendar.MINUTE),
                                                                        dose = doseFormatada, // MODIFICADO: Usa a dose formatada
                                                                        observacao = notes,
                                                                        proximaOcorrenciaMillis = timestamp,
                                                                        ativo = true
                                                                    )

                                                                    try {
                                                                        val idLembreteSalvo = lembreteDao.insertLembrete(novoLembrete)
                                                                        // Só agendar alarme se alarmsEnabled for true
                                                                        if (medicine.alarmsEnabled) {
                                                                            AlarmScheduler.scheduleAlarm(context, novoLembrete.copy(id = idLembreteSalvo))
                                                                            Log.d("MedicineFormScreen", "Lembrete (a_cada_x_horas) atualizado/agendado para medId: $medicineId, lembreteId: $idLembreteSalvo, timestamp: $timestamp")
                                                                        } else {
                                                                            Log.d("MedicineFormScreen", "Lembrete (a_cada_x_horas) salvo mas alarme não agendado (alarmsEnabled=false) para medId: $medicineId, lembreteId: $idLembreteSalvo")
                                                                        }
                                                                    } catch (e: Exception) {
                                                                        Log.e("MedicineFormScreen", "Erro ao salvar/agendar lembrete (a_cada_x_horas) para medId: $medicineId", e)
                                                                        Log.e("PILLORA_DEBUG", "Criando lembrete para 'a cada X horas'. Intervalo: $intervalHours, Data início: $startDate")
                                                                        lembretesProcessadosComSucesso = false
                                                                    }
                                                                }
                                                            } else {
                                                                Log.e("MedicineFormScreen", "Dados inválidos para lembretes 'a_cada_x_horas': intervalH=$intervalH, startT=$startT")
                                                                lembretesProcessadosComSucesso = false
                                                            }
                                                        }

                                                        isSaving = false
                                                        if (lembretesProcessadosComSucesso) {
                                                            Toast.makeText(context, "Medicamento atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                                                            navController.popBackStack()
                                                        } else {
                                                            Toast.makeText(context, "Medicamento atualizado, mas houve problemas com os lembretes.", Toast.LENGTH_LONG).show()
                                                            navController.popBackStack()
                                                        }
                                                    } catch (e: Exception) {
                                                        isSaving = false
                                                        Log.e("MedicineFormScreen", "Erro ao processar lembretes na atualização do medicamento", e)
                                                        Toast.makeText(context, "Erro ao processar lembretes: ${e.message}", Toast.LENGTH_LONG).show()
                                                        navController.popBackStack()
                                                    }
                                                }
                                            },
                                            onError = { exception ->
                                                isSaving = false
                                                Log.e("MedicineFormScreen", "Erro ao atualizar medicamento", exception)
                                                Toast.makeText(context, "Erro ao atualizar: ${exception.message}", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    } else {
                                        MedicineRepository.saveMedicine(
                                            medicine = medicine,
                                            onSuccess = { newMedicineId ->
                                                coroutineScope.launch {
                                                    try {
                                                        var lembretesProcessadosComSucesso = true
                                                        Log.d("MedicineFormScreen", "Iniciando criação de lembretes para novo medId: $newMedicineId")

                                                        if (frequencyType.value == "vezes_dia") {
                                                            if (horarios.isEmpty()) {
                                                                Log.w("MedicineFormScreen", "Nenhum horário definido para lembretes 'vezes_dia' na criação.")
                                                            }
                                                            horarios.forEach { horarioStr ->
                                                                val parts = horarioStr.split(":")
                                                                if (parts.size == 2) {
                                                                    val hora = parts[0].toIntOrNull()
                                                                    val minuto = parts[1].toIntOrNull()

                                                                    if (hora != null && minuto != null) {
                                                                        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                                                                        val dataInicioCalendar = Calendar.getInstance()
                                                                        try {
                                                                            val parsedDate: Date? = sdfDate.parse(startDate) // USANDO startDate DIRETAMENTE
                                                                            dataInicioCalendar.time = parsedDate ?: Date()
                                                                        } catch (e: Exception) {
                                                                            Log.e("MedicineFormScreen", "Erro ao parsear startDate ($startDate) para lembrete 'vezes_dia'. Usando data atual.", e)
                                                                            dataInicioCalendar.time = Date()
                                                                        }

                                                                        val lembreteCalendar = Calendar.getInstance().apply {
                                                                            time = dataInicioCalendar.time
                                                                            set(Calendar.HOUR_OF_DAY, hora)
                                                                            set(Calendar.MINUTE, minuto)
                                                                            set(Calendar.SECOND, 0)
                                                                            set(Calendar.MILLISECOND, 0)
                                                                        }

                                                                        while (lembreteCalendar.timeInMillis < System.currentTimeMillis()) {
                                                                            lembreteCalendar.add(Calendar.DAY_OF_MONTH, 1)
                                                                        }
                                                                        val proximaOcorrenciaMillis = lembreteCalendar.timeInMillis

                                                                        // MODIFICADO: Formatação da dose e inclusão do recipientName
                                                                        val doseFormatada = "$dose $doseUnit".trim()

                                                                        val novoLembrete = Lembrete(
                                                                            id = 0,
                                                                            medicamentoId = newMedicineId,
                                                                            nomeMedicamento = name,
                                                                            recipientName = medicine.recipientName, // ADICIONADO: Nome da pessoa
                                                                            hora = hora,
                                                                            minuto = minuto,
                                                                            dose = doseFormatada, // MODIFICADO: Usa a dose formatada
                                                                            observacao = notes,
                                                                            proximaOcorrenciaMillis = proximaOcorrenciaMillis,
                                                                            ativo = true
                                                                        )
                                                                        try {
                                                                            val idLembreteSalvo = lembreteDao.insertLembrete(novoLembrete)
                                                                            // Só agendar alarme se alarmsEnabled for true
                                                                            if (medicine.alarmsEnabled) {
                                                                                AlarmScheduler.scheduleAlarm(context, novoLembrete.copy(id = idLembreteSalvo))
                                                                                Log.d("MedicineFormScreen", "Lembrete (vezes_dia) criado/agendado para novo medId: $newMedicineId, lembreteId: $idLembreteSalvo")
                                                                            } else {
                                                                                Log.d("MedicineFormScreen", "Lembrete (vezes_dia) salvo mas alarme não agendado (alarmsEnabled=false) para novo medId: $newMedicineId, lembreteId: $idLembreteSalvo")
                                                                            }
                                                                        } catch (e: Exception) {
                                                                            Log.e("MedicineFormScreen", "Erro ao salvar/agendar lembrete (vezes_dia) para novo medId: $newMedicineId", e)
                                                                            lembretesProcessadosComSucesso = false
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        } else if (frequencyType.value == "intervalo" || frequencyType.value == "a_cada_x_horas") {
                                                            // MODIFICADO: Lógica para "a cada X horas" usando DateTimeUtils
                                                            val intervalH = medicine.intervalHours
                                                            val startT = medicine.startTime
                                                            if (intervalH != null && intervalH > 0 && startT != null) {
                                                                val durationD = if (medicine.duration == -1) -1 else medicine.duration

                                                                // Usar DateTimeUtils para calcular todas as ocorrências futuras
                                                                val timestamps = DateTimeUtils.calcularProximasOcorrenciasIntervalo(
                                                                    startDateString = medicine.startDate,
                                                                    startTimeString = startT,
                                                                    intervalHours = intervalH,
                                                                    durationDays = durationD
                                                                )

                                                                // MODIFICADO: Formatação da dose
                                                                val doseFormatada = "$dose $doseUnit".trim()

                                                                // Criar um lembrete individual para cada timestamp
                                                                timestamps.forEach { timestamp ->
                                                                    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                                                                    val novoLembrete = Lembrete(
                                                                        id = 0,
                                                                        medicamentoId = newMedicineId,
                                                                        nomeMedicamento = name,
                                                                        recipientName = medicine.recipientName, // ADICIONADO: Nome da pessoa
                                                                        hora = cal.get(Calendar.HOUR_OF_DAY),
                                                                        minuto = cal.get(Calendar.MINUTE),
                                                                        dose = doseFormatada, // MODIFICADO: Usa a dose formatada
                                                                        observacao = notes,
                                                                        proximaOcorrenciaMillis = timestamp,
                                                                        ativo = true
                                                                    )

                                                                    try {
                                                                        val idLembreteSalvo = lembreteDao.insertLembrete(novoLembrete)
                                                                        // Só agendar alarme se alarmsEnabled for true
                                                                        if (medicine.alarmsEnabled) {
                                                                            AlarmScheduler.scheduleAlarm(context, novoLembrete.copy(id = idLembreteSalvo))
                                                                            Log.d("MedicineFormScreen", "Lembrete (a_cada_x_horas) criado/agendado para novo medId: $newMedicineId, lembreteId: $idLembreteSalvo, timestamp: $timestamp")
                                                                        } else {
                                                                            Log.d("MedicineFormScreen", "Lembrete (a_cada_x_horas) salvo mas alarme não agendado (alarmsEnabled=false) para novo medId: $newMedicineId, lembreteId: $idLembreteSalvo")
                                                                        }
                                                                    } catch (e: Exception) {
                                                                        Log.e("MedicineFormScreen", "Erro ao salvar/agendar lembrete (a_cada_x_horas) para novo medId: $newMedicineId", e)
                                                                        lembretesProcessadosComSucesso = false
                                                                    }
                                                                }
                                                            } else {
                                                                Log.e("MedicineFormScreen", "Dados inválidos para lembretes 'a_cada_x_horas': intervalH=$intervalH, startT=$startT")
                                                                lembretesProcessadosComSucesso = false
                                                            }
                                                        }

                                                        isSaving = false
                                                        if (lembretesProcessadosComSucesso) {
                                                            Toast.makeText(context, "Medicamento salvo com sucesso!", Toast.LENGTH_SHORT).show()
                                                            navController.popBackStack()
                                                        } else {
                                                            Toast.makeText(context, "Medicamento salvo, mas houve problemas com os lembretes.", Toast.LENGTH_LONG).show()
                                                            navController.popBackStack()
                                                        }
                                                    } catch (e: Exception) {
                                                        isSaving = false
                                                        Log.e("MedicineFormScreen", "Erro ao processar lembretes na criação do medicamento", e)
                                                        Toast.makeText(context, "Erro ao processar lembretes: ${e.message}", Toast.LENGTH_LONG).show()
                                                        navController.popBackStack()
                                                    }
                                                }
                                            },
                                            onError = { exception ->
                                                isSaving = false
                                                Log.e("MedicineFormScreen", "Erro ao salvar medicamento", exception)
                                                Toast.makeText(context, "Erro ao salvar: ${exception.message}", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving // Desabilitar botão enquanto estiver salvando
                    ) {
                        Text(if (isEditing) "Atualizar Medicamento" else "Salvar Medicamento")
                    }
                }
            }
        }
    }
}

fun calculateAllTimes(startTime: String, intervalHours: Int): List<String> {
    if (startTime.isEmpty() || intervalHours <= 0) return emptyList()

    val times = mutableListOf<String>()
    // Formato de hora HH:mm
    val format = SimpleDateFormat("HH:mm", Locale.getDefault()) // Use Locale.getDefault()

    try {
        val date = format.parse(startTime)
            ?: return listOf(startTime) // Retorna apenas o horário inicial se falhar
        val calendar = Calendar.getInstance()
        calendar.time = date

        // Adiciona o horário inicial primeiro
        times.add(format.format(calendar.time))

        // Calcula os horários subsequentes em um período de 24 horas
        val startHour = calendar.get(Calendar.HOUR_OF_DAY)

        while (true) {
            calendar.add(Calendar.HOUR_OF_DAY, intervalHours)
            val nextHour = calendar.get(Calendar.HOUR_OF_DAY)
            // Para se já passou de 24h e voltou para antes do horário inicial
            // Ou se o intervalo for >= 24h e já adicionamos o primeiro horário
            if ((nextHour <= startHour && times.size > 1) || (intervalHours >= 24 && times.size >= 1)) break
            times.add(format.format(calendar.time))
            // Verificação básica para evitar loops infinitos com intervalos muito pequenos
            if (times.size > (24 / intervalHours.coerceAtLeast(1)) + 2) break // Adicionado +2 para segurança
        }
    } catch (e: Exception) {
        // Trata erro de parsing, retornando apenas o horário inicial
        println("Erro ao calcular horários: ") // Log do erro
        e.printStackTrace() // Imprime o stack trace para depuração
        return listOf(startTime)
    }

    return times.distinct() // Garante horários únicos
}

@Preview(showBackground = true)
@Composable
fun MedicineFormScreenPreview() {
    val navController = rememberNavController()
    MedicineFormScreen(navController = navController)
}
