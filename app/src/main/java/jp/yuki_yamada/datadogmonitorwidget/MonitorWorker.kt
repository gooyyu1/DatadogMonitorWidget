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
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MonitorWorker(
    private val context: Context,
    private val workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result {
        val appWidgetId = inputData.getInt("appWidgetId", -1)
        if (appWidgetId == -1) return ListenableWorker.Result.failure()

        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)

        val apiKey = prefs[stringPreferencesKey("api_key")] ?: ""
        val appKey = prefs[stringPreferencesKey("app_key")] ?: ""
        val query = prefs[stringPreferencesKey("query")] ?: ""
        val siteUrl = prefs[stringPreferencesKey("site_url")] ?: "https://api.datadoghq.com/"
        val interval = prefs[stringPreferencesKey("interval")] ?: "5"

        if (apiKey.isEmpty() || appKey.isEmpty()) {
            saveError(appWidgetId, "API Key or App Key is missing")
            updateWidgetUI(appWidgetId, "NA", MonitorStatus.ALERT, "[]")
            return ListenableWorker.Result.failure()
        }

        try {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            val json = Json { ignoreUnknownKeys = true }
            val retrofit = Retrofit.Builder()
                .baseUrl(siteUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            val service = retrofit.create(DatadogApiService::class.java)
            val response = service.searchMonitors(apiKey, appKey, query)

            val requestUrl = response.raw().request.url.toString()
            val responseBodyString = if (response.isSuccessful) {
                response.body()?.string()
            } else {
                response.errorBody()?.string()
            } ?: "Empty body"

            if (!response.isSuccessful) {
                throw Exception("API Error: ${response.code()}\nURL: $requestUrl\nResponse: $responseBodyString")
            }

            val monitorResponse = json.decodeFromString<MonitorSearchResponse>(responseBodyString)
            val monitors = monitorResponse.monitors
            val monitorDetails = monitors
                .map { it.toMonitorDetail() }
                .sortedBy { monitorStatusPriority(it.status) }
            val total = monitors.size
            val okCount = monitors.count { MonitorStatus.fromRaw(it.status) == MonitorStatus.OK }
            val alertCount = monitors.count { MonitorStatus.fromRaw(it.status) == MonitorStatus.ALERT }
            val warnCount = monitors.count { MonitorStatus.fromRaw(it.status) == MonitorStatus.WARN }

            val statusText = if (total == 0) "0/0" else "$okCount/$total"
            val monitorStatus = when {
                total == 0 -> MonitorStatus.NO_DATA
                alertCount > 0 -> MonitorStatus.ALERT
                warnCount > 0 -> MonitorStatus.WARN
                else -> MonitorStatus.OK
            }

            updateWidgetUI(appWidgetId, statusText, monitorStatus, json.encodeToString(monitorDetails))
            
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            saveSuccess(appWidgetId, currentTime)

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

    private suspend fun updateWidgetUI(
        appWidgetId: Int,
        text: String,
        status: MonitorStatus,
        monitorDetailsJson: String
    ) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[MonitorWidget.STATUS_TEXT] = text
                this[MonitorWidget.STATUS_COLOR] = status.name
                this[MonitorWidget.LAST_UPDATE_MILLIS] = System.currentTimeMillis()
                this[MonitorWidget.APP_WIDGET_ID] = appWidgetId
                this[MonitorWidget.MONITOR_DETAILS_JSON] = monitorDetailsJson
            }
        }
        MonitorWidget().update(context, glanceId)
    }

    private suspend fun saveError(appWidgetId: Int, error: String) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[stringPreferencesKey("last_error")] = error
            }
        }
    }

    private suspend fun saveSuccess(appWidgetId: Int, time: String) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[stringPreferencesKey("last_success_time")] = time
                this[stringPreferencesKey("last_error")] = ""
            }
        }
    }
}
