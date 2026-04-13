package jp.yuki_yamada.datadogmonitorwidget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
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

class MonitorWidget : GlanceAppWidget() {

    companion object {
        val STATUS_TEXT = stringPreferencesKey("status_text")
        val STATUS_COLOR = stringPreferencesKey("status_color")
        val LAST_UPDATE_MILLIS = longPreferencesKey("last_update_millis")
        val API_KEY = stringPreferencesKey("api_key")
        val APP_KEY = stringPreferencesKey("app_key")
        val QUERY = stringPreferencesKey("query")
        val SITE_URL = stringPreferencesKey("site_url")
        val INTERVAL_MIN = stringPreferencesKey("interval")
        val LAST_ERROR = stringPreferencesKey("last_error")
        val LAST_SUCCESS_TIME = stringPreferencesKey("last_success_time")
    }

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val statusText = prefs[STATUS_TEXT] ?: "NA"
            val statusName = prefs[STATUS_COLOR] ?: MonitorStatus.ALERT.name
            val monitorStatus = try {
                MonitorStatus.valueOf(statusName)
            } catch (e: Exception) {
                MonitorStatus.ALERT
            }
            val lastUpdate = prefs[LAST_UPDATE_MILLIS] ?: 0L

            val bgColor = when (monitorStatus) {
                MonitorStatus.OK -> Color(0xFF4CAF50)
                MonitorStatus.WARN -> Color(0xFFFFA000)
                MonitorStatus.ALERT -> Color(0xFFF44336)
                MonitorStatus.NO_DATA -> Color(0xFF9E9E9E)
            }
            
            val textColor = Color.White
            val lastUpdateText = if (lastUpdate > 0) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastUpdate))
            } else ""

            val datadogIntent = context.packageManager.getLaunchIntentForPackage("com.datadog.app")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://app.datadoghq.com/"))

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(4.dp)
                    .clickable(actionStartActivity(datadogIntent)),
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
                    Text(
                        text = statusText,
                        style = TextStyle(
                            color = ColorProvider(textColor),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
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

class MonitorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonitorWidget()
}
