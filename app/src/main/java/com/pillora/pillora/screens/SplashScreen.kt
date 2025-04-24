package com.pillora.pillora.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pillora.pillora.R
import com.pillora.pillora.repository.AuthRepository
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    navController: NavController,
    acceptedTerms: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Logo
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "Pillora Logo",
            modifier = Modifier.size(150.dp)
        )

        // Loading indicator
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        )

        // Check terms acceptance and authentication status
        LaunchedEffect(key1 = true) {
            delay(1500) // Show splash for 1.5 seconds

            // First check if terms are accepted
            if (!acceptedTerms) {
                navController.navigate("terms") {
                    popUpTo("splash") { inclusive = true }
                }
                return@LaunchedEffect
            }

            // Then check authentication status
            if (AuthRepository.isUserAuthenticated()) {
                navController.navigate("home") {
                    popUpTo("splash") { inclusive = true }
                }
            } else {
                navController.navigate("auth") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
    }
}
