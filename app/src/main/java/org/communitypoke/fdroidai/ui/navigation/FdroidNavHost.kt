package org.communitypoke.fdroidai.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.communitypoke.fdroidai.di.AppContainer
import org.communitypoke.fdroidai.ui.categories.CategoriesScreen
import org.communitypoke.fdroidai.ui.detail.AppDetailScreen
import org.communitypoke.fdroidai.ui.home.HomeScreen
import org.communitypoke.fdroidai.ui.repo.RepoInfoScreen
import org.communitypoke.fdroidai.ui.search.SearchScreen
import org.communitypoke.fdroidai.ui.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val BROWSE = "browse"
    const val CATEGORIES = "categories"
    const val SEARCH = "search"
    const val REPO = "repo"
    const val DETAIL = "app/{packageName}"

    fun detail(packageName: String) = "app/$packageName"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.BROWSE, "Browse", Icons.Filled.Apps),
    TopLevelDestination(Routes.CATEGORIES, "Categories", Icons.Filled.Category),
    TopLevelDestination(Routes.SEARCH, "Search", Icons.Filled.Search),
    TopLevelDestination(Routes.REPO, "Repo", Icons.Filled.Info),
)

@Composable
fun FdroidNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in topLevelDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    onFinished = {
                        navController.navigate(Routes.BROWSE) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.BROWSE) {
                HomeScreen(
                    container = container,
                    onAppClick = { navController.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(
                    container = container,
                    onCategoryClick = { category ->
                        container.browseCategoryFilter.value = category
                        navController.navigate(Routes.BROWSE) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    container = container,
                    onAppClick = { navController.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.REPO) {
                RepoInfoScreen(container = container)
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
            ) { entry ->
                AppDetailScreen(
                    container = container,
                    packageName = entry.arguments?.getString("packageName").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
