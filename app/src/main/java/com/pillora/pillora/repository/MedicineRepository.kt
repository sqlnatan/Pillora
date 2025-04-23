package com.pillora.pillora.repository

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.pillora.pillora.model.Medicine

object MedicineRepository {
    private const val COLLECTION_MEDICINES = "medicines"

    private val db by lazy { Firebase.firestore }
    private val medicinesCollection by lazy { db.collection(COLLECTION_MEDICINES) }

    fun saveMedicine(
        medicine: Medicine,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        medicinesCollection.add(medicine)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun updateMedicine(
        medicineId: String,
        medicine: Medicine,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        medicinesCollection.document(medicineId)
            .set(medicine)
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
                val document = snapshot.toObject(Medicine::class.java)
                onSuccess(document)
            }
            .addOnFailureListener { onError(it) }
    }

    fun getAllMedicines(
        onSuccess: (List<Pair<String, Medicine>>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        medicinesCollection.get()
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
