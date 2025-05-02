package com.pillora.pillora.repository

import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query // Import Query for sorting
import com.pillora.pillora.model.Vaccine

@SuppressLint("StaticFieldLeak") // Suppress warning for Firebase context in object
object VaccineRepository {

    private const val TAG = "VaccineRepository"
    private const val VACCINES_COLLECTION = "vaccines"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // --- CREATE ---
    fun addVaccine(
        vaccine: Vaccine,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "User not logged in")
            onFailure(Exception("Usuário não autenticado"))
            return
        }

        // Ensure userId is set in the vaccine object before saving
        val vaccineWithUserId = vaccine.copy(userId = userId)

        db.collection(VACCINES_COLLECTION)
            .add(vaccineWithUserId)
            .addOnSuccessListener {
                Log.d(TAG, "Vaccine reminder added successfully with ID: ${it.id}")
                onSuccess()
            }
            .addOnFailureListener {
                Log.e(TAG, "Error adding vaccine reminder", it)
                onFailure(it)
            }
    }

    // --- READ (Callback version, similar to ConsultationRepository's getAllConsultations) ---
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
            .orderBy("reminderDate", Query.Direction.ASCENDING) // Order by reminder date
            .get()
            .addOnSuccessListener { documents ->
                if (documents != null) {
                    // Map documents and include the ID
                    val vaccines = documents.mapNotNull { doc ->
                        doc.toObject(Vaccine::class.java).copy(id = doc.id)
                    }
                    Log.d(TAG, "Fetched ${vaccines.size} vaccine reminders")
                    onSuccess(vaccines)
                } else {
                    Log.d(TAG, "No vaccine reminders found")
                    onSuccess(emptyList())
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Error getting vaccine reminders", it)
                onFailure(it)
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
            onFailure(Exception("ID do lembrete de vacina está faltando"))
            return
        }

        db.collection(VACCINES_COLLECTION)
            .document(vaccineId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val vaccine = document.toObject(Vaccine::class.java)?.copy(id = document.id)
                    // Security check: Ensure the fetched vaccine belongs to the current user
                    if (vaccine?.userId == userId) {
                        Log.d(TAG, "Fetched vaccine reminder: ${vaccine.id}")
                        onSuccess(vaccine)
                    } else {
                        Log.w(TAG, "User $userId attempted to fetch vaccine reminder belonging to ${vaccine?.userId}")
                        onFailure(Exception("Lembrete não encontrado ou acesso negado"))
                    }
                } else {
                    Log.d(TAG, "No such vaccine reminder found: $vaccineId")
                    onSuccess(null) // Indicate not found
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Error getting vaccine reminder by ID", it)
                onFailure(it)
            }
    }

    // --- UPDATE ---
    fun updateVaccine(
        vaccine: Vaccine,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = currentUserId
        // Security check: Ensure userId matches and ID is present
        if (userId == null || vaccine.userId != userId) {
            Log.w(TAG, "User not logged in or trying to update another user's vaccine reminder")
            onFailure(Exception("Erro de autorização"))
            return
        }
        if (vaccine.id.isEmpty()) {
            Log.w(TAG, "Vaccine ID is empty, cannot update")
            onFailure(Exception("ID do lembrete de vacina está faltando"))
            return
        }

        db.collection(VACCINES_COLLECTION)
            .document(vaccine.id)
            .set(vaccine) // Use set to overwrite the document
            .addOnSuccessListener {
                Log.d(TAG, "Vaccine reminder updated successfully: ${vaccine.id}")
                onSuccess()
            }
            .addOnFailureListener {
                Log.e(TAG, "Error updating vaccine reminder", it)
                onFailure(it)
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
            onFailure(Exception("ID do lembrete de vacina está faltando"))
            return
        }

        // Optional: Verify ownership before deleting (requires fetching first)
        // getVaccineById(vaccineId, { vaccine -> ... check vaccine.userId ... }, onFailure)
        // For simplicity, assuming ID implies ownership (ensure Firestore rules enforce this)

        db.collection(VACCINES_COLLECTION)
            .document(vaccineId)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Vaccine reminder deleted successfully: $vaccineId")
                onSuccess()
            }
            .addOnFailureListener {
                Log.e(TAG, "Error deleting vaccine reminder", it)
                onFailure(it)
            }
    }
}
