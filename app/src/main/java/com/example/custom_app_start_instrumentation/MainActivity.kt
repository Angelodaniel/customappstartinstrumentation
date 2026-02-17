package com.example.custom_app_start_instrumentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.example.custom_app_start_instrumentation.ui.theme.CustomappstartinstrumentationTheme
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CustomappstartinstrumentationTheme {
                CustomappstartinstrumentationApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun CustomappstartinstrumentationApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    // Note: App start tracking is handled automatically by AppStartTracker
    // It will finish when the first frame is rendered via Choreographer

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            SentryDataGenerator(
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountBox),
}

@Composable
fun SentryDataGenerator(modifier: Modifier = Modifier) {
    var generatedCount by rememberSaveable { mutableIntStateOf(0) }
    var isGenerating by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Sentry Custom App Start Demo",
            style = MaterialTheme.typography.headlineMedium
        )

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "App Start Transactions Generated: $generatedCount",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Check your Sentry dashboard to see the data!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = {
                scope.launch {
                    isGenerating = true
                    withContext(Dispatchers.IO) {
                        AppStartTransactionHolder.simulateAppStart(isColdStart = true)
                    }
                    generatedCount++
                    isGenerating = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isGenerating
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(20.dp)
                        .padding(end = 8.dp)
                )
            }
            Text("Simulate Cold Start")
        }

        Button(
            onClick = {
                scope.launch {
                    isGenerating = true
                    withContext(Dispatchers.IO) {
                        AppStartTransactionHolder.simulateAppStart(isColdStart = false)
                    }
                    generatedCount++
                    isGenerating = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isGenerating
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(20.dp)
                        .padding(end = 8.dp)
                )
            }
            Text("Simulate Warm Start")
        }

        Button(
            onClick = {
                scope.launch {
                    isGenerating = true
                    withContext(Dispatchers.IO) {
                        repeat(5) {
                            AppStartTransactionHolder.simulateAppStart()
                            if (it < 4) kotlinx.coroutines.delay(500)
                        }
                    }
                    generatedCount += 5
                    isGenerating = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isGenerating
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(20.dp)
                        .padding(end = 8.dp)
                )
            }
            Text("Generate 5 Random App Starts")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "About Custom App Start",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "• Uses TransactionOptions with null timeouts\n" +
                           "• Reduces timer threads during app start\n" +
                           "• Manual control over app start measurement\n" +
                           "• Includes realistic initialization spans",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SentryDataGeneratorPreview() {
    CustomappstartinstrumentationTheme {
        SentryDataGenerator()
    }
}