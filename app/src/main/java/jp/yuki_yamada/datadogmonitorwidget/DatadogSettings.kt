package jp.yuki_yamada.datadogmonitorwidget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * アプリ全体の設定を DataStore を通じて管理するクラス。
 * API キー、アプリケーションキー、取得クエリなどの永続化を担当します。
 */
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

    /** API キーの Flow */
    val apiKey: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    /** アプリケーションキーの Flow */
    val appKey: Flow<String> = context.dataStore.data.map { it[APP_KEY] ?: "" }
    /** 検索クエリの Flow (例: "status:alert") */
    val query: Flow<String> = context.dataStore.data.map { it[QUERY] ?: "" }
    /** 更新間隔（分）の Flow */
    val interval: Flow<String> = context.dataStore.data.map { it[INTERVAL] ?: "5" }
    /** Datadog の API ベース URL (US1, EU, JP1 等に対応) */
    val siteUrl: Flow<String> = context.dataStore.data.map { it[SITE_URL] ?: "https://api.datadoghq.com/" }
    /** 直近に発生したエラーメッセージの Flow */
    val lastError: Flow<String> = context.dataStore.data.map { it[LAST_ERROR] ?: "" }

    /**
     * すべての設定値を一括で保存します。
     * 設定画面の「Save」ボタン押下時に呼び出されます。
     */
    suspend fun saveSettings(apiKey: String, appKey: String, query: String, interval: String, siteUrl: String) {
        context.dataStore.edit {
            it[API_KEY] = apiKey
            it[APP_KEY] = appKey
            it[QUERY] = query
            it[INTERVAL] = interval
            it[SITE_URL] = siteUrl
        }
    }

    /**
     * エラーメッセージのみを保存します。
     */
    suspend fun saveError(error: String) {
        context.dataStore.edit {
            it[LAST_ERROR] = error
        }
    }
}
