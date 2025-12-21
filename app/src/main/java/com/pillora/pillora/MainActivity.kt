package com.pillora.pillora

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.accompanist.permissions.isGranted
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.google.android.gms.ads.MobileAds
import com.pillora.pillora.navigation.*
import com.pillora.pillora.repository.AuthRepository
import com.pillora.pillora.screens.DrawerContent
import com.pillora.pillora.ui.theme.PilloraTheme
import com.pillora.pillora.viewmodel.ThemePreference
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val themePreferenceKey = "theme_preference"
    private var showExactAlarmPermissionDialog = false

    // 🔹 Launcher do Google Sign-In
    private lateinit var googleSignInLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Pillora)
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        val analytics = Firebase.analytics
        analytics.logEvent(com.google.firebase.analytics.FirebaseAnalytics.Event.APP_OPEN, null)

        MobileAds.initialize(this) { initializationStatus ->
            Log.d("AdMob", "SDK inicializado: $initializationStatus")
        }

        enableEdgeToEdge()

        // 🔹 Inicializa o launcher do Google Sign-In
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                AuthRepository.handleGoogleSignInResult(
                    result = result,
                    onSuccess = {
                        Toast.makeText(this, "Login com Google realizado com sucesso!", Toast.LENGTH_SHORT).show()
                    },
                    onError = { exception: Exception ->
                        Toast.makeText(this, "Erro ao fazer login com Google: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        setContent {
            var currentThemePreference by remember { mutableStateOf(getInitialThemePreference()) }
            var showExactAlarmPermissionDialog by remember { mutableStateOf(false) }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            // Atualiza tema quando mudar nas prefs
            DisposableEffect(Unit) {
                val prefs = context.getSharedPreferences("pillora_prefs", MODE_PRIVATE)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    if (key == themePreferenceKey) {
                        currentThemePreference = getThemePreferenceFromPrefs(sharedPreferences)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            val useDarkTheme = shouldUseDarkTheme(currentThemePreference)

            PilloraTheme(darkTheme = useDarkTheme) {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

                // Permissão de notificação (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notificationPermissionState = rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
                    LaunchedEffect(notificationPermissionState.status) {
                        if (!notificationPermissionState.status.isGranted && !notificationPermissionState.status.shouldShowRationale) {
                            notificationPermissionState.launchPermissionRequest()
                        }
                    }
                }

                // Permissão de alarme exato (Android 12+)
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val alarmManager = ContextCompat.getSystemService(context, AlarmManager::class.java)
                        if (alarmManager?.canScheduleExactAlarms() == false) {
                            showExactAlarmPermissionDialog = true
                        }
                    }
                }

                if (showExactAlarmPermissionDialog) {
                    ExactAlarmPermissionDialog(
                        onDismiss = { showExactAlarmPermissionDialog = false },
                        onConfirm = {
                            showExactAlarmPermissionDialog = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("Permissions", "Não foi possível abrir as configurações de alarme exato", e)
                                }
                            }
                        }
                    )
                }

                val statusBarHeightDp: Dp = with(LocalDensity.current) {
                    WindowInsets.statusBars.getTop(this).toDp()
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(with(LocalDensity.current) {
                                    LocalWindowInfo.current.containerSize.width.toDp() * 0.75f
                                })
                                .clip(RoundedCornerShape(24.dp))
                                .padding(16.dp),
                            drawerContainerColor = MaterialTheme.colorScheme.surface,
                            drawerTonalElevation = 8.dp
                        ) {
                            DrawerContent(
                                navController = navController,
                                scope = scope,
                                drawerState = drawerState,
                                authRepository = AuthRepository,
                                context = context,
                            )
                        }
                    }
                ) {
                    Scaffold(
                        bottomBar = {
                            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                            val shouldShowBottomBar = currentRoute !in listOf("terms?viewOnly={viewOnly}", "auth", "welcome")

                            if (shouldShowBottomBar) {
                                BottomNavigationBar(navController, currentRoute)
                            }
                        }
                    ) { padding ->
                        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                        val shouldShowBottomBar = currentRoute !in listOf("terms?viewOnly={viewOnly}", "auth", "welcome")

                        val adjustedPadding = if (shouldShowBottomBar) {
                            padding
                        } else {
                            PaddingValues(
                                top = padding.calculateTopPadding(),
                                bottom = 0.dp,
                                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                                end = padding.calculateEndPadding(LocalLayoutDirection.current)
                            )
                        }

                        Box(modifier = Modifier.padding(adjustedPadding)) {
                            AppNavigation(navController = navController)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms() && showExactAlarmPermissionDialog) {
                showExactAlarmPermissionDialog = false
            }
        }
    }

    private fun getInitialThemePreference(): ThemePreference {
        val prefs = getSharedPreferences("pillora_prefs", MODE_PRIVATE)
        return getThemePreferenceFromPrefs(prefs)
    }

    private fun getThemePreferenceFromPrefs(prefs: SharedPreferences): ThemePreference {
        return when (prefs.getString(themePreferenceKey, ThemePreference.SYSTEM.name)) {
            ThemePreference.LIGHT.name -> ThemePreference.LIGHT
            ThemePreference.DARK.name -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
    }
}

@Composable
private fun shouldUseDarkTheme(preference: ThemePreference): Boolean = when (preference) {
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
    else -> androidx.compose.foundation.isSystemInDarkTheme()
}

@Composable
fun ExactAlarmPermissionDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissão Necessária para Lembretes") },
        text = {
            Text("Para que os lembretes de medicamentos funcionem corretamente, o Pillora precisa da sua permissão para agendar alarmes precisos. Por favor, conceda essa permissão nas configurações do aplicativo.")
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Abrir Configurações") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Agora não") } }
    )
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

    NavigationBar(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        bottomNavItemsOrdered.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.contentDescription) },
                label = { Text(item.name) },
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
