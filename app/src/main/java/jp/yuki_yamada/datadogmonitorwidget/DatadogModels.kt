package jp.yuki_yamada.datadogmonitorwidget

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Datadog のモニター検索 API (Search API) から返される個々のモニター情報を表すモデル。
 */
@Serializable
data class Monitor(
    val id: Long,
    val name: String,
    val status: String
)

/**
 * モニター検索 API のレスポンス全体をラップするモデル。
 */
@Serializable
data class MonitorSearchResponse(
    val monitors: List<Monitor>
)

/**
 * モニターの状態を定義する列挙型。
 */
enum class MonitorStatus {
    OK, WARN, ALERT, NO_DATA, MUTED;

    companion object {
        fun fromRaw(raw: String?): MonitorStatus {
            val normalized = raw?.trim()?.uppercase()
            return when (normalized) {
                "ALERT", "ALERTING" -> ALERT
                "WARN", "WARNING" -> WARN
                "OK" -> OK
                "NO DATA", "NO_DATA", "NODATA", "UNKNOWN" -> NO_DATA
                "MUTED" -> MUTED
                else -> NO_DATA
            }
        }
    }
}

/**
 * ステータスの優先順位。
 */
fun monitorStatusPriority(status: MonitorStatus): Int = when (status) {
    MonitorStatus.ALERT -> 0
    MonitorStatus.WARN -> 1
    MonitorStatus.MUTED -> 2
    MonitorStatus.OK -> 3
    MonitorStatus.NO_DATA -> 4
}

/**
 * グループごとのステータスを集計するモデル。
 */
@Serializable
data class StatusCounts(
    val ok: Int,
    val warn: Int,
    val alert: Int,
    @SerialName("no_data")
    val noData: Int,
    val muted: Int = 0
)

/**
 * グループの状態を表すモデル。
 */
@Serializable
data class MonitorGroupStatus(
    val name: String,
    val status: MonitorStatus,
    @SerialName("last_triggered_ts")
    val lastTriggeredTs: Long? = null
)

/**
 * モニター詳細を表すメインデータモデル。
 */
@Serializable
data class MonitorDetail(
    val id: Long = -1,
    val name: String = "",
    val status: MonitorStatus = MonitorStatus.NO_DATA,
    @SerialName("is_multi_monitor")
    val isMultiMonitor: Boolean = false,
    @SerialName("group_statuses")
    val groupStatuses: List<MonitorGroupStatus> = emptyList(),
    val rawJson: String? = null
) {
    val isMultiAlert: Boolean
        get() = isMultiMonitor || groupStatuses.size > 1

    val resolvedStatusCounts: StatusCounts?
        get() = groupStatuses.takeIf { it.isNotEmpty() }?.let { groups ->
            StatusCounts(
                ok = groups.count { it.status == MonitorStatus.OK },
                warn = groups.count { it.status == MonitorStatus.WARN },
                alert = groups.count { it.status == MonitorStatus.ALERT },
                noData = groups.count { it.status == MonitorStatus.NO_DATA },
                muted = groups.count { it.status == MonitorStatus.MUTED }
            )
        }
}

/**
 * API からの生レスポンス [MonitorDetailResponse] をパースするためのモデル。
 */
@Serializable
data class MonitorDetailResponse(
    val id: Long,
    val name: String,
    @SerialName("overall_state")
    val overallState: String? = null,
    val multi: Boolean = false,
    val state: MonitorState? = null,
    val options: MonitorOptions? = null
)

@Serializable
data class MonitorOptions(
    val silenced: Map<String, Long>? = null
)

@Serializable
data class MonitorState(
    val groups: Map<String, MonitorStateGroup> = emptyMap()
)

@Serializable
data class MonitorStateGroup(
    val name: String? = null,
    val status: String? = null,
    @SerialName("overall_state")
    val overallState: String? = null,
    @SerialName("last_triggered_ts")
    val lastTriggeredTs: Long? = null
)

fun Monitor.toMonitorDetail(): MonitorDetail = MonitorDetail(
    id = id,
    name = name,
    status = MonitorStatus.fromRaw(status)
)

fun MonitorDetailResponse.toMonitorDetail(fallbackStatus: MonitorStatus, rawJson: String? = null): MonitorDetail {
    val mutedGroups = options?.silenced ?: emptyMap()
    val isAllMuted = mutedGroups.containsKey("*")

    val groups = state
        ?.groups
        ?.map { (groupKey, groupState) ->
            val rawStatus = MonitorStatus.fromRaw(groupState.status ?: groupState.overallState)
            val groupTags = groupKey.split(",").map { it.trim() }.toSet()
            val isMuted = isAllMuted || mutedGroups.keys.any { silencedScope ->
                if (silencedScope == "*") return@any false
                val silencedTags = silencedScope.split(",").map { it.trim() }.toSet()
                groupTags.containsAll(silencedTags)
            }
            val finalStatus = if (isMuted && (rawStatus == MonitorStatus.ALERT || rawStatus == MonitorStatus.WARN)) {
                MonitorStatus.MUTED
            } else {
                rawStatus
            }
            MonitorGroupStatus(
                name = groupState.name?.takeIf { it.isNotBlank() } ?: groupKey,
                status = finalStatus,
                lastTriggeredTs = groupState.lastTriggeredTs
            )
        }
        .orEmpty()
        .sortedBy { monitorStatusPriority(it.status) }

    val finalOverallStatus = if (groups.isNotEmpty()) {
        groups.minBy { monitorStatusPriority(it.status) }.status
    } else {
        val rawOverallStatus = if (overallState.isNullOrBlank()) fallbackStatus else MonitorStatus.fromRaw(overallState)
        if (isAllMuted && (rawOverallStatus == MonitorStatus.ALERT || rawOverallStatus == MonitorStatus.WARN)) {
            MonitorStatus.MUTED
        } else {
            rawOverallStatus
        }
    }

    return MonitorDetail(
        id = id,
        name = name,
        status = finalOverallStatus,
        isMultiMonitor = multi,
        groupStatuses = groups,
        rawJson = rawJson
    )
}

/**
 * 複数のモニターまたはグループを一括でミュートするための共通ヘルパー関数。
 * [monitorIds] にモニターIDリストを指定し [groupNames] が空の場合はモニター全体を、
 * [groupNames] に値がある場合は、特定のモニター（通常は1件）の特定グループをミュートします。
 */
suspend fun performBulkMute(
    context: Context,
    state: MonitorWidgetState,
    monitorIds: List<Long>,
    groupNames: List<String> = emptyList(),
    durationMinutes: Long?
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val client = DatadogApiClient(state.apiKey, state.appKey, state.siteUrl)
        if (groupNames.isEmpty()) {
            // モニター全体を一括ミュート
            for (id in monitorIds) {
                if (!client.muteMonitor(id, durationMinutes = durationMinutes)) {
                    throw Exception("Failed to mute monitor $id")
                }
            }
        } else {
            // 特定のモニター（1件）の特定グループを一括ミュート
            val monitorId = monitorIds.firstOrNull() ?: throw Exception("No monitor ID specified")
            for (scope in groupNames) {
                if (!client.muteMonitor(monitorId, scope = scope, durationMinutes = durationMinutes)) {
                    throw Exception("Failed to mute group: $scope")
                }
            }
        }
    }.onSuccess {
        withContext(Dispatchers.Main) {
            val message = if (groupNames.isEmpty()) "Muted ${monitorIds.size} monitors" else "Muted ${groupNames.size} groups"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }.onFailure { e ->
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Mute failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
