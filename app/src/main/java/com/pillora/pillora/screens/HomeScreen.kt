package com.pillora.pillora.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.model.Vaccine
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.repository.AuthRepository
import com.pillora.pillora.repository.TermsRepository
import com.pillora.pillora.viewmodel.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val medicinesToday by viewModel.medicinesToday.collectAsState()
    val upcomingConsultations by viewModel.upcomingConsultations.collectAsState()
    val upcomingVaccines by viewModel.upcomingVaccines.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val context = LocalContext.current
    val application = context.applicationContext as PilloraApplication
    val isPremium by application.userPreferences.isPremium.collectAsState(initial = false)
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Verificar se o usuário aceitou os termos atuais
    LaunchedEffect(Unit) {
        val userId = AuthRepository.getCurrentUser()?.uid
        if (userId != null) {
            val hasAccepted = TermsRepository.hasAcceptedCurrentTerms(userId)
            if (!hasAccepted) {
                navController.navigate("terms") {
                    popUpTo("home") { inclusive = true }
                }
            }
        }
    }

    LaunchedEffect(error) {
        error?.let {
            scope.launch {
                snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
            }
            viewModel.onErrorShown()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            val windowInfo = LocalWindowInfo.current
            val density = LocalDensity.current
            val drawerWidth = with(density) { (windowInfo.containerSize.width * 0.75f).toDp() }

            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 8.dp
            ) {
                DrawerContent(
                    navController = navController,
                    scope = scope,
                    drawerState = drawerState,
                    authRepository = AuthRepository,
                    context = context
                )
            }

        }
    ) {
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
                        onClick = {
                            scope.launch { drawerState.open() }
                        },
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
                    val currentUser = remember { AuthRepository.getCurrentUser() }
                    Text(
                        text = "Bem-vindo ${currentUser?.displayName ?: "Usuário"}",
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
                            Text("Nenhum medicamento agendado para hoje.")
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
                        navController = navController,
                        addIcon = if (isPremium) Icons.Default.AddCircleOutline else Icons.Default.Lock,
                        isPremium = isPremium
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

                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
fun DrawerContent(
    navController: NavController,
    scope: CoroutineScope,
    drawerState: DrawerState,
    authRepository: AuthRepository,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pillora",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Organize sua saúde",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        DrawerItem(icon = Icons.Default.Person, label = "Perfil") {
            scope.launch { drawerState.close() }
            navController.navigate(Screen.Profile.route)
        }
        Spacer(modifier = Modifier.height(8.dp))

        DrawerItem(icon = Icons.Default.Settings, label = "Configurações") {
            scope.launch { drawerState.close() }
            navController.navigate(Screen.Settings.route)
        }
        Spacer(modifier = Modifier.height(8.dp))

        DrawerItem(icon = Icons.Default.Description, label = "Relatórios") {
            scope.launch { drawerState.close() }
            navController.navigate(Screen.Reports.route)
        }
        Spacer(modifier = Modifier.height(8.dp))

        DrawerItem(icon = Icons.Default.Star, label = "Assinatura") {
            scope.launch { drawerState.close() }
            navController.navigate(Screen.Subscription.route)
        }

        Spacer(modifier = Modifier.weight(1f))

        DrawerItem(icon = Icons.AutoMirrored.Filled.ExitToApp, label = "Sair") {
            scope.launch {
                drawerState.close()
                authRepository.signOut()
                Log.d("Drawer", "Usuário saiu com sucesso")
                navController.navigate("auth") {
                    popUpTo(0)
                }
            }
        }
    }
}

@Composable

fun DrawerItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


// --- Generic Cards ---
@Composable
fun HomeCard(
    title: String,
    addRoute: String,
    listRoute: String,
    navController: NavController,
    addIcon: ImageVector = Icons.Default.AddCircleOutline,
    listIcon: ImageVector = Icons.AutoMirrored.Filled.List,
    isPremium: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    )
    {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    if (isPremium) {
                        navController.navigate(addRoute)
                    } else {
                        navController.navigate(Screen.Subscription.route)
                    }
                }) {
                    Icon(
                        imageVector = addIcon,
                        contentDescription = "Adicionar",
                        tint = if (isPremium)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
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
        } catch (_: Exception) {
            null
        }
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
        Text(
            text = "Dr(a). ${consultation.doctorName}",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Especialidade: ${consultation.specialty}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
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
    } catch (_: Exception) {
        null
    }

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

// --- Utils ---
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
    } catch (_: Exception) {
        null
    }
}
