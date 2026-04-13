package jp.yuki_yamada.datadogmonitorwidget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "datadog_settings")

class DatadogSettings(private val context: Context) {
    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val APP_KEY = stringPreferencesKey("app_key")
        private val QUERY = stringPreferencesKey("query")
        private val INTERVAL = stringPreferencesKey("interval")
        private val SITE_URL = stringPreferencesKey("site_url")
        private val LAST_ERROR = stringPreferencesKey("last_error")
    }

    val apiKey: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val appKey: Flow<String> = context.dataStore.data.map { it[APP_KEY] ?: "" }
    val query: Flow<String> = context.dataStore.data.map { it[QUERY] ?: "" }
    val interval: Flow<String> = context.dataStore.data.map { it[INTERVAL] ?: "5" }
    val siteUrl: Flow<String> = context.dataStore.data.map { it[SITE_URL] ?: "https://api.datadoghq.com/" }
    val lastError: Flow<String> = context.dataStore.data.map { it[LAST_ERROR] ?: "" }

    suspend fun saveSettings(apiKey: String, appKey: String, query: String, interval: String, siteUrl: String) {
        context.dataStore.edit {
            it[API_KEY] = apiKey
            it[APP_KEY] = appKey
            it[QUERY] = query
            it[INTERVAL] = interval
            it[SITE_URL] = siteUrl
        }
    }

    suspend fun saveError(error: String) {
        context.dataStore.edit {
            it[LAST_ERROR] = error
        }
    }
}
