package jp.yuki_yamada.datadogmonitorwidget

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * モニターをミュート（消音）するためのリクエストボディ。
 * [scope] にはミュート対象（例: "env:prod"）、[end] には終了時刻の Unix タイムスタンプを指定します。
 */
@Serializable
data class MuteMonitorRequest(
    val scope: String? = null,
    val end: Long? = null
)

/**
 * Datadog API (v1) のエンドポイントを定義するインターフェース。
 * Retrofit によって実装が生成されます。
 */
interface DatadogApiService {
    /**
     * 指定されたクエリに基づいてモニターを検索します。
     * ウィジェットに表示するモニターを特定するために最初に使用されます。
     */
    @GET("api/v1/monitor/search")
    suspend fun searchMonitors(
        @Header("DD-API-KEY") apiKey: String,
        @Header("DD-APPLICATION-KEY") appKey: String,
        @Query("query") query: String
    ): Response<ResponseBody>

    /**
     * 指定された ID のモニターの詳細情報を取得します。
     * ミュート状態やグループ（マルチモニター）の状態を取得するために使用されます。
     */
    @GET("api/v1/monitor/{monitor_id}")
    suspend fun getMonitor(
        @Path("monitor_id") monitorId: Long,
        @Header("DD-API-KEY") apiKey: String,
        @Header("DD-APPLICATION-KEY") appKey: String,
        @Query("group_states") groupStates: String = "all"
    ): Response<ResponseBody>

    /**
     * モニターを一時的にミュートします。
     * アプリの詳細画面から「Mute」ボタンを押した際に呼び出されます。
     */
    @POST("api/v1/monitor/{monitor_id}/mute")
    suspend fun muteMonitor(
        @Path("monitor_id") monitorId: Long,
        @Header("DD-API-KEY") apiKey: String,
        @Header("DD-APPLICATION-KEY") appKey: String,
        @Body request: MuteMonitorRequest
    ): Response<ResponseBody>
}
