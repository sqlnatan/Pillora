package com.pillora.pillora.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Repositório para gerenciar operações de autenticação com Firebase Auth
 */
object AuthRepository {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    /**
     * Obtém o usuário atualmente autenticado
     * @return FirebaseUser? - Usuário atual ou null se não estiver autenticado
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    /**
     * Verifica se o usuário está autenticado
     * @return Boolean - true se estiver autenticado, false caso contrário
     */
    fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null
    }

    /**
     * Realiza login com email e senha
     * @param email String - Email do usuário
     * @param password String - Senha do usuário
     * @param onSuccess Function0<Unit> - Callback de sucesso
     * @param onError Function1<Exception, Unit> - Callback de erro
     */
    fun signIn(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Cria uma nova conta com email e senha
     * @param email String - Email do usuário
     * @param password String - Senha do usuário
     * @param onSuccess Function0<Unit> - Callback de sucesso
     * @param onError Function1<Exception, Unit> - Callback de erro
     */
    fun signUp(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Envia email de recuperação de senha
     * @param email String - Email do usuário
     * @param onSuccess Function0<Unit> - Callback de sucesso
     * @param onError Function1<Exception, Unit> - Callback de erro
     */
    fun resetPassword(
        email: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Realiza logout do usuário atual
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Obtém o ID do usuário atual
     * @return String? - ID do usuário ou null se não estiver autenticado
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    /**
     * Obtém o email do usuário atual
     * @return String? - Email do usuário ou null se não estiver autenticado
     */
    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }
}
