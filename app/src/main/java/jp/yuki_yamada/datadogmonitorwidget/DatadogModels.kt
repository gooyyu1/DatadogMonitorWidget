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
    val name: String,
    val status: MonitorStatus,
    @SerialName("status_counts")
    val statusCounts: StatusCounts? = null,
    @SerialName("group_statuses")
    val groupStatuses: List<MonitorGroupStatus> = emptyList()
) {
    val isMultiAlert: Boolean
        get() = statusCounts != null
}
