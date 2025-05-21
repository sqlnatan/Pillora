package com.pillora.pillora.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.pillora.pillora.data.local.AppDatabase
import com.pillora.pillora.model.Lembrete
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.utils.AlarmScheduler // Para cancelar, se necessário no futuro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class HandleNotificationActionWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val lembreteId = inputData.getLong(NotificationWorker.EXTRA_LEMBRETE_ID, -1L)
        val medicamentoId = inputData.getString(NotificationWorker.EXTRA_MEDICAMENTO_ID) // Recebe o ID do Medicamento
        val actionType = inputData.getString("ACTION_TYPE") // Vem do NotificationActionReceiver

        Log.d("HandleActionWorker", "Processando ação '$actionType' para lembreteId: $lembreteId, medicamentoId: $medicamentoId")

        if (lembreteId == -1L || medicamentoId == null || actionType == null) {
            Log.e("HandleActionWorker", "Dados inválidos para processar ação (lembreteId, medicamentoId ou actionType nulos).")
            return@withContext Result.failure()
        }

        val lembreteDao = AppDatabase.getDatabase(applicationContext).lembreteDao()
        val lembrete = lembreteDao.getLembreteById(lembreteId)

        if (lembrete == null) {
            Log.w("HandleActionWorker", "Lembrete não encontrado no banco de dados local para o ID: $lembreteId. Pode já ter sido processado.")
            // Mesmo que o lembrete local tenha sido deletado (ex: edição rápida), tentamos atualizar o estoque se a ação for TOMADO
        }

        when (actionType) {
            NotificationWorker.ACTION_MEDICAMENTO_TOMADO -> {
                try {
                    val userId = Firebase.auth.currentUser?.uid
                    if (userId == null) {
                        Log.e("HandleActionWorker", "Usuário não logado. Não é possível atualizar o estoque.")
                        return@withContext Result.failure()
                    }

                    val firestore = FirebaseFirestore.getInstance()
                    val medicineDocRef = firestore.collection("users").document(userId)
                        .collection("medicines").document(medicamentoId) // Usa o medicamentoId recebido

                    val medicineSnapshot = medicineDocRef.get().await()
                    val medicine = medicineSnapshot.toObject(Medicine::class.java)

                    if (medicine == null) {
                        Log.e("HandleActionWorker", "Medicamento não encontrado no Firestore para o ID: $medicamentoId")
                        // Não podemos atualizar o estoque se o medicamento não for encontrado
                    } else {
                        if (medicine.trackStock) {
                            // CORREÇÃO: Extrair apenas o valor numérico da dose, independente do formato
                            // Isso garante que funcione tanto para "1" quanto para "1 Cápsula" ou "10 ml"
                            val doseString = medicine.dose // Ex: "1", "0.5", "1 Cápsula", "10 ml"

                            // Extrair apenas os dígitos e pontos/vírgulas do início da string
                            val doseNumerica = doseString.replace(",", ".")
                                .trim()
                                .split(" ").firstOrNull() // Pega apenas a primeira parte antes de qualquer espaço
                                ?.toDoubleOrNull()

                            Log.d("HandleActionWorker", "Tentando extrair dose numérica de '$doseString', resultado: $doseNumerica")
                            Log.e("PILLORA_DEBUG", "Tentando extrair dose de: '${medicine.dose}', resultado: $doseNumerica")

                            if (doseNumerica != null && doseNumerica > 0) {
                                if (medicine.stockQuantity >= doseNumerica) {
                                    val novaQuantidade = medicine.stockQuantity - doseNumerica
                                    medicineDocRef.update("stockQuantity", novaQuantidade).await()
                                    Log.d("HandleActionWorker", "Estoque do medicamento $medicamentoId atualizado para $novaQuantidade. Dose tomada: $doseNumerica")
                                    Log.e("PILLORA_DEBUG", "Estoque atualizado: anterior=${medicine.stockQuantity}, dose=$doseNumerica, novo=$novaQuantidade")

                                } else {
                                    Log.w("HandleActionWorker", "Dose ($doseNumerica) é maior que o estoque (${medicine.stockQuantity}). Estoque não atualizado para medId: $medicamentoId.")
                                    // Opcional: Atualizar para 0 ou notificar usuário sobre estoque insuficiente
                                    medicineDocRef.update("stockQuantity", 0.0).await() // Exemplo: Zera o estoque
                                }
                            } else {
                                Log.w("HandleActionWorker", "Não foi possível extrair um valor numérico válido da dose do medicamento '$doseString' para subtrair do estoque. MedId: $medicamentoId.")
                            }
                        } else {
                            Log.d("HandleActionWorker", "Rastreamento de estoque (trackStock) desativado para o medicamento $medicamentoId. Estoque não atualizado.")
                        }
                    }

                    // Marcar o lembrete específico como inativo (ou deletar)
                    // para que ele não dispare novamente e não apareça como pendente.
                    if (lembrete != null) {
                        lembreteDao.updateLembrete(lembrete.copy(ativo = false))
                        Log.d("HandleActionWorker", "Lembrete $lembreteId marcado como inativo.")
                        // Não é necessário cancelar o alarme aqui, pois ele já disparou.
                        // O AlarmScheduler só agenda alarmes para lembretes ativos e com proximaOcorrenciaMillis no futuro.
                        // A criação de lembretes futuros (para "a cada X horas" ou próximos dias) já é feita no MedicineFormScreen.
                    }

                } catch (e: Exception) {
                    Log.e("HandleActionWorker", "Erro ao processar AÇÃO_MEDICAMENTO_TOMADO para lembreteId $lembreteId, medicamentoId $medicamentoId", e)
                    return@withContext Result.failure()
                }
            }
            // Adicionar outros cases de ação aqui se necessário (ex: Adiar)
            else -> {
                Log.w("HandleActionWorker", "ActionType '$actionType' desconhecido para lembreteId $lembreteId")
            }
        }
        return@withContext Result.success()
    }
}
