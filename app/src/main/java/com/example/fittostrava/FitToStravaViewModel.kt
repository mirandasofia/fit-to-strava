package com.example.fittostrava

import android.util.Log
import androidx.health.connect.client.records.ExerciseRoute
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FitToStravaViewModel(
    private val healthConnectManager: HealthConnectManager,
    private val stravaOAuthClient: StravaOAuthClient,
    private val stravaTokenStore: StravaTokenStore,
) : ViewModel() {
    private companion object {
        const val TAG = "FitToStrava"
    }

    private val _uiState = MutableStateFlow(FitToStravaUiState())
    val uiState: StateFlow<FitToStravaUiState> = _uiState.asStateFlow()

    init {
        refresh()
        refreshStravaConnection()
    }

    fun refresh() {
        viewModelScope.launch {
            Log.d(TAG, "Iniciando busca/refresh de atividades")
            _uiState.update { it.copy(isLoading = true, errorMessage = null, routeMessage = null, permissionMessage = null) }
            runCatching {
                val available = withContext(Dispatchers.IO) { healthConnectManager.isAvailable() }
                val grantedPermissions = withContext(Dispatchers.IO) { healthConnectManager.getGrantedPermissions() }
                val hasAllPermissions = grantedPermissions.containsAll(healthConnectManager.permissions)
                val hasExercisePermission =
                    healthConnectManager.exerciseSessionPermission in grantedPermissions
                Log.d(
                    TAG,
                    "CheckPermissions no refresh: available=$available granted=$grantedPermissions required=${healthConnectManager.permissions} hasExercisePermission=$hasExercisePermission hasAllPermissions=$hasAllPermissions",
                )
                val activities = if (available && hasExercisePermission) {
                    withContext(Dispatchers.IO) { healthConnectManager.readActivities() }
                } else {
                    emptyList()
                }
                Log.d(TAG, "Refresh recebeu ${activities.size} atividades")

                _uiState.update {
                    it.copy(
                        isHealthConnectAvailable = available,
                        hasExerciseSessionPermission = hasExercisePermission,
                        isLoading = false,
                        activities = activities,
                        selectedDetail = null,
                        permissionMessage = if (available && grantedPermissions.isEmpty()) {
                            manualPermissionMessage()
                        } else {
                            null
                        },
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Erro no refresh", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activities = emptyList(),
                        errorMessage = error.localizedMessage ?: error.toString(),
                    )
                }
            }
        }
    }

    fun onPermissionsResult(grantedPermissions: Set<String>) {
        Log.d(
            TAG,
            "Resultado das permissoes concedidas pelo launcher: granted=$grantedPermissions",
        )
        Log.d(TAG, "Apos retorno do launcher: iniciando nova chamada de checkPermissions()")
        checkPermissionsAfterLauncher()
    }

    private fun checkPermissionsAfterLauncher() {
        viewModelScope.launch {
            Log.d(TAG, "Launcher retornou; chamando checkPermissions novamente")
            _uiState.update { it.copy(isLoading = true, errorMessage = null, routeMessage = null, permissionMessage = null) }
            runCatching {
                val available = withContext(Dispatchers.IO) { healthConnectManager.isAvailable() }
                val checkedGrantedPermissions = withContext(Dispatchers.IO) { healthConnectManager.getGrantedPermissions() }
                val hasExercisePermission =
                    healthConnectManager.exerciseSessionPermission in checkedGrantedPermissions
                Log.d(
                    TAG,
                    "Permissoes concedidas apos retorno do launcher: granted=$checkedGrantedPermissions",
                )
                Log.d(
                    TAG,
                    "CheckPermissions apos launcher: available=$available required=${healthConnectManager.permissions} hasExercisePermission=$hasExercisePermission",
                )
                val activities = if (available && hasExercisePermission) {
                    withContext(Dispatchers.IO) { healthConnectManager.readActivities() }
                } else {
                    emptyList()
                }
                _uiState.update {
                    it.copy(
                        isHealthConnectAvailable = available,
                        hasExerciseSessionPermission = hasExercisePermission,
                        isLoading = false,
                        activities = activities,
                        selectedDetail = null,
                        permissionMessage = if (available && checkedGrantedPermissions.isEmpty()) {
                            manualPermissionMessage()
                        } else {
                            null
                        },
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Erro ao checar permissoes apos launcher", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activities = emptyList(),
                        errorMessage = error.localizedMessage ?: error.toString(),
                    )
                }
            }
        }
    }

    fun selectActivity(activity: FitnessActivity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, errorMessage = null, routeMessage = null) }
            runCatching {
                withContext(Dispatchers.IO) { healthConnectManager.readActivityDetail(activity) }
            }.onSuccess { detail ->
                _uiState.update {
                    it.copy(
                        isDetailLoading = false,
                        selectedDetail = detail,
                        routeMessage = routeMessageFor(detail),
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Erro ao selecionar atividade id=${activity.id}", error)
                _uiState.update {
                    it.copy(isDetailLoading = false, errorMessage = error.localizedMessage)
                }
            }
        }
    }

    fun onRouteResult(route: ExerciseRoute?) {
        val currentDetail = uiState.value.selectedDetail ?: return
        if (route == null) {
            Log.d(TAG, "Resultado da solicitacao de rota: route=null")
            _uiState.update {
                it.copy(routeMessage = "A rota nao foi liberada ou nao existe para esta atividade.")
            }
            return
        }
        Log.d(TAG, "Resultado da solicitacao de rota: points=${route.route.size}")
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDetailLoading = true,
                    errorMessage = null,
                    routeMessage = "Rota liberada. Recarregando dados da atividade...",
                )
            }
            runCatching {
                val refreshedDetail = withContext(Dispatchers.IO) {
                    healthConnectManager.readActivityDetail(currentDetail.activity)
                }
                if (refreshedDetail.route == null) {
                    healthConnectManager.detailWithRoute(refreshedDetail, route)
                } else {
                    refreshedDetail
                }
            }.onSuccess { detail ->
                _uiState.update {
                    it.copy(
                        isDetailLoading = false,
                        selectedDetail = detail,
                        routeMessage = routeMessageFor(detail),
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Erro ao recarregar atividade apos consentimento de rota id=${currentDetail.activity.id}", error)
                val detailWithRoute = healthConnectManager.detailWithRoute(currentDetail, route)
                _uiState.update {
                    it.copy(
                        isDetailLoading = false,
                        selectedDetail = detailWithRoute,
                        errorMessage = error.localizedMessage ?: error.toString(),
                        routeMessage = routeMessageFor(detailWithRoute),
                    )
                }
            }
        }
    }

    fun generateTcx() {
        val currentDetail = uiState.value.selectedDetail ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingTcx = true, errorMessage = null) }
            runCatching {
                withContext(Dispatchers.IO) { healthConnectManager.generateTcx(currentDetail) }
            }.onSuccess { updatedDetail ->
                _uiState.update { it.copy(isGeneratingTcx = false, selectedDetail = updatedDetail) }
            }.onFailure { error ->
                Log.e(TAG, "Erro ao gerar TCX da atividade id=${currentDetail.activity.id}", error)
                _uiState.update {
                    it.copy(
                        isGeneratingTcx = false,
                        errorMessage = error.localizedMessage ?: error.toString(),
                    )
                }
            }
        }
    }

    fun refreshStravaConnection() {
        viewModelScope.launch {
            val isConnected = runCatching {
                stravaTokenStore.read()?.hasActivityWriteScope() == true
            }.getOrDefault(false)
            _uiState.update { it.copy(isStravaConnected = isConnected) }
        }
    }

    fun authorizationUri() = stravaOAuthClient.authorizationUri()

    fun onStravaAuthorizationLaunchFailed(error: Throwable) {
        _uiState.update {
            it.copy(
                isConnectingToStrava = false,
                errorMessage = error.localizedMessage ?: error.toString(),
            )
        }
    }

    fun onStravaOAuthRedirect(code: String?, scope: String?, error: String?) {
        if (error != null) {
            _uiState.update {
                it.copy(
                    isConnectingToStrava = false,
                    errorMessage = "Autorizacao do Strava cancelada ou negada: $error",
                )
            }
            return
        }
        if (code.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    isConnectingToStrava = false,
                    errorMessage = "Redirecionamento do Strava sem codigo de autorizacao.",
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectingToStrava = true, errorMessage = null) }
            runCatching {
                stravaOAuthClient.exchangeCode(code, scope.orEmpty())
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isConnectingToStrava = false,
                        isStravaConnected = true,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Erro ao concluir OAuth do Strava", error)
                _uiState.update {
                    it.copy(
                        isConnectingToStrava = false,
                        isStravaConnected = false,
                        errorMessage = error.localizedMessage ?: error.toString(),
                    )
                }
            }
        }
    }

    fun uploadToStrava() {
        val currentDetail = uiState.value.selectedDetail ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingToStrava = true, errorMessage = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val accessToken = stravaOAuthClient.getValidAccessToken()
                    val detailWithTcx = currentDetail.tcxExportResult?.let { currentDetail }
                        ?: healthConnectManager.generateTcx(currentDetail)
                    val tcxFile = File(requireNotNull(detailWithTcx.tcxExportResult).filePath)
                    val sportType = healthConnectManager.stravaSportTypeFor(detailWithTcx.activity.exerciseType)
                    val result = StravaApiClient(accessToken).uploadTcxAndUpdateActivity(
                        file = tcxFile,
                        sportType = sportType,
                        name = detailWithTcx.activity.exerciseType,
                        description = "Importado do Samsung Health via FitToStrava",
                        externalId = "fittostrava_${detailWithTcx.activity.id}.tcx",
                    )
                    detailWithTcx.copy(stravaUploadResult = result)
                }
            }.onSuccess { updatedDetail ->
                _uiState.update {
                    it.copy(
                        isUploadingToStrava = false,
                        selectedDetail = updatedDetail,
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Erro ao enviar atividade para o Strava id=${currentDetail.activity.id}", error)
                _uiState.update {
                    it.copy(
                        isUploadingToStrava = false,
                        errorMessage = error.localizedMessage ?: error.toString(),
                    )
                }
            }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedDetail = null, routeMessage = null, errorMessage = null) }
    }

    private fun routeMessageFor(detail: FitnessActivityDetail): String =
        when (detail.routeStatus) {
            RouteStatus.Available -> "Rota carregada com ${detail.routePointCount ?: 0} pontos."
            RouteStatus.ConsentRequired -> "Esta atividade tem rota, mas o Health Connect exige consentimento especifico."
            RouteStatus.NoData -> "Esta atividade nao possui rota no Health Connect."
        }

    private fun manualPermissionMessage(): String =
        "Nenhuma permissao foi retornada pelo Health Connect. Abra Health Connect > Permissoes do app > FitToStrava e libere Exercicios, Frequencia cardiaca e Distancia manualmente."

    class Factory(
        private val healthConnectManager: HealthConnectManager,
        private val stravaOAuthClient: StravaOAuthClient,
        private val stravaTokenStore: StravaTokenStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FitToStravaViewModel(
                healthConnectManager,
                stravaOAuthClient,
                stravaTokenStore,
            ) as T
        }
    }
}
