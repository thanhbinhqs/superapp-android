package com.superapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.superapp.ui.screens.*

object Routes {
    const val HOME = "home"
    const val TOOLS_GALLERY = "tools_gallery"
    const val CALCULATOR = "calculator"
    const val CONVERTER = "converter"
    const val QR = "qr"
    const val STOPWATCH = "stopwatch"
    const val FLASHLIGHT = "flashlight"
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(navController = navController)
        }
        composable(Routes.TOOLS_GALLERY) {
            ToolsGalleryScreen(navController = navController)
        }
        composable(Routes.CALCULATOR) {
            CalculatorScreen(navController = navController)
        }
        composable(Routes.CONVERTER) {
            UnitConverterScreen(navController = navController)
        }
        composable(Routes.QR) {
            QRGeneratorScreen(navController = navController)
        }
        composable(Routes.STOPWATCH) {
            StopwatchScreen(navController = navController)
        }
        composable(Routes.FLASHLIGHT) {
            FlashlightScreen(navController = navController)
        }
    }
}
