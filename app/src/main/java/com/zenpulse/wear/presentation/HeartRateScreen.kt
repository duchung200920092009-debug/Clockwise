package com.zenpulse.wear.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.zenpulse.wear.R
import com.zenpulse.wear.data.HeartError

private val HeartRed = Color(0xFFE5484D)

/**
 * Màn hình DUY NHẤT của Stage 1: hiện nhịp tim thật, cập nhật real-time.
 * Cố ý tối giản — mục tiêu tuần này là một số nhịp tim *thật* và *nhảy theo tim*, không cần đẹp.
 */
@Composable
fun HeartRateScreen(
    state: HeartRateUiState,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        TimeText()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state.phase) {
                HeartPhase.CONNECTING -> Connecting()
                HeartPhase.WAITING_SIGNAL -> WaitingForSignal()
                HeartPhase.LIVE -> LiveHeartRate(bpm = state.bpm)
                HeartPhase.ERROR -> ErrorState(error = state.error, onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun Connecting() {
    CircularProgressIndicator(modifier = Modifier.size(28.dp))
    Caption(text = stringResource(R.string.state_connecting), topPadding = 12.dp)
}

@Composable
private fun WaitingForSignal() {
    HeartIcon(color = Color.Gray)
    BigNumber(text = stringResource(R.string.hr_placeholder))
    Unit_()
    Caption(text = stringResource(R.string.state_waiting), topPadding = 6.dp)
    Caption(text = stringResource(R.string.state_waiting_hint), topPadding = 2.dp)
}

@Composable
private fun LiveHeartRate(bpm: Int?) {
    HeartIcon(color = HeartRed)
    BigNumber(text = bpm?.toString() ?: stringResource(R.string.hr_placeholder))
    Unit_()
    Caption(text = stringResource(R.string.state_live), topPadding = 6.dp)
}

@Composable
private fun ErrorState(error: HeartError?, onRetry: () -> Unit) {
    val messageRes = when (error) {
        HeartError.CONNECTION -> R.string.err_connection
        HeartError.PERMISSION -> R.string.err_permission
        HeartError.SDK_POLICY -> R.string.err_sdk_policy
        HeartError.UNSUPPORTED -> R.string.err_unsupported
        HeartError.UNKNOWN, null -> R.string.err_unknown
    }
    Text(
        text = stringResource(messageRes),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.body2,
    )
    Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
        Text(stringResource(R.string.retry))
    }
}

@Composable
private fun HeartIcon(color: Color) {
    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(28.dp),
    )
}

@Composable
private fun BigNumber(text: String) {
    Text(text = text, style = MaterialTheme.typography.display1)
}

// Tên có gạch dưới để không đụng với kiểu Unit của Kotlin.
@Composable
private fun Unit_() {
    Text(text = stringResource(R.string.hr_unit), style = MaterialTheme.typography.caption1)
}

@Composable
private fun Caption(text: String, topPadding: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier.padding(top = topPadding),
    )
}
