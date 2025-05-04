package com.pillora.pillora.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel // Ensure this import is present
import androidx.navigation.NavController
import com.pillora.pillora.viewmodel.VaccineViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccineFormScreen(
    navController: NavController,
    vaccineId: String? = null,
    viewModel: VaccineViewModel = viewModel() // Correct ViewModel injection
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() } // Correct SnackbarHostState
    val scope = rememberCoroutineScope()

    // Collect states from ViewModel
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val navigateBack by viewModel.navigateBack.collectAsState()

    // Form state directly from ViewModel's mutableStateOf properties
    val name = viewModel.name
    val reminderDate = viewModel.reminderDate
    val reminderTime = viewModel.reminderTime
    val location = viewModel.location
    val notes = viewModel.notes

    // Load vaccine data if ID is provided (editing mode)
    LaunchedEffect(vaccineId) {
        vaccineId?.let {
            if (it.isNotEmpty()) {
                viewModel.loadVaccine(it)
            }
        }
    }

    // Handle navigation when save is successful
    LaunchedEffect(navigateBack) {
        if (navigateBack) {
            navController.popBackStack()
            viewModel.onNavigationHandled() // Reset the flag in ViewModel
        }
    }

    // Show error messages using Snackbar
    LaunchedEffect(error) {
        error?.let {
            scope.launch {
                snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
            }
            viewModel.onErrorShown() // Reset error state in ViewModel
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }, // Correct SnackbarHost usage
        topBar = {
            TopAppBar(
                title = { Text(if (vaccineId == null) "Adicionar Lembrete" else "Editar Lembrete") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) {
            padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name.value,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Nome da Vacina/Lembrete*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Date Field
                    OutlinedTextField(
                        value = reminderDate.value,
                        onValueChange = { /* Read Only */ },
                        label = { Text("Data*") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.showDatePicker(context) },
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Filled.DateRange, contentDescription = "Selecionar Data",
                                modifier = Modifier.clickable { viewModel.showDatePicker(context) })
                        }
                    )
                    // Time Field
                    OutlinedTextField(
                        value = reminderTime.value,
                        onValueChange = { /* Read Only */ },
                        label = { Text("Hora*") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.showTimePicker(context) },
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Filled.Schedule, contentDescription = "Selecionar Hora",
                                modifier = Modifier.clickable { viewModel.showTimePicker(context) })
                        }
                    )
                }

                OutlinedTextField(
                    value = location.value,
                    onValueChange = viewModel::onLocationChange,
                    label = { Text("Local") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                OutlinedTextField(
                    value = notes.value,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text("Observações") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.saveVaccine() }, // Correct call to ViewModel function
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading // Correct usage of collected state
                ) {
                    if (isLoading) { // Correct usage of collected state
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (vaccineId == null) "Salvar Lembrete" else "Atualizar Lembrete")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

