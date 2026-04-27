package jp.yuki_yamada.datadogmonitorwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import jp.yuki_yamada.datadogmonitorwidget.ui.MuteDurationDialog
import jp.yuki_yamada.datadogmonitorwidget.ui.StatusCountBadge
import jp.yuki_yamada.datadogmonitorwidget.ui.theme.DatadogMonitorWidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * ウィジェットをタップした際に表示される、モニター一覧の詳細画面。
 * 複数のモニターの状態を一括で確認したり、ミュート操作を行ったりすることができます。
 */
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

/**
 * モニター詳細画面のメイン UI。
 * DataStore から現在のモニター情報を読み込み、リスト表示します。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MonitorDetailScreen(appWidgetId: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true; prettyPrint = true } }
    
    // 表示用のモニターリスト。API レスポンスの生 JSON も保持します。
    var monitorsWithRawJson by remember { mutableStateOf<List<MonitorDetailWithRaw>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var updateProgress by remember { mutableFloatStateOf(0f) }
    var appUrl by remember { mutableStateOf("https://app.datadoghq.com/") }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // 選択モード（長押しで開始）の状態管理
    val selectedMonitorIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedMonitorIds.isNotEmpty()
    var jsonLinesToShow by remember { mutableStateOf<List<String>?>(null) }
    var showMuteDialog by remember { mutableStateOf(false) }

    // 初期化時および更新時に DataStore からデータを読み込む
    LaunchedEffect(appWidgetId, refreshKey) {
        updateProgress = 0f
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val state = MonitorWidgetState(prefs)
        
        val apiKey = state.apiKey
        val appKey = state.appKey
        val siteUrl = state.siteUrl
        val monitorsJson = state.monitorDetailsJson
        appUrl = state.appUrl

        val initialMonitors = try {
            json.decodeFromString<List<MonitorDetail>>(monitorsJson)
        } catch (e: Exception) {
            emptyList()
        }

        // 最初に一回だけソートし、API取得後の再ソートは行わない（ユーザーの操作ミス防止）
        val sortedMonitors = initialMonitors.sortedBy { monitorStatusPriority(it.status) }

        // 1. API 取得前に、キャッシュデータを表示する
        val cachedItems = sortedMonitors.map { MonitorDetailWithRaw(it, null) }
        monitorsWithRawJson = cachedItems
        isLoading = false

        if (sortedMonitors.isNotEmpty()) {
            val client = DatadogApiClient(apiKey, appKey, siteUrl)
            val updatedList = cachedItems.toMutableList()
            
            // 2. 逐次、最新情報を取得して更新し、進捗を表示する
            sortedMonitors.forEachIndexed { index, monitor ->
                val result = fetchMonitorDetailOrFallback(client, monitor)
                updatedList[index] = result
                // 1件ごとにリストを更新（順序は維持）
                monitorsWithRawJson = updatedList.toList()
                // 進捗率を更新
                updateProgress = (index + 1).toFloat() / sortedMonitors.size
            }
        }
        isRefreshing = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            // 選択モード中の下部アクションバー
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
                        // 選択したモニターを一括でミュート
                        TextButton(onClick = { showMuteDialog = true }) {
                            Text("Mute")
                        }
                        // 選択したモニターの生 JSON を表示
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                refreshKey++
            },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
        Column(
            modifier = Modifier
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

            // 進捗バーのスムージング設定
            val animatedProgress by animateFloatAsState(
                targetValue = updateProgress,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                label = "progressSmoothing"
            )

            // 固定の高さを持つコンテナでプログレスバーを管理し、レイアウトのガタつきを防ぐ
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp), // バー(2dp) + 余白
                contentAlignment = Alignment.BottomCenter
            ) {
                if (updateProgress > 0f && updateProgress < 1f) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            if (monitorsWithRawJson.isEmpty() && !isLoading) {
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
                                            // マルチモニター（グループあり）の場合は内訳画面へ
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
                                            // 単一モニターの場合は Datadog の Web ページを開く
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
                            // マルチモニターの場合はステータスごとの件数をバッジで表示
                            if (isMulti && statusCounts != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (statusCounts.alert > 0) StatusCountBadge("ALERT ${statusCounts.alert}", MonitorStatus.ALERT)
                                    if (statusCounts.warn > 0) StatusCountBadge("WARN ${statusCounts.warn}", MonitorStatus.WARN)
                                    if (statusCounts.muted > 0) StatusCountBadge("MUTED ${statusCounts.muted}", MonitorStatus.MUTED)
                                    if (statusCounts.ok > 0) StatusCountBadge("OK ${statusCounts.ok}", MonitorStatus.OK)
                                    if (statusCounts.noData > 0) StatusCountBadge("NO DATA ${statusCounts.noData}", MonitorStatus.NO_DATA)
                                }
                            } else {
                                // 単一モニターの場合は現在のステータスをバッジで表示
                                StatusCountBadge(monitor.status.name.replace('_', ' '), monitor.status)
                            }
                        }
                    }
                }
            }
        }
        }
    }

    // 生 JSON 表示ダイアログ
    jsonLinesToShow?.let { lines ->
        Dialog(
            onDismissRequest = { jsonLinesToShow = null },
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Raw Monitor JSON", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        items(lines) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { jsonLinesToShow = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // ミュート時間選択ダイアログ
    if (showMuteDialog) {
        MuteDurationDialog(
            onDismiss = { showMuteDialog = false },
            onConfirm = { durationMins ->
                showMuteDialog = false
                scope.launch {
                    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                    val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
                    val state = MonitorWidgetState(prefs)
                    
                    val result = performBulkMute(
                        context = context,
                        state = state,
                        monitorIds = selectedMonitorIds.toList(),
                        durationMinutes = durationMins
                    )

                    if (result.isSuccess) {
                        selectedMonitorIds.clear()
                        // ミュート後に一覧を再取得して表示を更新する
                        val client = DatadogApiClient(state.apiKey, state.appKey, state.siteUrl)
                        val monitorsJson = state.monitorDetailsJson
                        val currentMonitors = json.decodeFromString<List<MonitorDetail>>(monitorsJson)
                        val currentOrder = monitorsWithRawJson.map { it.detail.id }
                        val updatedResults = currentMonitors.associateBy { it.id }.let { map ->
                            currentOrder.mapNotNull { id ->
                                map[id]?.let { fetchMonitorDetailOrFallback(client, it) }
                            }
                        }
                        if (updatedResults.isNotEmpty()) {
                            monitorsWithRawJson = updatedResults
                        }
                    }
                }
            }
        )
    }
}

/**
 * Datadog の Web サイト（ダッシュボード等）を開くボタン。
 */
