package com.pillora.pillora.repository

import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query // Import Query for ordering
import com.pillora.pillora.model.Consultation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@SuppressLint("StaticFieldLeak")
object ConsultationRepository {

    private const val TAG = "ConsultationRepository"
    private const val USERS_COLLECTION = "users"
    private const val CONSULTATIONS_COLLECTION = "consultations"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // Helper function to get the correct collection reference for the current user
    private fun getUserConsultationsCollection() = currentUserId?.let {
        db.collection(USERS_COLLECTION).document(it).collection(CONSULTATIONS_COLLECTION)
    }

    fun addConsultation(
        consultation: Consultation,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userConsultationsRef = getUserConsultationsCollection()
        if (userConsultationsRef == null) {
            Log.w(TAG, "User not logged in for addConsultation")
            onFailure(Exception("User not logged in"))
            return
        }

        // Ensure the consultation object has the correct userId (redundant but safe)
        val consultationWithUserId = consultation.copy(userId = currentUserId!!)

        userConsultationsRef
            .add(consultationWithUserId) // Add to the user's subcollection
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Consultation added successfully to user subcollection with ID: ${documentReference.id}")
                // Update the document with its own ID
                documentReference.update("id", documentReference.id)
                    .addOnSuccessListener {
                        Log.d(TAG, "Consultation document updated with its ID: ${documentReference.id}")
                        onSuccess(documentReference.id)
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "Error updating consultation document with its ID", it)
                        // Still call onSuccess as the document was created, but log the error
                        onSuccess(documentReference.id)
                    }
            }
            .addOnFailureListener {
                Log.e(TAG, "Error adding consultation to user subcollection", it)
                onFailure(it)
            }
    }

    // Modified to return a Flow for real-time updates from the user's subcollection
    fun getAllConsultationsFlow(): Flow<List<Consultation>> = callbackFlow {
        val userConsultationsRef = getUserConsultationsCollection()
        if (userConsultationsRef == null) {
            Log.w(TAG, "User not logged in, returning empty flow for consultations")
            trySend(emptyList())
            close(Exception("User not logged in"))
            return@callbackFlow
        }

        Log.d(TAG, "Setting up consultation snapshot listener for path: ${userConsultationsRef.path}")
        val listenerRegistration: ListenerRegistration = userConsultationsRef
            // No need for .whereEqualTo("userId", userId) as we are already in the user's subcollection
            // Consider ordering by dateTime if stored as Timestamp or a comparable string format
            // .orderBy("dateTime", Query.Direction.ASCENDING) // Uncomment if needed
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for consultation updates at ${userConsultationsRef.path}", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    Log.i(TAG, "Snapshot received for ${userConsultationsRef.path}. Metadata: hasPendingWrites=${snapshot.metadata.hasPendingWrites()}, isFromCache=${snapshot.metadata.isFromCache}")
                    Log.i(TAG, "Snapshot size: ${snapshot.size()} documents.")
                    snapshot.documentChanges.forEach { change ->
                        Log.i(TAG, "  Change: type=${change.type}, docId=${change.document.id}")
                    }

                    val consultations = snapshot.toObjects(Consultation::class.java)
                    val consultationIds = consultations.joinToString { it.id }
                    Log.i(TAG, "Parsed ${consultations.size} consultations from subcollection. IDs: [$consultationIds]")
                    Log.d(TAG, "Attempting to send ${consultations.size} consultations via trySend.")
                    val sendResult = trySend(consultations)
                    Log.d(TAG, "trySend result: isSuccess=${sendResult.isSuccess}, isClosed=${sendResult.isClosed}")
                } else {
                    Log.d(TAG, "Consultation snapshot was null for ${userConsultationsRef.path}")
                    trySend(emptyList())
                }
            }

        awaitClose {
            Log.d(TAG, "Closing consultation snapshot listener for path: ${userConsultationsRef.path}")
            listenerRegistration.remove()
        }
    }

    fun getConsultationById(
        consultationId: String,
        onSuccess: (Consultation?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userConsultationsRef = getUserConsultationsCollection()
        if (userConsultationsRef == null) {
            Log.w(TAG, "User not logged in for getConsultationById")
            onFailure(Exception("User not logged in"))
            return
        }
        if (consultationId.isEmpty()) {
            Log.w(TAG, "Consultation ID is empty, cannot fetch")
            onFailure(Exception("Consultation ID is missing"))
            return
        }

        userConsultationsRef
            .document(consultationId) // Get from the user's subcollection
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val consultation = document.toObject(Consultation::class.java)
                    // No need to check userId again, as we are in the correct subcollection
                    Log.d(TAG, "Fetched consultation from subcollection: ${consultation?.id}")
                    onSuccess(consultation)
                } else {
                    Log.d(TAG, "No such consultation found in subcollection: $consultationId")
                    onSuccess(null) // Indicate not found
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Error getting consultation by ID from subcollection", it)
                onFailure(it)
            }
    }

    fun updateConsultation(
        consultation: Consultation,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userConsultationsRef = getUserConsultationsCollection()
        if (userConsultationsRef == null) {
            Log.w(TAG, "User not logged in for updateConsultation")
            onFailure(Exception("User not logged in"))
            return
        }
        if (consultation.id.isEmpty()) {
            Log.w(TAG, "Consultation ID is empty, cannot update")
            onFailure(Exception("Consultation ID is missing"))
            return
        }
        // Ensure the consultation object has the correct userId (redundant but safe)
        val consultationWithUserId = consultation.copy(userId = currentUserId!!)

        userConsultationsRef
            .document(consultation.id) // Update in the user's subcollection
            .set(consultationWithUserId) // Use set to overwrite the document
            .addOnSuccessListener {
                Log.d(TAG, "Consultation updated successfully in subcollection: ${consultation.id}")
                onSuccess()
            }
            .addOnFailureListener {
                Log.e(TAG, "Error updating consultation in subcollection", it)
                onFailure(it)
            }
    }

    /**
     * Atualiza apenas o campo isActive de uma consulta.
     * Usado para ativar/desativar consultas no processo de downgrade.
     */
    fun updateConsultationActiveStatus(
        consultationId: String,
        isActive: Boolean,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userConsultationsRef = getUserConsultationsCollection()
        if (userConsultationsRef == null) {
            Log.w(TAG, "User not logged in for updateConsultationActiveStatus")
            onFailure(Exception("User not logged in"))
            return
        }
        if (consultationId.isEmpty()) {
            Log.w(TAG, "Consultation ID is empty, cannot update active status")
            onFailure(Exception("Consultation ID is missing"))
            return
        }

        userConsultationsRef
            .document(consultationId)
            .update("isActive", isActive)
            .addOnSuccessListener {
                Log.d(TAG, "Consultation active status updated to $isActive: $consultationId")
                onSuccess()
            }
            .addOnFailureListener {
                Log.e(TAG, "Error updating consultation active status", it)
                onFailure(it)
            }
    }

    fun deleteConsultation(
        consultationId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userConsultationsRef = getUserConsultationsCollection()
        if (userConsultationsRef == null) {
            Log.w(TAG, "User not logged in for deleteConsultation")
            onFailure(Exception("User not logged in"))
            return
        }
        if (consultationId.isEmpty()) {
            Log.w(TAG, "Consultation ID is empty, cannot delete")
            onFailure(Exception("Consultation ID is missing"))
            return
        }

        userConsultationsRef
            .document(consultationId) // Delete from the user's subcollection
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Consultation deleted successfully from subcollection: $consultationId")
                onSuccess()
            }
            .addOnFailureListener {
                Log.e(TAG, "Error deleting consultation from subcollection", it)
                onFailure(it)
            }
    }

    // --- Deprecated method using the old global collection approach ---
    @Deprecated("Uses incorrect global collection. Use getAllConsultationsFlow instead.")
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

        db.collection("consultations") // Incorrect global collection
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents != null) {
                    val consultations = documents.toObjects(Consultation::class.java)
                    Log.d(TAG, "Fetched ${consultations.size} consultations (from global collection - DEPRECATED)")
                    onSuccess(consultations)
                } else {
                    Log.d(TAG, "No consultations found (from global collection - DEPRECATED)")
                    onSuccess(emptyList())
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Error getting consultations (from global collection - DEPRECATED)", it)
                onFailure(it)
            }
    }
}

