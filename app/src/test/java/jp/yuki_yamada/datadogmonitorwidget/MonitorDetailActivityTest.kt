package jp.yuki_yamada.datadogmonitorwidget

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.serialization.json.Json

class MonitorDetailActivityTest {

    @Test
    fun `buildDatadogResponseJson returns empty array when there are no responses`() {
        assertEquals("[]", buildDatadogResponseJson(emptyList()))
    }

    @Test
    fun `buildDatadogResponseJson pretty prints monitor detail responses as array`() {
        val json = buildDatadogResponseJson(
            listOf(
                """{"id":1,"multi":true}""",
                """{"id":2,"multi":false}"""
            )
        )

        assertEquals(
            Json.parseToJsonElement("""[{"id":1,"multi":true},{"id":2,"multi":false}]"""),
            Json.parseToJsonElement(json)
        )
    }
}
