package jp.yuki_yamada.datadogmonitorwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.yuki_yamada.datadogmonitorwidget.ui.theme.DatadogMonitorWidgetTheme

/**
 * アプリのメインエントリポイントとなるアクティビティ。
 * ウィジェットの追加方法の案内と、ホーム画面へのピン留め機能を提供します。
 * また、バッテリー最適化の除外設定へのアクセスも提供します。
 */
class MainActivity : ComponentActivity() {

    // バッテリー最適化から除外されているかどうかを Compose で観察可能な状態として保持する
    private var isIgnoringBatteryOptimizations by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初期状態を onCreate で設定する（onResume でも更新される）
        val powerManager = getSystemService(PowerManager::class.java)
        isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
        enableEdgeToEdge()
        setContent {
            DatadogMonitorWidgetTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher),
                            contentDescription = "App Icon",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(24.dp))
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Datadog Monitor Widget",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Add a widget to your home screen to monitor your Datadog status at a glance.",
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        AddWidgetButton()
                        Spacer(modifier = Modifier.height(16.dp))
                        BatteryOptimizationSection(isIgnoringBatteryOptimizations)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 設定画面から戻ったときにバッテリー最適化の状態を更新する
        val powerManager = getSystemService(PowerManager::class.java)
        isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
    }
}

/**
 * システムの機能を使用して、ホーム画面にウィジェットを追加するボタン。
 */
@Composable
fun AddWidgetButton() {
    val context = LocalContext.current
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val myProvider = ComponentName(context, MonitorWidgetReceiver::class.java)

    // Android 8.0 (API 26) 以上でサポートされているピン留め機能
    if (appWidgetManager.isRequestPinAppWidgetSupported) {
        Button(
            onClick = {
                appWidgetManager.requestPinAppWidget(myProvider, null, null)
            }
        ) {
            Text("Add Widget to Home Screen")
        }
    }
}

/**
 * バッテリー最適化の状態を表示し、除外設定を行うためのセクション。
 * [isIgnoring] が false のときは警告と設定ボタンを表示する。
 */
@Composable
fun BatteryOptimizationSection(isIgnoring: Boolean) {
    val context = LocalContext.current
    val packageName = context.packageName

    if (isIgnoring) {
        Text(
            text = stringResource(R.string.battery_optimization_ok),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium
        )
    } else {
        Text(
            text = stringResource(R.string.battery_optimization_warning),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                // このアプリをバッテリー最適化から除外するようシステムに直接要求する
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                context.startActivity(intent)
            }
        ) {
            Text(stringResource(R.string.disable_battery_optimization))
        }
    }
}
