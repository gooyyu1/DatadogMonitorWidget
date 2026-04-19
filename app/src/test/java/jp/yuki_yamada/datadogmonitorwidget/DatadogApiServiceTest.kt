package jp.yuki_yamada.datadogmonitorwidget

import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Query

class DatadogApiServiceTest {

    @Test
    fun `getMonitor includes group_states query parameter`() {
        val method = DatadogApiService::class.java.methods.first { it.name == "getMonitor" }
        val queryValues = method.parameterAnnotations.mapNotNull { annotations ->
            annotations.filterIsInstance<Query>().firstOrNull()?.value
        }

        assertTrue(queryValues.contains("group_states"))
    }

}
