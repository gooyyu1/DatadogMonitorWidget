package jp.yuki_yamada.datadogmonitorwidget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import jp.yuki_yamada.datadogmonitorwidget.MonitorStatus

/**
 * モニター情報を 1 行で表示するコンポーネント。
 * 名称と、その右側に現在のステータスバッジを表示します。
 *
 * @param name モニター名
 * @param status モニターの現在のステータス
 * @param modifier レイアウト調整用の Modifier
 */
@Composable
fun MonitorRow(name: String, status: MonitorStatus, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        StatusCountBadge(status.name.replace('_', ' '), status)
    }
}

/**
 * ステータスを表示するための色付きバッジコンポーネント。
 * ステータスに応じた背景色（ALERT: 赤, WARN: オレンジ, MUTED: 紫, OK: 緑, NO_DATA: グレー）で表示されます。
 *
 * @param text バッジ内に表示するテキスト
 * @param status ステータス（背景色の決定に使用）
 */
@Composable
fun StatusCountBadge(text: String, status: MonitorStatus) {
    val color = when (status) {
        MonitorStatus.ALERT -> Color(0xFFF44336)
        MonitorStatus.WARN -> Color(0xFFFFA000)
        MonitorStatus.MUTED -> Color(0xFF9C27B0)
        MonitorStatus.OK -> Color(0xFF4CAF50)
        MonitorStatus.NO_DATA -> Color(0xFF9E9E9E)
    }
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * ミュート期間を選択するためのダイアログ。
 * 4時間、8時間、12時間、16時間、24時間、1ヶ月の選択肢を提供します。
 *
 * @param onDismiss ダイアログを閉じる際のコールバック
 * @param onConfirm 期間（ミリ秒）が選択された際のコールバック
 */
@Composable
fun MuteDurationDialog(
    onDismiss: () -> Unit,
    onConfirm: (durationMinutes: Long) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Mute Duration", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                val durations = listOf(
                    "4 hours" to 240L,
                    "8 hours" to 480L,
                    "12 hours" to 720L,
                    "16 hours" to 960L,
                    "24 hours" to 1440L,
                    "1 month" to 43200L
                )
                durations.forEach { (label, minutes) ->
                    Button(
                        onClick = { onConfirm(minutes) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
