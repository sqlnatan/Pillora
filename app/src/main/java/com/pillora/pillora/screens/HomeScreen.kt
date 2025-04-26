package com.pillora.pillora.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.repository.AuthRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pillora") },
                actions = {
                    IconButton(onClick = {
                        // Desloga do Firebase Auth
                        AuthRepository.signOut()
                        // Navega para a tela de autenticação e limpa a pilha
                        navController.navigate("auth") {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Bem-vindo ao Pillora",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Selecione uma opção abaixo para testar as funcionalidades",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Seção de Medicamentos
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Medicamentos",
                        style = MaterialTheme.typography.titleLarge
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    HomeMenuItem(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = "Lista de Medicamentos",
                        description = "Visualize, edite e exclua medicamentos",
                        onClick = { navController.navigate(Screen.MedicineList.route) }
                    )

                    HomeMenuItem(
                        icon = Icons.Default.Add,
                        title = "Cadastrar Medicamento",
                        description = "Adicione um novo medicamento",
                        onClick = { navController.navigate(Screen.MedicineForm.route) }
                    )
                }
            }

            // Seção de Testes
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Outras Funcionalidades",
                        style = MaterialTheme.typography.titleLarge
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    HomeMenuItem(
                        icon = Icons.Default.Storage,
                        title = "Firestore",
                        description = "Teste de conexão com o Firestore",
                        onClick = { navController.navigate(Screen.Firestore.route) }
                    )

                    HomeMenuItem(
                        icon = Icons.Default.Description,
                        title = "Termos de Uso",
                        description = "Visualize os termos de uso",
                        onClick = { navController.navigate(Screen.Terms.route) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun HomeMenuItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
