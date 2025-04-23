package com.pillora.pillora.screens

import android.app.TimePickerDialog
import android.widget.TimePicker
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.repository.MedicineRepository
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineFormScreen(navController: NavController, medicineId: String? = null) {
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf("") }

    var dose by remember { mutableStateOf("") }
    var doseError by remember { mutableStateOf("") }

    var doseUnit by remember { mutableStateOf("Cápsula") }
    val doseOptions = listOf("Cápsula", "ml")
    var expanded by remember { mutableStateOf(false) }

    val frequencyType = remember { mutableStateOf("vezes_dia") }

    var startDate by remember { mutableStateOf("") }
    var startDateError by remember { mutableStateOf("") }

    var duration by remember { mutableStateOf("") }
    var durationError by remember { mutableStateOf("") }
    var isContinuousMedication by remember { mutableStateOf(false) }

    var notes by remember { mutableStateOf("") }
    val context = LocalContext.current
    val horarios = remember { mutableStateListOf<String>() }
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

    // Carregar dados do medicamento se estiver editando
    LaunchedEffect(medicineId) {
        if (medicineId != null) {
            isEditing = true
            MedicineRepository.getMedicineById(
                medicineId = medicineId,
                onSuccess = { medicine ->
                    if (medicine != null) {
                        // Preencher os campos com os dados do medicamento
                        name = medicine.name
                        dose = medicine.dose
                        doseUnit = medicine.doseUnit ?: "Cápsula"
                        frequencyType.value = medicine.frequencyType ?: "vezes_dia"
                        startDate = medicine.startDate

                        isContinuousMedication = medicine.duration == -1
                        duration = if (medicine.duration == -1) "" else medicine.duration.toString()

                        notes = medicine.notes

                        if (medicine.frequencyType == "vezes_dia") {
                            timesPerDay = medicine.timesPerDay.toString()
                            // Carregar horários
                            horarios.clear()
                            medicine.horarios?.let { schedules ->
                                horarios.addAll(schedules)
                                // Se não houver horários suficientes, adicionar horários padrão
                                val count = timesPerDay.toIntOrNull() ?: 0
                                if (horarios.size < count) {
                                    repeat(count - horarios.size) { horarios.add("00:00") }
                                }
                            }
                        } else {
                            intervalHours = medicine.intervalHours.toString()
                            startTime = medicine.startTime ?: ""
                        }
                    }
                    isLoading = false
                },
                onError = { exception ->
                    // Mostrar mensagem de erro
                    Toast.makeText(context, "Erro ao carregar medicamento: ${exception.message}", Toast.LENGTH_LONG).show()
                    isLoading = false
                }
            )
        } else {
            // Inicializar horários quando a tela é carregada para novo medicamento
            if ((timesPerDay.toIntOrNull() ?: 0) > 0) {
                val count = timesPerDay.toIntOrNull() ?: 1
                if (horarios.isEmpty()) {
                    repeat(count) { horarios.add("00:00") }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar Medicamento" else "Cadastro de Medicamento") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
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

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dose,
                        onValueChange = {
                            dose = it
                            doseError = if (it.isBlank()) "Dose é obrigatória" else ""
                        },
                        label = { Text("Dose") },
                        modifier = Modifier.weight(1f),
                        isError = doseError.isNotEmpty(),
                        supportingText = {
                            if (doseError.isNotEmpty()) {
                                Text(
                                    text = doseError,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )

                    Box {
                        OutlinedButton(onClick = { expanded = true }) {
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            frequencyType.value = "vezes_dia"
                            intervalHours = ""
                            intervalHoursError = ""
                            startTime = ""
                            startTimeError = ""
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (frequencyType.value == "vezes_dia") selectedColor else unselectedColor
                        )
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
                        )
                    ) { Text("A cada X horas") }
                }

                if (frequencyType.value == "vezes_dia") {
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

                            // Sempre mostrar os horários, mesmo quando timesPerDay é 1
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

                OutlinedTextField(
                    value = startDate,
                    onValueChange = { input ->
                        if (input.length <= 10) {
                            val formatted = formatDateInput(input)
                            startDate = formatted
                            startDateError = ""
                        }
                    },
                    label = { Text("Data de início (dd/mm/aaaa)") },
                    visualTransformation = DateVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = startDateError.isNotEmpty(),
                    supportingText = {
                        if (startDateError.isNotEmpty()) {
                            Text(
                                text = startDateError,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
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
                                Text("Você selecionou uma data no futuro (${formatDateString(startDate)}).")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Deseja continuar com esta data?")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showFutureDateDialog = false
                                if (isEditing && medicineId != null) {
                                    // Atualizar medicamento existente
                                    MedicineRepository.updateMedicine(
                                        medicineId = medicineId,
                                        medicine = medicineToSave!!,
                                        onSuccess = {
                                            Toast.makeText(context, "Medicamento atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                                            navController.popBackStack()
                                        },
                                        onError = {
                                            Toast.makeText(context, "Erro ao atualizar: ${it.message}", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                } else {
                                    // Salvar novo medicamento
                                    MedicineRepository.saveMedicine(
                                        medicine = medicineToSave!!,
                                        onSuccess = {
                                            Toast.makeText(context, "Medicamento salvo com sucesso!", Toast.LENGTH_SHORT).show()
                                            navController.popBackStack()
                                        },
                                        onError = {
                                            Toast.makeText(context, "Erro ao salvar: ${it.message}", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            }) {
                                Text("Sim, continuar")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showFutureDateDialog = false }) {
                                Text("Não, ajustar data")
                            }
                        }
                    )
                }

                Button(
                    onClick = {
                        // Validar todos os campos
                        var isValid = true

                        if (name.isBlank()) {
                            nameError = "Nome do medicamento é obrigatório"
                            isValid = false
                        }

                        if (dose.isBlank()) {
                            doseError = "Dose é obrigatória"
                            isValid = false
                        }

                        if (frequencyType.value == "vezes_dia") {
                            val timesValue = timesPerDay.toIntOrNull()
                            if (timesValue == null || timesValue <= 0) {
                                timesPerDayError = "Número de vezes inválido"
                                isValid = false
                            }
                        } else {
                            if (intervalHours.isBlank() || intervalHours.toIntOrNull() == null) {
                                intervalHoursError = "Intervalo é obrigatório"
                                isValid = false
                            }

                            if (startTime.isBlank()) {
                                startTimeError = "Horário inicial é obrigatório"
                                isValid = false
                            }
                        }

                        // Validar data
                        validateDate(startDate) { valid, error ->
                            startDateError = if (!valid) error else ""
                            isValid = isValid && valid
                        }

                        // Validar duração se não for medicamento contínuo
                        if (!isContinuousMedication && (duration.isBlank() || duration.toIntOrNull() == null)) {
                            durationError = "Duração é obrigatória"
                            isValid = false
                        }

                        if (!isValid) {
                            return@Button
                        }

                        // Criar objeto Medicine
                        val medicine = Medicine(
                            name = name,
                            dose = dose,
                            doseUnit = doseUnit,
                            frequencyType = frequencyType.value,
                            timesPerDay = if (frequencyType.value == "vezes_dia") timesPerDay.toIntOrNull() ?: 0 else null,
                            intervalHours = if (frequencyType.value == "a_cada_x_horas") intervalHours.toIntOrNull() ?: 0 else null,
                            startTime = if (frequencyType.value == "a_cada_x_horas") startTime else null,
                            horarios = if (frequencyType.value == "vezes_dia") horarios.toList() else null,
                            startDate = startDate,
                            duration = if (isContinuousMedication) -1 else duration.toIntOrNull() ?: 0,
                            notes = notes
                        )

                        // Verificar se a data é futura
                        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        val today = Calendar.getInstance().time
                        val selectedDate = try {
                            sdf.parse(formatDateString(startDate))
                        } catch (e: Exception) {
                            null
                        }

                        if (selectedDate != null && selectedDate.after(today)) {
                            // Mostrar diálogo de confirmação
                            showFutureDateDialog = true
                            medicineToSave = medicine
                        } else {
                            // Salvar ou atualizar medicamento
                            if (isEditing && medicineId != null) {
                                // Atualizar medicamento existente
                                MedicineRepository.updateMedicine(
                                    medicineId = medicineId,
                                    medicine = medicine,
                                    onSuccess = {
                                        Toast.makeText(context, "Medicamento atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    },
                                    onError = {
                                        Toast.makeText(context, "Erro ao atualizar: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            } else {
                                // Salvar novo medicamento
                                MedicineRepository.saveMedicine(
                                    medicine = medicine,
                                    onSuccess = {
                                        Toast.makeText(context, "Medicamento salvo com sucesso!", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    },
                                    onError = {
                                        Toast.makeText(context, "Erro ao salvar: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isEditing) "Atualizar Medicamento" else "Salvar Medicamento")
                }
            }
        }
    }
}

// Função para formatar a entrada de data
private fun formatDateInput(input: String): String {
    val digitsOnly = input.filter { it.isDigit() }
    return when {
        digitsOnly.length <= 2 -> digitsOnly
        digitsOnly.length <= 4 -> "${digitsOnly.substring(0, 2)}/${digitsOnly.substring(2)}"
        else -> "${digitsOnly.substring(0, 2)}/${digitsOnly.substring(2, 4)}/${digitsOnly.substring(4, minOf(8, digitsOnly.length))}"
    }
}

// Função para formatar a string de data
private fun formatDateString(date: String): String {
    val parts = date.split("/")
    if (parts.size < 3) return date

    val day = parts[0].padStart(2, '0')
    val month = parts[1].padStart(2, '0')
    val year = parts[2].padStart(4, '0')

    return "$day/$month/$year"
}

// Função para validar a data
private fun validateDate(date: String, callback: (Boolean, String) -> Unit) {
    if (date.isBlank()) {
        callback(false, "Data é obrigatória")
        return
    }

    val parts = date.split("/")
    if (parts.size != 3) {
        callback(false, "Formato de data inválido")
        return
    }

    val day = parts[0].toIntOrNull()
    val month = parts[1].toIntOrNull()
    val year = parts[2].toIntOrNull()

    if (day == null || month == null || year == null) {
        callback(false, "Data inválida")
        return
    }

    if (year < 2000 || year > 2100) {
        callback(false, "Ano inválido")
        return
    }

    if (month < 1 || month > 12) {
        callback(false, "Mês inválido")
        return
    }

    val maxDays = when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    if (day < 1 || day > maxDays) {
        callback(false, "Dia inválido para o mês selecionado")
        return
    }

    callback(true, "")
}

// Transformação visual para formatar a data durante a digitação
class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= 8) text.text.substring(0..7) else text.text
        var output = ""
        for (i in trimmed.indices) {
            output += trimmed[i]
            if (i == 1 || i == 3) output += "/"
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 1) return offset
                if (offset <= 3) return offset + 1
                if (offset <= 8) return offset + 2
                return 10
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 2) return offset
                if (offset <= 5) return offset - 1
                if (offset <= 10) return offset - 2
                return 8
            }
        }

        return TransformedText(AnnotatedString(output), offsetMapping)
    }
}
