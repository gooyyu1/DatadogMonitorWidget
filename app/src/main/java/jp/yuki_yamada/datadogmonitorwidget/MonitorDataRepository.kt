package jp.yuki_yamada.datadogmonitorwidget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ウィジェット UI の DataStore フィールドを一括更新するヘルパー関数。
 * [MonitorDataRepository] の内部処理で使用され、テストからも参照されます。
 */
internal fun applyWidgetUiState(
    mutableState: MutableMonitorWidgetState,
    appWidgetId: Int,
    text: String,
    status: MonitorStatus,
    monitorDetailsJson: String
) {
    mutableState.statusText = text
    mutableState.statusColor = status.name
    mutableState.lastUpdateMillis = System.currentTimeMillis()
    mutableState.appWidgetId = appWidgetId
    mutableState.monitorDetailsJson = monitorDetailsJson
}

/**
 * ウィジェットごとのデータ管理クラス。
 * Datadog サーバーからのデータ取得と DataStore への保存を一元管理します。
 * [MonitorWorker] とアクティビティの両方から利用されることで、
 * データ更新の責務を一箇所に集約します。
 */
class MonitorDataRepository(
    private val context: Context,
    private val appWidgetId: Int
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * DataStore からウィジェットの設定と状態を読み込みます。
     */
    suspend fun getSettings(): MonitorWidgetState {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        return MonitorWidgetState(prefs)
    }

    /**
     * DataStore にキャッシュされたモニターリストを取得します。
     * サーバーへのアクセスは行いません。
     */
    suspend fun getCachedMonitors(): List<MonitorDetail> {
        val settings = getSettings()
        return try {
            json.decodeFromString(settings.monitorDetailsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * サーバーからデータを取得して DataStore を更新します。
     * ウィジェットの表示も更新されます。
     * 成功時は取得したモニターリストを、失敗時はエラー情報を返します。
     */
    suspend fun refresh(): Result<List<MonitorDetail>> {
        val settings = getSettings()
        val apiKey = settings.apiKey
        val appKey = settings.appKey
        val query = settings.query
        val siteUrl = settings.siteUrl

        if (apiKey.isEmpty() || appKey.isEmpty()) {
            saveError("API Key or App Key is missing")
            saveWidgetUi("NA", MonitorStatus.ALERT, "[]")
            return Result.failure(Exception("API Key or App Key is missing"))
        }

        return try {
            val client = DatadogApiClient(apiKey, appKey, siteUrl)
            val monitors = client.searchDetailedMonitors(query)

            val total = monitors.size
            val okCount = monitors.count { it.status == MonitorStatus.OK }
            val mutedCount = monitors.count { it.status == MonitorStatus.MUTED }
            // ミュートされているものは「問題なし」としてカウントに含める
            val displayOkCount = okCount + mutedCount
            val statusText = if (total == 0) "0/0" else "$displayOkCount/$total"
            // ウィジェット全体の背景色を決定するステータス（優先順位に従う）
            val monitorStatus = monitors.map { it.status }
                .minByOrNull { monitorStatusPriority(it) }
                ?: MonitorStatus.NO_DATA

            saveWidgetUi(statusText, monitorStatus, json.encodeToString(monitors))
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            saveSuccess(currentTime)

            Result.success(monitors)
        } catch (e: Exception) {
            saveError(e.message ?: "Unknown error")
            saveWidgetUi("NA", MonitorStatus.ALERT, "[]")
            Result.failure(e)
        }
    }

    /**
     * 指定されたモニターの最新詳細情報をサーバーから取得します。
     * [MonitorBreakdownActivity] のグループ情報更新など、単体モニターの再取得用です。
     */
    suspend fun getMonitorDetail(monitorId: Long): MonitorDetail {
        val settings = getSettings()
        val client = DatadogApiClient(settings.apiKey, settings.appKey, settings.siteUrl)
        return client.getMonitorDetail(monitorId)
    }

    private suspend fun saveWidgetUi(text: String, status: MonitorStatus, monitorsJson: String) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                val mutableState = MutableMonitorWidgetState(this)
                applyWidgetUiState(mutableState, appWidgetId, text, status, monitorsJson)
            }
        }
        // Glance にウィジェットを再描画するよう通知
        MonitorWidget().update(context, glanceId)
    }

    private suspend fun saveError(error: String) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                val mutableState = MutableMonitorWidgetState(this)
                mutableState.lastError = error
            }
        }
    }

    private suspend fun saveSuccess(time: String) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                val mutableState = MutableMonitorWidgetState(this)
                mutableState.lastSuccessTime = time
                mutableState.lastError = ""
            }
        }
    }
}
