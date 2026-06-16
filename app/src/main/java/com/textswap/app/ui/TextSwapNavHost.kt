package com.textswap.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.textswap.app.ui.screens.BatchReplaceScreen
import com.textswap.app.ui.screens.HomeScreen
import com.textswap.app.ui.screens.PreviewScreen
import com.textswap.app.ui.screens.ProgressScreen
import com.textswap.app.viewmodel.MainViewModel

object Routes {
    const val HOME = "home"
    const val BATCH_REPLACE = "batch_replace"
    const val PREVIEW = "preview"
    const val PROGRESS = "progress"
}

@Composable
fun TextSwapNavHost() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onStartReplace = { navController.navigate(Routes.BATCH_REPLACE) }
            )
        }

        composable(Routes.BATCH_REPLACE) {
            BatchReplaceScreen(
                viewModel = viewModel,
                onPreview = { navController.navigate(Routes.PREVIEW) },
                onStartProcessing = { navController.navigate(Routes.PROGRESS) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PREVIEW) {
            PreviewScreen(
                viewModel = viewModel,
                onConfirm = {
                    navController.popBackStack()
                    navController.navigate(Routes.PROGRESS)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PROGRESS) {
            ProgressScreen(
                viewModel = viewModel,
                onDone = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }
    }
}