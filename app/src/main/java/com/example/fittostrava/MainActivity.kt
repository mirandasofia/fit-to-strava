package com.example.fittostrava

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import com.example.fittostrava.ui.ActivityDetailScreen
import com.example.fittostrava.ui.ActivityListScreen
import com.example.fittostrava.ui.theme.FitToStravaTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private companion object {
        const val TAG = "FitToStrava"
    }

    private val healthConnectManager by lazy {
        HealthConnectManager(applicationContext)
    }

    private val stravaTokenStore by lazy {
        StravaTokenStore(applicationContext)
    }

    private val stravaOAuthClient by lazy {
        StravaOAuthClient(stravaTokenStore)
    }

    private val viewModel: FitToStravaViewModel by viewModels {
        FitToStravaViewModel.Factory(
            healthConnectManager,
            stravaOAuthClient,
            stravaTokenStore,
        )
    }

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        Log.d(TAG, "Resultado do launcher de permissoes: $grantedPermissions")
        viewModel.onPermissionsResult(grantedPermissions)
    }

    private val routeLauncher = registerForActivityResult(
        ExerciseRouteRequestContract()
    ) { route ->
        Log.d(TAG, "Resultado do launcher de rota: route=${route != null}")
        viewModel.onRouteResult(route)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthRedirect(intent)
        setContent {
            FitToStravaTheme {
                ActivityListScreen(
                    viewModel = viewModel,
                    onRequestPermissions = {
                        val requiredPermissions = healthConnectManager.permissions
                        Log.d(TAG, "Antes do request Health Connect: requiredPermissions=$requiredPermissions")
                        permissionLauncher.launch(requiredPermissions)
                        Log.d(TAG, "Depois de chamar requestPermissions.launch(requiredPermissions)")
                    },
                    onReloadActivities = viewModel::refresh,
                    onOpenDetail = { activity ->
                        viewModel.selectActivity(activity)
                    },
                    detailContent = { detail, errorMessage, isDetailLoading, isGeneratingTcx, routeMessage ->
                        ActivityDetailScreen(
                            detail = detail,
                            onBack = viewModel::clearSelection,
                            onRequestRoute = {
                                routeLauncher.launch(detail.activity.id)
                            },
                            onGenerateTcx = viewModel::generateTcx,
                            onShareTcx = {
                                detail.tcxExportResult?.let { shareTcx(File(it.filePath)) }
                            },
                            isStravaConnected = viewModel.uiState.value.isStravaConnected,
                            isConnectingToStrava = viewModel.uiState.value.isConnectingToStrava,
                            isDetailLoading = isDetailLoading,
                            isGeneratingTcx = isGeneratingTcx,
                            isUploadingToStrava = viewModel.uiState.value.isUploadingToStrava,
                            onConnectToStrava = ::connectToStrava,
                            onUploadToStrava = viewModel::uploadToStrava,
                            routeMessage = routeMessage,
                            errorMessage = errorMessage,
                        )
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthRedirect(intent)
    }

    private fun connectToStrava() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, viewModel.authorizationUri()))
        }.onFailure { error ->
            Log.e(TAG, "Erro ao abrir autorizacao do Strava", error)
            viewModel.onStravaAuthorizationLaunchFailed(error)
        }
    }

    private fun handleOAuthRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "fittostrava" || data.host != "oauth") return
        viewModel.onStravaOAuthRedirect(
            code = data.getQueryParameter("code"),
            scope = data.getQueryParameter("scope"),
            error = data.getQueryParameter("error"),
        )
    }

    private fun shareTcx(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.garmin.tcx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Compartilhar TCX"))
    }
}
