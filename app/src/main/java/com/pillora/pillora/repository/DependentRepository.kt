package com.pillora.pillora.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pillora.pillora.model.Dependent
import kotlinx.coroutines.tasks.await

class DependentRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    suspend fun addDependent(dependent: Dependent): Result<Unit> {
        return try {
            val userId = getCurrentUserId()
            if (userId == null) {
                Result.failure(Exception("Usuário não autenticado."))
            } else {
                val dependentWithUserId = dependent.copy(userId = userId)
                db.collection("users").document(userId).collection("dependents")
                    .add(dependentWithUserId).await()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDependents(): Result<List<Dependent>> {
        return try {
            val userId = getCurrentUserId()
            if (userId == null) {
                Result.failure(Exception("Usuário não autenticado."))
            } else {
                val snapshot = db.collection("users").document(userId).collection("dependents")
                    .get().await()
                val dependents = snapshot.documents.mapNotNull { document ->
                    document.toObject(Dependent::class.java)?.copy(id = document.id)
                }
                Result.success(dependents)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDependent(dependent: Dependent): Result<Unit> {
        return try {
            val userId = getCurrentUserId()
            if (userId == null) {
                Result.failure(Exception("Usuário não autenticado."))
            } else {
                db.collection("users").document(userId).collection("dependents")
                    .document(dependent.id).set(dependent).await()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDependent(dependentId: String): Result<Unit> {
        return try {
            val userId = getCurrentUserId()
            if (userId == null) {
                Result.failure(Exception("Usuário não autenticado."))
            } else {
                db.collection("users").document(userId).collection("dependents")
                    .document(dependentId).delete().await()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
