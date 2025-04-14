package com.pillora.pillora.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.util.Log
import androidx.compose.runtime.rememberCoroutineScope

class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun addUser(name: String, age: Int) {
        val user = hashMapOf(
            "name" to name,
            "age" to age
        )
        try {
            db.collection("users").add(user).await()
            Log.d("Firestore", "Usuário adicionado com sucesso")
        } catch (e: Exception) {
            Log.e("Firestore", "Erro ao adicionar usuário: ${e.message}")
        }
    }

    suspend fun getUsers(): List<Map<String, Any>> {
        return try {
            val snapshot = db.collection("users").get().await()
            snapshot.documents.map { it.data ?: emptyMap() }
        } catch (e: Exception) {
            Log.e("Firestore", "Erro ao buscar usuários: ${e.message}")
            emptyList()
        }
    }
}
