package jp.yuki_yamada.datadogmonitorwidget

import com.datadog.api.client.ApiClient
import com.datadog.api.client.v1.api.MonitorsApi
import com.datadog.api.client.v1.model.Monitor as DatadogMonitor
import com.datadog.api.client.v1.model.MonitorSearchResult

private const val INVALID_MONITOR_ID = -1L

fun createMonitorsApi(apiKey: String, appKey: String, siteUrl: String): MonitorsApi {
    val apiClient = ApiClient()
    apiClient.setBasePath(siteUrl.trimEnd('/'))
    apiClient.configureApiKeys(
        hashMapOf(
            "apiKeyAuth" to apiKey,
            "appKeyAuth" to appKey
        )
    )
    return MonitorsApi(apiClient)
}

fun MonitorSearchResult.toMonitorOrNull(): Monitor? {
    val monitorId = id ?: return null
    return Monitor(
        id = monitorId,
        name = name.orEmpty(),
        status = status?.toString().orEmpty()
    )
}

fun DatadogMonitor.toMonitorDetail(fallbackStatus: MonitorStatus): MonitorDetail {
    val groups = state
        ?.groups
        .orEmpty()
        .map { (groupKey, groupState) ->
            MonitorGroupStatus(
                name = groupState.name?.takeIf { it.isNotBlank() } ?: groupKey,
                status = MonitorStatus.fromRaw(groupState.status?.toString())
            )
        }
        .sortedBy { monitorStatusPriority(it.status) }

    return MonitorDetail(
        id = id ?: INVALID_MONITOR_ID,
        name = name.orEmpty(),
        status = overallState?.toString()?.let(MonitorStatus::fromRaw) ?: fallbackStatus,
        isMultiMonitor = multi ?: false,
        groupStatuses = groups
    )
}
