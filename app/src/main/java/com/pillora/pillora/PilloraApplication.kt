package com.pillora.pillora

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PilloraApplication : Application() {

    companion object {
        const val CHANNEL_LEMBRETES_MEDICAMENTOS_ID = "lembretes_medicamentos_channel"
        const val CHANNEL_ALERTAS_ESTOQUE_ID = "alertas_estoque_channel"
        const val CHANNEL_LEMBRETES_CONSULTAS_ID = "lembretes_consultas_channel"
        const val CHANNEL_LEMBRETES_VACINAS_ID = "lembretes_vacinas_channel"
        const val CHANNEL_ALERTAS_RECEITAS_ID = "alertas_receitas_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canalLembretesMed = NotificationChannel(
                CHANNEL_LEMBRETES_MEDICAMENTOS_ID,
                "Lembretes de Medicamentos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações para tomar seus medicamentos."
                // Você pode customizar mais, como som, vibração, etc.
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
                "Lembretes de Consultas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lembretes para suas consultas médicas."
            }

            val canalLembretesVacinas = NotificationChannel(
                CHANNEL_LEMBRETES_VACINAS_ID,
                "Lembretes de Vacinas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lembretes para suas vacinas agendadas."
            }

            val canalAlertasReceitas = NotificationChannel(
                CHANNEL_ALERTAS_RECEITAS_ID,
                "Alertas de Receitas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos sobre vencimento ou expiração de receitas."
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canalLembretesMed)
            manager.createNotificationChannel(canalAlertasEstoque)
            manager.createNotificationChannel(canalLembretesConsultas)
            manager.createNotificationChannel(canalLembretesVacinas)
            manager.createNotificationChannel(canalAlertasReceitas)
        }
    }
}
