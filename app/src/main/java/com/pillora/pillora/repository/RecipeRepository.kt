package com.pillora.pillora.repository

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.pillora.pillora.model.Recipe
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object RecipeRepository {
    private const val TAG = "RecipeRepository"
    private const val COLLECTION_RECIPES = "recipes"

    private val db by lazy { Firebase.firestore }

    // Obter o ID do usuário atual
    private val currentUserId: String?
        get() = Firebase.auth.currentUser?.uid

    // Obter a coleção de receitas filtrada pelo usuário atual
    private val recipesCollection by lazy {
        db.collection(COLLECTION_RECIPES)
    }

    fun saveRecipe(
        recipe: Recipe,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            onError(Exception("Usuário não autenticado"))
            return
        }
        // Adicionar o ID do usuário à receita antes de salvar
        val recipeWithUserId = recipe.copy(userId = userId)

        recipesCollection.add(recipeWithUserId)
            .addOnSuccessListener {
                Log.d(TAG, "Receita salva com sucesso.")
                onSuccess()
            }
            .addOnFailureListener {
                Log.e(TAG, "Erro ao salvar receita", it)
                onError(it)
            }
    }

    fun updateRecipe(
        recipeId: String,
        recipe: Recipe,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userId = currentUserId
        // Verificar se o usuário está logado e se a receita pertence a ele
        if (userId == null || recipe.userId != userId) {
            onError(Exception("Erro de autorização ou dados inválidos para atualização"))
            return
        }
        // Garantir que o ID do usuário seja mantido na atualização
        val recipeWithUserId = recipe.copy(userId = userId)

        recipesCollection.document(recipeId)
            .set(recipeWithUserId) // Usar set para sobrescrever completamente
            .addOnSuccessListener {
                Log.d(TAG, "Receita atualizada com sucesso: $recipeId")
                onSuccess()
            }
            .addOnFailureListener {
                Log.e(TAG, "Erro ao atualizar receita: $recipeId", it)
                onError(it)
            }
    }

    fun deleteRecipe(
        recipeId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            onError(Exception("Usuário não autenticado"))
            return
        }
        // Opcional: Verificar propriedade antes de deletar (requer leitura prévia)
        // Por simplicidade, deletamos diretamente. Regras de segurança do Firestore devem garantir a autorização.
        recipesCollection.document(recipeId)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Receita deletada com sucesso: $recipeId")
                onSuccess()
            }
            .addOnFailureListener {
                Log.e(TAG, "Erro ao deletar receita: $recipeId", it)
                onError(it)
            }
    }

    fun getRecipeById(
        recipeId: String,
        onSuccess: (Recipe?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            onError(Exception("Usuário não autenticado"))
            return
        }
        recipesCollection.document(recipeId)
            .get()
            .addOnSuccessListener { snapshot ->
                try {
                    val recipe = snapshot.toObject(Recipe::class.java)
                    // Verificar se a receita pertence ao usuário atual
                    if (recipe != null && recipe.userId == userId) {
                        Log.d(TAG, "Receita encontrada: $recipeId")
                        onSuccess(recipe.copy(id = snapshot.id)) // Incluir o ID do documento
                    } else {
                        Log.w(TAG, "Receita não encontrada ou não pertence ao usuário: $recipeId")
                        onSuccess(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao converter snapshot para Recipe: $recipeId", e)
                    onError(e)
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Erro ao buscar receita: $recipeId", it)
                onError(it)
            }
    }

    // Retorna um Flow para atualizações em tempo real
    fun getAllRecipesFlow(): Flow<List<Recipe>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "Usuário não autenticado, retornando fluxo vazio")
            trySend(emptyList())
            close(Exception("Usuário não autenticado"))
            return@callbackFlow
        }

        Log.d(TAG, "Configurando listener de snapshots para receitas do usuário: $userId")
        val listenerRegistration: ListenerRegistration = recipesCollection
            .whereEqualTo("userId", userId)
            // .orderBy("prescriptionDate", Query.Direction.DESCENDING) // Opcional: Ordenar por data
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erro ao ouvir atualizações de receitas", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val recipes = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(Recipe::class.java)?.copy(id = doc.id) // Incluir ID
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao converter documento ${doc.id} para Recipe", e)
                            null
                        }
                    }
                    Log.d(TAG, "Snapshot recebido. Encontradas ${recipes.size} receitas.")
                    trySend(recipes)
                } else {
                    Log.d(TAG, "Snapshot de receitas nulo")
                    trySend(emptyList())
                }
            }

        awaitClose {
            Log.d(TAG, "Fechando listener de snapshots de receitas para usuário: $userId")
            listenerRegistration.remove()
        }
    }
}

