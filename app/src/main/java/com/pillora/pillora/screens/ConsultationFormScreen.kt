package com.pillora.pillora.screens

import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.data.dao.LembreteDao
import com.pillora.pillora.viewmodel.ConsultationViewModel
import com.pillora.pillora.utils.FreeLimits
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.repository.ConsultationRepository
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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

    // Verificação de limite do plano Free
    val application = context.applicationContext as PilloraApplication
    val isPremium by application.userPreferences.isPremium.collectAsState(initial = false)
    var showFreeLimitDialog by remember { mutableStateOf(false) }
    var currentConsultationCount by remember { mutableStateOf(0) }

    // Obter o LembreteDao
    val database = remember { com.pillora.pillora.data.local.AppDatabase.getDatabase(context) }
    val lembreteDao: LembreteDao = remember { database.lembreteDao() }

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val navigateBack by viewModel.navigateBack.collectAsState()

    // Campos do formulário
    val specialty = viewModel.specialty
    val doctorName = viewModel.doctorName
    val patientName = viewModel.patientName
    val date = viewModel.date
    val time = viewModel.time
    val location = viewModel.location
    val observations = viewModel.observations

    // Contar consultas existentes para verificação de limite Free
    LaunchedEffect(Unit) {
        if (!isPremium && consultationId.isNullOrEmpty()) {
            // Só verifica limite se for nova consulta e usuário Free
            ConsultationRepository.getAllConsultationsFlow().collect { consultations ->
                currentConsultationCount = consultations.size
                if (currentConsultationCount >= FreeLimits.MAX_CONSULTATIONS_FREE) {
                    showFreeLimitDialog = true
                }
            }
        }
    }

    // Carregar dados se for edição
    LaunchedEffect(consultationId) {
        consultationId?.let {
            if (it.isNotEmpty()) {
                viewModel.loadConsultation(it)
            }
        }
    }

    // Navegação pós-salvamento
    LaunchedEffect(navigateBack) {
        if (navigateBack) {
            navController.popBackStack()
            viewModel.onNavigationHandled()
        }
    }

    // Erros
    LaunchedEffect(error) {
        error?.let {
            scope.launch {
                snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
            }
            viewModel.onErrorShown()
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
                        "Você atingiu o limite de ${FreeLimits.MAX_CONSULTATIONS_FREE} consulta do plano Free."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Para adicionar mais consultas, faça upgrade para o plano Premium."
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
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = specialty.value,
                    onValueChange = viewModel::onSpecialtyChange,
                    label = { Text("Especialidade*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )

                OutlinedTextField(
                    value = doctorName.value,
                    onValueChange = viewModel::onDoctorNameChange,
                    label = { Text("Nome do Médico") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
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
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                OutlinedTextField(
                    value = observations.value,
                    onValueChange = viewModel::onObservationsChange,
                    label = { Text("Observações (Preparo, levar exames, etc.)") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                OutlinedTextField(
                    value = patientName.value,
                    onValueChange = { viewModel.onPatientNameChange(it) },
                    label = { Text("Nome do Paciente") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.saveConsultation(context, lembreteDao) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (consultationId == null) "Salvar Consulta" else "Atualizar Consulta")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
