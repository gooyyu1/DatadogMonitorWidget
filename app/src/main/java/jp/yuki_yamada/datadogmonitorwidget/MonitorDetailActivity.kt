package jp.yuki_yamada.datadogmonitorwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import jp.yuki_yamada.datadogmonitorwidget.ui.theme.DatadogMonitorWidgetTheme
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class MonitorDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            DatadogMonitorWidgetTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MonitorDetailScreen(
                        appWidgetId = appWidgetId,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

class MonitorBreakdownActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val monitorName = intent.getStringExtra(EXTRA_MONITOR_NAME).orEmpty()
        val groupStatusesJson = intent.getStringExtra(EXTRA_GROUP_STATUSES_JSON).orEmpty()

        enableEdgeToEdge()
        setContent {
            DatadogMonitorWidgetTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MonitorBreakdownScreen(
                        monitorName = monitorName,
                        groupStatusesJson = groupStatusesJson,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitorBreakdownScreen(
    monitorName: String,
    groupStatusesJson: String,
    modifier: Modifier = Modifier
) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    var groups by rememberSaveable(stateSaver = monitorGroupStatusesSaver) {
        mutableStateOf(emptyList())
    }
    var isLoading by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(groupStatusesJson) {
        groups = withContext(Dispatchers.Default) {
            decodeMonitorGroupStatuses(json, groupStatusesJson)
        }
        isLoading = false
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        Text(
            text = if (monitorName.isBlank()) stringResource(R.string.monitor_breakdown_title) else monitorName,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups) { group ->
                    MonitorRow(
                        name = group.name,
                        status = group.status
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitorDetailScreen(appWidgetId: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val json = remember { Json { ignoreUnknownKeys = true } }
    var monitors by remember { mutableStateOf<List<MonitorDetail>>(emptyList()) }

    LaunchedEffect(appWidgetId) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val monitorDetailsJson = prefs[MonitorWidget.MONITOR_DETAILS_JSON] ?: "[]"
        val parsed = runCatching {
            json.decodeFromString<List<Monitor>>(monitorDetailsJson).map { it.toMonitorDetail() }
        }.getOrElse {
            runCatching {
                json.decodeFromString<List<MonitorDetail>>(monitorDetailsJson)
            }.getOrDefault(emptyList())
        }
        monitors = parsed.sortedBy { monitorStatusPriority(it.status) }

        val apiKey = prefs[stringPreferencesKey("api_key")] ?: ""
        val appKey = prefs[stringPreferencesKey("app_key")] ?: ""
        val siteUrl = prefs[stringPreferencesKey("site_url")] ?: "https://api.datadoghq.com/"
        val hasValidMonitorIds = monitors.any { it.id > 0 }
        if (apiKey.isBlank() || appKey.isBlank() || !hasValidMonitorIds) return@LaunchedEffect

        val service = createDatadogApiService(json, siteUrl)
        val refreshed = coroutineScope {
            monitors.map { monitor ->
                async(Dispatchers.IO) {
                    fetchMonitorDetailOrFallback(
                        service = service,
                        monitor = monitor,
                        apiKey = apiKey,
                        appKey = appKey,
                        json = json
                    )
                }
            }.awaitAll()
        }
        monitors = refreshed.map { it.monitorDetail }.sortedBy { monitorStatusPriority(it.status) }
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.monitor_details_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OpenDatadogButton()
            OpenWidgetSettingsButton(appWidgetId = appWidgetId)
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (monitors.isEmpty()) {
            Text(stringResource(R.string.no_monitor_data))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                monitors.forEach { monitor ->
                    val statusCounts = monitor.resolvedStatusCounts
                    val hasBreakdown = monitor.groupStatuses.isNotEmpty()
                    val rowModifier = if (hasBreakdown) {
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.startActivity(
                                    Intent(context, MonitorBreakdownActivity::class.java).apply {
                                        putExtra(EXTRA_MONITOR_NAME, monitor.name)
                                        putExtra(
                                            EXTRA_GROUP_STATUSES_JSON,
                                            json.encodeToString(monitor.groupStatuses)
                                        )
                                    }
                                )
                            }
                    } else {
                        Modifier.fillMaxWidth()
                    }
                    Column(modifier = rowModifier.padding(vertical = 4.dp)) {
                        Text(
                            text = monitor.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (monitor.isMultiAlert && statusCounts != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusCountBadge("ALERT ${statusCounts.alert}", MonitorStatus.ALERT)
                                StatusCountBadge("WARN ${statusCounts.warn}", MonitorStatus.WARN)
                                StatusCountBadge("OK ${statusCounts.ok}", MonitorStatus.OK)
                                StatusCountBadge("NO DATA ${statusCounts.noData}", MonitorStatus.NO_DATA)
                            }
                        } else {
                            StatusCountBadge(monitor.status.name.replace('_', ' '), monitor.status)
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun OpenDatadogButton() {
    val context = LocalContext.current
    Button(
        onClick = {
            val datadogIntent = context.packageManager.getLaunchIntentForPackage("com.datadog.app")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://app.datadoghq.com/"))
            context.startActivity(datadogIntent)
        }
    ) {
        Text(stringResource(R.string.open_datadog_app))
    }
}

@Composable
private fun OpenWidgetSettingsButton(appWidgetId: Int) {
    val context = LocalContext.current
    Button(
        onClick = {
            context.startActivity(
                Intent(context, WidgetConfigurationActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
            )
        }
    ) {
        Text(stringResource(R.string.open_widget_settings))
    }
}

@Composable
private fun MonitorRow(name: String, status: MonitorStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        StatusCountBadge(status.name.replace('_', ' '), status)
    }
}

@Composable
private fun StatusCountBadge(text: String, status: MonitorStatus) {
    val color = when (status) {
        MonitorStatus.ALERT -> Color(0xFFF44336)
        MonitorStatus.WARN -> Color(0xFFFFA000)
        MonitorStatus.OK -> Color(0xFF4CAF50)
        MonitorStatus.NO_DATA -> Color(0xFF9E9E9E)
    }
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private const val EXTRA_MONITOR_NAME = "monitor_name"
private const val EXTRA_GROUP_STATUSES_JSON = "group_statuses_json"
private val monitorGroupStatusesSaver = listSaver<List<MonitorGroupStatus>, String>(
    save = { groups -> saveMonitorGroupStatuses(groups) },
    restore = { saved -> restoreMonitorGroupStatuses(saved) }
)

internal fun decodeMonitorGroupStatuses(json: Json, groupStatusesJson: String): List<MonitorGroupStatus> =
    runCatching {
        json.decodeFromString<List<MonitorGroupStatus>>(groupStatusesJson)
    }.getOrDefault(emptyList())
        .sortedBy { monitorStatusPriority(it.status) }

internal fun saveMonitorGroupStatuses(groups: List<MonitorGroupStatus>): List<String> =
    groups.flatMap { listOf(it.name, it.status.name) }

internal fun restoreMonitorGroupStatuses(saved: List<String>): List<MonitorGroupStatus> {
    if (saved.size % 2 != 0) return emptyList()
    return runCatching {
        saved.chunked(2).map { chunk ->
            MonitorGroupStatus(
                name = chunk[0],
                status = MonitorStatus.valueOf(chunk[1])
            )
        }
    }.getOrDefault(emptyList())
}

private fun createDatadogApiService(json: Json, siteUrl: String): DatadogApiService =
    Retrofit.Builder()
        .baseUrl(siteUrl)
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(DatadogApiService::class.java)

private suspend fun fetchMonitorDetailOrFallback(
    service: DatadogApiService,
    monitor: MonitorDetail,
    apiKey: String,
    appKey: String,
    json: Json
): MonitorDetailFetchResult {
    if (monitor.id <= 0) return MonitorDetailFetchResult(monitorDetail = monitor)

    return runCatching {
        val response = service.getMonitor(monitor.id, apiKey, appKey)
        val responseBody = response.body()?.string()
        if (!response.isSuccessful || responseBody.isNullOrBlank()) {
            MonitorDetailFetchResult(monitorDetail = monitor)
        } else {
            MonitorDetailFetchResult(
                monitorDetail = json.decodeFromString<MonitorDetailResponse>(responseBody)
                    .toMonitorDetail(fallbackStatus = monitor.status)
            )
        }
    }.getOrDefault(MonitorDetailFetchResult(monitorDetail = monitor))
}

private data class MonitorDetailFetchResult(
    val monitorDetail: MonitorDetail
)
