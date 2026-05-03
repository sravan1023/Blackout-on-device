package com.example.blackout.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.blackout.ui.RedactionUiState
import com.example.blackout.ui.RedactionViewModel
import com.example.blackout.ui.screens.CategoryScreen
import com.example.blackout.ui.screens.DocumentDetailScreen
import com.example.blackout.ui.screens.HomeScreen
import com.example.blackout.ui.screens.ManageCategoriesScreen
import com.example.blackout.ui.screens.ModelSetupScreen
import com.example.blackout.ui.screens.ResultScreen
import com.example.blackout.ui.screens.ScannerScreen
import com.example.blackout.ui.screens.TextInputScreen
import com.example.blackout.ui.screens.TextSnippetsScreen
import com.example.blackout.ui.viewmodel.DocumentViewModel

object Routes {
    const val HOME = "home"
    const val TEXT_INPUT = "text_input"
    const val CAMERA = "camera"
    const val RESULT = "result"
    const val SETUP = "setup"
    const val MANAGE_CATEGORIES = "manage_categories"
    const val TEXT_SNIPPETS = "text_snippets"

    const val CATEGORY = "category/{categoryName}"
    fun category(name: String) = "category/$name"

    const val DOCUMENT_DETAIL = "document_detail/{documentId}"
    fun documentDetail(id: String) = "document_detail/$id"

}

@Composable
fun BlackoutNavGraph(
    redactionViewModel: RedactionViewModel,
    docViewModel: DocumentViewModel,
) {
    val navController = rememberNavController()
    val uiState by redactionViewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is RedactionUiState.ModelMissing) {
            navController.navigate(Routes.SETUP) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = "${Routes.HOME}?tab=0") {
        composable(Routes.SETUP) {
            ModelSetupScreen(viewModel = redactionViewModel)
        }
        composable(
            "${Routes.HOME}?tab={tab}",
            arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
        ) { backStack ->
            HomeScreen(
                docViewModel = docViewModel,
                redactionViewModel = redactionViewModel,
                navController = navController,
                initialTab = backStack.arguments?.getInt("tab") ?: 0,
            )
        }
        composable(Routes.TEXT_INPUT) {
            TextInputScreen(
                redactionViewModel = redactionViewModel,
                navController = navController,
            )
        }
        composable(Routes.CAMERA) {
            ScannerScreen(viewModel = redactionViewModel, navController = navController)
        }
        composable(Routes.RESULT) {
            ResultScreen(
                viewModel = redactionViewModel,
                docViewModel = docViewModel,
                navController = navController,
            )
        }
        composable(Routes.MANAGE_CATEGORIES) {
            ManageCategoriesScreen(docViewModel = docViewModel, navController = navController)
        }
        composable(Routes.TEXT_SNIPPETS) {
            TextSnippetsScreen(docViewModel = docViewModel, navController = navController)
        }
        composable(
            Routes.CATEGORY,
            arguments = listOf(navArgument("categoryName") { type = NavType.StringType }),
        ) { backStack ->
            CategoryScreen(
                categoryName = backStack.arguments?.getString("categoryName") ?: "",
                docViewModel = docViewModel,
                navController = navController,
            )
        }
        composable(
            Routes.DOCUMENT_DETAIL,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) { backStack ->
            DocumentDetailScreen(
                documentId = backStack.arguments?.getString("documentId") ?: "",
                docViewModel = docViewModel,
                navController = navController,
            )
        }
    }
}
