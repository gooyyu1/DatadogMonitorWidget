package jp.yuki_yamada.datadogmonitorwidget

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Monitor(
    val id: Long,
    val name: String,
    val status: String // Datadog Monitor Search API uses "status"
)

@Serializable
data class MonitorSearchResponse(
    val monitors: List<Monitor>
)

enum class MonitorStatus {
    OK, WARN, ALERT, NO_DATA;

    companion object {
        fun fromRaw(raw: String?): MonitorStatus {
            val normalized = raw?.trim()?.uppercase()
            return when (normalized) {
                "ALERT", "ALERTING" -> ALERT
                "WARN", "WARNING" -> WARN
                "OK" -> OK
                "NO DATA", "NO_DATA", "NODATA", "UNKNOWN" -> NO_DATA
                else -> NO_DATA
            }
        }
    }
}

fun monitorStatusPriority(status: MonitorStatus): Int = when (status) {
    MonitorStatus.ALERT -> 0
    MonitorStatus.WARN -> 1
    MonitorStatus.OK -> 2
    MonitorStatus.NO_DATA -> 3
}

@Serializable
data class StatusCounts(
    val ok: Int,
    val warn: Int,
    val alert: Int,
    @SerialName("no_data")
    val noData: Int
)

@Serializable
data class MonitorGroupStatus(
    val name: String,
    val status: MonitorStatus
)

@Serializable
data class MonitorDetail(
    val id: Long = -1,
    val name: String = "",
    val status: MonitorStatus = MonitorStatus.NO_DATA,
    @SerialName("is_multi_monitor")
    val isMultiMonitor: Boolean = false,
    @SerialName("group_statuses")
    val groupStatuses: List<MonitorGroupStatus> = emptyList()
) {
    val isMultiAlert: Boolean
        get() = isMultiMonitor

    val resolvedStatusCounts: StatusCounts?
        get() = groupStatuses.takeIf { it.isNotEmpty() }?.let { groups ->
            StatusCounts(
                ok = groups.count { it.status == MonitorStatus.OK },
                warn = groups.count { it.status == MonitorStatus.WARN },
                alert = groups.count { it.status == MonitorStatus.ALERT },
                noData = groups.count { it.status == MonitorStatus.NO_DATA }
            )
        }
}

@Serializable
data class MonitorDetailResponse(
    val id: Long,
    val name: String,
    @SerialName("overall_state")
    val overallState: String? = null,
    val multi: Boolean = false,
    val state: MonitorState? = null
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
    val overallState: String? = null
)

fun Monitor.toMonitorDetail(): MonitorDetail = MonitorDetail(
    id = id,
    name = name,
    status = MonitorStatus.fromRaw(status)
)

fun MonitorDetailResponse.toMonitorDetail(fallbackStatus: MonitorStatus): MonitorDetail {
    val groups = state
        ?.groups
        ?.map { (groupKey, groupState) ->
            MonitorGroupStatus(
                name = groupState.name?.takeIf { it.isNotBlank() } ?: groupKey,
                status = MonitorStatus.fromRaw(groupState.status ?: groupState.overallState)
            )
        }
        .orEmpty()
        .sortedBy { monitorStatusPriority(it.status) }

    return MonitorDetail(
        id = id,
        name = name,
        status = if (overallState.isNullOrBlank()) fallbackStatus else MonitorStatus.fromRaw(overallState),
        isMultiMonitor = multi,
        groupStatuses = groups
    )
}
