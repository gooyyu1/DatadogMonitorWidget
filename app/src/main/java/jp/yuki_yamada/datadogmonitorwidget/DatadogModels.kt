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
    OK, WARN, ALERT, NO_DATA
}
