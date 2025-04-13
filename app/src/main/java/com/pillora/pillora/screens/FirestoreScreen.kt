package com.pillora.pillora.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.data.FirestoreRepository
import kotlinx.coroutines.launch

@Composable
fun FirestoreScreen(navController: NavController) {



    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Tela do Firestore")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            navController.popBackStack()
        }) {
            Text("Voltar para Home")
        }
    }
}