package com.example.bakerydaily

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.*
import com.example.bakerydaily.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(applicationContext)
        setContent { BakeryApp() }
    }
}

@Composable
fun BakeryApp() {
    val nav = rememberNavController()
    var dark by remember { mutableStateOf(SettingsStore.isNightMode()) }
    BakeryTheme(dark = dark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            NavHost(navController = nav, startDestination = "select") {
                composable("select") { BakerySelection(nav) }
                composable("home/{id}") { e -> Home(nav, requireNotNull(e.arguments?.getString("id")).toLong()) }
                composable("stats/{id}") { e -> Stats(nav, requireNotNull(e.arguments?.getString("id")).toLong()) }
                composable("settings") { Settings(nav, dark) { dark = it; SettingsStore.setNightMode(it) } }
            }
        }
    }
}
