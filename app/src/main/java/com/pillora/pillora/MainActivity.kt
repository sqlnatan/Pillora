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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.pillora.pillora.navigation.AppNavigation
import com.pillora.pillora.ui.theme.PilloraTheme
import com.pillora.pillora.viewmodel.ThemePreference


class MainActivity : ComponentActivity() {

    private val themePreferenceKey = "theme_preference"
    private var showExactAlarmPermissionDialog by mutableStateOf(false)

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Pillora) // Manter o tema de splash nativo
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        val analytics = Firebase.analytics
        analytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)
        enableEdgeToEdge()

        // *** CORREÇÃO: Verificar parâmetros para edição de consulta E vacina ***
        val openConsultationEdit = intent?.getBooleanExtra("OPEN_CONSULTATION_EDIT", false) ?: false
        val consultationId = intent?.getStringExtra("CONSULTATION_ID")
        val openVaccineEdit = intent?.getBooleanExtra("OPEN_VACCINE_EDIT", false) ?: false
        val vaccineId = intent?.getStringExtra("VACCINE_ID")

        if (openConsultationEdit && consultationId != null) {
            Log.d("MainActivity", "Abrindo tela de edição para consulta: $consultationId")
        }
        // *** CORREÇÃO: Adicionar log para edição de vacina ***
        if (openVaccineEdit && vaccineId != null) {
            Log.d("MainActivity", "Abrindo tela de edição para vacina: $vaccineId")
        }

        setContent {
            var currentThemePreference by remember { mutableStateOf(getInitialThemePreference()) }
            val context = LocalContext.current

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
                // Lógica para Permissão de Notificação (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notificationPermissionState = rememberPermissionState(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )
                    LaunchedEffect(notificationPermissionState.status) {
                        if (!notificationPermissionState.status.isGranted && !notificationPermissionState.status.shouldShowRationale) {
                            notificationPermissionState.launchPermissionRequest()
                        }
                    }
                    if (notificationPermissionState.status.shouldShowRationale) {
                        Log.d("Permissions", "Deveria mostrar justificativa para permissão de notificação.")
                        // Aqui você pode adicionar um diálogo para explicar a necessidade da permissão de notificação
                    } else if (!notificationPermissionState.status.isGranted) {
                        Log.d("Permissions", "Permissão de notificação não concedida.")
                    }
                }

                // Lógica para Permissão de Alarme Exato (Android 12+)
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

                // *** CORREÇÃO: Passar os parâmetros de navegação para consulta E vacina ***
                AppNavigation(
                    openConsultationEdit = openConsultationEdit,
                    consultationId = consultationId,
                    openVaccineEdit = openVaccineEdit, // Passar parâmetro de vacina
                    vaccineId = vaccineId // Passar ID da vacina
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.d("Permissions", "Permissão de alarme exato ainda não concedida no onResume.")
            } else {
                if (showExactAlarmPermissionDialog) {
                    showExactAlarmPermissionDialog = false
                }
                Log.d("Permissions", "Permissão de alarme exato concedida no onResume.")
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
private fun shouldUseDarkTheme(preference: ThemePreference): Boolean {
    return when (preference) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        else -> isSystemInDarkTheme()
    }
}

@Composable
fun ExactAlarmPermissionDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissão Necessária para Lembretes") },
        text = { Text("Para que os lembretes de medicamentos funcionem corretamente, o Pillora precisa da sua permissão para agendar alarmes precisos. Por favor, conceda essa permissão nas configurações do aplicativo.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Abrir Configurações")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Agora não")
            }
        }
    )
}

