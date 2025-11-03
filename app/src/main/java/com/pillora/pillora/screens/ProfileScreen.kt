package com.pillora.pillora.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.pillora.pillora.repository.AuthRepository
import com.pillora.pillora.navigation.Screen
import kotlin.text.isNotBlank

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val currentUser = remember { AuthRepository.getCurrentUser() }

    // 🔒 Se não houver usuário, redireciona para login
    if (currentUser == null) {
        LaunchedEffect(Unit) {
            navController.navigate("auth") {
                popUpTo(Screen.Home.route) { inclusive = true }
            }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    var showEmailDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(48.dp),
                windowInsets = WindowInsets(0),
                title = { Text("Meu Perfil") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Informações da Conta",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    InfoRow(label = "ID de Usuário:", value = currentUser.uid)
                    InfoRow(
                        label = "Nome de Usuário:",
                        value = currentUser.displayName ?: "Não definido"
                    )
                    InfoRow(label = "Email:", value = currentUser.email ?: "Não disponível")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { showEmailDialog = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Alterar Email")
            }

            Button(
                onClick = { showPasswordDialog = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Alterar Senha")
            }

            // Botão de Logout
            Button(
                onClick = {
                    AuthRepository.signOut()
                    navController.navigate("auth") {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Sair", color = MaterialTheme.colorScheme.onError)
            }
        }
    }

    if (showEmailDialog) {
        ChangeEmailDialog(
            onDismiss = { showEmailDialog = false },
            onEmailUpdate = { newEmail ->
                AuthRepository.updateEmail(
                    newEmail,
                    onSuccess = {
                        showEmailDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Email alterado com sucesso! Verifique seu novo email.")
                        }
                    },
                    onError = { e ->
                        showEmailDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Erro ao alterar email: ${e.message}")
                        }
                    }
                )
            }
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onPasswordUpdate = { newPassword ->
                AuthRepository.updatePassword(
                    newPassword,
                    onSuccess = {
                        showPasswordDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Senha alterada com sucesso! Faça login novamente.")
                        }
                        AuthRepository.signOut()
                        navController.navigate("auth") {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    onError = { e ->
                        showPasswordDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Erro ao alterar senha: ${e.message}")
                        }
                    }
                )
            }
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
    }
}

@Composable
fun ChangeEmailDialog(onDismiss: () -> Unit, onEmailUpdate: (String) -> Unit) {
    var newEmail by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alterar Email") },
        text = {
            Column {
                Text("Digite seu novo endereço de email.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it },
                    label = { Text("Novo Email") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onEmailUpdate(newEmail) }, enabled = newEmail.isNotBlank()) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ChangePasswordDialog(onDismiss: () -> Unit, onPasswordUpdate: (String) -> Unit) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val passwordsMatch = newPassword == confirmPassword && newPassword.length >= 6

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alterar Senha") },
        text = {
            Column {
                Text("Digite sua nova senha (mínimo 6 caracteres).")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Nova Senha") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirmar Senha") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = confirmPassword.isNotBlank() && !passwordsMatch
                )
                if (confirmPassword.isNotBlank() && !passwordsMatch) {
                    Text(
                        "As senhas não coincidem ou são muito curtas.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPasswordUpdate(newPassword) }, enabled = passwordsMatch) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
