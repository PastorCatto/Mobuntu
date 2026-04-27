package org.mobuntu.chroot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.mobuntu.chroot.ui.CommandsScreen
import org.mobuntu.chroot.ui.HomeScreen
import org.mobuntu.chroot.ui.LogScreen
import org.mobuntu.chroot.ui.SettingsScreen
import org.mobuntu.chroot.ui.theme.MobuutuChrootTheme

sealed class Screen(val route: String, val label: String) {
    object Home     : Screen("home",     "Home")
    object Settings : Screen("settings", "Settings")
    object Commands : Screen("commands", "Commands")
    object Log      : Screen("log",      "Log")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MobuutuChrootTheme {
                val vm: MainViewModel = viewModel()
                val navController = rememberNavController()

                val items = listOf(Screen.Home, Screen.Settings, Screen.Commands, Screen.Log)

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDest = navBackStackEntry?.destination
                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            when (screen) {
                                                Screen.Home     -> Icons.Default.Home
                                                Screen.Settings -> Icons.Default.Settings
                                                Screen.Commands -> Icons.Default.List
                                                Screen.Log      -> Icons.Default.Terminal
                                            },
                                            contentDescription = screen.label,
                                        )
                                    },
                                    label   = { Text(screen.label) },
                                    selected = currentDest?.hierarchy?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState    = true
                                        }
                                    },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    NavHost(
                        navController    = navController,
                        startDestination = Screen.Home.route,
                        modifier         = Modifier.padding(innerPadding),
                    ) {
                        composable(Screen.Home.route)     { HomeScreen(vm) }
                        composable(Screen.Settings.route) { SettingsScreen(vm) }
                        composable(Screen.Commands.route) { CommandsScreen(vm) }
                        composable(Screen.Log.route)      { LogScreen(vm) }
                    }
                }
            }
        }
    }
}
