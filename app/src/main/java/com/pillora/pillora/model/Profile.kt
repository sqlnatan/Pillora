package com.pillora.pillora.model

import com.google.firebase.firestore.DocumentId

/**
 * Represents a user profile (main user or dependent).
 */
data class Profile(
    @DocumentId
    val id: String? = null, // Firestore document ID
    val userId: String = "", // Firebase Auth User ID of the manager
    val name: String = "",
    val relationship: String = "", // e.g., "Eu", "Mãe", "Filho"
    // val birthDate: String = "" // Optional: Consider adding later if needed
)
