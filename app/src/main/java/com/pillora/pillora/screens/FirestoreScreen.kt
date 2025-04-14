package com.pillora.pillora.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
    val firestore = FirestoreRepository()
    val coroutineScope = rememberCoroutineScope()

    var users by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Usuários cadastrados no Firestore")
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            coroutineScope.launch {
                isLoading = true
                users = firestore.getUsers()
                isLoading = false
            }
        }) {
            Text("Carregar usuários")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Text("Carregando...")
        } else {
            LazyColumn {
                items(users) { user ->
                    val name = user["name"] as? String ?: "Desconhecido"
                    val age = user["age"]?.toString() ?: "Idade indefinida"
                    Text(text = "Nome: $name | Idade: $age")
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            navController.popBackStack()
        }) {
            Text("Voltar para Home")
        }
    }
}
