package com.pillora.pillora.repository

import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query // Import Query for sorting
import com.google.firebase.firestore.ktx.snapshots // Correct import for KTX snapshots extension
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

        val vaccineWithUserId = vaccine.copy(userId = userId)

        db.collection(VACCINES_COLLECTION)
            .add(vaccineWithUserId)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Vaccine reminder added successfully with ID: ${documentReference.id}")
                onSuccess()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error adding vaccine reminder", exception)
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
            .orderBy("reminderDate", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val vaccines = documents.mapNotNull { doc ->
                    try {
                        // Reverted: Safe call `?.` IS necessary here because toObject can return null
                        doc.toObject(Vaccine::class.java)?.copy(id = doc.id)
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
    // The snapshots() warning about deprecation might be an IDE issue if KTX is correctly imported.
    // The parameterless snapshots() from com.google.firebase.firestore.ktx.snapshots is the standard
    // way for basic real-time updates with KTX as per Firebase documentation.
    fun getAllVaccinesFlow(): Flow<List<Vaccine>> {
        val userId = currentUserId ?: return flowOf(emptyList())

        return db.collection(VACCINES_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("reminderDate", Query.Direction.ASCENDING)
            .snapshots() // Use KTX snapshots() extension - Ignore IDE warning if present
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    try {
                        // Reverted: Safe call `?.` IS necessary here because toObject can return null
                        doc.toObject(Vaccine::class.java)?.copy(id = doc.id)
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
                        // Reverted: Safe call `?.` IS necessary here because toObject can return null
                        val vaccine = document.toObject(Vaccine::class.java)?.copy(id = document.id)
                        // Security check: Ensure the fetched vaccine belongs to the current user
                        if (vaccine != null && vaccine.userId == userId) { // Check vaccine is not null after potential failed conversion
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
        onSuccess: () -> Unit,
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

        db.collection(VACCINES_COLLECTION)
            .document(vaccine.id)
            .set(vaccine)
            .addOnSuccessListener {
                Log.d(TAG, "Vaccine reminder updated successfully: ${vaccine.id}")
                onSuccess()
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
    }
}

