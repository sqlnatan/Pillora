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
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { exception ->
                    // Se houver colisão (e-mail já existe com outro provedor)
                    if (exception is FirebaseAuthUserCollisionException) {
                        Log.d(TAG, "Colisão de usuário detectada no Google Sign-In")
                        linkGoogleWithExistingAccount(credential, onSuccess, onError)
                    } else {
                        onError(exception)
                    }
                }
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Vincula a credencial do Google a uma conta de e-mail existente.
     */
    private fun linkGoogleWithExistingAccount(
        credential: AuthCredential,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Para vincular, o Firebase exige que o usuário esteja "logado" ou que usemos fetchSignInMethods
        // Mas a forma mais moderna é tratar o erro de colisão.
        // No caso do Google, se o e-mail for o mesmo, o Firebase pode permitir o vínculo automático
        // se a configuração "One account per email address" estiver ativa no Console.

        // Tentativa de vínculo manual se necessário:
        auth.currentUser?.linkWithCredential(credential)
            ?.addOnSuccessListener { onSuccess() }
            ?.addOnFailureListener { onError(it) }
    }

    // -----------------------
    // Email & Password Login
    // -----------------------

    fun signIn(email: String, password: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { exception ->
                // Se o erro for que a conta existe mas com outro provedor (Google)
                // O Firebase geralmente não lança Collision no signIn, mas sim no signUp.
                // No signIn, ele apenas diz que a senha está errada se você tentar entrar com senha
                // em uma conta que só tem Google.
                onError(exception)
            }
    }

    fun signUp(email: String, password: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { exception ->
                if (exception is FirebaseAuthUserCollisionException) {
                    // Se o usuário já existe (ex: via Google), tentamos vincular a senha à conta
                    Log.d(TAG, "Colisão detectada no SignUp. Tentando vincular e-mail/senha.")
                    // Nota: Para vincular e-mail/senha a uma conta Google existente,
                    // o usuário precisaria estar logado via Google primeiro.
                    onError(Exception("Este e-mail já está em uso com outro método de login (Google). Faça login com o Google primeiro."))
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
            ?.addOnSuccessListener { onSuccess() }
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
