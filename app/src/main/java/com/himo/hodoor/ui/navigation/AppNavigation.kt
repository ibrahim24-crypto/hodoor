package com.himo.hodoor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.himo.hodoor.HodoorApp
import com.himo.hodoor.ui.HomeViewModelFactory
import com.himo.hodoor.ui.ImportViewModelFactory
import com.himo.hodoor.ui.ScannerViewModelFactory
import com.himo.hodoor.ui.SearchViewModelFactory
import com.himo.hodoor.ui.SettingsViewModelFactory
import com.himo.hodoor.ui.StudentViewModelFactory
import com.himo.hodoor.ui.screens.HomeScreen
import com.himo.hodoor.ui.screens.HomeViewModel
import com.himo.hodoor.ui.screens.ImportScreen
import com.himo.hodoor.ui.screens.ImportViewModel
import com.himo.hodoor.ui.screens.ScannerScreen
import com.himo.hodoor.ui.screens.ScannerViewModel
import com.himo.hodoor.ui.screens.SearchScreen
import com.himo.hodoor.ui.screens.SearchViewModel
import com.himo.hodoor.ui.screens.SettingsScreen
import com.himo.hodoor.ui.screens.SplashScreen
import com.himo.hodoor.ui.screens.SettingsViewModel
import com.himo.hodoor.ui.screens.StudentScreen
import com.himo.hodoor.ui.screens.StudentViewModel

object Route {
    const val ArgStudentCode = "studentCode"
    const val Splash = "splash"
    const val Home = "home"
    const val Import = "import"
    const val Search = "search"
    const val Scan = "scan"
    const val Settings = "settings"
    const val StudentRoute = "student/{$ArgStudentCode}"

    fun student(code: String) = "student/$code"
}

@Composable
fun HodoorNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Route.Splash
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Route.Splash) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Route.Home) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                }
            )
        }
        composable(Route.Home) {
            val app = LocalContext.current.applicationContext as HodoorApp
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModelFactory(app.container.repository)
            )
            val studentCount by homeViewModel.studentCount.collectAsStateWithLifecycle()
            HomeScreen(
                onScan = { navController.navigate(Route.Scan) },
                onSearch = { navController.navigate(Route.Search) },
                onImport = { navController.navigate(Route.Import) },
                onSettings = { navController.navigate(Route.Settings) },
                studentCount = studentCount
            )
        }
        composable(Route.Import) {
            val context = LocalContext.current
            val app = context.applicationContext as HodoorApp
            val importViewModel: ImportViewModel = viewModel(
                factory = ImportViewModelFactory(app.container.repository, context.applicationContext)
            )
            ImportScreen(
                viewModel = importViewModel,
                onBack = { navController.navigateUp() }
            )
        }
        composable(Route.Search) {
            val app = LocalContext.current.applicationContext as HodoorApp
            val searchViewModel: SearchViewModel = viewModel(
                factory = SearchViewModelFactory(app.container.repository)
            )
            SearchScreen(
                viewModel = searchViewModel,
                onStudentClick = { code -> navController.navigate(Route.student(code)) },
                onBack = { navController.navigateUp() }
            )
        }
        composable(Route.Scan) {
            val app = LocalContext.current.applicationContext as HodoorApp
            val scannerViewModel: ScannerViewModel = viewModel(
                factory = ScannerViewModelFactory(app.container.repository)
            )
            ScannerScreen(
                viewModel = scannerViewModel,
                onStudentFound = { code ->
                    navController.navigate(Route.student(code)) {
                        popUpTo(Route.Scan) { inclusive = true }
                    }
                },
                onBack = { navController.navigateUp() }
            )
        }
        composable(Route.Settings) {
            val app = LocalContext.current.applicationContext as HodoorApp
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(app.container.repository)
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.navigateUp() }
            )
        }
        composable(
            route = Route.StudentRoute,
            arguments = listOf(navArgument(Route.ArgStudentCode) { type = NavType.StringType })
        ) { backStackEntry ->
            val app = LocalContext.current.applicationContext as HodoorApp
            val studentViewModel: StudentViewModel = viewModel(
                factory = StudentViewModelFactory(app.container.repository, backStackEntry.savedStateHandle)
            )
            StudentScreen(
                viewModel = studentViewModel,
                onBack = { navController.navigateUp() },
                onAddRecord = { studentViewModel.startAdd() }
            )
        }
    }
}
