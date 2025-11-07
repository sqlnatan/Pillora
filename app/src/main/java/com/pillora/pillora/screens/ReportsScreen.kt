package com.pillora.pillora.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.viewmodel.ReportFile
import com.pillora.pillora.viewmodel.ReportsViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(navController: NavController) {
    val context = LocalContext.current
    val application = context.applicationContext as PilloraApplication

    // ✅ Corrigido: agora usa "application", não mais "context"
    val reportsViewModel: ReportsViewModel = viewModel(
        factory = ReportsViewModel.provideFactory(
            application = application,
            userPreferences = application.userPreferences
        )
    )

    val reportFiles by reportsViewModel.reportFiles.collectAsState()
    val isPremium = true // temporário até o sistema Premium estar pronto

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relatórios") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isPremium) {
                PremiumContent(
                    reportsViewModel = reportsViewModel,
                    reportFiles = reportFiles,
                    context = context
                )
            } else {
                FreeContent()
            }
        }
    }
}

@Composable
fun PremiumContent(
    reportsViewModel: ReportsViewModel,
    reportFiles: List<ReportFile>,
    context: Context
) {
    Button(
        onClick = {
            reportsViewModel.generateReport("Relatorio_Pillora")
            Toast.makeText(context, "Geração de relatório iniciada...", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Gerar Novo Relatório (PDF)")
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        "Relatórios Gerados:",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (reportFiles.isEmpty()) {
        Text("Nenhum relatório gerado ainda.", modifier = Modifier.padding(vertical = 24.dp))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(reportFiles) { report ->
                ReportFileItem(
                    report = report,
                    onDelete = { reportsViewModel.deleteReport(report) },
                    onShare = { shareReport(context, report) },
                    onOpen = { openReport(context, report) }
                )
            }
        }
    }
}

@Composable
fun FreeContent() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Funcionalidade Premium",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "A geração e o gerenciamento de relatórios em PDF são exclusivos para usuários Premium.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { /* TODO: Navegar para tela de upgrade */ }) {
                Text("Fazer Upgrade para Premium")
            }
        }
    }
}

@Composable
fun ReportFileItem(
    report: ReportFile,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val formattedDate = remember(report.lastModified) { dateFormat.format(Date(report.lastModified)) }
    val formattedSize = remember(report.size) { "${"%.2f".format(report.size / 1024.0)} KB" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(report.name, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Gerado em: $formattedDate", style = MaterialTheme.typography.bodySmall)
                Text("Tamanho: $formattedSize", style = MaterialTheme.typography.bodySmall)
            }

            Row {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Compartilhar")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir")
                }
            }
        }
    }
}

// --- Funções de Ação ---

fun openReport(context: Context, report: ReportFile) {
    val file = File(report.path)
    if (!file.exists()) {
        Toast.makeText(context, "Arquivo não encontrado.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }

        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Não foi possível abrir o arquivo. Verifique se há um leitor de PDF instalado.",
            Toast.LENGTH_LONG
        ).show()
    }
}

fun shareReport(context: Context, report: ReportFile) {
    val file = File(report.path)
    if (!file.exists()) {
        Toast.makeText(context, "Arquivo não encontrado.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "application/pdf"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Relatório"))
    } catch (_: Exception) {
        Toast.makeText(context, "Erro ao compartilhar o arquivo.", Toast.LENGTH_LONG).show()
    }
}
