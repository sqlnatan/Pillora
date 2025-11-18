package com.pillora.pillora.screens

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pillora.pillora.R
import com.pillora.pillora.repository.AuthRepository

enum class AuthMode {
    LOGIN, REGISTER, RESET_PASSWORD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }
    var isLoading by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }

    var emailError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    var confirmPasswordError by remember { mutableStateOf("") }

    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Web Client ID do Firebase - substitua pelo seu
    val webClientId = "426649307737-na96tus1vjtdb49imu5gpum40sgnsgf7.apps.googleusercontent.com"

    // Launcher para login com Google
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        AuthRepository.handleGoogleSignInResult(
            result = result,
            onSuccess = {
                isGoogleLoading = false
                Toast.makeText(context, "Login com Google realizado com sucesso!", Toast.LENGTH_SHORT).show()
                navController.navigate("home") {
                    popUpTo("auth") { inclusive = true }
                }
            },
            onError = { exception ->
                isGoogleLoading = false
                Toast.makeText(context, "Erro ao fazer login com Google: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(48.dp),
                title = {
                    Text(
                        when (authMode) {
                            AuthMode.LOGIN -> "Entrar"
                            AuthMode.REGISTER -> "Criar Conta"
                            AuthMode.RESET_PASSWORD -> "Recuperar Senha"
                        }
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Pillora Logo",
                modifier = Modifier.size(120.dp)
            )

            // Title
            Text(
                text = when (authMode) {
                    AuthMode.LOGIN -> "Bem-vindo ao Pillora"
                    AuthMode.REGISTER -> "Crie sua conta"
                    AuthMode.RESET_PASSWORD -> "Recupere sua senha"
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            // Subtitle
            Text(
                text = when (authMode) {
                    AuthMode.LOGIN -> "Entre para acessar seus medicamentos e lembretes"
                    AuthMode.REGISTER -> "Registre-se para começar a gerenciar seus medicamentos"
                    AuthMode.RESET_PASSWORD -> "Enviaremos um email para redefinir sua senha"
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Email field
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    emailError = ""
                },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                isError = emailError.isNotEmpty(),
                supportingText = {
                    if (emailError.isNotEmpty()) {
                        Text(text = emailError, color = MaterialTheme.colorScheme.error)
                    }
                },
                singleLine = true
            )

            // Password field (not shown for reset password)
            if (authMode != AuthMode.RESET_PASSWORD) {
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = ""
                    },
                    label = { Text("Senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    isError = passwordError.isNotEmpty(),
                    supportingText = {
                        if (passwordError.isNotEmpty()) {
                            Text(text = passwordError, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true
                )
            }

            // Confirm password field (only for register)
            if (authMode == AuthMode.REGISTER) {
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        confirmPasswordError = ""
                    },
                    label = { Text("Confirmar Senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    isError = confirmPasswordError.isNotEmpty(),
                    supportingText = {
                        if (confirmPasswordError.isNotEmpty()) {
                            Text(text = confirmPasswordError, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary action button
            Button(
                onClick = {
                    var isValid = true

                    if (email.isBlank()) {
                        emailError = "Email é obrigatório"
                        isValid = false
                    } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        emailError = "Email inválido"
                        isValid = false
                    }

                    if (authMode != AuthMode.RESET_PASSWORD) {
                        if (password.isBlank()) {
                            passwordError = "Senha é obrigatória"
                            isValid = false
                        } else if (password.length < 6) {
                            passwordError = "Senha deve ter pelo menos 6 caracteres"
                            isValid = false
                        }
                    }

                    if (authMode == AuthMode.REGISTER) {
                        if (confirmPassword.isBlank()) {
                            confirmPasswordError = "Confirmação de senha é obrigatória"
                            isValid = false
                        } else if (password != confirmPassword) {
                            confirmPasswordError = "As senhas não coincidem"
                            isValid = false
                        }
                    }

                    if (!isValid || isLoading) return@Button

                    isLoading = true

                    when (authMode) {
                        AuthMode.LOGIN -> {
                            AuthRepository.signIn(
                                email = email,
                                password = password,
                                onSuccess = {
                                    isLoading = false
                                    Toast.makeText(context, "Login realizado com sucesso!", Toast.LENGTH_SHORT).show()
                                    navController.navigate("home") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                },
                                onError = { exception ->
                                    isLoading = false
                                    Toast.makeText(context, "Erro ao fazer login: ${exception.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                        AuthMode.REGISTER -> {
                            AuthRepository.signUp(
                                email = email,
                                password = password,
                                onSuccess = {
                                    isLoading = false
                                    Toast.makeText(context, "Conta criada com sucesso!", Toast.LENGTH_SHORT).show()
                                    navController.navigate("home") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                },
                                onError = { exception ->
                                    isLoading = false
                                    Toast.makeText(context, "Erro ao criar conta: ${exception.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                        AuthMode.RESET_PASSWORD -> {
                            AuthRepository.resetPassword(
                                email = email,
                                onSuccess = {
                                    isLoading = false
                                    Toast.makeText(context, "Email de recuperação enviado!", Toast.LENGTH_SHORT).show()
                                    authMode = AuthMode.LOGIN
                                },
                                onError = { exception ->
                                    isLoading = false
                                    Toast.makeText(context, "Erro ao enviar email: ${exception.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && !isGoogleLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text(
                        when (authMode) {
                            AuthMode.LOGIN -> "Entrar"
                            AuthMode.REGISTER -> "Criar Conta"
                            AuthMode.RESET_PASSWORD -> "Enviar Email"
                        }
                    )
                }
            }

            // Divider com texto "ou"
            if (authMode != AuthMode.RESET_PASSWORD) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "ou",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                // Botão de login com Google
                OutlinedButton(
                    onClick = {
                        if (isGoogleLoading || isLoading) return@OutlinedButton

                        if (activity != null) {
                            isGoogleLoading = true
                            try {
                                AuthRepository.launchGoogleSignIn(
                                    activity = activity,
                                    webClientId = webClientId,
                                    launcher = googleSignInLauncher
                                )
                            } catch (e: Exception) {
                                isGoogleLoading = false
                                Toast.makeText(context, "Erro ao iniciar login com Google: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Erro ao inicializar login com Google", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && !isGoogleLoading,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    if (isGoogleLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_google),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (authMode == AuthMode.LOGIN) "Entrar com Google" else "Cadastrar com Google")
                        }
                    }
                }
            }

            // Secondary actions
            when (authMode) {
                AuthMode.LOGIN -> {
                    TextButton(onClick = { authMode = AuthMode.RESET_PASSWORD }, enabled = !isLoading && !isGoogleLoading) {
                        Text("Esqueceu a senha?")
                    }
                    TextButton(onClick = { authMode = AuthMode.REGISTER }, enabled = !isLoading && !isGoogleLoading) {
                        Text("Não tem uma conta? Cadastre-se")
                    }
                }
                AuthMode.REGISTER -> {
                    TextButton(onClick = { authMode = AuthMode.LOGIN }, enabled = !isLoading && !isGoogleLoading) {
                        Text("Já tem uma conta? Entre")
                    }
                }
                AuthMode.RESET_PASSWORD -> {
                    TextButton(onClick = { authMode = AuthMode.LOGIN }, enabled = !isLoading && !isGoogleLoading) {
                        Text("Voltar para o login")
                    }
                }
            }
        }
    }
}
