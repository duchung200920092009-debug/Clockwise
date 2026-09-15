package com.zenpulse.wear.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.zenpulse.wear.domain.BreathingPattern
import com.zenpulse.wear.presentation.breathing.BreathingScreen
import com.zenpulse.wear.presentation.breathing.BreathingViewModel
import com.zenpulse.wear.presentation.history.HistoryScreen
import com.zenpulse.wear.presentation.history.HistoryViewModel
import com.zenpulse.wear.presentation.home.HomeScreen
import com.zenpulse.wear.presentation.home.HomeViewModel
import com.zenpulse.wear.presentation.navigation.Destinations
import com.zenpulse.wear.presentation.onboarding.OnboardingScreen
import com.zenpulse.wear.presentation.settings.SettingsScreen
import com.zenpulse.wear.presentation.settings.SettingsViewModel
import com.zenpulse.wear.presentation.theme.ZenPulseTheme
import com.zenpulse.wear.service.MonitoringService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val openBreathing = intent?.getBooleanExtra(EXTRA_OPEN_BREATHING, false) ?: false
        val patternId = intent?.getStringExtra(EXTRA_PATTERN_ID)

        setContent {
            ZenPulseTheme {
                ZenPulseApp(
                    launchIntoBreathing = openBreathing,
                    launchPatternId = patternId,
                )
            }
        }
    }

    companion object {
        /** Set by the alert notification so tapping it lands directly on the exercise. */
        const val EXTRA_OPEN_BREATHING = "com.zenpulse.wear.extra.OPEN_BREATHING"
        const val EXTRA_PATTERN_ID = "com.zenpulse.wear.extra.PATTERN_ID"
    }
}

@Composable
private fun ZenPulseApp(
    launchIntoBreathing: Boolean,
    launchPatternId: String?,
) {
    val context = LocalContext.current
    val navController = rememberSwipeDismissableNavController()

    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)

    val monitorState by homeViewModel.monitorState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    var sensorsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        sensorsGranted = results[Manifest.permission.BODY_SENSORS] ?: sensorsGranted
    }

    // A notification tap should land on the exercise, not the home screen the user then has to
    // navigate from while already activated.
    LaunchedEffect(launchIntoBreathing) {
        if (launchIntoBreathing) {
            val pattern = launchPatternId ?: BreathingPattern.COHERENT.id
            navController.navigate(Destinations.breathing(pattern))
        }
    }

    if (!sensorsGranted) {
        PermissionRequest(
            onRequest = { permissionLauncher.launch(requiredPermissions()) },
        )
        return
    }

    Scaffold(timeText = { TimeText() }) {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = if (settings.onboardingComplete) {
                Destinations.HOME
            } else {
                Destinations.ONBOARDING
            },
        ) {
            composable(Destinations.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        settingsViewModel.markOnboardingComplete()
                        navController.navigate(Destinations.HOME) {
                            popUpTo(Destinations.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }

            composable(Destinations.HOME) {
                HomeScreen(
                    state = monitorState,
                    onToggleMonitoring = {
                        if (monitorState.running) {
                            MonitoringService.stop(context)
                        } else {
                            MonitoringService.start(context)
                        }
                    },
                    onBreathe = {
                        navController.navigate(
                            Destinations.breathing(settings.preferredPattern.id)
                        )
                    },
                    onHistory = { navController.navigate(Destinations.HISTORY) },
                    onSettings = { navController.navigate(Destinations.SETTINGS) },
                )
            }

            composable(Destinations.HISTORY) {
                val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
                val episodes by historyViewModel.episodes.collectAsStateWithLifecycle()
                HistoryScreen(
                    episodes = episodes,
                    onFeedback = historyViewModel::setFeedback,
                )
            }

            composable(Destinations.SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    onAlertsChanged = { settingsViewModel.setAlertsEnabled(it) },
                    onPassiveChanged = { settingsViewModel.setPassiveMonitoringEnabled(it) },
                    onLoggingChanged = { settingsViewModel.setSessionLoggingEnabled(it) },
                    onSensitivityChanged = { settingsViewModel.setSensitivity(it) },
                    onResetBaseline = { settingsViewModel.resetBaseline() },
                    onClearHistory = { settingsViewModel.clearHistory() },
                )
            }

            composable(
                route = Destinations.BREATHING,
                arguments = listOf(
                    navArgument(Destinations.ARG_PATTERN_ID) { type = NavType.StringType }
                ),
            ) { backStackEntry ->
                val breathingViewModel: BreathingViewModel =
                    viewModel(factory = BreathingViewModel.Factory)
                val patternId = backStackEntry.arguments
                    ?.getString(Destinations.ARG_PATTERN_ID)
                    ?: BreathingPattern.COHERENT.id

                BreathingScreen(
                    pattern = BreathingPattern.byId(patternId),
                    onPhaseChange = breathingViewModel::onPhaseChange,
                    onCompleted = breathingViewModel::onCompleted,
                    onExit = { navController.popBackStack() },
                )
            }
        }
    }
}

/** BODY_SENSORS is essential; notifications are how an alert actually reaches the user. */
private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.BODY_SENSORS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "ZenPulse reads your heart rate to notice when your body is activated.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.caption1,
        )
        Button(onClick = onRequest, modifier = Modifier.padding(top = 12.dp)) {
            Text("Allow")
        }
    }
}
