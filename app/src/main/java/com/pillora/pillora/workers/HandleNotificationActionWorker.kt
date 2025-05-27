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
import com.pillora.pillora.repository.VaccineRepository // Importar para usar constantes se necessário
import com.pillora.pillora.utils.AlarmScheduler // Para cancelar alarmes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class HandleNotificationActionWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val lembreteId = inputData.getLong(NotificationWorker.EXTRA_LEMBRETE_ID, -1L)
        val medicamentoId = inputData.getString(NotificationWorker.EXTRA_MEDICAMENTO_ID)
        val consultaId = inputData.getString(NotificationWorker.EXTRA_CONSULTA_ID)
        val vacinaId = inputData.getString(NotificationWorker.EXTRA_VACINA_ID) ?: medicamentoId // Vacina usa medicamentoId ou EXTRA_VACINA_ID
        val actionType = inputData.getString("ACTION_TYPE") // Vem do NotificationActionReceiver

        Log.d("HandleActionWorker", "Processando ação '$actionType' para lembreteId: $lembreteId, medId: $medicamentoId, consultaId: $consultaId, vacinaId: $vacinaId")

        if (lembreteId == -1L || actionType == null) {
            Log.e("HandleActionWorker", "Dados inválidos para processar ação (lembreteId ou actionType nulos).")
            return@withContext Result.failure()
        }

        val lembreteDao = AppDatabase.getDatabase(applicationContext).lembreteDao()
        val lembrete = lembreteDao.getLembreteById(lembreteId)

        if (lembrete == null && actionType != NotificationWorker.ACTION_VACINA_TOMADA) {
            // Para ACTION_VACINA_TOMADA, podemos prosseguir mesmo sem lembrete local, pois a ação principal é no Firestore
            Log.w("HandleActionWorker", "Lembrete não encontrado no banco de dados local para o ID: $lembreteId. Pode já ter sido processado.")
            // Se não for exclusão de vacina, falhar se o lembrete não existir
            if (actionType != NotificationWorker.ACTION_VACINA_TOMADA) {
                return@withContext Result.failure()
            }
        }

        when (actionType) {
            NotificationWorker.ACTION_MEDICAMENTO_TOMADO -> {
                try {
                    if (medicamentoId == null) {
                        Log.e("HandleActionWorker", "ID do Medicamento inválido.")
                        return@withContext Result.failure()
                    }

                    val userId = Firebase.auth.currentUser?.uid
                    if (userId == null) {
                        Log.e("HandleActionWorker", "Usuário não logado. Não é possível atualizar o estoque.")
                        return@withContext Result.failure()
                    }

                    val firestore = FirebaseFirestore.getInstance()
                    val medicineDocRef = firestore.collection("users").document(userId)
                        .collection("medicines").document(medicamentoId)

                    val medicineSnapshot = medicineDocRef.get().await()
                    val medicine = medicineSnapshot.toObject(Medicine::class.java)

                    if (medicine == null) {
                        Log.e("HandleActionWorker", "Medicamento não encontrado no Firestore para o ID: $medicamentoId")
                    } else {
                        if (medicine.trackStock) {
                            val doseString = medicine.dose
                            val doseNumerica = doseString.replace(",", ".")
                                .trim()
                                .split(" ").firstOrNull()
                                ?.toDoubleOrNull()

                            Log.d("HandleActionWorker", "Tentando extrair dose numérica de '$doseString', resultado: $doseNumerica")

                            if (doseNumerica != null && doseNumerica > 0) {
                                if (medicine.stockQuantity >= doseNumerica) {
                                    val novaQuantidade = medicine.stockQuantity - doseNumerica
                                    medicineDocRef.update("stockQuantity", novaQuantidade).await()
                                    Log.d("HandleActionWorker", "Estoque do medicamento $medicamentoId atualizado para $novaQuantidade. Dose tomada: $doseNumerica")
                                } else {
                                    Log.w("HandleActionWorker", "Dose ($doseNumerica) é maior que o estoque (${medicine.stockQuantity}). Estoque não atualizado para medId: $medicamentoId.")
                                    medicineDocRef.update("stockQuantity", 0.0).await()
                                }
                            } else {
                                Log.w("HandleActionWorker", "Não foi possível extrair um valor numérico válido da dose do medicamento '$doseString' para subtrair do estoque. MedId: $medicamentoId.")
                            }
                        } else {
                            Log.d("HandleActionWorker", "Rastreamento de estoque desativado para o medicamento $medicamentoId. Estoque não atualizado.")
                        }
                    }

                    if (lembrete != null) {
                        lembreteDao.updateLembrete(lembrete.copy(ativo = false))
                        Log.d("HandleActionWorker", "Lembrete $lembreteId marcado como inativo.")
                    }

                } catch (e: Exception) {
                    Log.e("HandleActionWorker", "Erro ao processar AÇÃO_MEDICAMENTO_TOMADO para lembreteId $lembreteId, medicamentoId $medicamentoId", e)
                    return@withContext Result.failure()
                }
            }

            NotificationWorker.ACTION_CONSULTA_COMPARECEU -> {
                Log.d("HandleActionWorker", "Iniciando processamento de ACTION_CONSULTA_COMPARECEU para consultaId: $consultaId")
                try {
                    if (consultaId == null) {
                        Log.e("HandleActionWorker", "Erro: ID da Consulta (consultaId) é nulo.")
                        return@withContext Result.failure()
                    }

                    val userId = Firebase.auth.currentUser?.uid
                    if (userId == null) {
                        Log.e("HandleActionWorker", "Erro: Usuário não autenticado no Firebase.")
                        return@withContext Result.failure()
                    }
                    Log.d("HandleActionWorker", "Usuário autenticado: $userId")

                    val firestore = FirebaseFirestore.getInstance()
                    val consultationRef = firestore.collection("users").document(userId)
                        .collection("consultations").document(consultaId)

                    Log.i("HandleActionWorker", "Tentando excluir consulta no Firestore. Path: ${consultationRef.path}, UserID: $userId, ConsultaID: $consultaId")

                    try {
                        consultationRef.delete().await()
                        Log.i("HandleActionWorker", "Consulta $consultaId excluída do Firestore com SUCESSO.")
                    } catch (firestoreError: Exception) {
                        Log.e("HandleActionWorker", "ERRO ao excluir consulta $consultaId do Firestore", firestoreError)
                        return@withContext Result.failure()
                    }

                    // Excluir todos os lembretes locais associados a esta consulta
                    try {
                        val lembretesExcluidos = lembreteDao.deleteLembretesByMedicamentoId(consultaId) // Consulta ID é armazenado em medicamentoId
                        Log.d("HandleActionWorker", "Excluídos $lembretesExcluidos lembretes locais para consulta $consultaId.")
                    } catch (dbError: Exception) {
                        Log.e("HandleActionWorker", "Erro ao excluir lembretes locais para consulta $consultaId", dbError)
                        // Continuar mesmo se houver erro no DB local
                    }

                    Log.d("HandleActionWorker", "Processamento de ACTION_CONSULTA_COMPARECEU concluído com sucesso para consulta $consultaId.")
                    return@withContext Result.success()
                } catch (e: Exception) {
                    Log.e("HandleActionWorker", "Erro GERAL ao processar ACTION_CONSULTA_COMPARECEU para consultaId $consultaId", e)
                    return@withContext Result.failure()
                }
            }

            NotificationWorker.ACTION_CONSULTA_REMARCAR -> {
                // Para remarcar, apenas marcamos o lembrete específico como inativo
                if (lembrete != null) {
                    lembreteDao.updateLembrete(lembrete.copy(ativo = false))
                    Log.d("HandleActionWorker", "Lembrete $lembreteId marcado como inativo para remarcar consulta.")
                }
                return@withContext Result.success()
            }

            // --- NOVAS AÇÕES PARA VACINA ---
            NotificationWorker.ACTION_VACINA_TOMADA -> {
                Log.d("HandleActionWorker", "Iniciando processamento de ACTION_VACINA_TOMADA para vacinaId: $vacinaId")
                try {
                    if (vacinaId == null) {
                        Log.e("HandleActionWorker", "Erro: ID da Vacina (vacinaId) é nulo.")
                        return@withContext Result.failure()
                    }

                    val userId = Firebase.auth.currentUser?.uid
                    if (userId == null) {
                        Log.e("HandleActionWorker", "Erro: Usuário não autenticado no Firebase.")
                        return@withContext Result.failure()
                    }
                    Log.d("HandleActionWorker", "Usuário autenticado: $userId")

                    // Excluir a vacina do Firestore
                    val firestore = FirebaseFirestore.getInstance()
                    val vaccineRef = firestore.collection("users").document(userId)
                        .collection("vaccines").document(vacinaId) // Coleção "vaccines"

                    Log.i("HandleActionWorker", "Tentando excluir vacina no Firestore. Path: ${vaccineRef.path}, UserID: $userId, VacinaID: $vacinaId")

                    try {
                        vaccineRef.delete().await()
                        Log.i("HandleActionWorker", "Vacina $vacinaId excluída do Firestore com SUCESSO.")
                    } catch (firestoreError: Exception) {
                        Log.e("HandleActionWorker", "ERRO ao excluir vacina $vacinaId do Firestore", firestoreError)
                        return@withContext Result.failure()
                    }

                    // Excluir todos os lembretes locais associados a esta vacina
                    try {
                        // Cancelar alarmes associados antes de excluir
                        val lembretesParaExcluir = lembreteDao.getLembretesByMedicamentoId(vacinaId)
                        for (lembreteAntigo in lembretesParaExcluir) {
                            AlarmScheduler.cancelAlarm(applicationContext, lembreteAntigo.id)
                            Log.d("HandleActionWorker", "Alarme cancelado para lembrete de vacina ID: ${lembreteAntigo.id}")
                        }
                        // Excluir lembretes do DB
                        val lembretesExcluidos = lembreteDao.deleteLembretesByMedicamentoId(vacinaId) // Vacina ID é armazenado em medicamentoId
                        Log.d("HandleActionWorker", "Excluídos $lembretesExcluidos lembretes locais para vacina $vacinaId.")
                    } catch (dbError: Exception) {
                        Log.e("HandleActionWorker", "Erro ao excluir/cancelar lembretes locais para vacina $vacinaId", dbError)
                        // Continuar mesmo se houver erro no DB local
                    }

                    Log.d("HandleActionWorker", "Processamento de ACTION_VACINA_TOMADA concluído com sucesso para vacina $vacinaId.")
                    return@withContext Result.success()
                } catch (e: Exception) {
                    Log.e("HandleActionWorker", "Erro GERAL ao processar ACTION_VACINA_TOMADA para vacinaId $vacinaId", e)
                    return@withContext Result.failure()
                }
            }

            NotificationWorker.ACTION_VACINA_REMARCAR -> {
                // Para remarcar, apenas marcamos o lembrete específico como inativo
                // A navegação para a tela de edição é feita pelo PendingIntent na notificação
                if (lembrete != null) {
                    try {
                        lembreteDao.updateLembrete(lembrete.copy(ativo = false))
                        Log.d("HandleActionWorker", "Lembrete $lembreteId marcado como inativo para remarcar vacina.")
                        // Cancelar o alarme/notificação associado a este lembrete específico
                        AlarmScheduler.cancelAlarm(applicationContext, lembrete.id)
                        Log.d("HandleActionWorker", "Alarme/Notificação cancelado para lembrete ID: ${lembrete.id}")
                    } catch (e: Exception) {
                        Log.e("HandleActionWorker", "Erro ao marcar lembrete $lembreteId como inativo ou cancelar alarme", e)
                        return@withContext Result.failure()
                    }
                } else {
                    Log.w("HandleActionWorker", "Lembrete $lembreteId não encontrado para marcar como inativo (remarcar vacina).")
                    // Não retornar falha aqui, pois a navegação pode ocorrer mesmo assim
                }
                return@withContext Result.success()
            }
            // --- FIM DAS NOVAS AÇÕES PARA VACINA ---

            else -> {
                Log.w("HandleActionWorker", "ActionType '$actionType' desconhecido para lembreteId $lembreteId")
            }
        }
        // Retornar sucesso por padrão se nenhuma falha explícita ocorreu
        return@withContext Result.success()
    }
}

