package com.pillora.pillora.repository

// Removed unused import: import com.google.firebase.ktx.Firebase
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.pillora.pillora.model.Profile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// *** REMOVED: Empty primary constructor ***
class ProfileRepository {

    private val db: FirebaseFirestore = Firebase.firestore
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO // Use IO dispatcher for DB operations

    companion object {
        private const val TAG = "ProfileRepository"
        private const val PROFILES_COLLECTION = "profiles"
        private const val RECIPES_COLLECTION = "recipes"
        private const val MEDICATIONS_COLLECTION = "medications"
        private const val CONSULTATIONS_COLLECTION = "consultations"
        private const val VACCINES_COLLECTION = "vaccines"
    }

    // Get all profiles for the current user as a Flow
    fun getAllProfilesFlow(userId: String): Flow<List<Profile>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val listenerRegistration = db.collection(PROFILES_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e)
                    close(e)
                    return@addSnapshotListener
                }

                // *** SIMPLIFIED: Removed redundant let ***
                val profiles = snapshots?.mapNotNull { doc ->
                    try {
                        doc.toObject(Profile::class.java).copy(id = doc.id)
                    } catch (convEx: Exception) {
                        Log.e(TAG, "Error converting profile document ${doc.id}", convEx)
                        null
                    }
                } ?: emptyList()
                trySend(profiles).isSuccess
            }

        awaitClose { listenerRegistration.remove() }
    }

    // Get a single profile by ID
    suspend fun getProfileById(profileId: String): Profile? = withContext(ioDispatcher) {
        try {
            val document = db.collection(PROFILES_COLLECTION).document(profileId).get().await()
            // *** SIMPLIFIED: Removed redundant let ***
            document.toObject(Profile::class.java)?.copy(id = document.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting profile $profileId", e)
            null
        }
    }

    // Save a new profile
    suspend fun saveProfile(profile: Profile): Result<String> = withContext(ioDispatcher) {
        val currentUserId = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("User not logged in"))

        try {
            val docRef = db.collection(PROFILES_COLLECTION)
                .add(profile.copy(userId = currentUserId))
                .await()
            Log.d(TAG, "Profile saved successfully with ID: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving profile", e)
            Result.failure(e)
        }
    }

    // Update an existing profile
    suspend fun updateProfile(profileId: String, profile: Profile): Result<Unit> = withContext(ioDispatcher) {
        try {
            db.collection(PROFILES_COLLECTION).document(profileId)
                .set(profile) // Use set to overwrite
                .await()
            Log.d(TAG, "Profile updated successfully: $profileId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile $profileId", e)
            Result.failure(e)
        }
    }

    // Delete a profile and all associated data permanently
    suspend fun deleteProfileAndData(profileId: String): Result<Unit> = withContext(ioDispatcher) {
        val batch = db.batch()
        try {
            // 1. Mark profile for deletion
            val profileRef = db.collection(PROFILES_COLLECTION).document(profileId)
            batch.delete(profileRef)

            // 2. Delete associated recipes
            val recipesQuery = db.collection(RECIPES_COLLECTION).whereEqualTo("profileId", profileId).get().await()
            recipesQuery.documents.forEach { batch.delete(it.reference) }

            // 3. Delete associated medications
            val medicationsQuery = db.collection(MEDICATIONS_COLLECTION).whereEqualTo("profileId", profileId).get().await()
            medicationsQuery.documents.forEach { batch.delete(it.reference) }

            // 4. Delete associated consultations
            val consultationsQuery = db.collection(CONSULTATIONS_COLLECTION).whereEqualTo("profileId", profileId).get().await()
            consultationsQuery.documents.forEach { batch.delete(it.reference) }

            // 5. Delete associated vaccines
            val vaccinesQuery = db.collection(VACCINES_COLLECTION).whereEqualTo("profileId", profileId).get().await()
            vaccinesQuery.documents.forEach { batch.delete(it.reference) }

            // 6. Commit batch
            batch.commit().await()
            Log.d(TAG, "Profile and associated data deleted successfully: $profileId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting profile $profileId and associated data", e)
            Result.failure(e)
        }
    }
}

