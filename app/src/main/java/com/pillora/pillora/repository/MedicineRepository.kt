package com.pillora.pillora.repository

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.pillora.pillora.model.Medicine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object MedicineRepository {
    private const val TAG = "MedicineRepository"
    private const val COLLECTION_MEDICINES = "medicines"

    private val db by lazy { Firebase.firestore }

    // Obter o ID do usuário atual
    private val currentUserId: String?
        get() = Firebase.auth.currentUser?.uid

    // Obter a coleção de medicamentos filtrada pelo usuário atual
    private val medicinesCollection by lazy {
        db.collection(COLLECTION_MEDICINES)
    }

    fun saveMedicine(
        medicine: Medicine,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            onError(Exception("User not logged in"))
            return
        }
        // Adicionar o ID do usuário ao medicamento antes de salvar
        val medicineWithUserId = medicine.copy(userId = userId)

        medicinesCollection.add(medicineWithUserId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun updateMedicine(
        medicineId: String,
        medicine: Medicine,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null || medicine.userId != userId) { // Ensure user owns the medicine being updated
            onError(Exception("Authorization error"))
            return
        }
        // Garantir que o ID do usuário seja mantido na atualização
        val medicineWithUserId = medicine.copy(userId = userId)

        medicinesCollection.document(medicineId)
            .set(medicineWithUserId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun deleteMedicine(
        medicineId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            onError(Exception("User not logged in"))
            return
        }
        // Optional: Verify ownership before deleting
        medicinesCollection.document(medicineId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun getMedicineById(
        medicineId: String,
        onSuccess: (Medicine?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            onError(Exception("User not logged in"))
            return
        }
        medicinesCollection.document(medicineId)
            .get()
            .addOnSuccessListener { snapshot ->
                val medicine = snapshot.toObject(Medicine::class.java)
                // Verificar se o medicamento pertence ao usuário atual
                if (medicine != null && medicine.userId == userId) {
                    onSuccess(medicine)
                } else {
                    onSuccess(null) // Retorna null se não pertencer ao usuário atual ou não encontrado
                }
            }
            .addOnFailureListener { onError(it) }
    }

    // Modified to return a Flow for real-time updates
    fun getAllMedicinesFlow(): Flow<List<Medicine>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "User not logged in, returning empty flow")
            trySend(emptyList()) // Send empty list if user not logged in
            close(Exception("User not logged in"))
            return@callbackFlow
        }

        Log.d(TAG, "Setting up snapshot listener for user: $userId")
        val listenerRegistration: ListenerRegistration = medicinesCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for medicine updates", error)
                    close(error) // Close the flow on error
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val medicines = snapshot.toObjects(Medicine::class.java)
                    Log.d(TAG, "Snapshot received. Found ${medicines.size} medicines.")
                    trySend(medicines) // Send the updated list to the flow
                } else {
                    Log.d(TAG, "Snapshot was null")
                    trySend(emptyList()) // Send empty list if snapshot is null
                }
            }

        // This block is called when the Flow collector is cancelled
        awaitClose {
            Log.d(TAG, "Closing snapshot listener for user: $userId")
            listenerRegistration.remove() // Remove the listener to prevent memory leaks
        }
    }

    // --- Kept the old method for compatibility if needed elsewhere, but marked as deprecated ---
    @Deprecated("Use getAllMedicinesFlow for real-time updates", ReplaceWith("getAllMedicinesFlow()"))
    fun getAllMedicines(
        onSuccess: (List<Pair<String, Medicine>>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            onError(Exception("User not logged in"))
            return
        }
        medicinesCollection
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                val medicines = snapshot.documents.mapNotNull { document ->
                    val medicine = document.toObject(Medicine::class.java)
                    if (medicine != null) {
                        Pair(document.id, medicine) // Note: Flow version doesn't include ID pair
                    } else {
                        null
                    }
                }
                onSuccess(medicines)
            }
            .addOnFailureListener { onError(it) }
    }
}

