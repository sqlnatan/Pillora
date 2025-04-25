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
     * @param activity ComponentActivity - Atividade atual
     * @param onSuccess Function0<Unit> - Callback de sucesso
     * @param onError Function1<Exception, Unit> - Callback de erro
     */
    fun signInWithGoogle(
        activity: ComponentActivity,
        webClientId: String, // Mantido para compatibilidade com a interface
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        onGoogleSignInSuccess = onSuccess
        onGoogleSignInError = onError

        // Usando o OAuthProvider do Firebase para autenticação Google
        val provider = OAuthProvider.newBuilder("google.com")

        // Opcional: configurar escopo para acesso a dados do Google
        provider.addCustomParameter("prompt", "select_account")

        // Iniciar o fluxo de autenticação
        auth.startActivityForSignInWithProvider(activity, provider.build())
            .addOnSuccessListener {
                // Autenticação bem-sucedida
                onGoogleSignInSuccess?.invoke()
            }
            .addOnFailureListener { exception ->
                // Autenticação falhou
                onGoogleSignInError?.invoke(exception)
            }
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
        // Desconectar do Firebase
        auth.signOut()
    }

    /**
     * Verifica se o usuário está autenticado
     * @return Boolean - true se estiver autenticado, false caso contrário
     */
    fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null
    }
}
