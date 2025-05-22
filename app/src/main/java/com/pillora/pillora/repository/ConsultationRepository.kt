package com.pillora.pillora.repository

import android.annotation.SuppressLint // Add import
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pillora.pillora.model.Consultation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@SuppressLint("StaticFieldLeak") // Suppress warning for Firebase context in object
object ConsultationRepository {

    private const val TAG = "ConsultationRepository"
    private const val CONSULTATIONS_COLLECTION = "consultations"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    fun addConsultation(
        consultation: Consultation,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "User not logged in")
            onFailure(Exception("User not logged in"))
            return
        }

        val consultationWithUserId = consultation.copy(userId = userId)

        db.collection(CONSULTATIONS_COLLECTION)
            .add(consultationWithUserId)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Consultation added successfully with ID: ${documentReference.id}")
                onSuccess(documentReference.id) // Aqui está a correção: passar o ID para o callback
            }
            .addOnFailureListener {
                Log.e(TAG, "Error adding consultation", it)
                onFailure(it)
            }
    }

    // Modified to return a Flow for real-time updates
    fun getAllConsultationsFlow(): Flow<List<Consultation>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "User not logged in, returning empty flow for consultations")
            trySend(emptyList())
            close(Exception("User not logged in"))
            return@callbackFlow
        }

        Log.d(TAG, "Setting up consultation snapshot listener for user: $userId")
        val listenerRegistration: ListenerRegistration = db.collection(CONSULTATIONS_COLLECTION)
            .whereEqualTo("userId", userId)
            // Consider ordering by dateTime if stored as Timestamp
            // .orderBy("dateTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for consultation updates", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val consultations = snapshot.toObjects(Consultation::class.java)
                    Log.d(TAG, "Consultation snapshot received. Found ${consultations.size} consultations.")
                    trySend(consultations)
                } else {
                    Log.d(TAG, "Consultation snapshot was null")
                    trySend(emptyList())
                }
            }

        awaitClose {
            Log.d(TAG, "Closing consultation snapshot listener for user: $userId")
            listenerRegistration.remove()
        }
    }

    fun getConsultationById(
        consultationId: String,
        onSuccess: (Consultation?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "User not logged in")
            onFailure(Exception("User not logged in"))
            return
        }
        if (consultationId.isEmpty()) {
            Log.w(TAG, "Consultation ID is empty, cannot fetch")
            onFailure(Exception("Consultation ID is missing"))
            return
        }

        db.collection(CONSULTATIONS_COLLECTION)
            .document(consultationId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val consultation = document.toObject(Consultation::class.java)
                    // Security check: Ensure the fetched consultation belongs to the current user
                    if (consultation?.userId == userId) {
                        Log.d(TAG, "Fetched consultation: ${consultation.id}")
                        onSuccess(consultation)
                    } else {
                        Log.w(TAG, "User $userId attempted to fetch consultation belonging to ${consultation?.userId}")
                        onFailure(Exception("Consultation not found or access denied"))
                    }
                } else {
                    Log.d(TAG, "No such consultation found: $consultationId")
                    onSuccess(null) // Indicate not found
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Error getting consultation by ID", it)
                onFailure(it)
            }
    }

    fun updateConsultation(
        consultation: Consultation,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null || consultation.userId != userId) {
            Log.w(TAG, "User not logged in or trying to update another user's consultation")
            onFailure(Exception("Authorization error"))
            return
        }
        if (consultation.id.isEmpty()) {
            Log.w(TAG, "Consultation ID is empty, cannot update")
            onFailure(Exception("Consultation ID is missing"))
            return
        }

        db.collection(CONSULTATIONS_COLLECTION)
            .document(consultation.id)
            .set(consultation) // Use set to overwrite the document
            .addOnSuccessListener {
                Log.d(TAG, "Consultation updated successfully: ${consultation.id}")
                onSuccess()
            }
            .addOnFailureListener {
                Log.e(TAG, "Error updating consultation", it)
                onFailure(it)
            }
    }

    fun deleteConsultation(
        consultationId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "User not logged in")
            onFailure(Exception("User not logged in"))
            return
        }
        if (consultationId.isEmpty()) {
            Log.w(TAG, "Consultation ID is empty, cannot delete")
            onFailure(Exception("Consultation ID is missing"))
            return
        }

        // Optional: Verify ownership before deleting (requires fetching the document first)
        // For simplicity here, we assume the ID belongs to the user if they have it.

        db.collection(CONSULTATIONS_COLLECTION)
            .document(consultationId)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Consultation deleted successfully: $consultationId")
                onSuccess()
            }
            .addOnFailureListener {
                Log.e(TAG, "Error deleting consultation", it)
                onFailure(it)
            }
    }

    // --- Kept the old method for compatibility if needed elsewhere, but marked as deprecated ---
    @Deprecated("Use getAllConsultationsFlow for real-time updates", ReplaceWith("getAllConsultationsFlow()"))
    fun getAllConsultations(
        onSuccess: (List<Consultation>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "User not logged in")
            onFailure(Exception("User not logged in"))
            return
        }

        db.collection(CONSULTATIONS_COLLECTION)
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents != null) {
                    val consultations = documents.toObjects(Consultation::class.java)
                    Log.d(TAG, "Fetched ${consultations.size} consultations")
                    onSuccess(consultations)
                } else {
                    Log.d(TAG, "No consultations found")
                    onSuccess(emptyList())
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Error getting consultations", it)
                onFailure(it)
            }
    }
}

