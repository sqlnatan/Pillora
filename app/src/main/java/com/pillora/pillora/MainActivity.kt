package com.pillora.pillora

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.pillora.pillora.navigation.*
import com.pillora.pillora.repository.AuthRepository
import com.pillora.pillora.screens.DrawerContent
import com.pillora.pillora.ui.theme.PilloraTheme
import com.pillora.pillora.viewmodel.ThemePreference
import com.pillora.pillora.utils.SupportDialog
import com.pillora.pillora.utils.PermissionHelper

class MainActivity : ComponentActivity() {

    private val themePreferenceKey = "theme_preference"

    private lateinit var googleSignInLauncher:
            androidx.activity.result.ActivityResultLauncher<Intent>

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Pillora)
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        Firebase.analytics.logEvent(
            com.google.firebase.analytics.FirebaseAnalytics.Event.APP_OPEN,
            null
        )

        MobileAds.initialize(this) {
            Log.d("AdMob", "SDK inicializado: $it")
        }

        enableEdgeToEdge()

        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                AuthRepository.handleGoogleSignInResult(
                    result,
                    onSuccess = {
                        Toast.makeText(
                            this,
                            "Login com Google realizado com sucesso!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onError = {
                        Toast.makeText(
                            this,
                            "Erro ao fazer login: ${it.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }

        setContent {
            var currentThemePreference by remember {
                mutableStateOf(getInitialThemePreference())
            }
            var showSupportDialog by remember { mutableStateOf(false) }

            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            DisposableEffect(Unit) {
                val prefs =
                    context.getSharedPreferences("pillora_prefs", MODE_PRIVATE)
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                        if (key == themePreferenceKey) {
                            currentThemePreference =
                                getThemePreferenceFromPrefs(sp)
                        }
                    }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            PilloraTheme(
                darkTheme = shouldUseDarkTheme(currentThemePreference)
            ) {
                val navController = rememberNavController()
                val drawerState =
                    rememberDrawerState(initialValue = DrawerValue.Closed)

                // Processar extras do Intent para navegacao direta - LER APENAS UMA VEZ
                // Usar rememberSaveable para persistir os valores e evitar reler do Intent a cada recomposição
                val openConsultationEdit = rememberSaveable {
                    mutableStateOf(intent?.getBooleanExtra("OPEN_CONSULTATION_EDIT", false) ?: false)
                }
                val consultationId = rememberSaveable {
                    mutableStateOf(intent?.getStringExtra("CONSULTATION_ID"))
                }
                val openVaccineEdit = rememberSaveable {
                    mutableStateOf(intent?.getBooleanExtra("OPEN_VACCINE_EDIT", false) ?: false)
                }
                val vaccineId = rememberSaveable {
                    mutableStateOf(intent?.getStringExtra("VACCINE_ID"))
                }

                val currentRoute =
                    navController.currentBackStackEntryAsState().value
                        ?.destination
                        ?.route

                val noDrawerRoutes = listOf(
                    "welcome",
                    "auth",
                    "terms",
                    "medicine_form",
                    "medicine_list",
                    "settings",
                    "permissions",
                    "consultation_list",
                    "vaccine_list",
                    "recipe_list",
                    "consultation_form",
                    "vaccine_form",
                    "recipe_form",
                    "profile",
                    "reports",
                    "subscription",
                    "age_verification",
                    "terms?viewOnly={viewOnly}"
                )

                val shouldShowDrawer =
                    currentRoute != null && currentRoute !in noDrawerRoutes


                // ========== GERENCIAMENTO DE PERMISSÕES ==========
                // Estado para controlar se já solicitou permissões
                var hasRequestedPermissions by rememberSaveable { mutableStateOf(false) }

                // Permissão de notificações (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notificationPermissionState = rememberPermissionState(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )

                    LaunchedEffect(notificationPermissionState.status) {
                        if (!hasRequestedPermissions &&
                            !notificationPermissionState.status.isGranted &&
                            !notificationPermissionState.status.shouldShowRationale
                        ) {
                            notificationPermissionState.launchPermissionRequest()
                            hasRequestedPermissions = true
                        }
                    }
                }

                // Verificar permissão de alarmes exatos (Android 12+)
                // Esta permissão não pode ser solicitada automaticamente, apenas verificada
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val hasExactAlarm = PermissionHelper.hasExactAlarmPermission(context)
                        if (!hasExactAlarm) {
                            // Log para debug - não solicitar automaticamente
                            Log.d("MainActivity", "SCHEDULE_EXACT_ALARM não concedida. Usuário pode conceder nas Configurações.")
                        } else {
                            Log.d("MainActivity", "SCHEDULE_EXACT_ALARM concedida. Alarmes exatos disponíveis.")
                        }
                    }
                }

                if (shouldShowDrawer) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(
                                        with(LocalDensity.current) {
                                            LocalWindowInfo.current
                                                .containerSize
                                                .width
                                                .toDp() * 0.75f
                                        }
                                    )
                                    .clip(RoundedCornerShape(24.dp))
                                    .padding(16.dp),
                                drawerContainerColor =
                                    MaterialTheme.colorScheme.surface,
                                drawerTonalElevation = 8.dp
                            ) {
                                DrawerContent(
                                    navController = navController,
                                    scope = scope,
                                    drawerState = drawerState,
                                    authRepository = AuthRepository,
                                    context = context,
                                    onShowSupportDialog = { showSupportDialog = true }
                                )
                            }
                        }
                    ) {
                        AppScaffold(
                            navController = navController,
                            openConsultationEdit = openConsultationEdit.value,
                            consultationId = consultationId.value,
                            openVaccineEdit = openVaccineEdit.value,
                            vaccineId = vaccineId.value
                        )
                    }
                } else {
                    AppScaffold(
                        navController = navController,
                        openConsultationEdit = openConsultationEdit.value,
                        consultationId = consultationId.value,
                        openVaccineEdit = openVaccineEdit.value,
                        vaccineId = vaccineId.value
                    )
                }

                // Exibir o Dialog de Suporte
                if (showSupportDialog) {
                    SupportDialog(onDismiss = { })
                }
            }
        }
    }


    private fun getInitialThemePreference(): ThemePreference {
        val prefs =
            getSharedPreferences("pillora_prefs", MODE_PRIVATE)
        return getThemePreferenceFromPrefs(prefs)
    }

    private fun getThemePreferenceFromPrefs(
        prefs: SharedPreferences
    ): ThemePreference =
        when (prefs.getString(
            themePreferenceKey,
            ThemePreference.SYSTEM.name
        )) {
            ThemePreference.LIGHT.name -> ThemePreference.LIGHT
            ThemePreference.DARK.name -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
}

