package jp.yuki_yamada.datadogmonitorwidget

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.http.Query

class DatadogApiServiceTest {

    @Test
    fun `getMonitor includes group_states query parameter`() {
        val method = DatadogApiService::class.java.methods.find { it.name == "getMonitor" }
        assertNotNull(method)
        val queryValues = method!!.parameterAnnotations.mapNotNull { annotations ->
            annotations.filterIsInstance<Query>().firstOrNull()?.value
        }

        assertTrue(queryValues.contains("group_states"))
    }

    @Test
    fun `getMonitor default group_states is all`() = runBlocking {
        var capturedGroupStates: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                capturedGroupStates = chain.request().url.queryParameter("group_states")
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val service = Retrofit.Builder()
            .baseUrl("https://example.com/")
            .client(client)
            .build()
            .create(DatadogApiService::class.java)

        service.getMonitor(1L, "api-key", "app-key")
        assertEquals("all", capturedGroupStates)
    }
}
