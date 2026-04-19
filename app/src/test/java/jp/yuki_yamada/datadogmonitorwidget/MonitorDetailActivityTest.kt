package jp.yuki_yamada.datadogmonitorwidget

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitorDetailActivityTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodeMonitorGroupStatuses sorts by status priority`() {
        val groupStatusesJson = """
            [
              {"name":"group-ok","status":"OK"},
              {"name":"group-alert","status":"ALERT"},
              {"name":"group-no-data","status":"NO_DATA"},
              {"name":"group-warn","status":"WARN"}
            ]
        """.trimIndent()

        val groups = decodeMonitorGroupStatuses(json, groupStatusesJson)

        assertEquals(
            listOf(MonitorStatus.ALERT, MonitorStatus.WARN, MonitorStatus.OK, MonitorStatus.NO_DATA),
            groups.map { it.status }
        )
    }

    @Test
    fun `decodeMonitorGroupStatuses returns empty list on invalid json`() {
        val groups = decodeMonitorGroupStatuses(json, "not-json")

        assertEquals(emptyList<MonitorGroupStatus>(), groups)
    }

    @Test
    fun `save and restore monitor group statuses round trip`() {
        val original = listOf(
            MonitorGroupStatus("group-alert", MonitorStatus.ALERT),
            MonitorGroupStatus("group-ok", MonitorStatus.OK)
        )

        val restored = restoreMonitorGroupStatuses(saveMonitorGroupStatuses(original))

        assertEquals(original, restored)
    }

    @Test
    fun `restore monitor group statuses returns empty list for odd-sized data`() {
        val restored = restoreMonitorGroupStatuses(listOf("group-alert", "ALERT", "dangling"))

        assertEquals(emptyList<MonitorGroupStatus>(), restored)
    }

    @Test
    fun `restore monitor group statuses returns empty list for invalid enum`() {
        val restored = restoreMonitorGroupStatuses(listOf("group-alert", "UNKNOWN_STATUS"))

        assertEquals(emptyList<MonitorGroupStatus>(), restored)
    }
}
