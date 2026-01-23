package com.pillora.pillora.utils

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * Helper para gerenciar permissões do app Pillora.
 *
 * Gerencia:
 * - POST_NOTIFICATIONS (Android 13+): Necessária para exibir notificações
 * - SCHEDULE_EXACT_ALARM (Android 12+): Necessária para alarmes exatos de medicamentos
 * - BATTERY_OPTIMIZATION: Verifica se o app está sem restrições de bateria
 */
object PermissionHelper {

    private const val TAG = "PermissionHelper"

    /**
     * Verifica se a permissão POST_NOTIFICATIONS foi concedida.
     * Necessária no Android 13+ (API 33+) para exibir notificações.
     *
     * @return true se concedida ou não necessária (Android < 13), false caso contrário
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "POST_NOTIFICATIONS permission: ${if (granted) "GRANTED" else "DENIED"}")
            granted
        } else {
            // Android < 13 não precisa dessa permissão
            Log.d(TAG, "POST_NOTIFICATIONS not required (Android < 13)")
            true
        }
    }

    /**
     * Verifica se a permissão SCHEDULE_EXACT_ALARM foi concedida.
     * Necessária no Android 12+ (API 31+) para agendar alarmes exatos de medicamentos.
     *
     * @return true se concedida ou não necessária (Android < 12), false caso contrário
     */
    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canSchedule = alarmManager.canScheduleExactAlarms()
            Log.d(TAG, "SCHEDULE_EXACT_ALARM permission: ${if (canSchedule) "GRANTED" else "DENIED"}")
            canSchedule
        } else {
            // Android < 12 não precisa dessa permissão
            Log.d(TAG, "SCHEDULE_EXACT_ALARM not required (Android < 12)")
            true
        }
    }

    /**
     * Verifica se o app está com uso de bateria "sem restrição".
     * Necessário para garantir que os alarmes funcionem corretamente.
     *
     * @return true se o app está sem restrições de bateria, false caso contrário
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            Log.d(TAG, "Battery optimization disabled: $isIgnoring")
            isIgnoring
        } else {
            // Android < 6.0 não tem otimização de bateria
            Log.d(TAG, "Battery optimization not applicable (Android < 6.0)")
            true
        }
    }

    /**
     * Abre as configurações do sistema para o usuário conceder permissão POST_NOTIFICATIONS.
     * Esta permissão só pode ser solicitada via runtime permission request no Android 13+.
     *
     * Nota: Use ActivityResultContracts.RequestPermission() para solicitar esta permissão.
     * Esta função é um fallback caso o usuário negue e precise ir manualmente nas configurações.
     */
    fun openNotificationSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent().apply {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "Opened notification settings")
            } else {
                // Fallback para versões antigas
                openAppSettings(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening notification settings", e)
            // Fallback: abrir configurações gerais do app
            openAppSettings(context)
        }
    }

    /**
     * Abre as configurações do sistema para o usuário conceder permissão SCHEDULE_EXACT_ALARM.
     * Esta permissão não pode ser solicitada via runtime permission - usuário deve conceder manualmente.
     *
     * Android 12+ (API 31+)
     */
    fun openExactAlarmSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    data = "package:${context.packageName}".toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "Opened exact alarm settings")
            } else {
                Log.w(TAG, "SCHEDULE_EXACT_ALARM settings not available on Android < 12")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening exact alarm settings", e)
            // Fallback: abrir configurações gerais do app
            openAppSettings(context)
        }
    }

    /**
     * Abre as configurações de bateria do aplicativo para o usuário desativar a otimização.
     * Redireciona para "Informações do app - Bateria".
     *
     * Android 6.0+ (API 23+)
     */
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = "package:${context.packageName}".toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "Opened battery optimization settings (app details)")
            } else {
                Log.w(TAG, "Battery optimization settings not available on Android < 6.0")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening battery optimization settings", e)
            // Fallback: abrir configurações gerais do app
            openAppSettings(context)
        }
    }

    /**
     * Abre as configurações gerais do aplicativo.
     * Fallback quando não é possível abrir configurações específicas.
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = "package:${context.packageName}".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened app settings")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app settings", e)
        }
    }

    /**
     * Retorna uma mensagem explicativa sobre por que a permissão POST_NOTIFICATIONS é necessária.
     */
    fun getNotificationPermissionRationale(): String {
        return """
            O Pillora precisa da permissão de notificações para:
            
            • Lembrá-lo de tomar seus medicamentos no horário correto
            • Alertá-lo sobre consultas e vacinas agendadas
            • Enviar lembretes importantes sobre suas receitas
            
            Sem essa permissão, você não receberá nenhum alerta do aplicativo.
        """.trimIndent()
    }

    /**
     * Retorna uma mensagem explicativa sobre por que a permissão SCHEDULE_EXACT_ALARM é necessária.
     */
    fun getExactAlarmPermissionRationale(): String {
        return """
            O Pillora precisa da permissão de alarmes exatos para:
            
            • Garantir que os lembretes de medicamentos toquem no horário EXATO
            • Evitar atrasos que podem comprometer seu tratamento
            • Funcionar corretamente mesmo com economia de bateria ativada
            
            Esta permissão é ESSENCIAL para medicamentos que exigem horários precisos.
            
            Consultas e vacinas não precisam de alarmes exatos e continuarão funcionando normalmente.
        """.trimIndent()
    }

    /**
     * Retorna uma mensagem explicativa sobre por que desativar a otimização de bateria é necessário.
     */
    fun getBatteryOptimizationRationale(): String {
        return """
            O Pillora precisa estar SEM RESTRIÇÕES de bateria para:
            
            • Garantir que os alarmes toquem mesmo com o app em segundo plano
            • Evitar que o sistema mate o app durante economia de bateria
            • Funcionar corretamente 24 horas por dia, 7 dias por semana
            
            Esta configuração é ESSENCIAL para que os lembretes funcionem corretamente.
            
            Vá em: Informações do app > Bateria > Selecione "Sem restrição"
        """.trimIndent()
    }

    /**
     * Retorna true se o dispositivo está no Android 12+ e precisa da permissão SCHEDULE_EXACT_ALARM.
     */
    fun needsExactAlarmPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    /**
     * Retorna true se o dispositivo está no Android 13+ e precisa da permissão POST_NOTIFICATIONS.
     */
    fun needsNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    /**
     * Retorna true se o dispositivo está no Android 6.0+ e precisa desativar otimização de bateria.
     */
    fun needsBatteryOptimizationCheck(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
}
