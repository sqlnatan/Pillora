package com.pillora.pillora.repository

import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthProvider

/**
 * Repositório para gerenciar operações de autenticação com Firebase Auth
 */
object AuthRepository {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    var onGoogleSignInSuccess: (() -> Unit)? = null
    var onGoogleSignInError: ((Exception) -> Unit)? = null

    /**
     * Inicia o processo de login com Google
     */
    fun signInWithGoogle(
        activity: ComponentActivity,
        webClientId: String, // Mantido para compatibilidade
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        onGoogleSignInSuccess = onSuccess
        onGoogleSignInError = onError

        val provider = OAuthProvider.newBuilder("google.com")
        provider.addCustomParameter("prompt", "select_account")

        auth.startActivityForSignInWithProvider(activity, provider.build())
            .addOnSuccessListener { onGoogleSignInSuccess?.invoke() }
            .addOnFailureListener { exception -> onGoogleSignInError?.invoke(exception) }
    }

    /**
     * Login com email e senha
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
     * Criação de conta com email e senha
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
     * Logout do usuário atual
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Verifica se o usuário está autenticado
     */
    fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null
    }

    /**
     * Retorna o usuário logado atualmente
     */
    fun getCurrentUser() = auth.currentUser

    /**
     * Atualiza o email do usuário logado
     */
    fun updateEmail(
        newEmail: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        auth.currentUser?.updateEmail(newEmail)
            ?.addOnSuccessListener { onSuccess() }
            ?.addOnFailureListener { onError(it) }
    }

    /**
     * Atualiza a senha do usuário logado
     */
    fun updatePassword(
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        auth.currentUser?.updatePassword(newPassword)
            ?.addOnSuccessListener { onSuccess() }
            ?.addOnFailureListener { onError(it) }
    }
}
