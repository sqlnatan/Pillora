package com.pillora.pillora.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.pillora.pillora.data.local.AppDatabase
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.utils.AlarmScheduler // Criaremos este helper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar

class HandleNotificationActionWorker(val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val lembreteId = inputData.getLong(NotificationWorker.EXTRA_LEMBRETE_ID, -1L)
        val actionType = inputData.getString("ACTION_TYPE")

        Log.d("HandleActionWorker", "Processando ação '$actionType' para lembreteId: $lembreteId")

        if (lembreteId == -1L || actionType == null) {
            Log.e("HandleActionWorker", "Dados inválidos para processar ação.")
            return@withContext Result.failure()
        }

        val lembreteDao = AppDatabase.getDatabase(applicationContext).lembreteDao()
        val lembrete = lembreteDao.getLembreteById(lembreteId)

        if (lembrete == null) {
            Log.e("HandleActionWorker", "Lembrete não encontrado para ação: $lembreteId")
            return@withContext Result.failure()
        }

        when (actionType) {
            NotificationWorker.ACTION_MEDICAMENTO_TOMADO -> {
                try {
                    // 1. Atualizar Firestore (estoque)
                    val firestore = FirebaseFirestore.getInstance()
                    val medicineDocRef = firestore.collection("medicines").document(lembrete.medicamentoId)
                    val medicineSnapshot = medicineDocRef.get().await()
                    val medicine = medicineSnapshot.toObject(Medicine::class.java)

                    if (medicine?.stockQuantity != null && medicine.dose != null && medicine.dose > 0) {
                        val novaQuantidade = medicine.stockQuantity - medicine.dose // Assumindo que Lembrete.dose é string e Medicine.doseQuantity é o numérico
                        medicineDocRef.update("stockQuantity", novaQuantidade).await()
                        Log.d("HandleActionWorker", "Estoque do medicamento ${lembrete.medicamentoId} atualizado para $novaQuantidade")

                        // TODO: Verificar se o estoque ficou baixo e agendar notificação de estoque baixo
                        // if (novaQuantidade <= LIMITE_ESTOQUE_BAIXO) { ... agendar alerta de estoque ... }
                    }

                    // 2. Atualizar Lembrete no Room e Reagendar
                    // Esta lógica de reagendamento é complexa e depende se é diário, dias da semana, etc.
                    // Vamos simplificar por agora: se for diário, adiciona 1 dia.
                    val proximaOcorrencia = Calendar.getInstance().apply {
                        timeInMillis = lembrete.proximaOcorrenciaMillis
                        add(Calendar.DAY_OF_MONTH, 1) // Simplificação: assume lembrete diário
                        // TODO: Implementar lógica correta de reagendamento com base em lembrete.diasDaSemana etc.
                    }.timeInMillis

                    lembrete.proximaOcorrenciaMillis = proximaOcorrencia
                    lembreteDao.updateLembrete(lembrete)
                    Log.d("HandleActionWorker", "Lembrete ${lembrete.id} atualizado. Próxima ocorrência: $proximaOcorrencia")

                    // 3. Reagendar com AlarmManager
                    AlarmScheduler.scheduleAlarm(applicationContext, lembrete) // Usaremos o helper
                    Log.d("HandleActionWorker", "Próximo alarme para lembrete ${lembrete.id} reagendado.")

                } catch (e: Exception) {
                    Log.e("HandleActionWorker", "Erro ao processar AÇÃO_MEDICAMENTO_TOMADO", e)
                    return@withContext Result.failure()
                }
            }
            // Adicionar outros cases para ACTION_MEDICAMENTO_NAO_TOMADO, etc.
        }
        return@withContext Result.success()
    }
}
