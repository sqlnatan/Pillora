package com.pillora.pillora.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout // Adicione esta importação
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.repository.AuthRepository

@Composable
fun HomeScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        // Botão de logout no canto superior direito
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = {
                // Desloga do Firebase Auth
                AuthRepository.signOut()
                // Navega para a tela de autenticação e limpa a pilha
                navController.navigate("auth") {
                    popUpTo(Screen.Home.route) { inclusive = true }
                }
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout, // Alterado nesta linha
                    contentDescription = "Logout"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Conteúdo principal centralizado
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Tela Home")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                navController.navigate(Screen.Firestore.route)
            }) {
                Text("Ir para Firestore")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                navController.navigate(Screen.MedicineForm.route)
            }) {
                Text("Cadastrar Medicamento")
            }
        }
    }
}