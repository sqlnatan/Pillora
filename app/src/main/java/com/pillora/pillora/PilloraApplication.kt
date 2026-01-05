package com.pillora.pillora

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pillora.pillora.data.local.AppDatabase
import com.pillora.pillora.repository.MedicineRepository
import com.pillora.pillora.repository.BillingRepository
import com.pillora.pillora.viewmodel.ReportsViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.pillora.pillora.data.UserPreferences

/**
 * Application class do Pillora.
 *
 * ATUALIZADO: Inicializa BillingRepository como fonte única da verdade para Premium.
 */
class PilloraApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val medicineRepository by lazy { MedicineRepository }
    val userPreferences by lazy { UserPreferences(this) }

    // 💳 NOVO: BillingRepository para gerenciar assinaturas Premium
    lateinit var billingRepository: BillingRepository
        private set

    val reportsViewModelFactory by lazy {
        val currentUserId = Firebase.auth.currentUser?.uid
        ReportsViewModel.provideFactory(this, userPreferences, currentUserId)
    }

    companion object {
        // Canais existentes
        const val CHANNEL_LEMBRETES_MEDICAMENTOS_ID = "lembretes_medicamentos_channel"
        const val CHANNEL_ALERTAS_ESTOQUE_ID = "alertas_estoque_channel"
        const val CHANNEL_LEMBRETES_CONSULTAS_ID = "lembretes_consultas_channel"
        const val CHANNEL_LEMBRETES_VACINAS_ID = "lembretes_vacinas_channel"
        const val CHANNEL_LEMBRETES_RECEITAS_ID = "lembretes_receitas_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // 💳 NOVO: Inicializar BillingRepository
        // IMPORTANTE: BillingRepository é a FONTE ÚNICA DA VERDADE para status Premium
        billingRepository = BillingRepository(this, userPreferences)
    }

    override fun onTerminate() {
        // 💳 NOVO: Liberar recursos do BillingRepository
        if (::billingRepository.isInitialized) {
            billingRepository.endConnection()
        }
        super.onTerminate()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal para Lembretes de Medicamentos (com som de alarme)
            val canalLembretesMed = NotificationChannel(
                CHANNEL_LEMBRETES_MEDICAMENTOS_ID,
                "Alarmes de Medicamentos / Consultas / Vacinas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "Notificações com som de alarme para medicamentos ou lembretes prévios de consultas/vacinas."

                val alarmSound = android.net.Uri.parse(
                    "android.resource://${packageName}/${R.raw.alarme}"
                )

                val attributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                setSound(alarmSound, attributes)

                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                enableLights(true)
                lightColor = 0xFFFF0000.toInt()
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // Canal para Alertas de Estoque (sem som alto)
            val canalAlertasEstoque = NotificationChannel(
                CHANNEL_ALERTAS_ESTOQUE_ID,
                "Alertas de Estoque",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos sobre estoque baixo ou fim de tratamento."
            }

            // Canal para Confirmações de Consultas (sem som alto)
            val canalLembretesConsultas = NotificationChannel(
                CHANNEL_LEMBRETES_CONSULTAS_ID,
                "Confirmações de Consultas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações para confirmar comparecimento em consultas."
            }

            // Canal para Confirmações de Vacinas (sem som alto)
            val canalLembretesVacinas = NotificationChannel(
                CHANNEL_LEMBRETES_VACINAS_ID,
                "Confirmações de Vacinas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações para confirmar comparecimento em vacinas."
            }

            // Canal para Lembretes de Receitas (sem som alto)
            val canalLembretesReceitas = NotificationChannel(
                CHANNEL_LEMBRETES_RECEITAS_ID,
                "Lembretes de Receitas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos sobre vencimento ou confirmação de compra de receitas."
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canalLembretesMed)
            manager.createNotificationChannel(canalAlertasEstoque)
            manager.createNotificationChannel(canalLembretesConsultas)
            manager.createNotificationChannel(canalLembretesVacinas)
            manager.createNotificationChannel(canalLembretesReceitas)
        }
    }
}
