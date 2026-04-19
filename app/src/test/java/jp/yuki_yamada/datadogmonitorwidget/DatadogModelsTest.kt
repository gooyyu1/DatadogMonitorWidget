package jp.yuki_yamada.datadogmonitorwidget

import com.datadog.api.client.ApiClient
import com.datadog.api.client.v1.model.Monitor as DatadogMonitor
import com.datadog.api.client.v1.model.MonitorSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class DatadogModelsTest {
    private val mapper = ApiClient().json.mapper

    @Test
    fun `fromRaw maps monitor statuses`() {
        assertEquals(MonitorStatus.ALERT, MonitorStatus.fromRaw("Alert"))
        assertEquals(MonitorStatus.ALERT, MonitorStatus.fromRaw("ALERTING"))
        assertEquals(MonitorStatus.WARN, MonitorStatus.fromRaw("Warn"))
        assertEquals(MonitorStatus.WARN, MonitorStatus.fromRaw("WARNING"))
        assertEquals(MonitorStatus.OK, MonitorStatus.fromRaw("OK"))
        assertEquals(MonitorStatus.NO_DATA, MonitorStatus.fromRaw("No Data"))
        assertEquals(MonitorStatus.NO_DATA, MonitorStatus.fromRaw("NO_DATA"))
        assertEquals(MonitorStatus.NO_DATA, MonitorStatus.fromRaw("NODATA"))
        assertEquals(MonitorStatus.NO_DATA, MonitorStatus.fromRaw("UNKNOWN"))
        assertEquals(MonitorStatus.NO_DATA, MonitorStatus.fromRaw(null))
        assertEquals(MonitorStatus.NO_DATA, MonitorStatus.fromRaw("something else"))
    }

    @Test
    fun `monitorStatusPriority sorts alert warn ok no_data order`() {
        val sorted = listOf(
            MonitorStatus.NO_DATA,
            MonitorStatus.OK,
            MonitorStatus.ALERT,
            MonitorStatus.WARN
        ).sortedBy { monitorStatusPriority(it) }

        assertEquals(
            listOf(MonitorStatus.ALERT, MonitorStatus.WARN, MonitorStatus.OK, MonitorStatus.NO_DATA),
            sorted
        )
    }

    @Test
    fun `isMultiAlert true when only group statuses exist`() {
        val detail = MonitorDetail(
            id = 1L,
            name = "multi",
            status = MonitorStatus.ALERT,
            isMultiMonitor = true,
            groupStatuses = listOf(
                MonitorGroupStatus("group-a", MonitorStatus.ALERT)
            )
        )

        assertEquals(true, detail.isMultiAlert)
    }

    @Test
    fun `resolvedStatusCounts derives counts from group statuses when status counts are missing`() {
        val detail = MonitorDetail(
            id = 1L,
            name = "multi",
            status = MonitorStatus.ALERT,
            groupStatuses = listOf(
                MonitorGroupStatus("a", MonitorStatus.ALERT),
                MonitorGroupStatus("b", MonitorStatus.WARN),
                MonitorGroupStatus("c", MonitorStatus.OK),
                MonitorGroupStatus("d", MonitorStatus.NO_DATA)
            )
        )

        assertEquals(1, detail.resolvedStatusCounts?.alert)
        assertEquals(1, detail.resolvedStatusCounts?.warn)
        assertEquals(1, detail.resolvedStatusCounts?.ok)
        assertEquals(1, detail.resolvedStatusCounts?.noData)
    }

    @Test
    fun `toMonitorDetail maps search monitor to simple detail`() {
        val detail = Monitor(id = 42L, name = "db", status = "Alert").toMonitorDetail()

        assertEquals(42L, detail.id)
        assertEquals("db", detail.name)
        assertEquals(MonitorStatus.ALERT, detail.status)
        assertEquals(false, detail.isMultiMonitor)
    }

    @Test
    fun `monitor search result maps to app monitor`() {
        val result = mapper.readValue(
            """{"id":99,"name":"search monitor","status":"Alert"}""",
            MonitorSearchResult::class.java
        )

        val monitor = result.toMonitorOrNull()

        assertEquals(99L, monitor?.id)
        assertEquals("search monitor", monitor?.name)
        assertEquals("Alert", monitor?.status)
    }

    @Test
    fun `monitor search result defaults missing optional fields to empty strings`() {
        val result = mapper.readValue(
            """{"id":100}""",
            MonitorSearchResult::class.java
        )

        val monitor = result.toMonitorOrNull()

        assertEquals(100L, monitor?.id)
        assertEquals("", monitor?.name)
        assertEquals("", monitor?.status)
    }

    @Test
    fun `datadog monitor maps multi and group statuses`() {
        val response = mapper.readValue(
            """
            {
              "id": 7,
              "name": "multi monitor",
              "query": "avg(last_5m):avg:system.cpu.user{*} > 90",
              "type": "query alert",
              "overall_state": "Warn",
              "multi": true,
              "state": {
                "groups": {
                  "env:prod": { "status": "Alert" },
                  "env:stg": { "status": "OK" }
                }
              }
            }
            """.trimIndent(),
            DatadogMonitor::class.java
        )

        val detail = response.toMonitorDetail(fallbackStatus = MonitorStatus.NO_DATA)

        assertEquals(7L, detail.id)
        assertEquals("multi monitor", detail.name)
        assertEquals(MonitorStatus.WARN, detail.status)
        assertEquals(true, detail.isMultiMonitor)
        assertEquals(2, detail.groupStatuses.size)
        assertEquals(MonitorStatus.ALERT, detail.groupStatuses[0].status)
        assertEquals(MonitorStatus.OK, detail.groupStatuses[1].status)
    }

    @Test
    fun `datadog monitor group name falls back to group key when blank`() {
        val response = mapper.readValue(
            """
            {
              "id": 8,
              "name": "fallback monitor",
              "query": "avg(last_5m):avg:system.cpu.user{*} > 90",
              "type": "query alert",
              "state": {
                "groups": {
                  "env:prod": { "name": "", "status": "Alert" }
                }
              }
            }
            """.trimIndent(),
            DatadogMonitor::class.java
        )

        val detail = response.toMonitorDetail(fallbackStatus = MonitorStatus.NO_DATA)

        assertEquals(1, detail.groupStatuses.size)
        assertEquals("env:prod", detail.groupStatuses[0].name)
        assertEquals(MonitorStatus.ALERT, detail.groupStatuses[0].status)
    }
}
