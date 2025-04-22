package com.pillora.pillora.repository

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.pillora.pillora.model.Medicine

object MedicineRepository {
    fun saveMedicine(
        medicine: Medicine,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val db = Firebase.firestore
        db.collection("medicines")
            .add(medicine)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { exception -> onError(exception) }
    }
}
