package jp.yuki_yamada.datadogmonitorwidget

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [MonitorDataRepository] の純粋ロジック部分（DataStore 更新ヘルパー）のテスト。
 * Android 依存のある API 通信やウィジェット更新は直接テストせず、
 * DataStore への書き込みを担う [applyWidgetUiState] をテストします。
 */
class MonitorDataRepositoryTest {

    @Test
    fun applyWidgetUiState_setsAllUiFields() {
        val prefs = mutablePreferencesOf()
        val mutableState = MutableMonitorWidgetState(prefs)

        applyWidgetUiState(
            mutableState = mutableState,
            appWidgetId = 42,
            text = "3/5",
            status = MonitorStatus.WARN,
            monitorDetailsJson = """[{"id":1,"name":"test","status":"WARN"}]"""
        )

        assertEquals("3/5", mutableState.statusText)
        assertEquals(MonitorStatus.WARN.name, mutableState.statusColor)
        assertEquals(42, mutableState.appWidgetId)
        assertEquals("""[{"id":1,"name":"test","status":"WARN"}]""", mutableState.monitorDetailsJson)
        assertNotEquals(0L, mutableState.lastUpdateMillis)
    }

    @Test
    fun applyWidgetUiState_doesNotClearExistingErrorOrSuccessTime() {
        val prefs = mutablePreferencesOf(
            MonitorWidgetState.LAST_ERROR to "timeout",
            MonitorWidgetState.LAST_SUCCESS_TIME to "2026-04-20 10:00:00"
        )
        val mutableState = MutableMonitorWidgetState(prefs)

        applyWidgetUiState(
            mutableState = mutableState,
            appWidgetId = 1,
            text = "NA",
            status = MonitorStatus.ALERT,
            monitorDetailsJson = "[]"
        )

        // LAST_ERROR と LAST_SUCCESS_TIME はリポジトリの saveSuccess/saveError が担当する
        assertEquals("timeout", mutableState.lastError)
        assertEquals("2026-04-20 10:00:00", mutableState.lastSuccessTime)
    }

    @Test
    fun applyWidgetUiState_noDataStatus_setsCorrectColor() {
        val prefs = mutablePreferencesOf()
        val mutableState = MutableMonitorWidgetState(prefs)

        applyWidgetUiState(
            mutableState = mutableState,
            appWidgetId = 99,
            text = "0/0",
            status = MonitorStatus.NO_DATA,
            monitorDetailsJson = "[]"
        )

        assertEquals(MonitorStatus.NO_DATA.name, mutableState.statusColor)
    }

    @Test
    fun applyWidgetUiState_mutedStatus_setsCorrectColor() {
        val prefs = mutablePreferencesOf()
        val mutableState = MutableMonitorWidgetState(prefs)

        applyWidgetUiState(
            mutableState = mutableState,
            appWidgetId = 7,
            text = "2/2",
            status = MonitorStatus.MUTED,
            monitorDetailsJson = "[]"
        )

        assertEquals(MonitorStatus.MUTED.name, mutableState.statusColor)
    }
}
