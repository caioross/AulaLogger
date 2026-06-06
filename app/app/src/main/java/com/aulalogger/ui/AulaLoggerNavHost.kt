package com.aulalogger.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aulalogger.ui.anim.Motion
import com.aulalogger.ui.screens.AboutScreen
import com.aulalogger.ui.screens.AnalysisScreen
import com.aulalogger.ui.screens.ApiKeysScreen
import com.aulalogger.ui.screens.HomeScreen
import com.aulalogger.ui.screens.ModelsScreen
import com.aulalogger.ui.screens.RecordingScreen
import com.aulalogger.ui.screens.SessionDetailScreen
import com.aulalogger.ui.screens.SettingsScreen

@Composable
fun AulaLoggerNavHost(
    hasMicPermission: Boolean,
    requestMicPermission: () -> Unit,
    requestBatteryOptimizationExemption: () -> Unit
) {
    val navController = rememberNavController()
    val tween = tween<androidx.compose.ui.unit.IntOffset>(Motion.DurationMedium, easing = Motion.EmphasizedEasing)
    val fadeTween = tween<Float>(Motion.DurationMedium, easing = Motion.StandardEasing)

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = {
            slideInHorizontally(tween) { it / 6 } + fadeIn(fadeTween)
        },
        exitTransition = {
            fadeOut(fadeTween)
        },
        popEnterTransition = {
            fadeIn(fadeTween)
        },
        popExitTransition = {
            slideOutHorizontally(tween) { it / 6 } + fadeOut(fadeTween)
        }
    ) {
        composable("home") {
            HomeScreen(
                hasMicPermission = hasMicPermission,
                requestMicPermission = requestMicPermission,
                onStartRecording = {
                    requestBatteryOptimizationExemption()
                    navController.navigate("recording")
                },
                onOpenSession = { id -> navController.navigate("session/$id") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }

        composable("recording") {
            RecordingScreen(
                onFinished = { sessionId ->
                    navController.navigate("session/$sessionId") { popUpTo("home") }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            "session/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("id") ?: return@composable
            SessionDetailScreen(
                sessionId = id,
                onBack = { navController.popBackStack() },
                onOpenAnalysis = { navController.navigate("analysis/$it") }
            )
        }

        composable(
            "analysis/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("id") ?: return@composable
            AnalysisScreen(sessionId = id, onBack = { navController.popBackStack() })
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenModels = { navController.navigate("models") },
                onOpenApiKeys = { navController.navigate("apikeys") },
                onOpenAbout = { navController.navigate("about") },
                requestMicPermission = requestMicPermission
            )
        }

        composable("models") {
            ModelsScreen(onBack = { navController.popBackStack() })
        }

        composable("apikeys") {
            ApiKeysScreen(onBack = { navController.popBackStack() })
        }

        composable("about") {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}

