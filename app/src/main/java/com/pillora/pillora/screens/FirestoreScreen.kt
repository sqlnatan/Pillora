package com.pillora.pillora.screens

import android.graphics.drawable.Icon
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pillora.pillora.data.FirestoreRepository
import kotlinx.coroutines.launch

@Composable
fun FirestoreScreen(navController: NavController) {
    val repository = FirestoreRepository()
    val scope = rememberCoroutineScope()

    // Lista de pares (documentId, dados do usuário)
    var users by remember { mutableStateOf<List<Pair<String, Map<String, Any>>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Usuários cadastrados no Firestore")
        Spacer(modifier = Modifier.height(16.dp))

        // Botão para carregar usuários
        Button(onClick = {
            scope.launch {
                isLoading = true
                users = repository.getUsersWithId()
                isLoading = false
            }
        }) {
            Text("Carregar usuários")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Text("Carregando...")
        } else {
            // Aqui começa o LazyColumn
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cole aqui o bloco items(users) { ... }
                items(users) { (id, user) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Nome: ${user["name"]} | Idade: ${user["age"]}")
                        IconButton(onClick = {
                            scope.launch {
                                repository.deleteUser(id)
                                users = repository.getUsersWithId()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Excluir usuário"
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = { navController.popBackStack() }) {
            Text("Voltar para Home")
        }
    }
}
