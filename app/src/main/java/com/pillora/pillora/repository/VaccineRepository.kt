package com.pillora.pillora.repository

import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query // Import Query for sorting
import com.google.firebase.firestore.QuerySnapshot // *** ADICIONADO: Import necessário ***
import com.google.firebase.firestore.snapshots // Import snapshots from main module
import com.google.firebase.firestore.toObject // Import toObject from main module
import com.pillora.pillora.model.Vaccine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf // Import flowOf for returning empty flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart // *** ADICIONADO: Import necessário ***

// *** ADICIONADO: Definição da classe selada para representar o estado da UI ***
sealed class DataResult<out T> {
    data object Loading : DataResult<Nothing>()
    data class Success<out T>(val data: T) : DataResult<T>()
    data class Error(val message: String?) : DataResult<Nothing>()
}

@SuppressLint("StaticFieldLeak") // Suppress warning for Firebase context in object
object VaccineRepository {

    private const val TAG = "VaccineRepository" // Convention: UPPER_SNAKE_CASE
    private const val VACCINES_COLLECTION = "vaccines"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // --- CREATE ---
    fun addVaccine(
        vaccine: Vaccine,
        onSuccess: (String) -> Unit, // Modificado para receber o ID da vacina
        onFailure: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "User not logged in")
            onFailure(Exception("Usuário não autenticado"))
            return
        }

        val vaccineWithUserId = vaccine.copy(userId = userId)

        db.collection("users").document(userId)
            .collection(VACCINES_COLLECTION)
            .add(vaccineWithUserId)
            .addOnSuccessListener { documentReference ->
                val newVaccineId = documentReference.id
                Log.d(TAG, "Vaccine reminder added successfully with ID: $newVaccineId")
                onSuccess(newVaccineId) // Passa o ID da vacina para o callback
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error adding vaccine reminder", exception)
                onFailure(exception)
            }
    }

    // --- READ (Callback version - Mantida para compatibilidade, se necessário) ---
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

        db.collection("users").document(userId)
            .collection(VACCINES_COLLECTION)
            .orderBy("reminderDate", Query.Direction.ASCENDING)
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
                Log.d(TAG, "Fetched ${vaccines.size} vaccine reminders (callback)")
                onSuccess(vaccines)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error getting vaccine reminders (callback)", exception)
                onFailure(exception)
            }
    }

    // --- READ (Flow version - CORRIGIDA para usar DataResult) ---
    fun getAllVaccinesFlow(): Flow<DataResult<List<Vaccine>>> { // *** CORREÇÃO: Mudar tipo de retorno ***
        val userId = currentUserId ?: return flowOf(DataResult.Error("Usuário não autenticado"))

        return db.collection("users").document(userId)
            .collection(VACCINES_COLLECTION)
            .orderBy("reminderDate", Query.Direction.ASCENDING)
            .snapshots() // Usa snapshots para tempo real
            .map<QuerySnapshot, DataResult<List<Vaccine>>> { snapshot -> // *** CORREÇÃO: Mapear para DataResult ***
                val vaccines = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject<Vaccine>()?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting document ${doc.id} to Vaccine", e)
                        null
                    }
                }
                DataResult.Success(vaccines) // *** CORREÇÃO: Emitir Success com a lista ***
            }
            .catch { exception ->
                Log.e(TAG, "Error in getAllVaccinesFlow", exception)
                // *** CORREÇÃO: Emitir Error em caso de exceção ***
                emit(DataResult.Error(exception.message ?: "Erro ao buscar vacinas"))
            }
            .onStart { emit(DataResult.Loading) } // *** CORREÇÃO: Emitir Loading no início ***
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

        db.collection("users").document(userId)
            .collection(VACCINES_COLLECTION)
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
                            onSuccess(null)
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
    fun updateVaccine(
        vaccine: Vaccine,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (vaccine.id.isEmpty()) {
            Log.w(TAG, "Vaccine ID is empty, cannot update")
            onFailure(IllegalArgumentException("ID do lembrete de vacina está faltando para atualização"))
            return
        }
        if (userId == null || vaccine.userId != userId) {
            Log.w(TAG, "User not logged in or trying to update another user's vaccine reminder")
            onFailure(SecurityException("Erro de autorização ou dados inválidos para atualização"))
            return
        }

        db.collection("users").document(userId)
            .collection(VACCINES_COLLECTION)
            .document(vaccine.id)
            .set(vaccine)
            .addOnSuccessListener {
                Log.d(TAG, "Vaccine reminder updated successfully: ${vaccine.id}")
                onSuccess(vaccine.id)
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

        db.collection("users").document(userId)
            .collection(VACCINES_COLLECTION)
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
    }
}

