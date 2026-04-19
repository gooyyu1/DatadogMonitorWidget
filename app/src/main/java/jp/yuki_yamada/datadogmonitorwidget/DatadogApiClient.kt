package jp.yuki_yamada.datadogmonitorwidget

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Datadog API との通信を管理するクライアントクラス。
 * API キーを使用した認証、リクエストの実行、およびレスポンスのパースを担当します。
 */
class DatadogApiClient(
    private val apiKey: String,
    private val appKey: String,
    private val siteUrl: String
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val service: DatadogApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(siteUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DatadogApiService::class.java)
    }

    /**
     * クエリに一致するモニターを検索し、それぞれの詳細情報を並列で取得します。
     * ウィジェットの更新や、初期設定時のモニター確認に使用されます。
     */
    suspend fun searchDetailedMonitors(query: String): List<MonitorDetail> = coroutineScope {
        val response = service.searchMonitors(apiKey, appKey, query)
        if (!response.isSuccessful) return@coroutineScope emptyList()

        val bodyString = response.body()?.string() ?: return@coroutineScope emptyList()
        val searchResults = json.decodeFromString<MonitorSearchResponse>(bodyString).monitors

        // 各モニターの詳細情報を並列で取得して、ミュート状態などを確定させる
        searchResults.map { monitor ->
            async {
                getMonitorDetail(monitor.id, fallbackStatus = MonitorStatus.fromRaw(monitor.status))
            }
        }.awaitAll()
    }

    /**
     * 指定されたモニターの詳細情報を取得します。
     * ミュート設定やグループごとのステータスを含む [MonitorDetail] を返します。
     * 詳細画面の表示や、個別のモニター更新時に使用されます。
     */
    suspend fun getMonitorDetail(monitorId: Long, fallbackStatus: MonitorStatus = MonitorStatus.NO_DATA): MonitorDetail {
        val response = try {
            service.getMonitor(monitorId, apiKey, appKey)
        } catch (e: Exception) {
            // ネットワークエラーなどの場合は、エラー内容を rawJson に含めて返す
            return MonitorDetail(
                id = monitorId,
                name = "Error Fetching Monitor ($monitorId)",
                status = fallbackStatus,
                isMultiMonitor = false,
                groupStatuses = emptyList(),
                rawJson = e.message
            )
        }
        
        // 成功時も失敗時もレスポンスボディを保持する（デバッグ用）
        val bodyString = if (response.isSuccessful) response.body()?.string() else response.errorBody()?.string()

        return if (response.isSuccessful && bodyString != null) {
            try {
                val detailResp = json.decodeFromString<MonitorDetailResponse>(bodyString)
                detailResp.toMonitorDetail(fallbackStatus = fallbackStatus, rawJson = bodyString)
            } catch (e: Exception) {
                MonitorDetail(
                    id = monitorId,
                    name = "Parse Error ($monitorId)",
                    status = fallbackStatus,
                    rawJson = "Parse error: ${e.message}\n\nOriginal body: $bodyString"
                )
            }
        } else {
            // API がエラーを返した場合は、エラーメッセージを含む最小限の情報を返す
            MonitorDetail(
                id = monitorId,
                name = "Unknown Monitor ($monitorId)",
                status = fallbackStatus,
                isMultiMonitor = false,
                groupStatuses = emptyList(),
                rawJson = bodyString
            )
        }
    }

    /**
     * モニターを一定時間ミュート（沈黙）させます。
     * [scope] が null の場合はモニター全体を、指定がある場合は特定のタグを対象にします。
     */
    suspend fun muteMonitor(monitorId: Long, scope: String? = null, durationMinutes: Long? = null): Boolean {
        // 現在時刻に期間を足して終了時刻(Unixタイムスタンプ)を算出する
        val endTimestamp = durationMinutes?.let { 
            (System.currentTimeMillis() / 1000) + (it * 60)
        }
        val request = MuteMonitorRequest(scope = scope, end = endTimestamp)
        val response = service.muteMonitor(monitorId, apiKey, appKey, request)
        return response.isSuccessful
    }
}
