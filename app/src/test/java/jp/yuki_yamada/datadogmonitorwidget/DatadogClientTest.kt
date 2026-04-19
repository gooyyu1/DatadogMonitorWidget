package jp.yuki_yamada.datadogmonitorwidget

import com.datadog.api.client.v1.model.MonitorSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DatadogClientTest {

    @Test
    fun `createMonitorsApi normalizes trailing slash in base path`() {
        val monitorsApi = createMonitorsApi(
            apiKey = "api-key",
            appKey = "app-key",
            siteUrl = "https://api.datadoghq.com/"
        )

        assertEquals("https://api.datadoghq.com", monitorsApi.apiClient.basePath)
    }

    @Test
    fun `toMonitorOrNull returns null when monitor id is missing`() {
        val result = MonitorSearchResult()

        assertNull(result.toMonitorOrNull())
    }
}
