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
// Remova a importação de kotlinx.coroutines.tasks.await se não for usada diretamente aqui
// import kotlinx.coroutines.tasks.await

object MedicineRepository {
    private const val TAG = "MedicineRepository"
    private const val USERS_COLLECTION = "users"
    private const val MEDICINES_SUBCOLLECTION = "medicines"

    private val db by lazy { Firebase.firestore }

    private val currentUserId: String?
        get() = Firebase.auth.currentUser?.uid

    // Função auxiliar para obter a referência da subcoleção de medicamentos do usuário atual
    private fun getUserMedicinesCollectionRef() = currentUserId?.let {
        db.collection(USERS_COLLECTION).document(it).collection(MEDICINES_SUBCOLLECTION)
    }

    fun saveMedicine(
        medicine: Medicine,
        onSuccess: (String) -> Unit, // Modificado para retornar o ID do novo medicamento
        onError: (Exception) -> Unit
    ) {
        val userMedicinesRef = getUserMedicinesCollectionRef()
        if (userMedicinesRef == null) {
            onError(Exception("User not logged in or user ID is invalid"))
            return
        }

        // O ID do usuário já deve estar no objeto medicine ou ser adicionado aqui
        // Se o seu objeto Medicine já tem userId, ótimo. Senão, copie-o.
        val medicineToSave = medicine.copy(userId = currentUserId) // Garante que userId está no objeto

        userMedicinesRef.add(medicineToSave) // .add() gera um ID automaticamente
            .addOnSuccessListener { documentReference ->
                onSuccess(documentReference.id) // Retorna o ID do documento criado
            }
            .addOnFailureListener { onError(it) }
    }

	    fun updateMedicine(
	        medicine: Medicine,
	        onSuccess: () -> Unit,
	        onError: (Exception) -> Unit
	    ) {
	        val medicineId = medicine.id
	        if (medicineId.isNullOrBlank()) { // Verifica se medicineId é nulo ou vazio
	            onError(Exception("Medicine ID is missing or invalid for update"))
	            return
	        }
	        val userMedicinesRef = getUserMedicinesCollectionRef()
	        if (userMedicinesRef == null) {
	            onError(Exception("User not logged in or user ID is invalid for update"))
	            return
	        }
	
	        // Garante que o userId está no objeto e remove o ID para evitar que seja salvo como campo
	        val medicineToUpdate = medicine.copy(userId = currentUserId, id = null)
	
	        userMedicinesRef.document(medicineId)
	            .set(medicineToUpdate)
	            .addOnSuccessListener { onSuccess() }
	            .addOnFailureListener { onError(it) }
	    }

    fun deleteMedicine(
        medicineId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (medicineId.isBlank()) {
            onError(Exception("Medicine ID is missing or invalid for delete"))
            return
        }
        val userMedicinesRef = getUserMedicinesCollectionRef()
        if (userMedicinesRef == null) {
            onError(Exception("User not logged in or user ID is invalid for delete"))
            return
        }

        userMedicinesRef.document(medicineId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun getMedicineById(
        medicineId: String,
        onSuccess: (Medicine?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (medicineId.isBlank()) {
            onError(Exception("Medicine ID is missing or invalid for get"))
            // Você pode optar por chamar onSuccess(null) aqui também se preferir
            // onSuccess(null)
            return
        }
        val userMedicinesRef = getUserMedicinesCollectionRef()
        if (userMedicinesRef == null) {
            onError(Exception("User not logged in or user ID is invalid for get"))
            return
        }

        userMedicinesRef.document(medicineId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val medicine = documentSnapshot.toObject(Medicine::class.java)?.copy(
                        id = documentSnapshot.id // ATRIBUI O ID DO DOCUMENTO
                    )
                    // A verificação de medicine.userId == currentUserId é redundante se buscamos da subcoleção
                    onSuccess(medicine)
                } else {
                    onSuccess(null) // Documento não encontrado
                }
            }
            .addOnFailureListener { onError(it) }
    }

    fun getMedicineByIdSync(
        medicineId: String?
    ): Medicine? {
        if (medicineId.isNullOrBlank()) return null
        val userId = currentUserId ?: return null
        return try {
            val documentSnapshot = db.collection(USERS_COLLECTION).document(userId)
                .collection(MEDICINES_SUBCOLLECTION).document(medicineId)
                .get().result // Bloqueia a thread, deve ser usado com cuidado (dentro de coroutine)
            documentSnapshot.toObject(Medicine::class.java)?.copy(id = documentSnapshot.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting medicine $medicineId synchronously", e)
            null
        }
    }

    fun getAllMedicinesFlow(): Flow<List<Medicine>> = callbackFlow {
        val userMedicinesRef = getUserMedicinesCollectionRef()
        if (userMedicinesRef == null) {
            Log.w(TAG, "User not logged in for getAllMedicinesFlow, sending empty list")
            trySend(emptyList()).isSuccess // Use isSuccess para evitar warning
            close(Exception("User not logged in"))
            return@callbackFlow
        }

        Log.d(TAG, "Setting up snapshot listener for user medicines")
        val listenerRegistration: ListenerRegistration = userMedicinesRef
            // .whereEqualTo("userId", currentUserId) // Não é mais necessário com subcoleções
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for medicine updates", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val medicines = snapshot.documents.mapNotNull { document ->
                        try {
                            document.toObject(Medicine::class.java)?.copy(
                                id = document.id // ATRIBUI O ID DO DOCUMENTO
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document ${document.id} to Medicine", e)
                            null
                        }
                    }
                    Log.d(TAG, "Snapshot received. Found ${medicines.size} medicines.")
                    trySend(medicines).isSuccess
                } else {
                    Log.d(TAG, "Snapshot was null for medicines")
                    trySend(emptyList()).isSuccess
                }
            }

        awaitClose {
            Log.d(TAG, "Closing medicine snapshot listener")
            listenerRegistration.remove()
        }
    }

    // A função getAllMedicines (deprecated) também precisaria da mesma lógica.
    // Considere removê-la ou atualizá-la se realmente precisar dela.
    @Deprecated("Use getAllMedicinesFlow for real-time updates")
    fun getAllMedicines(
        onSuccess: (List<Medicine>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userMedicinesRef = getUserMedicinesCollectionRef()
        if (userMedicinesRef == null) {
            onError(Exception("User not logged in"))
            return
        }
        userMedicinesRef.get()
            .addOnSuccessListener { snapshot ->
                val medicines = snapshot.documents.mapNotNull { document ->
                    document.toObject(Medicine::class.java)?.copy(id = document.id)
                }
                onSuccess(medicines)
            }
            .addOnFailureListener { onError(it) }
    }
}
