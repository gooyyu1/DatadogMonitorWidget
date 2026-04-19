package jp.yuki_yamada.datadogmonitorwidget

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface DatadogApiService {
    @GET("api/v1/monitor/search")
    suspend fun searchMonitors(
        @Header("DD-API-KEY") apiKey: String,
        @Header("DD-APPLICATION-KEY") appKey: String,
        @Query("query") query: String
    ): Response<ResponseBody>

    @GET("api/v1/monitor/{monitor_id}")
    suspend fun getMonitor(
        @Path("monitor_id") monitorId: Long,
        @Header("DD-API-KEY") apiKey: String,
        @Header("DD-APPLICATION-KEY") appKey: String
    ): Response<ResponseBody>
}
