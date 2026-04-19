package jp.yuki_yamada.datadogmonitorwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import jp.yuki_yamada.datadogmonitorwidget.ui.MonitorRow
import jp.yuki_yamada.datadogmonitorwidget.ui.MuteDurationDialog
import jp.yuki_yamada.datadogmonitorwidget.ui.StatusCountBadge
import jp.yuki_yamada.datadogmonitorwidget.ui.theme.DatadogMonitorWidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * マルチモニター内の個々のグループ（内訳）を表示するアクティビティ。
 * モニター詳細画面から特定のマルチモニターをタップした際に開かれます。
 */
class MonitorBreakdownActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val monitorId = intent.getLongExtra(EXTRA_MONITOR_ID, -1)
        val monitorName = intent.getStringExtra(EXTRA_MONITOR_NAME).orEmpty()
        val groupStatusesJson = intent.getStringExtra(EXTRA_GROUP_STATUSES_JSON).orEmpty()
        val appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, -1)

        enableEdgeToEdge()
        setContent {
            DatadogMonitorWidgetTheme {
                MonitorBreakdownScreen(
                    appWidgetId = appWidgetId,
                    monitorId = monitorId,
                    monitorName = monitorName,
                    groupStatusesJson = groupStatusesJson,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    companion object {
        const val EXTRA_MONITOR_ID = "monitor_id"
        const val EXTRA_MONITOR_NAME = "monitor_name"
        const val EXTRA_GROUP_STATUSES_JSON = "group_statuses_json"
        const val EXTRA_APPWIDGET_ID = "appwidget_id"
    }
}

/**
 * 内訳表示画面のメイン UI。
 * ステータスごとにタブを分け、グループの一覧を表示します。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonitorBreakdownScreen(
    appWidgetId: Int,
    monitorId: Long,
    monitorName: String,
    groupStatusesJson: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }
    
    // 表示するグループリストの状態管理
    var groups by rememberSaveable(stateSaver = monitorGroupStatusesSaver) {
        mutableStateOf(emptyList())
    }
    var isLoading by rememberSaveable { mutableStateOf(true) }
    var appUrl by remember { mutableStateOf("https://app.datadoghq.com/") }
    var apiKey by remember { mutableStateOf("") }
    var appKey by remember { mutableStateOf("") }
    var siteUrl by remember { mutableStateOf("https://api.datadoghq.com/") }

    // 選択（一括ミュート用）の状態管理
    val selectedGroupNames = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedGroupNames.isNotEmpty()
    var showMuteDialog by remember { mutableStateOf(false) }

    // 表示するタブ（ステータス）の順序定義
    val statusTabs = remember {
        listOf(
            MonitorStatus.ALERT,
            MonitorStatus.WARN,
            MonitorStatus.MUTED,
            MonitorStatus.OK,
            MonitorStatus.NO_DATA
        )
    }
    var selectedTabIndex by rememberSaveable(groupStatusesJson) { mutableIntStateOf(0) }
    var hasDefaultTabBeenSet by rememberSaveable(groupStatusesJson) { mutableStateOf(false) }

    // インテント経由で渡された JSON データをパースし、キー情報を読み込む
    LaunchedEffect(groupStatusesJson, appWidgetId) {
        val decodedGroups = withContext(Dispatchers.Default) {
            decodeMonitorGroupStatuses(json, groupStatusesJson)
        }
        groups = decodedGroups

        // データが存在する最初のステータスタブを自動選択する
        if (!hasDefaultTabBeenSet && decodedGroups.isNotEmpty()) {
            val firstNonEmptyIndex = statusTabs.indexOfFirst { status ->
                decodedGroups.any { it.status == status }
            }
            if (firstNonEmptyIndex != -1) {
                selectedTabIndex = firstNonEmptyIndex
            }
            hasDefaultTabBeenSet = true
        }

        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val state = MonitorWidgetState(prefs)
        
        appUrl = state.appUrl
        apiKey = state.apiKey
        appKey = state.appKey
        siteUrl = state.siteUrl

        isLoading = false
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            // グループ選択中の操作バー
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
                            "${selectedGroupNames.size} selected",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = { showMuteDialog = true }) {
                            Text("Mute")
                        }
                        TextButton(onClick = { selectedGroupNames.clear() }) {
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
                .padding(top = 16.dp)
                .fillMaxSize()
        ) {
            Text(
                text = if (monitorName.isBlank()) stringResource(R.string.monitor_breakdown_title) else monitorName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp)
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
                // データが存在するステータスのみタブとして表示する
                val availableTabs = statusTabs.filter { status -> groups.any { it.status == status } }
                val selectedStatus = statusTabs.getOrNull(selectedTabIndex) ?: MonitorStatus.OK
                val selectedTabRowIndex = availableTabs.indexOf(selectedStatus).coerceAtLeast(0)
                val filteredGroups = groups.filter { it.status == availableTabs.getOrNull(selectedTabRowIndex) }

                if (availableTabs.isNotEmpty()) {
                    TabRow(
                        selectedTabIndex = selectedTabRowIndex,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = { tabPositions ->
                            if (selectedTabRowIndex < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabRowIndex]),
                                    color = getStatusColor(availableTabs[selectedTabRowIndex])
                                )
                            }
                        }
                    ) {
                        availableTabs.forEach { status ->
                            val count = groups.count { it.status == status }
                            val isSelected = status == availableTabs.getOrNull(selectedTabRowIndex)
                            Tab(
                                selected = isSelected,
                                onClick = { selectedTabIndex = statusTabs.indexOf(status) },
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 4.dp)
                                        .alpha(if (isSelected) 1f else 0.6f)
                                ) {
                                    StatusCountBadge(
                                        text = "${status.name.replace('_', ' ')} $count",
                                        status = status
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_monitor_data),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                    ) {
                        items(filteredGroups) { group ->
                            val isSelected = group.name in selectedGroupNames
                            MonitorRow(
                                name = formatGroupName(group.name),
                                status = group.status,
                                modifier = Modifier
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else Color.Transparent
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                if (isSelected) selectedGroupNames.remove(group.name)
                                                else selectedGroupNames.add(group.name)
                                            } else {
                                                if (monitorId > 0) {
                                                    // 特定のグループに絞り込んだ状態で Datadog の Web ページを開く
                                                    val monitorUrl = buildString {
                                                        append("${appUrl.trimEnd('/')}/monitors/$monitorId?q=${Uri.encode(group.name)}")
                                                        group.lastTriggeredTs?.let { ts ->
                                                            val fromTs = ts - 3600
                                                            val now = System.currentTimeMillis() / 1000
                                                            append("&from_ts=$fromTs")
                                                            append("&start=$fromTs")
                                                            append("&to_ts=$now")
                                                            append("&end=$now")
                                                        }
                                                    }
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(monitorUrl))
                                                    context.startActivity(intent)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelected) {
                                                selectedGroupNames.add(group.name)
                                            }
                                        }
                                    )
                            )
                        }
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
                        monitorIds = listOf(monitorId),
                        groupNames = selectedGroupNames.toList(),
                        durationMinutes = durationMins
                    )

                    result.onSuccess {
                        selectedGroupNames.clear()
                        // 画面を閉じて親（DetailActivity）に戻ることで再取得を促す
                        (context as? ComponentActivity)?.finish()
                    }
                }
            }
        )
    }
}

/**
 * Datadog の内部タグ形式を、表示用にフレンドリーな形式に整形します。
 * 例: "host:my-host,service:api" -> "my-host:api"
 */
private fun formatGroupName(name: String): String {
    return name.split(",")
        .map { part ->
            val colonIndex = part.indexOf(':')
            if (colonIndex != -1) {
                part.substring(colonIndex + 1).trim()
            } else {
                part.trim()
            }
        }
        .filter { it.isNotEmpty() }
        .joinToString(":")
}

private fun getStatusColor(status: MonitorStatus): Color = when (status) {
    MonitorStatus.ALERT -> Color(0xFFF44336)
    MonitorStatus.WARN -> Color(0xFFFFA000)
    MonitorStatus.MUTED -> Color(0xFF9C27B0)
    MonitorStatus.OK -> Color(0xFF4CAF50)
    MonitorStatus.NO_DATA -> Color(0xFF9E9E9E)
}

/**
 * Compose の再構成時にグループリストの状態を保持するための Saver。
 */
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
    groups.flatMap { listOf(it.name, it.status.name, it.lastTriggeredTs?.toString() ?: "") }

internal fun restoreMonitorGroupStatuses(saved: List<String>): List<MonitorGroupStatus> {
    if (saved.size % 3 != 0) return emptyList()
    return runCatching {
        saved.chunked(3).map { chunk ->
            MonitorGroupStatus(
                name = chunk[0],
                status = MonitorStatus.valueOf(chunk[1]),
                lastTriggeredTs = chunk[2].toLongOrNull()
            )
        }
    }.getOrDefault(emptyList())
}