@Composable
private fun OpenDatadogButton() {
    val context = LocalContext.current
    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://app.datadoghq.com/monitors/manage"))
            context.startActivity(intent)
        }
    ) {
        Text(stringResource(R.string.open_datadog_app))
    }
}

/**
 * ウィジェットの設定画面を開くボタン。
 */
@Composable
private fun OpenWidgetSettingsButton(appWidgetId: Int) {
    val context = LocalContext.current
    Button(
        onClick = {
            val intent = Intent(context, WidgetConfigurationActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            context.startActivity(intent)
        }
    ) {
        Text(stringResource(R.string.open_widget_settings))
    }
}

/**
 * 最新のモニター詳細を取得します。取得に失敗した場合は、既存の情報を保持します。
 */
private suspend fun fetchMonitorDetailOrFallback(
    client: DatadogApiClient,
    monitor: MonitorDetail
): MonitorDetailWithRaw = withContext(Dispatchers.IO) {
    if (monitor.id <= 0) return@withContext MonitorDetailWithRaw(monitor, null)

    runCatching {
        val detail = client.getMonitorDetail(monitor.id, fallbackStatus = monitor.status)
        MonitorDetailWithRaw(detail, detail.rawJson)
    }.getOrDefault(MonitorDetailWithRaw(monitor, null))
}

/**
 * 画面表示用のラッパーモデル。モニター詳細と生 JSON をペアで保持します。
 */
private data class MonitorDetailWithRaw(
    val detail: MonitorDetail,
    val rawJson: String?
)
