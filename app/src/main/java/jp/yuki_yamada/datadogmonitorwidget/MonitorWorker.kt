package jp.yuki_yamada.datadogmonitorwidget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import java.util.concurrent.TimeUnit

/**
 * 背面で Datadog API からデータを取得し、ウィジェットの表示内容を更新する Worker クラス。
 * WorkManager によって定期的に実行されます（[scheduleNextWork] による連鎖実行）。
 * データの取得・保存はすべて [MonitorDataRepository] に委譲します。
 */
class MonitorWorker(
    private val context: Context,
    private val workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    /**
     * 非同期でのデータ取得処理の本体。
     * [MonitorDataRepository] を通じてデータを取得し、DataStore とウィジェットを更新します。
     */
    override suspend fun doWork(): ListenableWorker.Result {
        val appWidgetId = inputData.getInt("appWidgetId", -1)
        if (appWidgetId == -1) return ListenableWorker.Result.failure()

        val repository = MonitorDataRepository(context, appWidgetId)
        // 設定された間隔を先に読んでおく（refresh() が DataStore を書き換える前に取得）
        val interval = repository.getSettings().intervalMin

        val result = repository.refresh()

        // 設定された間隔で次回の更新を予約
        val intervalMins = interval.toLongOrNull() ?: 5L
        scheduleNextWork(appWidgetId, intervalMins)

        return if (result.isSuccess) ListenableWorker.Result.success()
        else ListenableWorker.Result.failure()
    }

    /**
     * 指定された分数後に自分自身を再度実行するように WorkManager に登録します。
     * これにより、ウィジェットの定期的かつ自動的な更新ループが形成されます。
     */
    private suspend fun scheduleNextWork(appWidgetId: Int, intervalMinutes: Long) {
        val nextWork = OneTimeWorkRequestBuilder<MonitorWorker>()
            .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
            .setInputData(androidx.work.workDataOf("appWidgetId" to appWidgetId))
            .addTag("MonitorUpdate_$appWidgetId")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "MonitorUpdate_$appWidgetId",
            ExistingWorkPolicy.REPLACE,
            nextWork
        )
    }
}
