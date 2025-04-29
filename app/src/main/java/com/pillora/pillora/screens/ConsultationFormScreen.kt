package com.pillora.pillora.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Save // Use Save icon for FAB
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.viewmodel.ConsultationViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultationFormScreen(
    navController: NavController,
    consultationId: String? = null,
    viewModel: ConsultationViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val navigateBack by viewModel.navigateBack.collectAsState()

    // Form state from ViewModel
    val specialty = viewModel.specialty
    val doctorName = viewModel.doctorName
    val date = viewModel.date
    val time = viewModel.time
    val location = viewModel.location
    val observations = viewModel.observations

    // Load consultation data if ID is provided (editing mode)
    LaunchedEffect(consultationId) {
        consultationId?.let {
            if (it.isNotEmpty()) {
                viewModel.loadConsultation(it) // Call loadConsultation from ViewModel
            }
        }
    }

    // Handle navigation when save is successful
    LaunchedEffect(navigateBack) {
        if (navigateBack) {
            navController.popBackStack()
            viewModel.onNavigationHandled() // Reset the flag
        }
    }

    // Show error messages
    LaunchedEffect(error) {
        error?.let {
            scope.launch {
                snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
            }
            viewModel.onErrorShown() // Call onErrorShown from ViewModel
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (consultationId == null) "Adicionar Consulta" else "Editar Consulta") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.saveConsultation() },
                icon = { Icon(Icons.Filled.Save, "Salvar Consulta") }, // Changed icon to Save
                text = { Text("Salvar") },
                expanded = true
            )
        },
        floatingActionButtonPosition = FabPosition.Center
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
                    value = specialty.value,
                    onValueChange = viewModel::onSpecialtyChange,
                    label = { Text("Especialidade*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions( // Fully qualified name
                        capitalization = KeyboardCapitalization.Words
                    )
                )

                OutlinedTextField(
                    value = doctorName.value,
                    onValueChange = viewModel::onDoctorNameChange,
                    label = { Text("Nome do Médico") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions( // Fully qualified name
                        capitalization = KeyboardCapitalization.Words
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = date.value,
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
                    OutlinedTextField(
                        value = time.value,
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
                    label = { Text("Local (Clínica/Endereço)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions( // Fully qualified name
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                OutlinedTextField(
                    value = observations.value,
                    onValueChange = viewModel::onObservationsChange,
                    label = { Text("Observações (Preparo, levar exames, etc.)") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions( // Fully qualified name
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                Spacer(modifier = Modifier.height(64.dp)) // Space for FAB
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

