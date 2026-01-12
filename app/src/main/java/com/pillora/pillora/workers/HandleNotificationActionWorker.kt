package com.pillora.pillora.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkManager // *** CORRIGIDO: Import adicionado ***
import androidx.work.WorkerParameters
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.pillora.pillora.data.local.AppDatabase
import com.pillora.pillora.model.Lembrete
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.repository.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class HandleNotificationActionWorker(val appContext: Context, workerParams: WorkerParameters) : // *** CORRIGIDO: appContext adicionado ***
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val lembreteId = inputData.getLong(NotificationWorker.EXTRA_LEMBRETE_ID, -1L)
        val medicamentoId = inputData.getString(NotificationWorker.EXTRA_MEDICAMENTO_ID)
        val consultaId = inputData.getString(NotificationWorker.EXTRA_CONSULTA_ID)
        val vacinaId = inputData.getString(NotificationWorker.EXTRA_VACINA_ID)
        val recipeId = inputData.getString("EXTRA_RECIPE_ID") // Vem do NotificationActionReceiver
        val actionType = inputData.getString("ACTION_TYPE")

        Log.d("HandleActionWorker", "Processando ação 	\'$actionType\'	 para lembreteId: $lembreteId, medId: $medicamentoId, consultaId: $consultaId, vacinaId: $vacinaId, recipeId: $recipeId")

        if (lembreteId == -1L || actionType == null) {
            Log.e("HandleActionWorker", "Dados inválidos para processar ação (lembreteId ou actionType nulos).")
            return@withContext Result.failure()
        }

        val lembreteDao = AppDatabase.getDatabase(applicationContext).lembreteDao()
        val lembrete = lembreteDao.getLembreteById(lembreteId)

        // Permitir exclusão de receita mesmo se o lembrete local sumir, mas logar aviso para outros casos
        if (lembrete == null && actionType != NotificationWorker.ACTION_RECEITA_CONFIRMADA_EXCLUIR) {
            Log.w("HandleActionWorker", "Lembrete não encontrado no banco de dados local para o ID: $lembreteId. Pode já ter sido processado.")
        }

        try {
            when (actionType) {
                NotificationWorker.ACTION_MEDICAMENTO_TOMADO -> {
                    if (medicamentoId == null) {
                        Log.e("HandleActionWorker", "ID do Medicamento inválido para ACTION_MEDICAMENTO_TOMADO.")
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
                            Log.d("HandleActionWorker", "Tentando extrair dose numérica de 	\'$doseString\'	, resultado: $doseNumerica")
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
                                Log.w("HandleActionWorker", "Não foi possível extrair um valor numérico válido da dose do medicamento 	\'$doseString\'	 para subtrair do estoque. MedId: $medicamentoId.")
                            }
                        } else {
                            Log.d("HandleActionWorker", "Rastreamento de estoque (trackStock) desativado para o medicamento $medicamentoId. Estoque não atualizado.")
                        }
                    }
                    // NÃO desativar o lembrete para medicamentos recorrentes!
                    // O reagendamento automático já cuida de agendar o próximo alarme.
                    // Desativar aqui impediria que o medicamento toque novamente.
                    Log.d("HandleActionWorker", "Estoque atualizado para medicamento $medicamentoId. Lembrete $lembreteId permanece ativo para próximas ocorrências.")
                }

                NotificationWorker.ACTION_CONSULTA_COMPARECEU -> {
                    if (consultaId == null) {
                        Log.e("HandleActionWorker", "Erro: ID da Consulta (consultaId) é nulo para ACTION_CONSULTA_COMPARECEU.")
                        return@withContext Result.failure()
                    }
                    val userId = Firebase.auth.currentUser?.uid
                    if (userId == null) {
                        Log.e("HandleActionWorker", "Erro: Usuário não autenticado no Firebase.")
                        return@withContext Result.failure()
                    }
                    val firestore = FirebaseFirestore.getInstance()
                    val consultationRef = firestore.collection("users").document(userId)
                        .collection("consultations").document(consultaId)
                    Log.i("HandleActionWorker", "Tentando excluir consulta no Firestore. Path: ${consultationRef.path}")
                    consultationRef.delete().await()
                    Log.i("HandleActionWorker", "Consulta $consultaId excluída do Firestore com SUCESSO.")
                    // Marcar lembrete como inativo APENAS se ele existir
                    lembrete?.let { lembreteDao.updateLembrete(it.copy(ativo = false)) }
                    Log.d("HandleActionWorker", "Lembrete $lembreteId (consulta) marcado como inativo, se encontrado.")
                }

                NotificationWorker.ACTION_CONSULTA_REMARCAR -> {
                    // Apenas marcar lembrete como inativo
                    lembrete?.let { lembreteDao.updateLembrete(it.copy(ativo = false)) }
                    Log.d("HandleActionWorker", "Lembrete $lembreteId (consulta remarcar) marcado como inativo, se encontrado.")
                }

                NotificationWorker.ACTION_VACINA_TOMADA -> {
                    if (vacinaId == null) {
                        Log.e("HandleActionWorker", "Erro: ID da Vacina (vacinaId) é nulo para ACTION_VACINA_TOMADA.")
                        return@withContext Result.failure()
                    }
                    val userId = Firebase.auth.currentUser?.uid
                    if (userId == null) {
                        Log.e("HandleActionWorker", "Erro: Usuário não autenticado no Firebase.")
                        return@withContext Result.failure()
                    }
                    val firestore = FirebaseFirestore.getInstance()
                    val vaccineRef = firestore.collection("users").document(userId)
                        .collection("vaccines").document(vacinaId)
                    Log.i("HandleActionWorker", "Tentando excluir vacina no Firestore. Path: ${vaccineRef.path}")
                    vaccineRef.delete().await()
                    Log.i("HandleActionWorker", "Vacina $vacinaId excluída do Firestore com SUCESSO.")
                    // Marcar lembrete como inativo APENAS se ele existir
                    lembrete?.let { lembreteDao.updateLembrete(it.copy(ativo = false)) }
                    Log.d("HandleActionWorker", "Lembrete $lembreteId (vacina) marcado como inativo, se encontrado.")
                }

                NotificationWorker.ACTION_VACINA_REMARCAR -> {
                    // Apenas marcar lembrete como inativo
                    lembrete?.let { lembreteDao.updateLembrete(it.copy(ativo = false)) }
                    Log.d("HandleActionWorker", "Lembrete $lembreteId (vacina remarcar) marcado como inativo, se encontrado.")
                }

                NotificationWorker.ACTION_RECEITA_CONFIRMADA_EXCLUIR -> {
                    if (recipeId == null) {
                        Log.e("HandleActionWorker", "Erro: ID da Receita (recipeId) é nulo para ACTION_RECEITA_CONFIRMADA_EXCLUIR.")
                        return@withContext Result.failure()
                    }
                    val userId = Firebase.auth.currentUser?.uid
                    if (userId == null) {
                        Log.e("HandleActionWorker", "Erro: Usuário não autenticado no Firebase.")
                        return@withContext Result.failure()
                    }

                    // 1. Excluir a receita do Firestore
                    // Usando viewModelScope aqui pode não ser ideal, mas para simplificar:
                    RecipeRepository.deleteRecipe(recipeId, onSuccess = {
                        Log.i("HandleActionWorker", "Receita $recipeId excluída do Firestore com SUCESSO.")
                    }, onError = { e ->
                        Log.e("HandleActionWorker", "ERRO ao excluir receita $recipeId do Firestore", e)
                        // Não retornar falha aqui, tentar limpar localmente mesmo assim
                    })

                    // 2. Marcar o lembrete de confirmação como inativo (se existir)
                    lembrete?.let { lembreteDao.updateLembrete(it.copy(ativo = false)) }
                    Log.d("HandleActionWorker", "Lembrete $lembreteId (confirmação receita) marcado como inativo, se encontrado.")

                    // 3. Cancelar e excluir outros lembretes PENDENTES para esta receita
                    val outrosLembretesReceita = lembreteDao.getLembretesByMedicamentoId(recipeId).filter { it.isReceita && it.id != lembreteId }
                    if (outrosLembretesReceita.isNotEmpty()) {
                        Log.d("HandleActionWorker", "Cancelando ${outrosLembretesReceita.size} outros lembretes pendentes para a receita $recipeId.")
                        outrosLembretesReceita.forEach { outroLembrete ->
                            val workTag = "receita_${recipeId}_${outroLembrete.id}"
                            // *** CORRIGIDO: Usar appContext ***
                            WorkManager.getInstance(appContext).cancelAllWorkByTag(workTag)
                            Log.d("HandleActionWorker", "WorkManager job cancelado para tag: $workTag (Lembrete ID: ${outroLembrete.id})")
                        }
                        // *** CORRIGIDO: Usar o método correto do DAO ***
                        lembreteDao.deleteLembretes(outrosLembretesReceita)
                        Log.d("HandleActionWorker", "Excluídos ${outrosLembretesReceita.size} outros lembretes de receita do DB para $recipeId")
                    }
                }

                else -> {
                    Log.w("HandleActionWorker", "ActionType 	\'$actionType\'	 desconhecido para lembreteId $lembreteId")
                }
            }
            return@withContext Result.success()

        } catch (e: Exception) {
            Log.e("HandleActionWorker", "Erro GERAL ao processar ação 	\'$actionType\'	 para lembreteId $lembreteId", e)
            return@withContext Result.failure()
        }
    }
}

