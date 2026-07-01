package com.example.fittostrava.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fittostrava.FitnessActivityDetail
import com.example.fittostrava.RouteStatus

@Composable
fun ActivityDetailScreen(
    detail: FitnessActivityDetail,
    onBack: () -> Unit,
    onRequestRoute: () -> Unit,
    onGenerateTcx: () -> Unit,
    onShareTcx: () -> Unit,
    isStravaConnected: Boolean,
    isConnectingToStrava: Boolean,
    isDetailLoading: Boolean,
    isGeneratingTcx: Boolean,
    isUploadingToStrava: Boolean,
    onConnectToStrava: () -> Unit,
    onUploadToStrava: () -> Unit,
    routeMessage: String? = null,
    errorMessage: String? = null,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Voltar")
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = detail.activity.exerciseType,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = formatDate(detail.activity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            LoadingMessage(
                visible = isDetailLoading,
                message = "Recarregando dados da atividade...",
            )

            DetailRow("Duracao", formatDuration(detail.activity))
            DetailRow("Distancia", formatDistance(detail.activity.distanceMeters))
            DetailRow("Registros de distancia", detail.distanceRecordCount.toString())
            DetailRow("Amostras cardiacas", detail.heartRateSampleCount.toString())
            DetailRow("FC media", detail.averageHeartRateBpm?.let { "$it bpm" } ?: "Indisponivel")
            DetailRow("FC minima", detail.minHeartRateBpm?.let { "$it bpm" } ?: "Indisponivel")
            DetailRow("FC maxima", detail.maxHeartRateBpm?.let { "$it bpm" } ?: "Indisponivel")
            DetailRow("Calorias", detail.caloriesKcal?.let { "${it.toInt()} kcal" } ?: "Indisponivel")

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Rota",
                style = MaterialTheme.typography.titleMedium,
            )
            when (detail.routeStatus) {
                RouteStatus.Available -> {
                    DetailRow("Status", "Disponivel")
                    DetailRow("Pontos", detail.routePointCount?.toString() ?: "0")
                }
                RouteStatus.ConsentRequired -> {
                    Text(
                        text = "A rota exige consentimento especifico para esta atividade.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onRequestRoute) {
                        Text("Solicitar rota")
                    }
                }
                RouteStatus.NoData -> {
                    Text(
                        text = "O Health Connect nao retornou rota para esta atividade.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            routeMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            Button(
                onClick = onGenerateTcx,
                enabled = !isGeneratingTcx,
            ) {
                Text(if (isGeneratingTcx) "Gerando TCX..." else "Gerar TCX")
            }
            LoadingMessage(
                visible = isGeneratingTcx,
                message = "Gerando arquivo TCX...",
            )

            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            detail.tcxExportResult?.let { result ->
                Text(
                    text = "TCX gerado",
                    style = MaterialTheme.typography.titleMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Caminho completo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = result.filePath,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                DetailRow("Pontos de rota", result.routePointCount.toString())
                DetailRow("Amostras cardiacas", result.heartRateSampleCount.toString())
                Button(onClick = onShareTcx) {
                    Text("Compartilhar TCX")
                }
            }

            HorizontalDivider()

            Text(
                text = "Strava",
                style = MaterialTheme.typography.titleMedium,
            )
            DetailRow(
                "Status",
                if (isStravaConnected) "Conectado" else "Nao conectado",
            )
            OutlinedButton(
                onClick = onConnectToStrava,
                enabled = !isConnectingToStrava && !isUploadingToStrava,
            ) {
                Text(if (isConnectingToStrava) "Conectando..." else "Conectar ao Strava")
            }
            LoadingMessage(
                visible = isConnectingToStrava,
                message = "Concluindo autorizacao do Strava...",
            )
            Button(
                onClick = onUploadToStrava,
                enabled = isStravaConnected && !isUploadingToStrava,
            ) {
                Text(if (isUploadingToStrava) "Enviando..." else "Enviar para o Strava")
            }
            LoadingMessage(
                visible = isUploadingToStrava,
                message = "Enviando atividade para o Strava...",
            )
            detail.stravaUploadResult?.let { result ->
                Text(
                    text = "Enviado para o Strava",
                    style = MaterialTheme.typography.titleMedium,
                )
                DetailRow("Upload", result.uploadId)
                DetailRow("Atividade", result.activityId)
                DetailRow("Tipo", result.sportType)
            }
        }
    }
}

@Composable
private fun LoadingMessage(visible: Boolean, message: String) {
    if (!visible) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
