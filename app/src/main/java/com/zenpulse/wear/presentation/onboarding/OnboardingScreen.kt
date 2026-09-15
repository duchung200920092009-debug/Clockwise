package com.zenpulse.wear.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.zenpulse.wear.presentation.theme.StressColors

private data class OnboardingPage(
    val title: String,
    val body: String,
)

/**
 * First-run explanation.
 *
 * These four screens exist to set expectations that the rest of the app then has to keep. Someone
 * installing this may be hoping it will tell them when a panic attack is coming — it cannot, and
 * letting them believe otherwise would be both dishonest and, if they relied on it, harmful. So
 * the limits are stated up front, before any permission is requested.
 */
private val pages = listOf(
    OnboardingPage(
        title = "ZenPulse",
        body = "Your watch notices when your body looks activated, and offers a minute of breathing.",
    ),
    OnboardingPage(
        title = "Not a doctor",
        body = "This is a wellbeing tool, not a medical device. It can't diagnose anything or predict a panic attack.",
    ),
    OnboardingPage(
        title = "It learns you",
        body = "For the first ~10 minutes of calm wear it just watches, learning what your normal looks like.",
    ),
    OnboardingPage(
        title = "Stays on your watch",
        body = "Your data stays on your watch and your paired phone. Nothing is uploaded anywhere.",
    ),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var pageIndex by remember { mutableStateOf(0) }
    val page = pages[pageIndex]
    val isLast = pageIndex == pages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = page.title,
            style = MaterialTheme.typography.title3,
            color = StressColors.Calm,
            textAlign = TextAlign.Center,
        )
        Text(
            text = page.body,
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "${pageIndex + 1} / ${pages.size}",
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Button(
            onClick = { if (isLast) onFinished() else pageIndex++ },
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Text(if (isLast) "Start" else "Next")
        }
    }
}
