package com.pillora.pillora.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Use AutoMirrored icon
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel // Import Cancel icon
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit // Import Edit icon
import androidx.compose.material.icons.filled.Save // Import Save icon for medication edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight // Import FontWeight explicitly
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign // Import TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.data.local.AppDatabase // *** CORRIGIDO: Import AppDatabase ***
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
    // *** CORRIGIDO: Obter LembreteDao ***
    val lembreteDao = remember { AppDatabase.getDatabase(context).lembreteDao() }

    val detailState by recipeViewModel.recipeDetailState.collectAsState()
    val formState = recipeViewModel.recipeUiState
    val prescribedMedications = recipeViewModel.prescribedMedicationsState
    val currentMedication = recipeViewModel.currentPrescribedMedicationState
    val editingMedIndex = recipeViewModel.editingMedicationIndex

    val isEditingRecipe = !recipeId.isNullOrBlank()
    val screenTitle = if (isEditingRecipe) "Editar Receita" else "Adicionar Receita"

    // Load recipe details when editing, only if not already loaded or loading
    LaunchedEffect(recipeId) {
        if (detailState is RecipeDetailUiState.Idle && !recipeId.isNullOrBlank()) {
            recipeViewModel.loadRecipeDetails(recipeId)
        }
    }

    // Handle navigation after successful operation (save/update/delete)
    LaunchedEffect(detailState) {
        if (detailState is RecipeDetailUiState.OperationSuccess) {
            navController.popBackStack()
            recipeViewModel.resetDetailState() // Reset state AFTER navigation
        }
    }

    // --- Date Picker Dialogs ---
    val calendar = Calendar.getInstance()
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    // Prescription Date Picker
    var initialPrescriptionYear = calendar.get(Calendar.YEAR)
    var initialPrescriptionMonth = calendar.get(Calendar.MONTH)
    var initialPrescriptionDay = calendar.get(Calendar.DAY_OF_MONTH)
    if (formState.prescriptionDate.isNotBlank()) {
        try {
            sdf.parse(formState.prescriptionDate)?.let { date ->
                val cal = Calendar.getInstance().apply { time = date }
                initialPrescriptionYear = cal.get(Calendar.YEAR)
                initialPrescriptionMonth = cal.get(Calendar.MONTH)
                initialPrescriptionDay = cal.get(Calendar.DAY_OF_MONTH)
            }
        } catch (e: Exception) { /* Ignore parsing error */ }
    }
    val prescriptionDatePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            val selectedDate = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
            recipeViewModel.updatePrescriptionDate(sdf.format(selectedDate.time))
        },
        initialPrescriptionYear, initialPrescriptionMonth, initialPrescriptionDay
    )

    // Validity Date Picker
    var initialValidityYear = calendar.get(Calendar.YEAR)
    var initialValidityMonth = calendar.get(Calendar.MONTH)
    var initialValidityDay = calendar.get(Calendar.DAY_OF_MONTH)
    if (formState.validityDate.isNotBlank()) {
        try {
            sdf.parse(formState.validityDate)?.let { date ->
                val cal = Calendar.getInstance().apply { time = date }
                initialValidityYear = cal.get(Calendar.YEAR)
                initialValidityMonth = cal.get(Calendar.MONTH)
                initialValidityDay = cal.get(Calendar.DAY_OF_MONTH)
            }
        } catch (e: Exception) { /* Ignore parsing error */ }
    }
    val validityDatePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            val selectedDate = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
            recipeViewModel.updateValidityDate(sdf.format(selectedDate.time))
        },
        initialValidityYear, initialValidityMonth, initialValidityDay
    )

    // --- UI ---
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (isEditingRecipe) {
                        IconButton(onClick = {
                            recipeId?.let { id ->
                                // *** CORRIGIDO: Passar context e lembreteDao ***
                                recipeViewModel.deleteRecipe(context, lembreteDao, id)
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Deletar Receita", tint = MaterialTheme.colorScheme.error)
                        }
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Spacer(modifier = Modifier.height(0.dp)) // Keep spacer for padding consistency

            // Display error message
            if (detailState is RecipeDetailUiState.Error) {
                Text(
                    text = (detailState as RecipeDetailUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            // Recipe Fields are always displayed, but button might be disabled during loading
            OutlinedTextField(value = formState.doctorName, onValueChange = recipeViewModel::updateDoctorName, label = { Text("Nome do Médico") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), isError = detailState is RecipeDetailUiState.Error && formState.doctorName.isBlank())
            OutlinedTextField(value = formState.crm, onValueChange = recipeViewModel::updateCrm, label = { Text("CRM do Médico (Opcional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = formState.patientName, onValueChange = recipeViewModel::updatePatientName, label = { Text("Nome do Paciente") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), isError = detailState is RecipeDetailUiState.Error && formState.patientName.isBlank())
            OutlinedTextField(value = formState.prescriptionDate, onValueChange = {}, label = { Text("Data da Prescrição (DD/MM/AAAA)") }, modifier = Modifier.fillMaxWidth(), readOnly = true, trailingIcon = { Icon(Icons.Default.CalendarToday, "Selecionar Data", modifier = Modifier.clickable { prescriptionDatePickerDialog.show() }) }, isError = detailState is RecipeDetailUiState.Error && formState.prescriptionDate.isBlank())
            OutlinedTextField(
                value = formState.validityDate,
                onValueChange = {}, // Not directly changeable
                label = { Text("Data de Validade (DD/MM/AAAA)") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = "Selecionar Data de Validade",
                        modifier = Modifier.clickable { validityDatePickerDialog.show() }
                    )
                },
                isError = detailState is RecipeDetailUiState.Error && formState.validityDate.isBlank() // Example validation
            )

            // Prescribed Medications Section
            Text("Medicamentos Prescritos", style = MaterialTheme.typography.titleMedium)

            prescribedMedications.forEachIndexed { index, med ->
                PrescribedMedicationItem(
                    medication = med,
                    isCurrentlyEditingThis = (index == editingMedIndex),
                    isAnyMedicationBeingEdited = (editingMedIndex != -1),
                    onEditClick = { recipeViewModel.startEditingPrescribedMedication(index) },
                    onRemoveClick = { recipeViewModel.removePrescribedMedication(index) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (detailState is RecipeDetailUiState.Error && prescribedMedications.isEmpty()) {
                Text("Adicione pelo menos um medicamento.", color = MaterialTheme.colorScheme.error)
            }

            // Form to add/edit a medication
            MedicationInputCard(
                medication = currentMedication,
                isEditing = editingMedIndex != -1,
                onNameChange = recipeViewModel::updateCurrentMedName,
                onDoseChange = recipeViewModel::updateCurrentMedDose,
                onInstructionsChange = recipeViewModel::updateCurrentMedInstructions,
                onSaveClick = { recipeViewModel.saveOrUpdateCurrentPrescribedMedication() },
                onCancelClick = { recipeViewModel.cancelEditingPrescribedMedication() }
            )

            // Other Recipe Fields
            OutlinedTextField(value = formState.generalInstructions, onValueChange = recipeViewModel::updateGeneralInstructions, label = { Text("Instruções Gerais (Opcional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 5)
            OutlinedTextField(value = formState.notes, onValueChange = recipeViewModel::updateNotes, label = { Text("Notas Adicionais (Opcional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 5)

            // Save Recipe Button
            Button(
                onClick = {
                    // *** CORRIGIDO: Passar context e lembreteDao ***
                    recipeViewModel.saveOrUpdateRecipe(context, lembreteDao)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                // Disable button only when the detail state is Loading
                enabled = detailState !is RecipeDetailUiState.Loading
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Show progress indicator inside button when loading
                    if (detailState is RecipeDetailUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary // Use contrast color for visibility
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    // Always show the text
                    Text(if (isEditingRecipe) "Atualizar Receita" else "Salvar Receita")
                }
            }
        }
    }
}

@Composable
fun MedicationInputCard(
    medication: PrescribedMedication,
    isEditing: Boolean,
    onNameChange: (String) -> Unit,
    onDoseChange: (String) -> Unit,
    onInstructionsChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (isEditing) "Editando Medicamento" else "Adicionar Novo Medicamento",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(value = medication.name, onValueChange = onNameChange, label = { Text("Nome do Medicamento") }, modifier = Modifier.fillMaxWidth(), isError = isEditing && medication.name.isBlank())
            OutlinedTextField(value = medication.dose, onValueChange = onDoseChange, label = { Text("Dose (ex: 500mg)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = medication.instructions, onValueChange = onInstructionsChange, label = { Text("Instruções Específicas") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isEditing) Arrangement.SpaceBetween else Arrangement.End
            ) {
                if (isEditing) {
                    Button(
                        onClick = onCancelClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Cancelar Edição")
                    }
                }
                Button(
                    onClick = onSaveClick,
                    enabled = medication.name.isNotBlank()
                ) {
                    val icon = if (isEditing) Icons.Default.Save else Icons.Default.Add
                    val text = if (isEditing) "Salvar Alterações" else "Adicionar Medicamento"
                    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(text)
                }
            }
        }
    }
}

@Composable
fun PrescribedMedicationItem(
    medication: PrescribedMedication,
    isCurrentlyEditingThis: Boolean,
    isAnyMedicationBeingEdited: Boolean,
    onEditClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(medication.name, fontWeight = FontWeight.Bold)
            if (medication.dose.isNotBlank()) Text("Dose: ${medication.dose}")
            if (medication.instructions.isNotBlank()) Text("Instruções: ${medication.instructions}")
        }
        Row {
            if (!isCurrentlyEditingThis && !isAnyMedicationBeingEdited) {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar Medicamento")
                }
            }
            if (!isAnyMedicationBeingEdited) {
                IconButton(onClick = onRemoveClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Remover Medicamento", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

