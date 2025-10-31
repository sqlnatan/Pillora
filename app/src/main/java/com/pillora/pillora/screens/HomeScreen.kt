package com.pillora.pillora.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.model.Vaccine
import com.pillora.pillora.navigation.RECIPE_FORM_ROUTE
import com.pillora.pillora.navigation.RECIPE_LIST_ROUTE
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val medicinesToday by viewModel.medicinesToday.collectAsState()
    val stockAlerts by viewModel.stockAlerts.collectAsState()
    val upcomingConsultations by viewModel.upcomingConsultations.collectAsState()
    val upcomingVaccines by viewModel.upcomingVaccines.collectAsState()
    val expiringRecipes by viewModel.expiringRecipes.collectAsState()
    val allRecipes by viewModel.allRecipes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(error) {
        error?.let {
            scope.launch {
                snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
            }
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val statusBarHeightDp: Dp = with(LocalDensity.current) {
                WindowInsets.statusBars.getTop(this).toDp()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        top = statusBarHeightDp + 4.dp,
                        bottom = 8.dp,
                        end = 16.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { /* abrir drawer futuramente */ },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Pillora",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- Conteúdo original ---
                Text(
                    text = "Bem-vindo ao Pillora",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // --- Medicamentos de Hoje ---
                HomeCard(
                    title = "Medicamentos de Hoje",
                    addRoute = Screen.MedicineForm.route,
                    listRoute = Screen.MedicineList.route,
                    navController = navController
                ) {
                    if (medicinesToday.isNotEmpty()) {
                        medicinesToday.forEach { med ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${med.name} - ${med.dose} ${med.doseUnit ?: ""} - ${med.horarios?.joinToString() ?: med.startTime ?: "N/A"}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (med.recipientName.isNotBlank()) {
                                    Text(
                                        text = "Para: ${med.recipientName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    } else {
                        Text("Nenhum medicamento agendado para hoje.", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // --- Consultas ---
                HomeCard(
                    title = "Próximas Consultas (7 dias)",
                    addRoute = Screen.ConsultationForm.route + "?id=",
                    listRoute = Screen.ConsultationList.route,
                    navController = navController
                ) {
                    if (upcomingConsultations.isNotEmpty()) {
                        upcomingConsultations.forEachIndexed { i, consultation ->
                            UpcomingConsultationItem(consultation)
                            if (i < upcomingConsultations.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else {
                        Text("Nenhuma consulta agendada para os próximos 7 dias.")
                    }
                }

                // --- Vacinas ---
                HomeCard(
                    title = "Próximas Vacinas (15 dias)",
                    addRoute = Screen.VaccineForm.route + "?id=",
                    listRoute = Screen.VaccineList.route,
                    navController = navController
                ) {
                    if (upcomingVaccines.isNotEmpty()) {
                        upcomingVaccines.forEachIndexed { i, vaccine ->
                            UpcomingVaccineItem(vaccine)
                            if (i < upcomingVaccines.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else {
                        Text("Nenhum lembrete de vacina para os próximos 15 dias.")
                    }
                }

                // --- Receitas Médicas ---
                HomeCard(
                    title = "Receitas Médicas",
                    addRoute = "$RECIPE_FORM_ROUTE?id=",
                    listRoute = RECIPE_LIST_ROUTE,
                    listIcon = Icons.Filled.Notes,
                    navController = navController
                ) {
                    if (allRecipes.isNotEmpty()) {
                        allRecipes.take(3).forEach { recipe ->
                            val doctorInfo = "(Dr. ${recipe.doctorName}) - ${recipe.prescriptionDate}"
                            val displayText = if (recipe.patientName.isNotBlank()) {
                                "Receita p/ ${recipe.patientName} $doctorInfo"
                            } else {
                                "Receita $doctorInfo"
                            }
                            Text(displayText)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (allRecipes.size > 3) {
                            Text("... e mais ${allRecipes.size - 3}")
                        }
                    } else {
                        Text("Nenhuma receita cadastrada.")
                    }
                }

                // --- Alertas de Validade ---
                if (expiringRecipes.isNotEmpty()) {
                    AlertCard(
                        title = "Alertas de Validade (Próximos 15 dias)",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        expiringRecipes.forEach { recipe ->
                            val daysLeft = calculateDaysUntil(recipe.validityDate)
                            val daysText = when (daysLeft) {
                                0L -> "(Hoje)"
                                1L -> "(Amanhã)"
                                null -> ""
                                else -> "(em $daysLeft dias)"
                            }
                            Text("Receita para ${recipe.patientName} vence em ${recipe.validityDate} $daysText")
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                // --- Alertas de Estoque ---
                if (stockAlerts.isNotEmpty()) {
                    AlertCard(
                        title = "Alertas de Estoque",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        stockAlerts.forEach { med ->
                            Text("${med.name}: Estoque baixo (${med.stockQuantity} ${med.stockUnit})")
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

// Restante do código (HomeCard, AlertCard, UpcomingConsultationItem, UpcomingVaccineItem, calculateDaysUntil) permanece igual


// --- Generic Cards ---
@Composable
fun HomeCard(
    title: String,
    addRoute: String,
    listRoute: String,
    navController: NavController,
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
            Text(title, style = MaterialTheme.typography.titleLarge, color = contentColor)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = contentColor.copy(alpha = 0.5f))
            content()
        }
    }
}

// --- Item Components ---
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
        } catch (_: Exception) { null }
    }

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
        if (consultation.patientName.isNotBlank()) {
            Text("Paciente: ${consultation.patientName}", style = MaterialTheme.typography.bodySmall)
        }
        Text("Data: $dateText")
        if (consultation.location.isNotBlank()) Text("Local: ${consultation.location}")
    }
}

@Composable
fun UpcomingVaccineItem(vaccine: Vaccine) {
    val sdfDate = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val dateText = try {
        sdfDate.parse(vaccine.reminderDate)?.let { sdfDate.format(it) }
    } catch (_: Exception) { null }

    val dayLabel = when (val daysUntil = calculateDaysUntil(vaccine.reminderDate)) {
        0L -> "(Hoje)"
        1L -> "(Amanhã)"
        null -> ""
        else -> "(em $daysUntil dias)"
    }

    Column {
        Text(vaccine.name, fontWeight = FontWeight.Bold)
        if (vaccine.patientName.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Paciente: ${vaccine.patientName}", style = MaterialTheme.typography.bodySmall)
        }
        Text("Lembrete: ${dateText ?: "Data inválida"} ${vaccine.reminderTime} $dayLabel")
        if (vaccine.notes.isNotBlank()) Text("Notas: ${vaccine.notes}")
    }
}

fun calculateDaysUntil(
    dateStr: String?,
    format: SimpleDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
): Long? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        format.isLenient = false
        val targetDate = format.parse(dateStr) ?: return null
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetCal = Calendar.getInstance().apply {
            time = targetDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diff = targetCal.timeInMillis - todayCal.timeInMillis
        if (diff < 0) null else TimeUnit.MILLISECONDS.toDays(diff)
    } catch (_: Exception) { null }
}
