package jp.yuki_yamada.datadogmonitorwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ホーム画面に表示されるウィジェットの UI 定義。
 * Jetpack Glance を使用して、リモートビューとして構成されます。
 */
class MonitorWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    /**
     * ウィジェットの表示内容を構築します。
     * DataStore の値が更新されるたびに再呼び出しされます。
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val state = MonitorWidgetState(prefs)
            
            val statusText = state.statusText
            val monitorStatus = state.monitorStatus
            val lastUpdate = state.lastUpdateMillis

            // ステータスに応じた背景色の決定。
            // ユーザーに安心感を与えるため、MUTED も OK と同じ緑色で表示します。
            val bgColor = when (monitorStatus) {
                MonitorStatus.OK, MonitorStatus.MUTED -> Color(0xFF4CAF50)
                MonitorStatus.WARN -> Color(0xFFFFA000)
                MonitorStatus.ALERT -> Color(0xFFF44336)
                MonitorStatus.NO_DATA -> Color(0xFF9E9E9E)
            }
            
            val textColor = Color.White
            val lastUpdateText = if (lastUpdate > 0) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastUpdate))
            } else ""

            val appWidgetId = state.appWidgetId
            // ウィジェットをタップした際に開くアクティビティ（詳細画面）の定義
            val detailsIntent = Intent(context, MonitorListActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(4.dp)
                    .clickable(actionStartActivity(detailsIntent)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(bgColor)
                        .cornerRadius(12.dp)
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 「成功数/総数」などのメインテキスト
                    Text(
                        text = statusText,
                        style = TextStyle(
                            color = ColorProvider(textColor),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    // 最終更新時刻を小さく表示
                    if (lastUpdateText.isNotEmpty()) {
                        Text(
                            text = lastUpdateText,
                            style = TextStyle(
                                color = ColorProvider(textColor.copy(alpha = 0.8f)),
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * ウィジェットのライフサイクルイベント（追加、削除、更新）を受け取るレシーバー。
 * AndroidManifest.xml に登録されます。
 */
class MonitorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonitorWidget()
}
