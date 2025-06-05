package com.pillora.pillora

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat

class PilloraApplication : Application() {

    companion object {
        // Canais existentes
        const val CHANNEL_LEMBRETES_MEDICAMENTOS_ID = "lembretes_medicamentos_channel" // Com alarme
        const val CHANNEL_ALERTAS_ESTOQUE_ID = "alertas_estoque_channel" // Padrão
        const val CHANNEL_LEMBRETES_CONSULTAS_ID = "lembretes_consultas_channel" // Padrão (usado para confirmação pós-consulta)
        const val CHANNEL_LEMBRETES_VACINAS_ID = "lembretes_vacinas_channel" // Padrão (usado para confirmação pós-vacina)

        // *** NOVO CANAL PARA RECEITAS (Silencioso/Padrão) ***
        const val CHANNEL_LEMBRETES_RECEITAS_ID = "lembretes_receitas_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal para Lembretes de Medicamentos (com som de alarme)
            val canalLembretesMed = NotificationChannel(
                CHANNEL_LEMBRETES_MEDICAMENTOS_ID,
                "Lembretes de Medicamentos/Consultas/Vacinas", // Nome mais genérico, pois é usado para pré-consulta/vacina também
                NotificationManager.IMPORTANCE_HIGH // Alta importância para aparecer como Heads-up
            ).apply {
                description = "Notificações com alarme para tomar medicamentos ou lembretes prévios de consultas/vacinas."
                setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                enableLights(true)
                lightColor = 0xFF0000FF.toInt() // Azul
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
                // Som e vibração padrão
            }

            // Canal para Confirmações de Consultas (sem som alto)
            val canalLembretesConsultas = NotificationChannel(
                CHANNEL_LEMBRETES_CONSULTAS_ID,
                "Confirmações de Consultas",
                NotificationManager.IMPORTANCE_DEFAULT // Importância padrão é suficiente
            ).apply {
                description = "Notificações para confirmar comparecimento em consultas."
                // Som e vibração padrão
            }

            // Canal para Confirmações de Vacinas (sem som alto)
            val canalLembretesVacinas = NotificationChannel(
                CHANNEL_LEMBRETES_VACINAS_ID,
                "Confirmações de Vacinas",
                NotificationManager.IMPORTANCE_DEFAULT // Importância padrão é suficiente
            ).apply {
                description = "Notificações para confirmar comparecimento em vacinas."
                // Som e vibração padrão
            }

            // *** NOVO: Canal para Lembretes de Receitas (sem som alto) ***
            val canalLembretesReceitas = NotificationChannel(
                CHANNEL_LEMBRETES_RECEITAS_ID,
                "Lembretes de Receitas",
                NotificationManager.IMPORTANCE_DEFAULT // Importância padrão, sem alarme
            ).apply {
                description = "Avisos sobre vencimento ou confirmação de compra de receitas."
                // Som e vibração padrão (sem som de alarme)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canalLembretesMed)
            manager.createNotificationChannel(canalAlertasEstoque)
            manager.createNotificationChannel(canalLembretesConsultas)
            manager.createNotificationChannel(canalLembretesVacinas)
            manager.createNotificationChannel(canalLembretesReceitas) // Registrar o novo canal
        }
    }
}

