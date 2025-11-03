package com.pillora.pillora

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pillora.pillora.data.local.AppDatabase
import com.pillora.pillora.repository.MedicineRepository

class PilloraApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val medicineRepository by lazy { MedicineRepository }

    companion object {
        // Canais existentes
        const val CHANNEL_LEMBRETES_MEDICAMENTOS_ID = "lembretes_medicamentos_channel"
        const val CHANNEL_ALERTAS_ESTOQUE_ID = "alertas_estoque_channel"
        const val CHANNEL_LEMBRETES_CONSULTAS_ID = "lembretes_consultas_channel"
        const val CHANNEL_LEMBRETES_VACINAS_ID = "lembretes_vacinas_channel"
        const val CHANNEL_LEMBRETES_RECEITAS_ID = "lembretes_receitas_channel"

        // Novos canais para o NotificationWorker
        const val CHANNEL_LEMBRETES_SONORO_ID = "lembretes_sonoro"
        const val CHANNEL_LEMBRETES_SILENCIOSO_ID = "lembretes_silencioso"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // 🔔 Canal sonoro (permite som personalizado via Worker)
            val canalSonoro = NotificationChannel(
                CHANNEL_LEMBRETES_SONORO_ID,
                "Lembretes com Som",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações com som e vibração personalizável."
                setSound(null, null) // ❗ som será definido no NotificationWorker
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // 🤫 Canal silencioso
            val canalSilencioso = NotificationChannel(
                CHANNEL_LEMBRETES_SILENCIOSO_ID,
                "Lembretes Silenciosos",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificações sem som nem vibração."
                setSound(null, null)
                enableVibration(false)
            }

            // 🧠 Canais antigos (mantidos)
            val canalLembretesMed = NotificationChannel(
                CHANNEL_LEMBRETES_MEDICAMENTOS_ID,
                "Lembretes de Medicamentos/Consultas/Vacinas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações com alarme para tomar medicamentos ou lembretes prévios."
                setSound(null, null)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val canalAlertasEstoque = NotificationChannel(
                CHANNEL_ALERTAS_ESTOQUE_ID,
                "Alertas de Estoque",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos sobre estoque baixo ou fim de tratamento."
            }

            val canalLembretesConsultas = NotificationChannel(
                CHANNEL_LEMBRETES_CONSULTAS_ID,
                "Confirmações de Consultas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações para confirmar comparecimento em consultas."
            }

            val canalLembretesVacinas = NotificationChannel(
                CHANNEL_LEMBRETES_VACINAS_ID,
                "Confirmações de Vacinas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações para confirmar comparecimento em vacinas."
            }

            val canalLembretesReceitas = NotificationChannel(
                CHANNEL_LEMBRETES_RECEITAS_ID,
                "Lembretes de Receitas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos sobre vencimento ou confirmação de compra de receitas."
            }

            // Registrar todos os canais
            manager.createNotificationChannel(canalSonoro)
            manager.createNotificationChannel(canalSilencioso)
            manager.createNotificationChannel(canalLembretesMed)
            manager.createNotificationChannel(canalAlertasEstoque)
            manager.createNotificationChannel(canalLembretesConsultas)
            manager.createNotificationChannel(canalLembretesVacinas)
            manager.createNotificationChannel(canalLembretesReceitas)
        }
    }
}
