package com.pillora.pillora.repository

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.pillora.pillora.model.Medicine

object MedicineRepository {
    private const val COLLECTION_MEDICINES = "medicines"

    private val db by lazy { Firebase.firestore }

    // Obter o ID do usuário atual
    private val currentUserId: String
        get() = Firebase.auth.currentUser?.uid ?: ""

    // Obter a coleção de medicamentos filtrada pelo usuário atual
    private val medicinesCollection by lazy {
        db.collection(COLLECTION_MEDICINES)
    }

    fun saveMedicine(
        medicine: Medicine,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Adicionar o ID do usuário ao medicamento antes de salvar
        val medicineWithUserId = medicine.copy(userId = currentUserId)

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
        // Garantir que o ID do usuário seja mantido na atualização
        val medicineWithUserId = medicine.copy(userId = currentUserId)

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
        medicinesCollection.document(medicineId)
            .get()
            .addOnSuccessListener { snapshot ->
                val medicine = snapshot.toObject(Medicine::class.java)
                // Verificar se o medicamento pertence ao usuário atual
                if (medicine != null && (medicine.userId == currentUserId || medicine.userId == null)) {
                    onSuccess(medicine)
                } else {
                    onSuccess(null) // Retorna null se não pertencer ao usuário atual
                }
            }
            .addOnFailureListener { onError(it) }
    }

    fun getAllMedicines(
        onSuccess: (List<Pair<String, Medicine>>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Filtrar medicamentos pelo ID do usuário atual
        medicinesCollection
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { snapshot ->
                val medicines = snapshot.documents.mapNotNull { document ->
                    val medicine = document.toObject(Medicine::class.java)
                    if (medicine != null) {
                        Pair(document.id, medicine)
                    } else {
                        null
                    }
                }
                onSuccess(medicines)
            }
            .addOnFailureListener { onError(it) }
    }
}
