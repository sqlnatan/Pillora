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
import androidx.compose.ui.graphics.Color // Import Color
import androidx.compose.ui.text.font.FontWeight // Import FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.model.Consultation // Import Consultation
// import com.pillora.pillora.model.Recipe // <<< ADDED: Import Recipe
import com.pillora.pillora.model.Vaccine // Importar Vaccine
import com.pillora.pillora.navigation.RECIPE_FORM_ROUTE // Importar rota de formulário de receita
import com.pillora.pillora.navigation.RECIPE_LIST_ROUTE // Importar rota de lista de receita
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.repository.AuthRepository
import androidx.compose.material3.MaterialTheme // <<< ADDED: Import MaterialTheme
import com.pillora.pillora.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit // <<< ADDED: Import TimeUnit

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
    val upcomingVaccines by viewModel.upcomingVaccines.collectAsState()
    val expiringRecipes by viewModel.expiringRecipes.collectAsState() // <<< ADDED: Observe expiring recipes
    val allRecipes by viewModel.allRecipes.collectAsState() // <<< NEW: Observe all recipes
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
            viewModel.onErrorShown() // <<< ADDED: Dismiss error after showing
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
                HomeCard(
                    title = "Medicamentos de Hoje",
                    addRoute = Screen.MedicineForm.route,
                    listRoute = Screen.MedicineList.route,
                    navController = navController // Pass NavController
                ) {
                    if (medicinesToday.isNotEmpty()) {
                        medicinesToday.forEach { med ->
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

                // Card "Consultas Médicas"
                HomeCard(
                    title = "Próximas Consultas (7 dias)",
                    addRoute = Screen.ConsultationForm.route + "?id=",
                    listRoute = Screen.ConsultationList.route,
                    navController = navController // Pass NavController
                ) {
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

                // Card "Próximas Vacinas (15 dias)"
                HomeCard(
                    title = "Próximas Vacinas (15 dias)",
                    addRoute = Screen.VaccineForm.route + "?id=",
                    listRoute = Screen.VaccineList.route,
                    navController = navController // Pass NavController
                ) {
                    if (upcomingVaccines.isNotEmpty()) {
                        upcomingVaccines.forEach { vaccine ->
                            UpcomingVaccineItem(vaccine = vaccine)
                            if (upcomingVaccines.last() != vaccine) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    } else {
                        Text("Nenhum lembrete de vacina para os próximos 15 dias.", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // <<< UPDATED: Card "Receitas Médicas" >>>
                HomeCard(
                    title = "Receitas Médicas",
                    addRoute = "$RECIPE_FORM_ROUTE?id=",
                    listRoute = RECIPE_LIST_ROUTE,
                    listIcon = Icons.AutoMirrored.Filled.Notes, // Use specific icon
                    navController = navController // Pass NavController
                ) {
                    // <<< UPDATED: Display list of recipes >>>
                    if (allRecipes.isNotEmpty()) {
                        // Display a few recent recipes, for example
                        allRecipes.take(3).forEach { recipe -> // Limit to 3 for brevity on home screen
                            Text(
                                text = "Receita p/ ${recipe.patientName} (Dr. ${recipe.doctorName}) - ${recipe.prescriptionDate}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (allRecipes.size > 3) {
                            Text("... e mais ${allRecipes.size - 3}", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text("Nenhuma receita cadastrada.", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // <<< ADDED: Card "Alertas de Validade" >>>
                if (expiringRecipes.isNotEmpty()) {
                    AlertCard(
                        title = "Alertas de Validade (Próximos 15 dias)",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer, // Use theme color
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer // Use theme color
                    ) {
                        expiringRecipes.forEach { recipe ->
                            val daysLeft = calculateDaysUntil(recipe.validityDate)
                            val daysText = when {
                                daysLeft == 0L -> "(Hoje)"
                                daysLeft == 1L -> "(Amanhã)"
                                daysLeft != null -> "(em $daysLeft dias)"
                                else -> ""
                            }
                            Text(
                                text = "Receita para ${recipe.patientName} vence em ${recipe.validityDate} $daysText",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer // Use theme color
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                // Card "Alertas de Estoque" (Using AlertCard)
                if (stockAlerts.isNotEmpty()) {
                    AlertCard(
                        title = "Alertas de Estoque",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
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

                Spacer(modifier = Modifier.height(16.dp)) // Space at the end
            }

            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

// <<< UPDATED: Generic Home Card Composable with NavController >>>
@Composable
fun HomeCard(
    title: String,
    addRoute: String,
    listRoute: String,
    navController: NavController, // Added NavController parameter
    addIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.AddCircleOutline,
    listIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.AutoMirrored.Filled.List,
    content: @Composable ColumnScope.() -> Unit
) {
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
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { navController.navigate(addRoute) }) {
                    Icon(addIcon, contentDescription = "Adicionar")
                }
                IconButton(onClick = { navController.navigate(listRoute) }) {
                    Icon(listIcon, contentDescription = "Ver Lista")
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

// <<< ADDED: Generic Alert Card Composable >>>
@Composable
fun AlertCard(
    title: String,
    containerColor: Color,
    contentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = contentColor
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = contentColor.copy(alpha = 0.5f))
            content()
        }
    }
}

// --- Item Composables (Kept from original, ensure they are correct) ---

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

    // <<< UPDATED: Use calculateDaysUntil for consistency >>>
    val dateText = consultationCal?.let { cal ->
        val datePart = sdfDate.format(cal.time)
        val timePart = sdfTime.format(cal.time)
        val dayLabel = when (val daysUntil = calculateDaysUntil(consultation.dateTime, sdfParse)) {
            0L -> "(Hoje)"
            1L -> "(Amanhã)"
            null -> ""
            else -> "(em $daysUntil dias)"
        }
        "$datePart às $timePart $dayLabel"
    } ?: "Data inválida"

    Column {
        Text("Dr(a). ${consultation.doctorName}", fontWeight = FontWeight.Bold)
        Text("Especialidade: ${consultation.specialty}")
        Text("Data: $dateText")
        if (consultation.location.isNotBlank()) {
            Text("Local: ${consultation.location}")
        }
    }
}

@Composable
fun UpcomingVaccineItem(vaccine: Vaccine) {
    val sdfDate = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val dateText = try {
        // Use flexible date parsing if needed, assuming dd/MM/yyyy for now
        sdfDate.parse(vaccine.reminderDate)?.let { sdfDate.format(it) }
    } catch (e: Exception) { null }

    // <<< UPDATED: Use calculateDaysUntil for consistency >>>
    val dayLabel = when (val daysUntil = calculateDaysUntil(vaccine.reminderDate)) {
        0L -> "(Hoje)"
        1L -> "(Amanhã)"
        null -> ""
        else -> "(em $daysUntil dias)"
    }

    Column {
        Text(vaccine.name, fontWeight = FontWeight.Bold)
        if (dateText != null) {
            Text("Lembrete: $dateText ${vaccine.reminderTime} $dayLabel")
        } else {
            Text("Lembrete: Data inválida")
        }
        if (vaccine.notes.isNotBlank()) {
            Text("Notas: ${vaccine.notes}")
        }
    }
}

// <<< ADDED: Helper Function calculateDaysUntil >>>
fun calculateDaysUntil(dateStr: String?, format: SimpleDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())): Long? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        format.isLenient = false // Ensure strict parsing
        format.parse(dateStr)?.let { targetDate ->
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val target = Calendar.getInstance().apply {
                time = targetDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            if (target < today) {
                null // Date is in the past
            } else {
                TimeUnit.MILLISECONDS.toDays(target - today)
            }
        }
    } catch (e: Exception) {
        null // Return null if parsing fails or any other error occurs
    }
}

