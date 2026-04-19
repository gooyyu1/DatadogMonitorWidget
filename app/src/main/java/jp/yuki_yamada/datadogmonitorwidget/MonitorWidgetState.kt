package jp.yuki_yamada.datadogmonitorwidget

import android.appwidget.AppWidgetManager
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * ウィジェットの設定と状態を型安全に読み取るためのラッパークラス。
 */
class MonitorWidgetState(private val prefs: Preferences) {
    companion object {
        val STATUS_TEXT = stringPreferencesKey("status_text")
        val STATUS_COLOR = stringPreferencesKey("status_color")
        val LAST_UPDATE_MILLIS = longPreferencesKey("last_update_millis")
        val API_KEY = stringPreferencesKey("api_key")
        val APP_KEY = stringPreferencesKey("app_key")
        val QUERY = stringPreferencesKey("query")
        val SITE_URL = stringPreferencesKey("site_url")
        val APP_URL = stringPreferencesKey("app_url")
        val INTERVAL_MIN = stringPreferencesKey("interval")
        val LAST_ERROR = stringPreferencesKey("last_error")
        val LAST_SUCCESS_TIME = stringPreferencesKey("last_success_time")
        val APP_WIDGET_ID = intPreferencesKey("app_widget_id")
        val MONITOR_DETAILS_JSON = stringPreferencesKey("monitor_details_json")
    }

    val statusText: String get() = prefs[STATUS_TEXT] ?: "NA"
    val statusColor: String get() = prefs[STATUS_COLOR] ?: MonitorStatus.ALERT.name
    val lastUpdateMillis: Long get() = prefs[LAST_UPDATE_MILLIS] ?: 0L
    val apiKey: String get() = prefs[API_KEY] ?: ""
    val appKey: String get() = prefs[APP_KEY] ?: ""
    val query: String get() = prefs[QUERY] ?: ""
    val siteUrl: String get() = prefs[SITE_URL] ?: "https://api.datadoghq.com/"
    val appUrl: String get() = prefs[APP_URL] ?: "https://app.datadoghq.com/"
    val intervalMin: String get() = prefs[INTERVAL_MIN] ?: "5"
    val lastError: String get() = prefs[LAST_ERROR] ?: ""
    val lastSuccessTime: String get() = prefs[LAST_SUCCESS_TIME] ?: ""
    val appWidgetId: Int get() = prefs[APP_WIDGET_ID] ?: AppWidgetManager.INVALID_APPWIDGET_ID
    val monitorDetailsJson: String get() = prefs[MONITOR_DETAILS_JSON] ?: "[]"

    val monitorStatus: MonitorStatus get() = try {
        MonitorStatus.valueOf(statusColor)
    } catch (e: Exception) {
        MonitorStatus.ALERT
    }
}

/**
 * ウィジェットの設定と状態を更新するためのラッパークラス。
 */
class MutableMonitorWidgetState(private val prefs: MutablePreferences) {
    var statusText: String
        get() = prefs[MonitorWidgetState.STATUS_TEXT] ?: ""
        set(value) { prefs[MonitorWidgetState.STATUS_TEXT] = value }

    var statusColor: String
        get() = prefs[MonitorWidgetState.STATUS_COLOR] ?: ""
        set(value) { prefs[MonitorWidgetState.STATUS_COLOR] = value }

    var lastUpdateMillis: Long
        get() = prefs[MonitorWidgetState.LAST_UPDATE_MILLIS] ?: 0L
        set(value) { prefs[MonitorWidgetState.LAST_UPDATE_MILLIS] = value }

    var apiKey: String
        get() = prefs[MonitorWidgetState.API_KEY] ?: ""
        set(value) { prefs[MonitorWidgetState.API_KEY] = value }

    var appKey: String
        get() = prefs[MonitorWidgetState.APP_KEY] ?: ""
        set(value) { prefs[MonitorWidgetState.APP_KEY] = value }

    var query: String
        get() = prefs[MonitorWidgetState.QUERY] ?: ""
        set(value) { prefs[MonitorWidgetState.QUERY] = value }

    var siteUrl: String
        get() = prefs[MonitorWidgetState.SITE_URL] ?: ""
        set(value) { prefs[MonitorWidgetState.SITE_URL] = value }

    var appUrl: String
        get() = prefs[MonitorWidgetState.APP_URL] ?: ""
        set(value) { prefs[MonitorWidgetState.APP_URL] = value }

    var intervalMin: String
        get() = prefs[MonitorWidgetState.INTERVAL_MIN] ?: ""
        set(value) { prefs[MonitorWidgetState.INTERVAL_MIN] = value }

    var lastError: String
        get() = prefs[MonitorWidgetState.LAST_ERROR] ?: ""
        set(value) { prefs[MonitorWidgetState.LAST_ERROR] = value }

    var lastSuccessTime: String
        get() = prefs[MonitorWidgetState.LAST_SUCCESS_TIME] ?: ""
        set(value) { prefs[MonitorWidgetState.LAST_SUCCESS_TIME] = value }

    var appWidgetId: Int
        get() = prefs[MonitorWidgetState.APP_WIDGET_ID] ?: AppWidgetManager.INVALID_APPWIDGET_ID
        set(value) { prefs[MonitorWidgetState.APP_WIDGET_ID] = value }

    var monitorDetailsJson: String
        get() = prefs[MonitorWidgetState.MONITOR_DETAILS_JSON] ?: ""
        set(value) { prefs[MonitorWidgetState.MONITOR_DETAILS_JSON] = value }
}
