package jp.yuki_yamada.datadogmonitorwidget

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
            name = "multi",
            status = MonitorStatus.ALERT,
            groupStatuses = listOf(
                MonitorGroupStatus("group-a", MonitorStatus.ALERT)
            )
        )

        assertEquals(true, detail.isMultiAlert)
    }

    @Test
    fun `resolvedStatusCounts derives counts from group statuses when status counts are missing`() {
        val detail = MonitorDetail(
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
}
