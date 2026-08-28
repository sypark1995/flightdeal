package com.sypark.flightdeal.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sypark.flightdeal.booking.BookingLauncher
import com.sypark.flightdeal.ui.theme.FlightDealTheme
import com.sypark.flightdeal.worker.NotificationStatus
import com.sypark.flightdeal.worker.WorkScheduler
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val historyCount by viewModel.historyCount.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // onClick 람다는 클릭 시점에 실행되는 일반 람다라 그 안에서 Composable인
    // FlightDealTheme.colors를 읽을 수 없다. 미리 변수로 꺼내둔다.
    val indigo = FlightDealTheme.colors.indigo
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 알림 on/off는 이 화면이 계산하지 않는다. NotificationStatus가 유일한 판정이고
    // PriceChangeNotifier도 같은 함수를 부른다 — 여기서 따로 만들면 둘이 어긋난다.
    var notificationsAllowed by remember { mutableStateOf(NotificationStatus.isAllowed(context)) }

    // 사용자가 시스템 설정에서 채널을 끄고/켜고 돌아왔을 때 이 화면이 그대로 "꺼짐"을
    // 보여주면 고쳐지지 않은 줄 안다. ON_RESUME마다 다시 읽는다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = NotificationStatus.isAllowed(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("가격 이력을 지울까요?") },
            // 추적 항목은 남는다는 걸 분명히 말한다 — 안 그러면 사용자는 추적까지
            // 사라진 줄 알고 불안해한다.
            text = { Text("지금까지 쌓인 가격 관측 이력만 지워져요. 추적 중인 노선은 그대로 남아요.") },
            confirmButton = {
                TextButton(onClick = { showClearConfirm = false; viewModel.clearHistory() }) {
                    Text("지우기")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("취소") }
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FlightDealTheme.colors.background)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "내정보",
                color = FlightDealTheme.colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp),
            )

            ProfileSection(title = "알림") {
                Text(
                    text = if (notificationsAllowed) "켜짐" else "꺼짐",
                    // 꺼짐은 눈에 띄어야 한다. TextSecondary로 두면 사용자가 놓친다.
                    color = if (notificationsAllowed) {
                        FlightDealTheme.colors.textPrimary
                    } else {
                        FlightDealTheme.colors.priceUp
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (!notificationsAllowed) {
                    Text(
                        text = "알림이 꺼져 있어 가격이 바뀌어도 알려드릴 수 없어요",
                        color = FlightDealTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Button(
                    onClick = {
                        openNotificationSettings(context) {
                            scope.launch { snackbarHostState.showSnackbar("설정 화면을 열 수 없어요") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FlightDealTheme.colors.indigo,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Text(text = "알림 설정 열기")
                }
            }

            ProfileSection(title = "가격 확인") {
                Text(
                    text = "${WorkScheduler.INTERVAL_HOURS}시간마다 확인해요",
                    color = FlightDealTheme.colors.textPrimary,
                    fontSize = 14.sp,
                )
                Text(
                    // WorkManager는 정확한 시각을 보장하지 않는다. 약속하지 않은 것을
                    // 약속하지 않는다.
                    text = "기기가 절전 상태면 조금 늦을 수 있어요",
                    color = FlightDealTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            ProfileSection(title = "저장된 데이터") {
                Text(
                    text = "가격 이력 ${historyCount}건",
                    color = FlightDealTheme.colors.textPrimary,
                    fontSize = 14.sp,
                )
                Text(
                    text = "모든 데이터는 이 기기에만 저장돼요. 계정도, 서버도 없어요.",
                    color = FlightDealTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                // 0건이면 지울 게 없다. 버튼을 숨긴다.
                if (historyCount > 0) {
                    Text(
                        text = "이력 지우기",
                        color = FlightDealTheme.colors.priceUp,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clickable { showClearConfirm = true },
                    )
                }
            }

            ProfileSection(title = "가격 정보") {
                Text(
                    text = "Travelpayouts (Aviasales)",
                    color = FlightDealTheme.colors.indigo,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        if (!BookingLauncher.open(context, "https://www.aviasales.com", indigo)) {
                            // 이 화면은 알림 설정 실패도 같은 방식(화면에 붙은 코루틴 스코프 +
                            // 로컬 SnackbarHostState)으로 알린다. ViewModel에 없는 메시지
                            // 채널을 이 하나 때문에 새로 만들지 않는다.
                            scope.launch { snackbarHostState.showSnackbar("예약 페이지를 열 수 있는 앱이 없어요") }
                        }
                    },
                )
                Text(
                    text = "표시 가격은 참고가예요",
                    color = FlightDealTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            ProfileSection(title = "앱", showDivider = false) {
                Text(
                    text = "버전 ${appVersionName(context)}",
                    color = FlightDealTheme.colors.textSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun ProfileSection(
    title: String,
    showDivider: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = title,
            color = FlightDealTheme.colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            content()
        }
    }
    if (showDivider) {
        HorizontalDivider(color = FlightDealTheme.colors.outline)
    }
}

/**
 * `Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS`는 API 26부터 있지만, 채널이 아직
 * 없는 상태(알림을 한 번도 안 보낸 새 설치)에서 열면 예외 없이 그냥 빈 화면이 열렸다가
 * 바로 끝난다 — 그래서 여는 시도 전에 [NotificationStatus.ensureChannel]로 채널부터
 * 만들어둔다. 아래 `ActivityNotFoundException` 처리는 이 경우를 잡지 못한다: 그건
 * 이 기기에 해당 액션을 처리할 Settings 액티비티 자체가 없는, 더 드문 경우를 잡는다.
 * 앱 알림 설정으로, 그것도 안 되면 앱 상세 설정으로 물러선다. 셋 다 실패하면
 * 조용히 아무 일도 없으면 안 되므로 [onAllFailed]로 알린다.
 */
private fun openNotificationSettings(context: android.content.Context, onAllFailed: () -> Unit) {
    NotificationStatus.ensureChannel(context)
    val candidates = listOf(
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationStatus.CHANNEL_ID),
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null)),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // 다음 후보로 물러선다.
        }
    }
    onAllFailed()
}

/**
 * `:presentation`은 `buildFeatures.buildConfig`를 켜지 않았다 — 이 화면 하나 때문에
 * 빌드 기능을 새로 켜는 대신 이미 있는 PackageManager로 읽는다.
 */
private fun appVersionName(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "-"
