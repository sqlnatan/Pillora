package com.pillora.pillora.screens

import android.app.TimePickerDialog
import android.widget.TimePicker
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.repository.MedicineRepository
import com.pillora.pillora.ui.components.DateTextField
import com.pillora.pillora.utils.DateValidator
import java.util.Calendar
import java.util.Locale
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat

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
    var isEditing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(medicineId != null) }
    // Adicionar estado para controlar o carregamento durante o salvamento
    var isSaving by remember { mutableStateOf(false) }
    // Carregar dados do medicamento se estiver editando
    // Carregar dados do medicamento se estiver editando
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
                        isEditing = false
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

    Scaffold(
        topBar = {
            TopAppBar(
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
                            nameError = if (it.isBlank()) "Nome do medicamento é obrigatório" else ""
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
                                    val decimalFiltered = newValue.filter { it.isDigit() || it == '.' || it == ',' }
                                    val standardized = decimalFiltered.replace(',', '.') // Use '.' as standard
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
                                doseError = if (filteredValue.isBlank()) "Dose é obrigatória" else ""
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
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                                                        repeat(horarios.size - count) { horarios.removeAt(horarios.lastIndex) }
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
                                                        val time = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
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
                                    intervalHoursError = if (intervalHours.isBlank()) "Intervalo é obrigatório" else ""
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
                                        startTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
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
                            Text("Horários calculados:", style = MaterialTheme.typography.titleMedium)
                            // Exibe cada horário calculado em uma nova linha
                            calculatedTimes.forEach { time ->
                                Text(
                                    text = time,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp) // Adiciona um leve recuo
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
                                durationError = if (duration.isBlank()) "Duração é obrigatória" else ""
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

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = stockQuantity,
                                        onValueChange = {
                                            // Aceitar apenas números e ponto decimal
                                            stockQuantity = it.filter { c -> c.isDigit() || c == '.' }
                                            stockQuantityError = if (stockQuantity.isBlank()) "Quantidade é obrigatória" else ""
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
                                        DropdownMenu(expanded = stockUnitExpanded, onDismissRequest = { stockUnitExpanded = false }) {
                                            stockUnitOptions.forEach { option ->
                                                DropdownMenuItem(text = { Text(option) }, onClick = {
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

                    if (showFutureDateDialog && medicineToSave != null) {
                        AlertDialog(
                            onDismissRequest = { showFutureDateDialog = false },
                            title = { Text("Data futura selecionada") },
                            text = {
                                Column {
                                    Text("Você selecionou uma data no futuro (${DateValidator.formatDateString(startDate)}).")
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
                                                    Toast.makeText(context, "Medicamento atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                                                    navController.popBackStack()
                                                },
                                                onError = { exception ->
                                                    isSaving = false // Finaliza o carregamento mesmo em caso de erro
                                                    Toast.makeText(context, "Erro ao atualizar: ${exception.message}", Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        } else {
                                            MedicineRepository.saveMedicine(
                                                medicine = medicineToSave!!,
                                                onSuccess = {
                                                    isSaving = false // Finaliza o carregamento
                                                    Toast.makeText(context, "Medicamento salvo com sucesso!", Toast.LENGTH_SHORT).show()
                                                    navController.popBackStack()
                                                },
                                                onError = { exception ->
                                                    isSaving = false // Finaliza o carregamento mesmo em caso de erro
                                                    Toast.makeText(context, "Erro ao salvar: ${exception.message}", Toast.LENGTH_LONG).show()
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

                            if (frequencyType.value == "vezes_dia") {
                                val timesValue = timesPerDay.toIntOrNull()
                                if (timesValue == null || timesValue <= 0) {
                                    timesPerDayError = "Número de vezes inválido"
                                    isValid = false
                                } else {
                                    timesPerDayError = ""
                                }
                            } else {
                                if (intervalHours.isBlank() || intervalHours.toIntOrNull() == null) {
                                    intervalHoursError = "Intervalo é obrigatório"
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

                            // Validação de data
                            if (startDate.length == 8) {
                                val (isValidDate, message) = DateValidator.validateDate(startDate)
                                if (!isValidDate) {
                                    startDateError = message ?: "Data inválida"
                                    isValid = false
                                } else {
                                    startDateError = ""
                                }
                            } else {
                                startDateError = "Data incompleta. Use o formato DD/MM/AAAA"
                                isValid = false
                            }

                            if (!isContinuousMedication && (duration.isBlank() || duration.toIntOrNull() == null)) {
                                durationError = "Duração é obrigatória"
                                isValid = false
                            } else {
                                durationError = ""
                            }

                            // Validação dos campos de rastreamento de estoque
                            if (trackStock && stockQuantity.isBlank()) {
                                stockQuantityError = "Quantidade em estoque é obrigatória"
                                isValid = false
                            } else {
                                stockQuantityError = ""
                            }

                            if (isValid) {
                                val currentUserId = Firebase.auth.currentUser?.uid ?: ""
                                // Dentro da função que cria o objeto Medicine para salvar
                                val medicine = Medicine(
                                    id = if (isEditing) medicineId else null,
                                    userId = currentUserId, // Certifique-se de que este campo foi adicionado
                                    name = name,
                                    dose = dose,
                                    doseUnit = doseUnit,
                                    frequencyType = frequencyType.value,
                                    startDate = startDate,
                                    duration = if (isContinuousMedication) -1 else duration.toIntOrNull() ?: 0,
                                    timesPerDay = if (frequencyType.value == "vezes_dia") timesPerDay.toIntOrNull() ?: 1 else 0,
                                    horarios = if (frequencyType.value == "vezes_dia") horarios.toList() else null,
                                    // Aqui está a correção:
                                    intervalHours = if (frequencyType.value == "intervalo" || frequencyType.value == "a_cada_x_horas")
                                        intervalHours.toIntOrNull() ?: 0
                                    else 0,
                                    startTime = if (frequencyType.value == "intervalo" || frequencyType.value == "a_cada_x_horas") startTime else null,
                                    notes = notes,
                                    trackStock = trackStock,
                                    stockQuantity = if (trackStock) (stockQuantity.toDoubleOrNull() ?: 0.0) else 0.0,
                                    stockUnit = stockUnit
                                )


                                // Verificar se a data é futura
                                val (_, message) = DateValidator.validateDate(startDate)
                                if (message != null && message.contains("futuro")) {
                                    medicineToSave = medicine
                                    showFutureDateDialog = true
                                } else {
                                    // Definir isSaving como true antes de salvar
                                    isSaving = true
                                    if (isEditing && medicineId != null) {
                                        MedicineRepository.updateMedicine(
                                            medicineId = medicineId,
                                            medicine = medicine,
                                            onSuccess = {
                                                isSaving = false // Finaliza o carregamento
                                                Toast.makeText(context, "Medicamento atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                                                navController.popBackStack()
                                            },
                                            onError = { exception ->
                                                isSaving = false // Finaliza o carregamento mesmo em caso de erro
                                                Toast.makeText(context, "Erro ao atualizar: ${exception.message}", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    } else {
                                        MedicineRepository.saveMedicine(
                                            medicine = medicine,
                                            onSuccess = {
                                                isSaving = false // Finaliza o carregamento
                                                Toast.makeText(context, "Medicamento salvo com sucesso!", Toast.LENGTH_SHORT).show()
                                                navController.popBackStack()
                                            },
                                            onError = { exception ->
                                                isSaving = false // Finaliza o carregamento mesmo em caso de erro
                                                Toast.makeText(context, "Erro ao salvar: ${exception.message}", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isEditing) "Atualizar" else "Salvar")
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
