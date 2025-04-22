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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.repository.MedicineRepository
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineFormScreen(navController: NavController) {
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var doseUnit by remember { mutableStateOf("Cápsula") }
    val doseOptions = listOf("Cápsula", "ml")
    var expanded by remember { mutableStateOf(false) }

    val frequencyType = remember { mutableStateOf("vezes_dia") }
    var startDate by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val context = LocalContext.current
    val horarios = remember { mutableStateListOf<String>() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var timesPerDay by remember { mutableStateOf("1") }
    var intervalHours by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }

    var showFutureDateDialog by remember { mutableStateOf(false) }
    var medicineToSave by remember { mutableStateOf<Medicine?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Cadastro de Medicamento") })
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do medicamento") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    label = { Text("Dose") },
                    modifier = Modifier.weight(1f)
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
                        startTime = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (frequencyType.value == "vezes_dia") selectedColor else unselectedColor
                    )
                ) { Text("Vezes ao dia") }

                Button(
                    onClick = {
                        frequencyType.value = "a_cada_x_horas"
                        timesPerDay = "1"
                        horarios.clear()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (frequencyType.value == "a_cada_x_horas") selectedColor else unselectedColor
                    )
                ) { Text("A cada X horas") }
            }

            if (frequencyType.value == "vezes_dia") {
                OutlinedTextField(
                    value = timesPerDay,
                    onValueChange = {
                        timesPerDay = it.filter { char -> char.isDigit() }
                        val count = timesPerDay.toIntOrNull() ?: 0
                        if (horarios.size < count) {
                            repeat(count - horarios.size) { horarios.add("00:00") }
                        } else if (horarios.size > count) {
                            repeat(horarios.size - count) { horarios.removeAt(horarios.lastIndex) }
                        }
                    },
                    label = { Text("Quantas vezes ao dia?") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (horarios.isNotEmpty()) {
                    TabRow(selectedTabIndex = selectedTabIndex) {
                        horarios.forEachIndexed { index, _ ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { Text("Horário ${index + 1}") }
                            )
                        }
                    }

                    Button(
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
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Selecionar horário ${selectedTabIndex + 1}")
                    }

                    Text("Horários selecionados: ${horarios.joinToString()}", style = MaterialTheme.typography.bodyMedium)
                }

            } else {
                OutlinedTextField(
                    value = intervalHours,
                    onValueChange = { intervalHours = it.filter { c -> c.isDigit() } },
                    label = { Text("Intervalo (em horas)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Horário inicial: $startTime")
                Button(
                    onClick = {
                        val cal = Calendar.getInstance()
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        val minute = cal.get(Calendar.MINUTE)
                        TimePickerDialog(
                            context,
                            { _: TimePicker, selectedHour: Int, selectedMinute: Int ->
                                val time = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
                                startTime = time
                            },
                            hour, minute, true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Selecionar horário inicial")
                }
            }

            OutlinedTextField(
                value = startDate,
                onValueChange = { newValue ->
                    val digitsOnly = newValue.filter { it.isDigit() }.take(8)
                    startDate = digitsOnly
                },
                label = { Text("Data de início (DD/MM/AAAA)") },
                visualTransformation = DateVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it.filter { c -> c.isDigit() } },
                label = { Text("Duração (dias)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Observações") },
                modifier = Modifier.fillMaxWidth()
            )

            if (showFutureDateDialog && medicineToSave != null) {
                AlertDialog(
                    onDismissRequest = { showFutureDateDialog = false },
                    title = { Text("Data futura selecionada") },
                    text = { Text("Você selecionou uma data no futuro (${medicineToSave!!.startDate}). Deseja continuar?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showFutureDateDialog = false
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
                        }) {
                            Text("Sim")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showFutureDateDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            Button(
                onClick = {
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val today = Calendar.getInstance().time
                    val selectedDate = try {
                        sdf.parse(startDate)
                    } catch (e: Exception) {
                        null
                    }

                    if (selectedDate == null) {
                        Toast.makeText(context, "Data de início inválida.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val medicine = Medicine(
                        name = name,
                        dose = dose,
                        doseUnit = doseUnit,
                        frequencyType = frequencyType.value,
                        timesPerDay = if (frequencyType.value == "vezes_dia") timesPerDay.toIntOrNull() else null,
                        horarios = if (frequencyType.value == "vezes_dia") horarios else null,
                        intervalHours = if (frequencyType.value == "a_cada_x_horas") intervalHours.toIntOrNull() else null,
                        startTime = if (frequencyType.value == "a_cada_x_horas") startTime else null,
                        startDate = startDate,
                        duration = duration.toIntOrNull() ?: 0,
                        notes = notes
                    )

                    if (selectedDate.after(today)) {
                        medicineToSave = medicine
                        showFutureDateDialog = true
                    } else {
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
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salvar")
            }
        }
    }
}

class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= 8) text.text.substring(0..7) else text.text
        val formatted = buildString {
            for (i in trimmed.indices) {
                append(trimmed[i])
                if ((i == 1 || i == 3) && i != trimmed.lastIndex) append('/')
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return when {
                    offset <= 2 -> offset
                    offset <= 4 -> offset + 1
                    offset <= 8 -> offset + 2
                    else -> 10
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                return when {
                    offset <= 2 -> offset
                    offset <= 5 -> offset - 1
                    offset <= 10 -> offset - 2
                    else -> 8
                }
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
