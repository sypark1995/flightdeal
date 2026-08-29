package com.sypark.flightdeal.tracking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.feed.formatWon
import com.sypark.flightdeal.ui.theme.FlightDealTheme

/** [Won.amount]가 Int라 9자리(9억 원)를 넘기면 넘친다. 입력 단계에서부터 막는다. */
private const val MAX_DIGITS = 9

/**
 * 목표가를 정하거나 바꾸거나 해제하는 다이얼로그.
 *
 * @param currentPrice 지금 관측된 가격. 없으면(관측 전이면) 빈 칸으로 열고 안내도 생략한다.
 * @param existingTarget 이미 정해 둔 목표가. 있으면 그 값으로 채워 연다.
 */
@Composable
fun TargetPriceDialog(
    currentPrice: Won?,
    existingTarget: Won?,
    onDismiss: () -> Unit,
    onSave: (Won) -> Unit,
    onClear: () -> Unit,
) {
    // 빈 칸보다 고칠 값이 있는 편이 빠르고, 얼마쯤이 현실적인지도 알려준다 —
    // 그래서 기존 목표가가 없으면 현재가로 채워서 연다.
    var text by remember {
        mutableStateOf((existingTarget ?: currentPrice)?.amount?.toString().orEmpty())
    }
    val parsed = parseTargetPrice(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("목표가 설정") },
        text = {
            Column {
                if (currentPrice != null) {
                    Text(
                        text = "지금 가격 ${formatWon(currentPrice)}",
                        color = FlightDealTheme.colors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        // 숫자만 남기고 자릿수를 제한한다. 붙여넣기로 쉼표나 "원"이
                        // 딸려와도 걸러지고, Won.amount(Int)가 넘치기 전에 막는다.
                        text = input.filter { it.isDigit() }.take(MAX_DIGITS)
                    },
                    label = { Text("목표가") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                // 막지는 않는다. 사용자의 선택이고, 다만 결과를 알려준다.
                if (currentPrice != null && parsed != null && parsed >= currentPrice) {
                    Text(
                        text = "지금 가격보다 높아요. 바로 도달로 표시돼요.",
                        color = FlightDealTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) {
                Text("저장")
            }
        },
        dismissButton = {
            Row {
                // 기존 목표가가 있을 때만 해제할 게 있다.
                if (existingTarget != null) {
                    TextButton(onClick = onClear) { Text("해제") }
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}
