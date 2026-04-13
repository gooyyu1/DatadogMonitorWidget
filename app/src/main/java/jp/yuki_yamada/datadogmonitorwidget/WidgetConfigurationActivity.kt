package jp.yuki_yamada.datadogmonitorwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import jp.yuki_yamada.datadogmonitorwidget.ui.theme.DatadogMonitorWidgetTheme
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, resultValue)

        enableEdgeToEdge()
        setContent {
            DatadogMonitorWidgetTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ConfigurationScreen(
                        appWidgetId = appWidgetId,
                        modifier = Modifier.padding(innerPadding),
                        onSaved = {
                            val successResult = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            setResult(RESULT_OK, successResult)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ConfigurationScreen(
    appWidgetId: Int,
    modifier: Modifier = Modifier,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf("") }
    var appKey by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("5") }
    var siteUrl by remember { mutableStateOf("https://api.datadoghq.com/") }
    var lastError by remember { mutableStateOf("") }
    var lastSuccessTime by remember { mutableStateOf("") }

    LaunchedEffect(appWidgetId) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        apiKey = prefs[stringPreferencesKey("api_key")] ?: ""
        appKey = prefs[stringPreferencesKey("app_key")] ?: ""
        query = prefs[stringPreferencesKey("query")] ?: ""
        interval = prefs[stringPreferencesKey("interval")] ?: "5"
        siteUrl = prefs[stringPreferencesKey("site_url")] ?: "https://api.datadoghq.com/"
        lastError = prefs[stringPreferencesKey("last_error")] ?: ""
        lastSuccessTime = prefs[stringPreferencesKey("last_success_time")] ?: ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Widget Configuration", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = appKey,
            onValueChange = { appKey = it },
            label = { Text("Application Key") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Query (e.g. status:alert)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Text("Datadog Site (Region)", fontWeight = FontWeight.Bold)
        val sites = mapOf(
            "US1" to "https://api.datadoghq.com/",
            "US3" to "https://api.us3.datadoghq.com/",
            "US5" to "https://api.us5.datadoghq.com/",
            "EU1" to "https://api.datadoghq.eu/",
            "AP1" to "https://api.ap1.datadoghq.com/",
            "US1-FED" to "https://api.ddog-gov.com/"
        )
        sites.forEach { (name, url) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = siteUrl == url, onClick = { siteUrl = url })
                Text(text = name)
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("Update Interval (minutes)", fontWeight = FontWeight.Bold)
        val intervals = listOf("1", "3", "5", "10", "30")
        intervals.forEach { i ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = interval == i, onClick = { interval = i })
                Text(text = "$i min")
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[stringPreferencesKey("api_key")] = apiKey
                            this[stringPreferencesKey("app_key")] = appKey
                            this[stringPreferencesKey("query")] = query
                            this[stringPreferencesKey("interval")] = interval
                            this[stringPreferencesKey("site_url")] = siteUrl
                        }
                    }
                    MonitorWidget().update(context, glanceId)
                    startWork(context, appWidgetId, interval.toLong())
                    onSaved()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save and Start")
        }

        if (lastError.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Last Status:", color = Color.Red, fontWeight = FontWeight.Bold)
            Text(lastError, color = Color.Red)
        } else if (lastSuccessTime.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Last Status:", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
            Text("Successfully updated at $lastSuccessTime", color = Color(0xFF4CAF50))
        }
    }
}

private fun startWork(context: Context, appWidgetId: Int, intervalMinutes: Long) {
    val workRequest = OneTimeWorkRequestBuilder<MonitorWorker>()
        .setInputData(androidx.work.workDataOf("appWidgetId" to appWidgetId))
        .addTag("MonitorUpdate_$appWidgetId")
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "MonitorUpdate_$appWidgetId",
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
}
