package jp.yuki_yamada.datadogmonitorwidget

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MonitorWorkerStateUpdateTest {

    @Test
    fun applyWidgetUiState_keepsExistingErrorAndSuccessStatus() {
        val prefs = mutablePreferencesOf(
            MonitorWidgetState.LAST_ERROR to "network failure",
            MonitorWidgetState.LAST_SUCCESS_TIME to "2026-04-20 00:00:00"
        )
        val mutableState = MutableMonitorWidgetState(prefs)

        applyWidgetUiState(
            mutableState = mutableState,
            appWidgetId = 101,
            text = "NA",
            status = MonitorStatus.ALERT,
            monitorDetailsJson = "[]"
        )

        assertEquals("network failure", mutableState.lastError)
        assertEquals("2026-04-20 00:00:00", mutableState.lastSuccessTime)
        assertEquals("NA", mutableState.statusText)
        assertEquals(MonitorStatus.ALERT.name, mutableState.statusColor)
        assertEquals(101, mutableState.appWidgetId)
        assertEquals("[]", mutableState.monitorDetailsJson)
        assertNotEquals(0L, mutableState.lastUpdateMillis)
    }

    @Test
    fun saveSuccessPattern_clearsError() {
        val prefs = mutablePreferencesOf(
            MonitorWidgetState.LAST_ERROR to "network failure",
            MonitorWidgetState.LAST_SUCCESS_TIME to ""
        )
        val mutableState = MutableMonitorWidgetState(prefs)

        mutableState.lastSuccessTime = "2026-04-20 01:23:45"
        mutableState.lastError = ""

        assertEquals("", mutableState.lastError)
        assertEquals("2026-04-20 01:23:45", mutableState.lastSuccessTime)
    }
}
