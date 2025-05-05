package com.pillora.pillora.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Notes // Ícone para Receitas
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.model.Consultation // Import Consultation
import com.pillora.pillora.model.Vaccine // Importar Vaccine
import com.pillora.pillora.navigation.RECIPE_FORM_ROUTE // Importar rota de formulário de receita
import com.pillora.pillora.navigation.RECIPE_LIST_ROUTE // Importar rota de lista de receita
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
    val upcomingConsultations by viewModel.upcomingConsultations.collectAsState()
    val upcomingVaccines by viewModel.upcomingVaccines.collectAsState() // Observar estado das vacinas
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
            // Consider adding viewModel.onErrorShown() here if you want errors to be dismissable
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
                            IconButton(onClick = { navController.navigate(Screen.MedicineForm.route + "?id=") }) { // Passar ID vazio para adicionar
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
                            Text("Próximas Consultas (7 dias)", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { navController.navigate(Screen.ConsultationForm.route + "?id=") }) { // Passar ID vazio para adicionar
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Adicionar Consulta")
                            }
                            IconButton(onClick = { navController.navigate(Screen.ConsultationList.route) }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Ver Lista de Consultas")
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        if (upcomingConsultations.isNotEmpty()) {
                            upcomingConsultations.forEach { consultation ->
                                UpcomingConsultationItem(consultation = consultation)
                                if (upcomingConsultations.last() != consultation) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        } else {
                            Text("Nenhuma consulta agendada para os próximos 7 dias.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Card "Próximas Vacinas (15 dias)"
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
                            Text("Próximas Vacinas (15 dias)", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { navController.navigate(Screen.VaccineForm.route + "?id=") }) { // Passar ID vazio para adicionar
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Adicionar Lembrete de Vacina")
                            }
                            IconButton(onClick = { navController.navigate(Screen.VaccineList.route) }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Ver Lista de Lembretes")
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        if (upcomingVaccines.isNotEmpty()) {
                            upcomingVaccines.forEach { vaccine ->
                                UpcomingVaccineItem(vaccine = vaccine) // Usar um Composable dedicado
                                if (upcomingVaccines.last() != vaccine) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        } else {
                            Text("Nenhum lembrete de vacina para os próximos 15 dias.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Card "Receitas Médicas" - NOVO CARD
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
                            Text("Receitas Médicas", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { navController.navigate("$RECIPE_FORM_ROUTE?id=") }) { // Navegar para adicionar receita
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Adicionar Receita")
                            }
                            IconButton(onClick = { navController.navigate(RECIPE_LIST_ROUTE) }) { // Navegar para lista de receitas
                                Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = "Ver Lista de Receitas") // Usar ícone Notes
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        // TODO: Opcionalmente, mostrar um resumo das últimas receitas aqui
                        Text("Gerencie suas receitas médicas.", style = MaterialTheme.typography.bodyMedium)
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

                Spacer(modifier = Modifier.height(16.dp)) // Espaço no final da coluna
            }

            // Indicador de carregamento centralizado
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

// Composable para item de consulta (mantido como estava)
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
                    modifier = Modifier.size(16.dp),
                    tint = LocalContentColor.current.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = consultation.location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// Novo Composable para exibir item de vacina na HomeScreen
@Composable
fun UpcomingVaccineItem(vaccine: Vaccine) {
    // Formatação de data/hora similar a UpcomingConsultationItem, se necessário
    // Adapte para mostrar as informações relevantes da vacina
    Column {
        Text(
            text = vaccine.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Data Lembrete: ${vaccine.reminderDate}", // Ajustar conforme necessário
            style = MaterialTheme.typography.bodyMedium
        )
        // Adicionar mais detalhes se relevante (ex: dose, lote)
    }
}

