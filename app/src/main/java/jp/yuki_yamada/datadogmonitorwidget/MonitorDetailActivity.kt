package jp.yuki_yamada.datadogmonitorwidget

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import jp.yuki_yamada.datadogmonitorwidget.ui.StatusCountBadge
import jp.yuki_yamada.datadogmonitorwidget.ui.theme.DatadogMonitorWidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
                MonitorDetailScreen(appWidgetId = appWidgetId)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonitorDetailScreen(appWidgetId: Int) {
    val context = LocalContext.current
    val json = remember { Json { ignoreUnknownKeys = true; prettyPrint = true } }
    var monitorsWithRawJson by remember { mutableStateOf<List<MonitorDetailWithRaw>>(emptyList()) }
    var appUrl by remember { mutableStateOf("https://app.datadoghq.com/") }

    val selectedMonitorIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedMonitorIds.isNotEmpty()
    var jsonLinesToShow by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(appWidgetId) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val monitorDetailsJson = prefs[MonitorWidget.MONITOR_DETAILS_JSON] ?: "[]"
        val initialMonitors = runCatching {
            json.decodeFromString<List<Monitor>>(monitorDetailsJson).map { it.toMonitorDetail() }
        }.getOrElse {
            runCatching {
                json.decodeFromString<List<MonitorDetail>>(monitorDetailsJson)
            }.getOrDefault(emptyList())
        }
        monitorsWithRawJson = initialMonitors.map { MonitorDetailWithRaw(it, null) }
            .sortedBy { monitorStatusPriority(it.detail.status) }

        val apiKey = prefs[stringPreferencesKey("api_key")] ?: ""
        val appKey = prefs[stringPreferencesKey("app_key")] ?: ""
        val siteUrl = prefs[stringPreferencesKey("site_url")] ?: "https://api.datadoghq.com/"
        appUrl = prefs[stringPreferencesKey("app_url")] ?: "https://app.datadoghq.com/"
        val hasValidMonitorIds = initialMonitors.any { it.id > 0 }
        if (apiKey.isBlank() || appKey.isBlank() || !hasValidMonitorIds) return@LaunchedEffect

        val service = createDatadogApiService(json, siteUrl)
        val refreshed = coroutineScope {
            initialMonitors.map { monitor ->
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
        monitorsWithRawJson = refreshed.sortedBy { monitorStatusPriority(it.detail.status) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${selectedMonitorIds.size} selected",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = { /* TODO: Mute implementation */ }) {
                            Text("Mute")
                        }
                        TextButton(onClick = {
                            val selectedItems = monitorsWithRawJson.filter { it.detail.id in selectedMonitorIds }
                            val fullJson = selectedItems.joinToString("\n\n---\n\n") { item ->
                                val raw = item.rawJson ?: "No raw JSON available"
                                try {
                                    val element = json.parseToJsonElement(raw)
                                    json.encodeToString(JsonElement.serializer(), element)
                                } catch (e: Exception) {
                                    raw
                                }
                            }
                            jsonLinesToShow = fullJson.split("\n")
                        }) {
                            Text("JSON")
                        }
                        TextButton(onClick = { selectedMonitorIds.clear() }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
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

            if (monitorsWithRawJson.isEmpty()) {
                Text(stringResource(R.string.no_monitor_data))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    monitorsWithRawJson.forEach { item ->
                        val monitor = item.detail
                        val statusCounts = monitor.resolvedStatusCounts
                        val isMulti = monitor.isMultiAlert
                        val isSelected = monitor.id in selectedMonitorIds

                        val rowModifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedMonitorIds.remove(monitor.id)
                                        else selectedMonitorIds.add(monitor.id)
                                    } else {
                                        if (isMulti) {
                                            context.startActivity(
                                                Intent(context, MonitorBreakdownActivity::class.java).apply {
                                                    putExtra(MonitorBreakdownActivity.EXTRA_APPWIDGET_ID, appWidgetId)
                                                    putExtra(MonitorBreakdownActivity.EXTRA_MONITOR_ID, monitor.id)
                                                    putExtra(MonitorBreakdownActivity.EXTRA_MONITOR_NAME, monitor.name)
                                                    putExtra(
                                                        MonitorBreakdownActivity.EXTRA_GROUP_STATUSES_JSON,
                                                        json.encodeToString(monitor.groupStatuses)
                                                    )
                                                }
                                            )
                                        } else if (monitor.id > 0) {
                                            val monitorUrl = "${appUrl.trimEnd('/')}/monitors/${monitor.id}"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(monitorUrl))
                                            context.startActivity(intent)
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!isSelected) {
                                        selectedMonitorIds.add(monitor.id)
                                    }
                                }
                            )
                        Column(modifier = rowModifier.padding(vertical = 4.dp, horizontal = 8.dp)) {
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

    jsonLinesToShow?.let { lines ->
        Dialog(
            onDismissRequest = { jsonLinesToShow = null },
            properties = DialogProperties(
                decorFitsSystemWindows = true
            )
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Black.copy(alpha = 0.5f), // 背景を暗く
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Monitor JSON", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(lines) { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        val text = lines.joinToString("\n")
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Monitor JSON", text)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Copy")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { jsonLinesToShow = null }) {
                                    Text("Close")
                                }
                            }
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
): MonitorDetailWithRaw {
    if (monitor.id <= 0) return MonitorDetailWithRaw(monitor, null)

    return runCatching {
        val response = service.getMonitor(monitor.id, apiKey, appKey)
        val responseBody = response.body()?.string()
        if (!response.isSuccessful || responseBody.isNullOrBlank()) {
            MonitorDetailWithRaw(monitor, responseBody)
        } else {
            MonitorDetailWithRaw(
                detail = json.decodeFromString<MonitorDetailResponse>(responseBody)
                    .toMonitorDetail(fallbackStatus = monitor.status),
                rawJson = responseBody
            )
        }
    }.getOrDefault(MonitorDetailWithRaw(monitor, null))
}

private data class MonitorDetailWithRaw(
    val detail: MonitorDetail,
    val rawJson: String?
)
