package com.pillora.pillora.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.model.PrescribedMedication
import com.pillora.pillora.viewmodel.RecipeDetailUiState
import com.pillora.pillora.viewmodel.RecipeViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeFormScreen(
    navController: NavController,
    recipeId: String?,
    recipeViewModel: RecipeViewModel = viewModel()
) {
    val context = LocalContext.current
    val detailState by recipeViewModel.recipeDetailState.collectAsState()
    val formState = recipeViewModel.recipeUiState
    val prescribedMedications = recipeViewModel.prescribedMedicationsState
    val currentMedication = recipeViewModel.currentPrescribedMedicationState

    val isEditing = recipeId != null && recipeId.isNotBlank()
    val screenTitle = if (isEditing) "Editar Receita" else "Adicionar Receita"

    // Load recipe details when editing
    LaunchedEffect(recipeId) {
        recipeViewModel.loadRecipeDetails(recipeId ?: "")
    }

    // Handle state changes after save/delete/error
    LaunchedEffect(detailState) {
        when (detailState) {
            is RecipeDetailUiState.Idle -> {
                // If idle after a successful save/delete, navigate back
                if (recipeViewModel.recipeUiState.id == null && !isEditing) { // Check if it was a new save
                    // navController.popBackStack() // Consider navigating back after save
                } else if (recipeViewModel.recipeUiState.id != null && isEditing) { // Check if it was an update
                    // navController.popBackStack() // Consider navigating back after update
                }
                // If idle after delete, popBackStack is usually handled by the caller
            }
            is RecipeDetailUiState.Error -> {
                // Show snackbar or dialog with error message
                // For now, error is displayed in the form
            }
            else -> { /* Loading or Success states handled by UI */ }
        }
    }

    // Date Picker Dialog
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            calendar.set(year, month, dayOfMonth)
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            recipeViewModel.updatePrescriptionDate(sdf.format(calendar.time))
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = {
                            recipeViewModel.deleteRecipe(recipeId!!)
                            navController.popBackStack() // Navigate back immediately after requesting delete
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Deletar Receita")
                        }
                    }
                    IconButton(onClick = { recipeViewModel.saveOrUpdateRecipe() }) {
                        Icon(Icons.Default.Save, contentDescription = "Salvar Receita")
                    }
                }
            )
        }
    ) {
            paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Show loading indicator
            if (detailState is RecipeDetailUiState.Loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Show error message
            if (detailState is RecipeDetailUiState.Error) {
                Text(
                    text = (detailState as RecipeDetailUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            OutlinedTextField(
                value = formState.patientName,
                onValueChange = recipeViewModel::updatePatientName,
                label = { Text("Nome do Paciente") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            OutlinedTextField(
                value = formState.doctorName,
                onValueChange = recipeViewModel::updateDoctorName,
                label = { Text("Nome do Médico") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            OutlinedTextField(
                value = formState.crm,
                onValueChange = recipeViewModel::updateCrm,
                label = { Text("CRM do Médico (Opcional)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = formState.prescriptionDate,
                onValueChange = recipeViewModel::updatePrescriptionDate, // Direct update, validation on save
                label = { Text("Data da Prescrição (DD/MM/AAAA)") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true, // Make it read-only to force using the picker
                trailingIcon = {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = "Selecionar Data",
                        modifier = Modifier.clickable { datePickerDialog.show() }
                    )
                }
            )

            // Section for Prescribed Medications
            Text("Medicamentos Prescritos", style = MaterialTheme.typography.titleMedium)

            // List of added medications
            prescribedMedications.forEach { med ->
                PrescribedMedicationItem(medication = med) {
                    recipeViewModel.removePrescribedMedication(med)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Form to add a new medication
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentMedication.name,
                        onValueChange = recipeViewModel::updateCurrentMedName,
                        label = { Text("Nome do Medicamento") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = currentMedication.dose,
                        onValueChange = recipeViewModel::updateCurrentMedDose,
                        label = { Text("Dose (ex: 500mg)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = currentMedication.instructions,
                        onValueChange = recipeViewModel::updateCurrentMedInstructions,
                        label = { Text("Instruções Específicas") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Button(
                        onClick = { recipeViewModel.addCurrentPrescribedMedication() },
                        modifier = Modifier.align(Alignment.End),
                        enabled = currentMedication.name.isNotBlank() // Enable only if name is entered
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Adicionar Medicamento")
                    }
                }
            }

            OutlinedTextField(
                value = formState.generalInstructions,
                onValueChange = recipeViewModel::updateGeneralInstructions,
                label = { Text("Instruções Gerais (Opcional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )

            OutlinedTextField(
                value = formState.notes,
                onValueChange = recipeViewModel::updateNotes,
                label = { Text("Notas Adicionais (Opcional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )

            // TODO: Add Image Picker functionality
            // Button(onClick = { /* TODO: Launch image picker */ }) {
            //     Text("Adicionar Imagem da Receita")
            // }
            // if (formState.imageUri != null) {
            //     // TODO: Display selected image preview
            //     Text("Imagem selecionada: ${formState.imageUri}")
            // }
        }
    }
}

@Composable
fun PrescribedMedicationItem(medication: PrescribedMedication, onRemoveClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(medication.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("Dose: ${medication.dose}")
            Text("Instruções: ${medication.instructions}")
        }
        IconButton(onClick = onRemoveClick) {
            Icon(Icons.Default.Delete, contentDescription = "Remover Medicamento")
        }
    }
    Divider()
}

