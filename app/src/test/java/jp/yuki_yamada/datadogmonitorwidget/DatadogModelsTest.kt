package jp.yuki_yamada.datadogmonitorwidget

import com.datadog.api.client.v1.model.Monitor as DatadogMonitor
import com.datadog.api.client.v1.model.MonitorOverallStates
import com.datadog.api.client.v1.model.MonitorSearchResult
import com.datadog.api.client.v1.model.MonitorState
import com.datadog.api.client.v1.model.MonitorStateGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class DatadogModelsTest {

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
        val result = MonitorSearchResult()
        setPrivateField(result, "id", 99L)
        setPrivateField(result, "name", "search monitor")
        setPrivateField(result, "status", MonitorOverallStates.ALERT)

        val monitor = result.toMonitorOrNull()

        assertEquals(99L, monitor?.id)
        assertEquals("search monitor", monitor?.name)
        assertEquals("Alert", monitor?.status)
    }

    @Test
    fun `datadog monitor maps multi and group statuses`() {
        val prodGroup = MonitorStateGroup()
        setPrivateField(prodGroup, "status", MonitorOverallStates.ALERT)
        val stgGroup = MonitorStateGroup()
        setPrivateField(stgGroup, "status", MonitorOverallStates.OK)

        val response = DatadogMonitor()
        setPrivateField(response, "id", 7L)
        setPrivateField(response, "name", "multi monitor")
        setPrivateField(response, "overallState", MonitorOverallStates.WARN)
        setPrivateField(response, "multi", true)
        setPrivateField(
            response,
            "state",
            MonitorState().groups(
                linkedMapOf(
                    "env:prod" to prodGroup,
                    "env:stg" to stgGroup
                )
            )
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

    private fun setPrivateField(target: Any, fieldName: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}
