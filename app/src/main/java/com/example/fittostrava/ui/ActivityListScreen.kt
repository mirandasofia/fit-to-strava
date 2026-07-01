package com.example.fittostrava.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fittostrava.FitToStravaViewModel
import com.example.fittostrava.FitnessActivity
import com.example.fittostrava.FitnessActivityDetail
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun ActivityListScreen(
    viewModel: FitToStravaViewModel,
    onRequestPermissions: () -> Unit,
    onReloadActivities: () -> Unit,
    onOpenDetail: (FitnessActivity) -> Unit,
    detailContent: @Composable (FitnessActivityDetail, String?, Boolean, Boolean, String?) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val detail = uiState.selectedDetail

    if (detail != null) {
        detailContent(
            detail,
            uiState.errorMessage,
            uiState.isDetailLoading,
            uiState.isGeneratingTcx,
            uiState.routeMessage,
        )
        return
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "FitToStrava",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Atividades dos ultimos 30 dias",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onReloadActivities,
                enabled = !uiState.isLoading,
            ) {
                Text("Recarregar atividades")
            }
            Spacer(modifier = Modifier.height(20.dp))

            when {
                uiState.isLoading -> LoadingContent()
                !uiState.isHealthConnectAvailable -> EmptyMessage(
                    title = "Health Connect indisponivel",
                    message = "Instale ou atualize o Health Connect para ler suas atividades.",
                )
                !uiState.hasExerciseSessionPermission -> PermissionContent(onRequestPermissions)
                uiState.activities.isEmpty() -> EmptyMessage(
                    title = "Nenhuma atividade encontrada",
                    message = "A consulta ao Health Connect terminou sem ExerciseSessionRecord no intervalo dos ultimos 30 dias. Verifique no Logcat a tag FitToStrava para confirmar permissoes e datas consultadas.",
                )
                else -> ActivityList(
                    activities = uiState.activities,
                    onOpenDetail = onOpenDetail,
                )
            }

            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            uiState.permissionMessage?.let { message ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PermissionContent(onRequestPermissions: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmptyMessage(
            title = "Permissoes necessarias",
            message = "Conceda acesso a exercicios. Distancia, frequencia cardiaca e rota serao usadas quando estiverem disponiveis.",
        )
        Button(onClick = onRequestPermissions) {
            Text("Conceder permissoes")
        }
    }
}

@Composable
private fun EmptyMessage(title: String, message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActivityList(
    activities: List<FitnessActivity>,
    onOpenDetail: (FitnessActivity) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        items(activities, key = { it.id }) { activity ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDetail(activity) },
            ) {
                ListItem(
                    headlineContent = {
                        Text(activity.exerciseType)
                    },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(formatDate(activity))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(formatDuration(activity))
                                Text(formatDistance(activity.distanceMeters))
                            }
                        }
                    },
                )
            }
            HorizontalDivider()
        }
    }
}

internal fun formatDate(activity: FitnessActivity): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(activity.startTime)
}

internal fun formatDuration(activity: FitnessActivity): String {
    val minutes = activity.duration.toMinutes()
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) {
        "${hours}h ${remainingMinutes}min"
    } else {
        "${remainingMinutes}min"
    }
}

internal fun formatDistance(distanceMeters: Double?): String {
    if (distanceMeters == null) return "Distancia indisponivel"
    return if (distanceMeters >= 1000.0) {
        val kilometers = distanceMeters / 1000.0
        "${(kilometers * 100.0).roundToInt() / 100.0} km"
    } else {
        "${distanceMeters.roundToInt()} m"
    }
}
