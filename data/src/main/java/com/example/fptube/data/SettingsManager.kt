package br.com.fpagency.fptube.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val SIMULTANEOUS_DOWNLOADS = intPreferencesKey("simultaneous_downloads")
        val SPEED_LIMIT = stringPreferencesKey("speed_limit")
        val QUALITY_MODE = stringPreferencesKey("quality_mode")
        val SHOW_PROGRESS_NOTIFICATION = booleanPreferencesKey("show_progress_notification")
        val SHOW_COMPLETED_NOTIFICATION = booleanPreferencesKey("show_completed_notification")
        val DEFAULT_DOWNLOAD_TYPE = stringPreferencesKey("default_download_type")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val DOWNLOAD_LOCATION_URI = stringPreferencesKey("download_location_uri")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LOGS_ENABLED = booleanPreferencesKey("logs_enabled")
        val LOGS_TO_SHEETS = booleanPreferencesKey("logs_to_sheets")
        val LOG_LEVEL = stringPreferencesKey("log_level")
        val INSTALLED_PLUGINS = stringSetPreferencesKey("installed_plugins")
        val EXTERNAL_REPOSITORIES = stringSetPreferencesKey("external_repositories")
        val USE_OAUTH2 = booleanPreferencesKey("use_oauth2")
        val LOG_FAILURES = booleanPreferencesKey("log_failures")
        val LOG_PERFORMANCE = booleanPreferencesKey("log_performance")
        val AUTO_UPDATE_COMPONENTS = booleanPreferencesKey("auto_update_components")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val HAS_ASKED_OVERLAY_PERMISSION = booleanPreferencesKey("has_asked_overlay_permission")
    }

    val simultaneousDownloads: Flow<Int> = dataStore.data.map { preferences ->
        preferences[SIMULTANEOUS_DOWNLOADS] ?: 5
    }

    val speedLimit: Flow<String> = dataStore.data.map { preferences ->
        preferences[SPEED_LIMIT] ?: "Ilimitado"
    }

    val qualityMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[QUALITY_MODE] ?: "Automático"
    }

    val showProgressNotification: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_PROGRESS_NOTIFICATION] ?: true
    }

    val showCompletedNotification: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_COMPLETED_NOTIFICATION] ?: true
    }

    val defaultDownloadType: Flow<String> = dataStore.data.map { preferences ->
        preferences[DEFAULT_DOWNLOAD_TYPE] ?: "Vídeo"
    }

    val audioQuality: Flow<String> = dataStore.data.map { preferences ->
        preferences[AUDIO_QUALITY] ?: "M4A rápido (128 kbps)"
    }

    val downloadLocationUri: Flow<String?> = dataStore.data.map { preferences ->
        preferences[DOWNLOAD_LOCATION_URI]
    }

    val themeMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "Sistema"
    }

    val logsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LOGS_ENABLED] ?: true
    }

    val logsToSheets: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LOGS_TO_SHEETS] ?: true
    }

    val logLevel: Flow<String> = dataStore.data.map { preferences ->
        preferences[LOG_LEVEL] ?: "INFO"
    }

    val installedPlugins: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[INSTALLED_PLUGINS] ?: emptySet()
    }

    val externalRepositories: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[EXTERNAL_REPOSITORIES] ?: emptySet()
    }

    val useOAuth2: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[USE_OAUTH2] ?: false
    }

    val logFailures: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LOG_FAILURES] ?: true
    }

    val logPerformance: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LOG_PERFORMANCE] ?: true
    }

    val autoUpdateComponents: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_UPDATE_COMPONENTS] ?: true
    }

    val appLanguage: Flow<String> = dataStore.data.map { preferences ->
        preferences[APP_LANGUAGE] ?: "System"
    }

    val hasAskedOverlayPermission: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[HAS_ASKED_OVERLAY_PERMISSION] ?: false
    }

    suspend fun updateHasAskedOverlayPermission(value: Boolean) {
        dataStore.edit { preferences -> preferences[HAS_ASKED_OVERLAY_PERMISSION] = value }
    }

    suspend fun updateSimultaneousDownloads(value: Int) {
        dataStore.edit { preferences -> preferences[SIMULTANEOUS_DOWNLOADS] = value }
    }

    suspend fun updateSpeedLimit(value: String) {
        dataStore.edit { preferences -> preferences[SPEED_LIMIT] = value }
    }

    suspend fun updateQualityMode(value: String) {
        dataStore.edit { preferences -> preferences[QUALITY_MODE] = value }
    }

    suspend fun updateShowProgressNotification(value: Boolean) {
        dataStore.edit { preferences -> preferences[SHOW_PROGRESS_NOTIFICATION] = value }
    }

    suspend fun updateShowCompletedNotification(value: Boolean) {
        dataStore.edit { preferences -> preferences[SHOW_COMPLETED_NOTIFICATION] = value }
    }

    suspend fun updateDefaultDownloadType(value: String) {
        dataStore.edit { preferences -> preferences[DEFAULT_DOWNLOAD_TYPE] = value }
    }

    suspend fun updateAudioQuality(value: String) {
        dataStore.edit { preferences -> preferences[AUDIO_QUALITY] = value }
    }

    suspend fun updateDownloadLocationUri(value: String?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(DOWNLOAD_LOCATION_URI)
            } else {
                preferences[DOWNLOAD_LOCATION_URI] = value
            }
        }
    }

    suspend fun updateThemeMode(value: String) {
        dataStore.edit { preferences -> preferences[THEME_MODE] = value }
    }

    suspend fun updateLogsEnabled(value: Boolean) {
        dataStore.edit { preferences -> preferences[LOGS_ENABLED] = value }
    }

    suspend fun updateLogsToSheets(value: Boolean) {
        dataStore.edit { preferences -> preferences[LOGS_TO_SHEETS] = value }
    }

    suspend fun updateLogLevel(value: String) {
        dataStore.edit { preferences -> preferences[LOG_LEVEL] = value }
    }

    suspend fun addInstalledPlugin(id: String) {
        dataStore.edit { preferences ->
            val current = preferences[INSTALLED_PLUGINS] ?: emptySet()
            preferences[INSTALLED_PLUGINS] = current + id
        }
    }

    suspend fun removeInstalledPlugin(id: String) {
        dataStore.edit { preferences ->
            val current = preferences[INSTALLED_PLUGINS] ?: emptySet()
            preferences[INSTALLED_PLUGINS] = current - id
        }
    }

    suspend fun addRepository(url: String) {
        dataStore.edit { preferences ->
            val current = preferences[EXTERNAL_REPOSITORIES] ?: emptySet()
            preferences[EXTERNAL_REPOSITORIES] = current + url
        }
    }

    suspend fun removeRepository(url: String) {
        dataStore.edit { preferences ->
            val current = preferences[EXTERNAL_REPOSITORIES] ?: emptySet()
            preferences[EXTERNAL_REPOSITORIES] = current - url
        }
    }

    suspend fun updateUseOAuth2(value: Boolean) {
        dataStore.edit { preferences -> preferences[USE_OAUTH2] = value }
    }

    suspend fun updateLogFailures(value: Boolean) {
        dataStore.edit { preferences -> preferences[LOG_FAILURES] = value }
    }

    suspend fun updateLogPerformance(value: Boolean) {
        dataStore.edit { preferences -> preferences[LOG_PERFORMANCE] = value }
    }

    suspend fun updateAutoUpdateComponents(value: Boolean) {
        dataStore.edit { preferences -> preferences[AUTO_UPDATE_COMPONENTS] = value }
    }

    suspend fun updateAppLanguage(value: String) {
        dataStore.edit { preferences -> preferences[APP_LANGUAGE] = value }
    }
}
