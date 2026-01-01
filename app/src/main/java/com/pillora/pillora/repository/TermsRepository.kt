package com.pillora.pillora.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repositório para gerenciar aceitação de termos de uso no Firestore
 * Armazena por usuário (userId) e versão dos termos
 */
object TermsRepository {

    private val db = FirebaseFirestore.getInstance()
    private const val COLLECTION_TERMS = "user_terms_acceptance"

    // Versão atual dos termos - incrementar quando atualizar os termos
    const val CURRENT_TERMS_VERSION = 3

    /**
     * Verifica se o usuário aceitou a versão atual dos termos
     */
    suspend fun hasAcceptedCurrentTerms(userId: String): Boolean {
        return try {
            val document = db.collection(COLLECTION_TERMS)
                .document(userId)
                .get()
                .await()

            if (document.exists()) {
                val acceptedVersion = document.getLong("termsVersion")?.toInt() ?: 0
                val accepted = document.getBoolean("accepted") ?: false

                // Retorna true apenas se aceitou E a versão é a atual
                accepted && acceptedVersion >= CURRENT_TERMS_VERSION
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("TermsRepository", "Erro ao verificar aceitação dos termos: ${e.message}")
            false
        }
    }

    /**
     * Registra a aceitação dos termos pelo usuário
     */
    suspend fun acceptTerms(userId: String): Boolean {
        return try {
            val termsData = hashMapOf(
                "userId" to userId,
                "accepted" to true,
                "termsVersion" to CURRENT_TERMS_VERSION,
                "acceptedAt" to com.google.firebase.Timestamp.now()
            )

            db.collection(COLLECTION_TERMS)
                .document(userId)
                .set(termsData)
                .await()

            Log.d("TermsRepository", "Termos aceitos para usuário $userId, versão $CURRENT_TERMS_VERSION")
            true
        } catch (e: Exception) {
            Log.e("TermsRepository", "Erro ao registrar aceitação dos termos: ${e.message}")
            false
        }
    }

    /**
     * Obtém a versão dos termos aceita pelo usuário
     */
    suspend fun getAcceptedTermsVersion(userId: String): Int {
        return try {
            val document = db.collection(COLLECTION_TERMS)
                .document(userId)
                .get()
                .await()

            document.getLong("termsVersion")?.toInt() ?: 0
        } catch (e: Exception) {
            Log.e("TermsRepository", "Erro ao obter versão dos termos: ${e.message}")
            0
        }
    }
}
