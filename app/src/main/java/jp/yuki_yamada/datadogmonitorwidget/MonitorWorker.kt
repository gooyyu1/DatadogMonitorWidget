package jp.yuki_yamada.datadogmonitorwidget

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 背面で Datadog API からデータを取得し、ウィジェットの表示内容を更新する Worker クラス。
 * WorkManager によって定期的に実行されます（[scheduleNextWork] による連鎖実行）。
 */
class MonitorWorker(
    private val context: Context,
    private val workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    /**
     * 非同期でのデータ取得処理の本体。
     * 設定された API キーとクエリを使用してモニターの状態を確認し、ウィジェットの DataStore を更新します。
     */
    override suspend fun doWork(): ListenableWorker.Result {
        val appWidgetId = inputData.getInt("appWidgetId", -1)
        if (appWidgetId == -1) return ListenableWorker.Result.failure()

        // ウィジェット固有の設定（APIキー、クエリ等）を DataStore から読み込む
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val state = MonitorWidgetState(prefs)

        val apiKey = state.apiKey
        val appKey = state.appKey
        val query = state.query
        val siteUrl = state.siteUrl
        val interval = state.intervalMin

        if (apiKey.isEmpty() || appKey.isEmpty()) {
            saveError(appWidgetId, "API Key or App Key is missing")
            updateWidgetUI(appWidgetId, "NA", MonitorStatus.ALERT, "[]")
            return ListenableWorker.Result.failure()
        }

        try {
            val client = DatadogApiClient(apiKey, appKey, siteUrl)
            // クエリに合致する全モニターの詳細情報を取得（並列実行される）
            val detailedMonitors = client.searchDetailedMonitors(query)

            val total = detailedMonitors.size
            val okCount = detailedMonitors.count { it.status == MonitorStatus.OK }
            val mutedCount = detailedMonitors.count { it.status == MonitorStatus.MUTED }

            // ウィジェット上の「成功数/総数」表示の計算
            // ミュートされているものは「問題なし」としてカウントに含める
            val displayOkCount = okCount + mutedCount
            val statusText = if (total == 0) "0/0" else "$displayOkCount/$total"
            
            // ウィジェット全体の背景色を決定するステータス（優先順位に従う）
            val monitorStatus = detailedMonitors
                .map { it.status }
                .minByOrNull { monitorStatusPriority(it) }
                ?: MonitorStatus.NO_DATA

            val json = Json { ignoreUnknownKeys = true }
            // ウィジェットの状態を更新（これにより GlanceAppWidget.provideGlance が再構成される）
            updateWidgetUI(appWidgetId, statusText, monitorStatus, json.encodeToString(detailedMonitors))
            
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            saveSuccess(appWidgetId, currentTime)

            // 設定された間隔で次回の更新を予約
            val intervalMins = interval.toLongOrNull() ?: 5L
            scheduleNextWork(appWidgetId, intervalMins)
            return ListenableWorker.Result.success()
        } catch (e: Exception) {
            saveError(appWidgetId, e.message ?: "Unknown error")
            updateWidgetUI(appWidgetId, "NA", MonitorStatus.ALERT, "[]")
            val intervalMins = interval.toLongOrNull() ?: 5L
            scheduleNextWork(appWidgetId, intervalMins)
            return ListenableWorker.Result.failure()
        }
    }

    /**
     * 指定された分数後に自分自身を再度実行するように WorkManager に登録します。
     * これにより、ウィジェットの定期的かつ自動的な更新ループが形成されます。
     */
    private suspend fun scheduleNextWork(appWidgetId: Int, intervalMinutes: Long) {
        val nextWork = OneTimeWorkRequestBuilder<MonitorWorker>()
            .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
            .setInputData(androidx.work.workDataOf("appWidgetId" to appWidgetId))
            .addTag("MonitorUpdate_$appWidgetId")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "MonitorUpdate_$appWidgetId",
            ExistingWorkPolicy.REPLACE,
            nextWork
        )
    }

    /**
     * ウィジェットの UI 表示に必要なデータを DataStore に保存し、ウィジェット自体の更新をトリガーします。
     */
    private suspend fun updateWidgetUI(
        appWidgetId: Int,
        text: String,
        status: MonitorStatus,
        monitorDetailsJson: String
    ) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                val mutableState = MutableMonitorWidgetState(this)
                mutableState.statusText = text
                mutableState.statusColor = status.name
                mutableState.lastUpdateMillis = System.currentTimeMillis()
                mutableState.appWidgetId = appWidgetId
                mutableState.monitorDetailsJson = monitorDetailsJson
                mutableState.lastError = "" // 成功時はエラーをクリア
            }
        }
        // Glance にウィジェットを再描画するよう通知
        MonitorWidget().update(context, glanceId)
    }

    /**
     * 取得失敗時のエラーメッセージを DataStore に保存します。
     * デバッグ目的で、設定画面や詳細画面から参照される可能性があります。
     */
    private suspend fun saveError(appWidgetId: Int, error: String) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                val mutableState = MutableMonitorWidgetState(this)
                mutableState.lastError = error
            }
        }
    }

    /**
     * 取得成功時の時刻を記録します。
     */
    private suspend fun saveSuccess(appWidgetId: Int, time: String) {
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
