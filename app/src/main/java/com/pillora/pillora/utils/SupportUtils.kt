package com.pillora.pillora.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.pillora.pillora.R
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// --- Funções de Utilidade ---

/**
 * Abre o aplicativo de e-mail com o endereço de destino preenchido.
 */
fun openEmail(context: Context, email: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:") // Somente aplicativos de e-mail devem lidar com isso
        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        putExtra(Intent.EXTRA_SUBJECT, "Suporte - App Pillora")
    }
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

/**
 * Abre o navegador com a URL fornecida.
 */
fun openWebsite(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

// --- Composable do Dialog ---

@Composable
fun SupportDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val email = "pillora.app@gmail.com"
    val website = "https://www.pillora.com.br"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Suporte Pillora",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Entre em contato conosco para dúvidas, sugestões ou problemas.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // E-mail
                TextButton(
                    onClick = { openEmail(context, email) },
                    modifier = Modifier.clickable { openEmail(context, email) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "E-mail",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = email,
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Website
                TextButton(
                    onClick = { openWebsite(context, website) },
                    modifier = Modifier.clickable { openWebsite(context, website) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Website",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = website,
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
        icon = {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Logo Pillora",
                modifier = Modifier.height(40.dp)
            )
        }
    )
}
