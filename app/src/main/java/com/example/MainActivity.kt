package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core MainViewModel initialization
        val viewModel = ViewModelProvider(
            this,
            MainViewModelFactory(application)
        )[MainViewModel::class.java]

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
            val isGuestMode by viewModel.isGuestMode.collectAsState()

            MyApplicationTheme(darkTheme = isDarkMode) {
                var activeTab by remember { mutableIntStateOf(0) }

                // Collect remote database edit notifications reactively
                LaunchedEffect(key1 = true) {
                    viewModel.syncAlerts.collect { alert ->
                        Toast.makeText(this@MainActivity, alert, Toast.LENGTH_LONG).show()
                    }
                }

                if (!isUserLoggedIn && !isGuestMode) {
                    AuthScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            NavigationBar(
                                modifier = Modifier.testTag("main_navigation_bar"),
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp,
                                windowInsets = WindowInsets.navigationBars
                            ) {
                                val tabs = listOf(
                                    NavigationTabItem("Lista", Icons.Default.List, 0, "tab_list"),
                                    NavigationTabItem("Comprar", Icons.Default.ShoppingCart, 1, "tab_shopping_mode"),
                                    NavigationTabItem("Catálogo", Icons.Default.Star, 2, "tab_predefined"),
                                    NavigationTabItem("Historial", Icons.Default.Refresh, 3, "tab_history"),
                                    NavigationTabItem("Ajustes", Icons.Default.Settings, 4, "tab_settings")
                                )

                                tabs.forEach { tab ->
                                    val isSelected = activeTab == tab.index
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { activeTab = tab.index },
                                        icon = {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        alwaysShowLabel = true,
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        // Beautiful fade animation when switching screens
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            AnimatedContent(
                                targetState = activeTab,
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                },
                                label = "screen_transition"
                            ) { targetTab ->
                                when (targetTab) {
                                    0 -> ShoppingListScreen(viewModel, innerPadding)
                                    1 -> ShoppingModeScreen(viewModel, innerPadding)
                                    2 -> PredefinedItemsScreen(viewModel, innerPadding)
                                    3 -> HistoryScreen(viewModel, innerPadding)
                                    4 -> SettingsScreen(viewModel, innerPadding)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class NavigationTabItem(
    val label: String,
    val icon: ImageVector,
    val index: Int,
    val testTag: String
)
