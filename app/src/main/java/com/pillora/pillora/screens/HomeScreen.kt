package com.pillora.pillora.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.R
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.model.Vaccine
import com.pillora.pillora.navigation.RECIPE_FORM_ROUTE
import com.pillora.pillora.navigation.RECIPE_LIST_ROUTE
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.repository.AuthRepository
// import com.pillora.pillora.repository.TermsRepository // Movido para AppNavigation
import com.pillora.pillora.ads.NativeAdCard
import com.pillora.pillora.utils.FreeLimits
import com.pillora.pillora.utils.startInAppReview
import com.pillora.pillora.utils.SupportDialog
import com.pillora.pillora.viewmodel.HomeViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.pillora.pillora.utils.PermissionHelper
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

    val stockAlerts by viewModel.stockAlerts.collectAsState()
    val expiringRecipes by viewModel.expiringRecipes.collectAsState() // <<< ADDED: Observe expiring recipes
    val allRecipes by viewModel.allRecipes.collectAsState() // <<< NEW: Observe all recipes

    // Contadores totais para verificação de limites do plano Free
    val totalMedicinesCount by viewModel.totalMedicinesCount.collectAsState()
    val totalConsultationsCount by viewModel.totalConsultationsCount.collectAsState()

    val context = LocalContext.current
    val application = context.applicationContext as PilloraApplication
    val isPremium by application.userPreferences.isPremium.collectAsState(initial = false)
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSupportDialog by remember { mutableStateOf(false) } // NOVO: Estado para o Dialog de Suporte

    // CORREÇÃO: A verificação dos termos foi movida para o AppNavigation
    // Isso garante que a verificação só ocorra UMA VEZ na inicialização do app,
    // e não toda vez que a HomeScreen é recriada (ex: ao voltar das configurações)

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
                    context = context,
                    onShowSupportDialog = { showSupportDialog = true } // NOVO: Callback
                )
            }

        }
    ) {
        // NOVO: Exibir o Dialog de Suporte
        if (showSupportDialog) {
            SupportDialog(onDismiss = { showSupportDialog = false })
        }

        // CORREÇÃO: Remover o Scaffold interno e usar apenas Box com padding manual
        // Isso evita conflito com o padding do AppScaffold
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // CORREÇÃO: TopBar customizada com padding para status bar
                val statusBarHeightDp: Dp = with(LocalDensity.current) {
                    WindowInsets.statusBars.getTop(this).toDp()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            top = statusBarHeightDp + 8.dp,
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

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "Logo Pillora",
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pillora",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Thin,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // CORREÇÃO: Conteúdo com padding horizontal e bottom para BottomNavigationBar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(bottom = 80.dp), // Espaço para a BottomNavigationBar
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val currentUser = remember { AuthRepository.getCurrentUser() }
                    Text(
                        text = "Bem-vindo ${currentUser?.displayName ?: "Usuário"}",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // --- Avisos de Permissões e Bateria (NO TOPO) ---
                    val hasNotificationPermission = remember { PermissionHelper.hasNotificationPermission(context) }
                    val hasExactAlarmPermission = remember { PermissionHelper.hasExactAlarmPermission(context) }
                    val isBatteryOptimizationDisabled = remember { PermissionHelper.isBatteryOptimizationDisabled(context) }

                    if (!hasNotificationPermission || !hasExactAlarmPermission) {
                        AlertCard(
                            title = "⚠️ Permissões Necessárias",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            if (!hasNotificationPermission) {
                                Text(
                                    text = "• Permissão de Notificações não concedida. O app não poderá enviar lembretes!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            if (!hasExactAlarmPermission) {
                                Text(
                                    text = "• Permissão de Alarmes Exatos não concedida. Os lembretes podem não tocar no horário correto!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { navController.navigate("permissions") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Conceder Permissões Agora")
                            }
                        }
                    }

                    if (!isBatteryOptimizationDisabled) {
                        AlertCard(
                            title = "🔋 Otimização de Bateria Ativa",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Text(
                                text = "O app está com restrições de bateria. Os alarmes podem não tocar corretamente!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Para funcionar corretamente, o Pillora precisa estar configurado como \"Sem restrição\" nas configurações de bateria.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { PermissionHelper.openBatteryOptimizationSettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Ir para Configurações de Bateria")
                            }
                        }
                    }

                    // --- Medicamentos de Hoje ---
                    // Verifica se usuário Free atingiu o limite de medicamentos
                    val canAddMedicine = isPremium || totalMedicinesCount < FreeLimits.MAX_MEDICINES_FREE
                    HomeCard(
                        title = "Medicamentos de Hoje",
                        addRoute = Screen.MedicineForm.route,
                        listRoute = Screen.MedicineList.route,
                        navController = navController,
                        isPremium = canAddMedicine,
                        addIcon = if (canAddMedicine) Icons.Default.AddCircleOutline else Icons.Default.Lock,
                        freeLimitText = if (!isPremium) "Limite Free: ${FreeLimits.MAX_MEDICINES_FREE}" else null,
                        currentCount = if (!isPremium) totalMedicinesCount else null,
                        maxCount = if (!isPremium) FreeLimits.MAX_MEDICINES_FREE else null
                    ) {
                        if (medicinesToday.isNotEmpty()) {
                            medicinesToday
                                .sortedBy { med ->
                                    // pega o primeiro horário disponível
                                    med.horarios?.minOrNull() ?: med.startTime ?: "99:99"
                                }
                                .forEach { med ->
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "${med.name} - ${med.dose} ${med.doseUnit ?: ""} - ${med.horarios?.joinToString() ?: med.startTime ?: "N/A"}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (med.recipientName.isNotBlank()) {
                                            Text(
                                                text = "Para: ${med.recipientName}",
                                                style = MaterialTheme.typography.bodyMedium,
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

                    // --- Anúncio Nativo (apenas para usuários FREE) ---
                    if (!isPremium) {
                        NativeAdCard(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // --- Consultas ---
                    // Verifica se usuário Free atingiu o limite de consultas
                    val canAddConsultation = isPremium || totalConsultationsCount < FreeLimits.MAX_CONSULTATIONS_FREE
                    HomeCard(
                        title = "Próximas Consultas (7 dias)",
                        addRoute = Screen.ConsultationForm.route + "?id=",
                        listRoute = Screen.ConsultationList.route,
                        navController = navController,
                        isPremium = canAddConsultation,
                        addIcon = if (canAddConsultation) Icons.Default.AddCircleOutline else Icons.Default.Lock,
                        freeLimitText = if (!isPremium) "Limite Free: ${FreeLimits.MAX_CONSULTATIONS_FREE}" else null,
                        currentCount = if (!isPremium) totalConsultationsCount else null,
                        maxCount = if (!isPremium) FreeLimits.MAX_CONSULTATIONS_FREE else null
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

                    // <<< UPDATED: Card "Receitas Médicas" >>>
                    HomeCard(
                        title = "Receitas Médicas (15 dias)",
                        addRoute = "$RECIPE_FORM_ROUTE?id=",
                        listRoute = RECIPE_LIST_ROUTE,
                        listIcon = Icons.AutoMirrored.Filled.Notes, // Use specific icon
                        navController = navController,
                        addIcon = if (isPremium) Icons.Default.AddCircleOutline else Icons.Default.Lock,
                        isPremium = isPremium
                    ) {
                        // <<< UPDATED: Display list of recipes >>>
                        if (allRecipes.isNotEmpty()) {
                            // Display a few recent recipes, for example
                            allRecipes.take(3).forEach { recipe -> // Limit to 3 for brevity on home screen
                                val doctorInfo = "( ${recipe.doctorName}) - ${recipe.prescriptionDate}"
                                val displayText = if (recipe.patientName.isNotBlank()) {
                                    "Receita p/ ${recipe.patientName} $doctorInfo"
                                } else {
                                    "Receita $doctorInfo"
                                }
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            if (allRecipes.size > 3) {
                                Text("... e mais ${allRecipes.size - 3}", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            Text("Nenhuma receita cadastrada.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // <<< ADDED: Card "Alertas de Validade" >>>
                    if (expiringRecipes.isNotEmpty()) {
                        AlertCard(
                            title = "Alertas de Validade (15 dias)",
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
                                    text = "Receita de ${recipe.doctorName} vence em ${recipe.validityDate} $daysText",
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

                    // --- Avisos de Permissões e Bateria movidos para o TOPO ---

                    // --- Aviso sobre Economia de Energia ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ℹ️ Bateria baixa ou modo economia de energia podem interferir nos lembretes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // SnackbarHost no topo da tela
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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

@Composable
fun DrawerContent(
    navController: NavController,
    scope: CoroutineScope,
    drawerState: DrawerState,
    authRepository: AuthRepository,
    context: Context,
    onShowSupportDialog: () -> Unit // NOVO: Callback para mostrar o dialog
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "Logo Pillora",
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Pillora",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Organize sua saúde",
            style = MaterialTheme.typography.bodyMedium,
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
        Spacer(modifier = Modifier.height(8.dp))

        DrawerItem(icon = Icons.Default.Info, label = "Suporte") {
            scope.launch { drawerState.close() }
            onShowSupportDialog()
        }

        Spacer(modifier = Modifier.weight(1f))

        DrawerItem(icon = Icons.Default.Star, label = "Avaliar o App") {
            scope.launch {
                drawerState.close()
                startInAppReview(context, scope)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

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
            style = MaterialTheme.typography.titleLarge,
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
    freeLimitText: String? = null,
    currentCount: Int? = null,
    maxCount: Int? = null,
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

                // Aviso de limite Free ao lado do botão de adicionar
                if (freeLimitText != null && currentCount != null && maxCount != null) {
                    Text(
                        text = "$currentCount/$maxCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (currentCount >= maxCount)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

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
            text = consultation.doctorName,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Especialidade: ${consultation.specialty}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        if (consultation.patientName.isNotBlank()) {
            Text("Paciente: ${consultation.patientName}", style = MaterialTheme.typography.bodyMedium)
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
            Text("Paciente: ${vaccine.patientName}", style = MaterialTheme.typography.bodyMedium)
        }
        Text("Dia ${dateText ?: "Data inválida"} ${vaccine.reminderTime} $dayLabel")
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
