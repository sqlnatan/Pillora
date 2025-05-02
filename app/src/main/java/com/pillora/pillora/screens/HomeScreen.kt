package com.pillora.pillora.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember // Adicionado
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight // Import FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // Import sp for font size
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.model.Consultation // Import Consultation
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.repository.AuthRepository
import com.pillora.pillora.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel() // Injetar ViewModel
) {
    // Observar os estados do ViewModel
    val medicinesToday by viewModel.medicinesToday.collectAsState()
    val stockAlerts by viewModel.stockAlerts.collectAsState()
    val upcomingConsultations by viewModel.upcomingConsultations.collectAsState() // Observe upcoming consultations
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Mostrar Snackbar em caso de erro
    LaunchedEffect(error) {
        error?.let {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = it,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pillora") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                    IconButton(onClick = {
                        AuthRepository.signOut()
                        navController.navigate("auth") {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Bem-vindo ao Pillora",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Card "Medicamentos de Hoje"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Medicamentos de Hoje", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { navController.navigate(Screen.MedicineForm.route) }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Cadastrar Medicamento")
                            }
                            IconButton(onClick = { navController.navigate(Screen.MedicineList.route) }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Ver Lista de Medicamentos")
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        if (medicinesToday.isNotEmpty()) {
                            medicinesToday.forEach { med ->
                                // Simple display for today's medicines
                                Text(
                                    text = "${med.name} - ${med.dose} ${med.doseUnit ?: ""} - ${med.horarios?.joinToString() ?: med.startTime ?: "N/A"}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        } else {
                            Text("Nenhum medicamento agendado para hoje.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Card "Consultas Médicas"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Próximas Consultas (7 dias)", style = MaterialTheme.typography.titleLarge) // Updated title
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { navController.navigate(Screen.ConsultationForm.route) }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Adicionar Consulta")
                            }
                            IconButton(onClick = { navController.navigate(Screen.ConsultationList.route) }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Ver Lista de Consultas")
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        // Display upcoming consultations in the new compact format
                        if (upcomingConsultations.isNotEmpty()) {
                            upcomingConsultations.forEach { consultation ->
                                UpcomingConsultationItem(consultation = consultation)
                                if (upcomingConsultations.last() != consultation) {
                                    Spacer(modifier = Modifier.height(8.dp)) // Space between items
                                }
                            }
                        } else {
                            Text("Nenhuma consulta agendada para os próximos 7 dias.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Card "Alertas"
                if (stockAlerts.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Alertas de Estoque",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f))
                            stockAlerts.forEach { med ->
                                Text(
                                    text = "${med.name}: Estoque baixo (${med.stockQuantity} ${med.stockUnit})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Indicador de carregamento centralizado
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

// New Composable for the compact consultation item display
@Composable
fun UpcomingConsultationItem(consultation: Consultation) {
    val sdfParse = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val sdfDate = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val sdfTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val consultationCal: Calendar? = remember(consultation.dateTime) {
        try {
            sdfParse.parse(consultation.dateTime)?.let { date ->
                Calendar.getInstance().apply { time = date }
            }
        } catch (e: Exception) {
            null
        }
    }

    val today = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val tomorrow = remember {
        (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    }

    val dateText = consultationCal?.let { cal ->
        val datePart = sdfDate.format(cal.time)
        val consultationDayStart = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when {
            consultationDayStart.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    consultationDayStart.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "$datePart (Hoje)"

            consultationDayStart.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
                    consultationDayStart.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR) -> "$datePart (Amanhã)"

            else -> datePart
        }
    } ?: consultation.dateTime // Fallback to original string if parsing fails

    val timeText = consultationCal?.let { sdfTime.format(it.time) } ?: ""

    Column {
        Text(
            text = "${consultation.specialty} - Dr(a). ${consultation.doctorName.ifEmpty { "-" }}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$dateText - $timeText",
                style = MaterialTheme.typography.bodyMedium
            )
            if (consultation.location.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = "Local",
                    modifier = Modifier.size(16.dp), // Corrected: Use dp instead of sp
                    tint = LocalContentColor.current.copy(alpha = 0.7f) // Subtle color
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = consultation.location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f) // Subtle color
                )
            }
        }
    }
}

