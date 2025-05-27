package com.pillora.pillora.repository

import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query // Import Query for sorting
import com.google.firebase.firestore.snapshots // Import snapshots from main module
import com.google.firebase.firestore.toObject // Import toObject from main module
import com.pillora.pillora.model.Vaccine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf // Import flowOf for returning empty flow
import kotlinx.coroutines.flow.map

@SuppressLint("StaticFieldLeak") // Suppress warning for Firebase context in object
object VaccineRepository {

    private const val TAG = "VaccineRepository" // Convention: UPPER_SNAKE_CASE
    private const val VACCINES_COLLECTION = "vaccines"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // --- CREATE ---
    // <<< MODIFICADO: onSuccess agora recebe o ID (String) >>>
    fun addVaccine(
        vaccine: Vaccine,
        onSuccess: (String) -> Unit, // <<< ALTERADO AQUI
        onFailure: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "User not logged in")
            onFailure(Exception("Usuário não autenticado"))
            return
        }

        // Gerar um novo ID se a vacina ainda não tiver um (importante para consistência)
        // Usar o ID existente se já houver um (caso de re-salvar após erro, etc.)
        val newDocumentRef = if (vaccine.id.isEmpty()) {
            db.collection(VACCINES_COLLECTION).document()
        } else {
            db.collection(VACCINES_COLLECTION).document(vaccine.id)
        }
        val vaccineWithUserIdAndId = vaccine.copy(userId = userId, id = newDocumentRef.id)

        newDocumentRef.set(vaccineWithUserIdAndId) // Salvar o objeto completo com ID
            .addOnSuccessListener {
                Log.d(TAG, "Vaccine reminder added/updated successfully with ID: ${vaccineWithUserIdAndId.id}")
                onSuccess(vaccineWithUserIdAndId.id) // <<< ALTERADO AQUI: Passar o ID
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error adding/updating vaccine reminder", exception)
                onFailure(exception)
            }
    }

    // --- READ (Callback version) ---
    fun getAllVaccines(
        onSuccess: (List<Vaccine>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "User not logged in")
            onFailure(Exception("Usuário não autenticado"))
            return
        }

        db.collection(VACCINES_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("reminderDate", Query.Direction.ASCENDING) // Ordenar por data
            .get()
            .addOnSuccessListener { documents ->
                val vaccines = documents.mapNotNull { doc ->
                    try {
                        doc.toObject<Vaccine>().copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting document ${doc.id} to Vaccine", e)
                        null
                    }
                }
                Log.d(TAG, "Fetched ${vaccines.size} vaccine reminders")
                onSuccess(vaccines)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error getting vaccine reminders", exception)
                onFailure(exception)
            }
    }

    // --- READ (Flow version) ---
    fun getAllVaccinesFlow(): Flow<List<Vaccine>> {
        val userId = currentUserId ?: return flowOf(emptyList())

        return db.collection(VACCINES_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("reminderDate", Query.Direction.ASCENDING) // Ordenar por data
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject<Vaccine>()?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting document ${doc.id} to Vaccine", e)
                        null
                    }
                }
            }
            .catch { exception ->
                Log.e(TAG, "Error in getAllVaccinesFlow", exception)
                emit(emptyList())
            }
    }

    // --- READ by ID ---
    fun getVaccineById(
        vaccineId: String,
        onSuccess: (Vaccine?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "User not logged in")
            onFailure(Exception("Usuário não autenticado"))
            return
        }
        if (vaccineId.isEmpty()) {
            Log.w(TAG, "Vaccine ID is empty, cannot fetch")
            onFailure(IllegalArgumentException("ID do lembrete de vacina está faltando"))
            return
        }

        db.collection(VACCINES_COLLECTION)
            .document(vaccineId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    try {
                        val vaccine = document.toObject<Vaccine>()?.copy(id = document.id)
                        if (vaccine != null && vaccine.userId == userId) {
                            Log.d(TAG, "Fetched vaccine reminder: ${vaccine.id}")
                            onSuccess(vaccine)
                        } else {
                            Log.w(TAG, "User $userId attempted to fetch vaccine reminder ${document.id} belonging to ${vaccine?.userId} or vaccine is null")
                            onSuccess(null) // Não encontrado ou pertence a outro usuário
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting document ${document.id} to Vaccine", e)
                        onFailure(e)
                    }
                } else {
                    Log.d(TAG, "No such vaccine reminder found: $vaccineId")
                    onSuccess(null)
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error getting vaccine reminder by ID", exception)
                onFailure(exception)
            }
    }

    // --- UPDATE ---
    // <<< MODIFICADO: onSuccess agora recebe o ID (String) para consistência >>>
    fun updateVaccine(
        vaccine: Vaccine,
        onSuccess: (String) -> Unit, // <<< ALTERADO AQUI
        onFailure: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (vaccine.id.isEmpty()) {
            Log.w(TAG, "Vaccine ID is empty, cannot update")
            onFailure(IllegalArgumentException("ID do lembrete de vacina está faltando para atualização"))
            return
        }
        // Garantir que o userId da vacina seja o do usuário logado
        if (userId == null || vaccine.userId != userId) {
            Log.w(TAG, "User not logged in or trying to update another user's vaccine reminder")
            onFailure(SecurityException("Erro de autorização ou dados inválidos para atualização"))
            return
        }

        db.collection(VACCINES_COLLECTION)
            .document(vaccine.id)
            .set(vaccine) // Usar set para sobrescrever completamente
            .addOnSuccessListener {
                Log.d(TAG, "Vaccine reminder updated successfully: ${vaccine.id}")
                onSuccess(vaccine.id) // <<< ALTERADO AQUI: Passar o ID
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error updating vaccine reminder", exception)
                onFailure(exception)
            }
    }

    // --- DELETE ---
    fun deleteVaccine(
        vaccineId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "User not logged in")
            onFailure(Exception("Usuário não autenticado"))
            return
        }
        if (vaccineId.isEmpty()) {
            Log.w(TAG, "Vaccine ID is empty, cannot delete")
            onFailure(IllegalArgumentException("ID do lembrete de vacina está faltando para exclusão"))
            return
        }

        // Primeiro, verificar se a vacina pertence ao usuário atual antes de deletar
        getVaccineById(vaccineId, { vaccine ->
            if (vaccine != null) { // Vacina encontrada e pertence ao usuário
                db.collection(VACCINES_COLLECTION)
                    .document(vaccineId)
                    .delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "Vaccine reminder deleted successfully: $vaccineId")
                        onSuccess()
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Error deleting vaccine reminder", exception)
                        onFailure(exception)
                    }
            } else {
                // Vacina não encontrada ou não pertence ao usuário
                Log.w(TAG, "Vaccine reminder $vaccineId not found or access denied for deletion.")
                onFailure(Exception("Lembrete de vacina não encontrado ou acesso negado."))
            }
        }, { exception ->
            // Erro ao tentar buscar a vacina antes de deletar
            Log.e(TAG, "Error checking vaccine ownership before deletion", exception)
            onFailure(exception)
        })
    }
}

