package com.zenpulse.wear.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.zenpulse.wear.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ZenPulseApp()
            }
        }
    }
}

@Composable
private fun ZenPulseApp(viewModel: HeartRateViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var granted by remember { mutableStateOf(context.hasBodySensorsPermission()) }
    var askedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        askedOnce = true
    }

    // Chỉ thu nhịp tim khi đã có quyền; mất quyền thì dừng ngay (đỡ tốn pin).
    LaunchedEffect(granted) {
        if (granted) viewModel.start() else viewModel.stop()
    }

    if (granted) {
        HeartRateScreen(
            state = state,
            onRetry = viewModel::restart,
        )
    } else {
        PermissionGate(
            deniedBefore = askedOnce,
            onGrant = { permissionLauncher.launch(Manifest.permission.BODY_SENSORS) },
            onOpenSettings = { context.openAppSettings() },
        )
    }
}

@Composable
private fun PermissionGate(
    deniedBefore: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.perm_rationale),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.body2,
        )
        Button(onClick = onGrant, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.perm_grant))
        }
        // Người dùng đã từ chối ít nhất một lần → có thể họ đã chọn "không hỏi lại".
        // Mở thẳng Cài đặt để họ tự bật quyền.
        if (deniedBefore) {
            Text(
                text = stringResource(R.string.perm_denied_hint),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 6.dp)) {
                Text(stringResource(R.string.perm_open_settings))
            }
        }
    }
}

private fun Context.hasBodySensorsPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
