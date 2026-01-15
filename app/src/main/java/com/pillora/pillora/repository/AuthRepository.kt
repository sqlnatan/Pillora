package com.pillora.pillora.repository

import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest

@Suppress("DEPRECATION")
object AuthRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private lateinit var googleClient: GoogleSignInClient
    private const val TAG = "AuthRepository"

    // -----------------------
    // Google Sign-In
    // -----------------------

    fun launchGoogleSignIn(
        activity: ComponentActivity,
        webClientId: String,
        launcher: ActivityResultLauncher<Intent>
    ) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        googleClient = GoogleSignIn.getClient(activity, gso)
        val signInIntent = googleClient.signInIntent
        launcher.launch(signInIntent)
    }

    fun handleGoogleSignInResult(
        result: ActivityResult,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val data = result.data
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            // Tenta fazer login com a credencial do Google
            auth.signInWithCredential(credential)
                .addOnSuccessListener { authResult ->
                    Log.d(TAG, "Login com Google bem-sucedido. UID: ${authResult.user?.uid}")
                    onSuccess()
                }
                .addOnFailureListener { exception ->
                    // Se houver colisão (e-mail já existe com outro provedor)
                    if (exception is FirebaseAuthUserCollisionException) {
                        Log.d(TAG, "Colisão de usuário detectada no Google Sign-In")
                        // CORRIGIDO: Não vincular automaticamente, apenas fazer login
                        // O Firebase permite login com múltiplos provedores no mesmo email
                        handleCollisionOnGoogleSignIn(account.email ?: "", credential, onSuccess, onError)
                    } else {
                        onError(exception)
                    }
                }
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * CORRIGIDO: Trata colisão de email no login com Google.
     * Ao invés de vincular (que sobrescreve), apenas faz login.
     * O Firebase suporta múltiplos provedores para o mesmo email.
     */
    private fun handleCollisionOnGoogleSignIn(
        email: String,
        googleCredential: AuthCredential,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Verificar quais métodos de login estão disponíveis para este email
        auth.fetchSignInMethodsForEmail(email)
            .addOnSuccessListener { result ->
                val signInMethods = result.signInMethods ?: emptyList()
                Log.d(TAG, "Métodos de login existentes para $email: $signInMethods")

                if (signInMethods.contains(EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD)) {
                    // Conta criada com email/senha existe
                    // CORRIGIDO: Apenas fazer login com Google sem remover a senha
                    Log.d(TAG, "Conta com email/senha detectada. Fazendo login com Google sem remover senha.")

                    // Tentar login novamente (às vezes a segunda tentativa funciona)
                    auth.signInWithCredential(googleCredential)
                        .addOnSuccessListener {
                            Log.d(TAG, "Login com Google bem-sucedido após detecção de colisão")
                            onSuccess()
                        }
                        .addOnFailureListener { error ->
                            Log.e(TAG, "Erro ao fazer login com Google após colisão", error)
                            onError(Exception("Este email já está em uso. Tente fazer login com email e senha primeiro, depois vincule o Google nas configurações."))
                        }
                } else {
                    // Outro provedor (improvável, mas tratar)
                    onError(Exception("Este email já está em uso com outro método de login."))
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Erro ao verificar métodos de login", error)
                onError(error)
            }
    }

    /**
     * NOVO: Vincula a credencial do Google a uma conta de email/senha existente.
     * Deve ser chamado APENAS quando o usuário está logado e quer adicionar Google como método alternativo.
     * NÃO remove a senha existente.
     */
    fun linkGoogleAccount(
        credential: AuthCredential,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onError(Exception("Usuário não está logado. Faça login primeiro."))
            return
        }

        // Verificar se o Google já está vinculado
        val providers = currentUser.providerData.map { it.providerId }
        if (providers.contains(GoogleAuthProvider.PROVIDER_ID)) {
            Log.d(TAG, "Google já está vinculado a esta conta")
            onSuccess() // Já vinculado, considerar sucesso
            return
        }

        // Vincular Google à conta atual (mantém email/senha)
        currentUser.linkWithCredential(credential)
            .addOnSuccessListener {
                Log.d(TAG, "Google vinculado com sucesso à conta existente")
                onSuccess()
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Erro ao vincular Google", error)
                onError(error)
            }
    }

    // -----------------------
    // Email & Password Login
    // -----------------------

    fun signIn(email: String, password: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                Log.d(TAG, "Login com email/senha bem-sucedido. UID: ${authResult.user?.uid}")
                onSuccess()
            }
            .addOnFailureListener { exception ->
                // CORRIGIDO: Melhor tratamento de erros
                Log.e(TAG, "Erro ao fazer login com email/senha", exception)

                // Verificar se a conta existe mas com outro provedor
                auth.fetchSignInMethodsForEmail(email)
                    .addOnSuccessListener { result ->
                        val signInMethods = result.signInMethods ?: emptyList()

                        if (signInMethods.isEmpty()) {
                            // Conta não existe
                            onError(Exception("Email ou senha incorretos."))
                        } else if (!signInMethods.contains(EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD)) {
                            // Conta existe mas sem senha (apenas Google)
                            onError(Exception("Esta conta foi criada com Google. Faça login com Google ou defina uma senha nas configurações."))
                        } else {
                            // Senha incorreta
                            onError(exception)
                        }
                    }
                    .addOnFailureListener {
                        // Fallback: retornar erro original
                        onError(exception)
                    }
            }
    }

    fun signUp(email: String, password: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                Log.d(TAG, "Conta criada com email/senha. UID: ${authResult.user?.uid}")
                onSuccess()
            }
            .addOnFailureListener { exception ->
                if (exception is FirebaseAuthUserCollisionException) {
                    // CORRIGIDO: Verificar se a conta tem senha ou apenas Google
                    Log.d(TAG, "Colisão detectada no SignUp. Verificando provedores...")

                    auth.fetchSignInMethodsForEmail(email)
                        .addOnSuccessListener { result ->
                            val signInMethods = result.signInMethods ?: emptyList()

                            if (signInMethods.contains(EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD)) {
                                // Conta com senha já existe
                                onError(Exception("Este email já está cadastrado. Faça login."))
                            } else if (signInMethods.contains(GoogleAuthProvider.GOOGLE_SIGN_IN_METHOD)) {
                                // Conta existe apenas com Google
                                onError(Exception("Este email já está em uso com login do Google. Faça login com o Google primeiro e depois defina uma senha nas configurações."))
                            } else {
                                // Outro provedor
                                onError(Exception("Este email já está em uso com outro método de login."))
                            }
                        }
                        .addOnFailureListener {
                            // Fallback: mensagem genérica
                            onError(Exception("Este email já está em uso."))
                        }
                } else {
                    onError(exception)
                }
            }
    }

    // -----------------------
    // Outros Métodos
    // -----------------------

    fun resetPassword(email: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun signOut() {
        auth.signOut()
        if (::googleClient.isInitialized) {
            googleClient.signOut()
        }
    }

    fun isUserAuthenticated(): Boolean = auth.currentUser != null
    fun getCurrentUser() = auth.currentUser

    /**
     * NOVO: Retorna lista de provedores vinculados à conta atual.
     * Útil para mostrar ao usuário quais métodos de login estão ativos.
     */
    fun getLinkedProviders(): List<String> {
        return auth.currentUser?.providerData?.map { it.providerId } ?: emptyList()
    }

    /**
     * NOVO: Verifica se a conta tem senha definida.
     */
    fun hasPasswordProvider(): Boolean {
        return getLinkedProviders().contains(EmailAuthProvider.PROVIDER_ID)
    }

    /**
     * NOVO: Verifica se a conta tem Google vinculado.
     */
    fun hasGoogleProvider(): Boolean {
        return getLinkedProviders().contains(GoogleAuthProvider.PROVIDER_ID)
    }

    fun updateEmail(newEmail: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        // updateEmail está depreciado. O Firebase recomenda verifyBeforeUpdateEmail para maior segurança.
        // No entanto, para manter a funcionalidade idêntica sem exigir verificação imediata:
        auth.currentUser?.verifyBeforeUpdateEmail(newEmail)
            ?.addOnSuccessListener { onSuccess() }
            ?.addOnFailureListener { exception ->
                // Fallback para o método antigo caso o novo não seja desejado ou falhe por regras específicas
                auth.currentUser?.updateEmail(newEmail)
                    ?.addOnSuccessListener { onSuccess() }
                    ?.addOnFailureListener { onError(it) }
            }
    }

    fun updatePassword(newPassword: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        auth.currentUser?.updatePassword(newPassword)
            ?.addOnSuccessListener {
                Log.d(TAG, "Senha atualizada com sucesso")
                onSuccess()
            }
            ?.addOnFailureListener { onError(it) }
    }

    fun updateDisplayName(newName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(newName)
            .build()

        auth.currentUser?.updateProfile(profileUpdates)
            ?.addOnSuccessListener { onSuccess() }
            ?.addOnFailureListener { onError(it) }
    }

    fun deleteAccount(onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        auth.currentUser?.delete()
            ?.addOnSuccessListener {
                if (::googleClient.isInitialized) {
                    googleClient.signOut()
                }
                onSuccess()
            }
            ?.addOnFailureListener { onError(it) }
    }
}
