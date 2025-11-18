package com.pillora.pillora.repository

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest

object AuthRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private lateinit var googleClient: GoogleSignInClient

    // -----------------------
    // Google Sign-In
    // -----------------------

    /**
     * Inicializa e lança o login do Google usando ActivityResultLauncher
     */
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

    /**
     * Trata o resultado do Google Sign-In
     */
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
            auth.signInWithCredential(credential)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it) }
        } catch (e: Exception) {
            onError(e)
        }
    }

    // -----------------------
    // Email & Password Login
    // -----------------------

    fun signIn(email: String, password: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun signUp(email: String, password: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

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
        auth.currentUser?.updateEmail(newEmail)
            ?.addOnSuccessListener { onSuccess() }
            ?.addOnFailureListener { onError(it) }
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
}