@Composable
private fun shouldUseDarkTheme(
    preference: ThemePreference
): Boolean =
    when (preference) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

@Composable
fun AppScaffold(
    navController: NavHostController,
    openConsultationEdit: Boolean = false,
    consultationId: String? = null,
    openVaccineEdit: Boolean = false,
    vaccineId: String? = null
) {

    val currentRoute =
        navController.currentBackStackEntryAsState().value
            ?.destination
            ?.route

    val noBottomBarRoutes = listOf(
        "welcome",
        "auth",
        "age_verification",
        "terms?viewOnly={viewOnly}"
    )

    // CORREÇÃO: Usar contentWindowInsets para controlar onde o Scaffold aplica padding
    // Isso permite que o conteúdo fique edge-to-edge, mas a BottomNavigationBar respeite os insets
    Scaffold(
        contentWindowInsets = WindowInsets(0), // Remove padding automático do conteúdo
        bottomBar = {
            if (
                currentRoute != null &&
                currentRoute !in noBottomBarRoutes
            ) {
                BottomNavigationBar(navController, currentRoute)
            }
        }
    ) { padding ->
        // CORREÇÃO: Aplicar padding apenas para a BottomNavigationBar
        // O padding top (status bar) será gerenciado por cada tela individualmente
        Box(modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            AppNavigation(
                navController = navController,
                openConsultationEdit = openConsultationEdit,
                consultationId = consultationId,
                openVaccineEdit = openVaccineEdit,
                vaccineId = vaccineId
            )
        }
    }
}


@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    currentRoute: String?
) {
    val bottomNavItemsOrdered = listOf(
        bottomNavItems.first { it.name == "Início" },
        bottomNavItems.first { it.name == "Medicação" },
        bottomNavItems.first { it.name == "Consultas" },
        bottomNavItems.first { it.name == "Vacinas" },
        bottomNavItems.first { it.name == "Receitas" }
    )

    NavigationBar {
        bottomNavItemsOrdered.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(item.icon, contentDescription = item.contentDescription)
                },
                label = { Text(item.name) },
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(
                                navController.graph.startDestinationId
                            ) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
