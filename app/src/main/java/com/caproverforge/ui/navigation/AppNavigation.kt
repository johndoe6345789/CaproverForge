package com.caproverforge.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.caproverforge.AppContainer
import com.caproverforge.ui.appdetail.AppDetailScreen
import com.caproverforge.ui.components.LocalSnackbar
import com.caproverforge.ui.home.HomeScreen
import com.caproverforge.ui.login.LoginScreen
import com.caproverforge.ui.oneclick.OneClickDeployScreen
import com.caproverforge.ui.oneclick.OneClickReposScreen
import com.caproverforge.ui.server.AppearanceScreen
import com.caproverforge.ui.server.DiskCleanupScreen
import com.caproverforge.ui.server.DomainScreen
import com.caproverforge.ui.server.MonitoringScreen
import com.caproverforge.ui.server.NginxScreen
import com.caproverforge.ui.server.NodesScreen
import com.caproverforge.ui.server.PasswordScreen
import com.caproverforge.ui.server.ProjectsScreen
import com.caproverforge.ui.server.RegistriesScreen
import com.caproverforge.ui.server.ServerStatsScreen
import com.caproverforge.ui.server.UpdateScreen
import kotlinx.serialization.Serializable

@Serializable object LoginRoute
@Serializable object HomeRoute
@Serializable data class AppDetailRoute(val appName: String)
@Serializable data class OneClickDeployRoute(val name: String, val baseUrl: String, val displayName: String)
@Serializable object OneClickReposRoute
@Serializable object NodesRoute
@Serializable object RegistriesRoute
@Serializable object DiskCleanupRoute
@Serializable object DomainRoute
@Serializable object NginxRoute
@Serializable object MonitoringRoute
@Serializable object ProjectsRoute
@Serializable object PasswordRoute
@Serializable object UpdateRoute
@Serializable object AppearanceRoute
@Serializable object ServerStatsRoute

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** `viewModel { }` with access to the app's dependencies. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContainer.current
    return viewModel(key = key) { create(container) }
}

/** Navigation actions available to the tab screens. */
class Navigator(private val nav: NavHostController, private val container: AppContainer) {
    fun openApp(appName: String) = nav.navigate(AppDetailRoute(appName))
    fun openOneClick(name: String, baseUrl: String, displayName: String) =
        nav.navigate(OneClickDeployRoute(name, baseUrl, displayName))
    fun open(route: Any) = nav.navigate(route)
    fun back() = nav.popBackStack()
    fun signOut() {
        container.repository.logout()
        nav.navigate(LoginRoute) { popUpTo(0) { inclusive = true } }
    }
}

@Composable
fun AppRoot(container: AppContainer) {
    val snackbar = remember { SnackbarHostState() }
    val nav = rememberNavController()
    val navigator = remember(nav) { Navigator(nav, container) }
    val start: Any = remember { if (container.sessionStore.session.value != null) HomeRoute else LoginRoute }

    LaunchedEffect(Unit) {
        container.api.sessionExpired.collect {
            if (nav.currentDestination?.route?.contains("LoginRoute") != true) {
                nav.navigate(LoginRoute) { popUpTo(0) { inclusive = true } }
                snackbar.showSnackbar("Your session expired. Please sign in again.")
            }
        }
    }

    CompositionLocalProvider(LocalContainer provides container, LocalSnackbar provides snackbar) {
        NavHost(
            navController = nav,
            startDestination = start,
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) + fadeIn(tween(300))
            },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(250)) },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) + fadeOut(tween(300))
            },
        ) {
            composable<LoginRoute> {
                LoginScreen(onSignedIn = {
                    nav.navigate(HomeRoute) { popUpTo(0) { inclusive = true } }
                })
            }
            composable<HomeRoute> { HomeScreen(navigator) }
            composable<AppDetailRoute> { entry ->
                AppDetailScreen(entry.toRoute<AppDetailRoute>().appName, onBack = navigator::back)
            }
            composable<OneClickDeployRoute> { entry ->
                val route = entry.toRoute<OneClickDeployRoute>()
                OneClickDeployScreen(
                    name = route.name,
                    baseUrl = route.baseUrl,
                    displayName = route.displayName,
                    onBack = navigator::back,
                    onOpenApp = { app ->
                        nav.navigate(AppDetailRoute(app)) { popUpTo(HomeRoute) }
                    },
                )
            }
            composable<OneClickReposRoute> { OneClickReposScreen(navigator::back) }
            composable<NodesRoute> { NodesScreen(navigator::back) }
            composable<RegistriesRoute> { RegistriesScreen(navigator::back) }
            composable<DiskCleanupRoute> { DiskCleanupScreen(navigator::back) }
            composable<DomainRoute> { DomainScreen(navigator::back) }
            composable<NginxRoute> { NginxScreen(navigator::back) }
            composable<MonitoringRoute> { MonitoringScreen(navigator::back, onOpenStats = { navigator.open(ServerStatsRoute) }) }
            composable<ProjectsRoute> { ProjectsScreen(navigator::back) }
            composable<PasswordRoute> { PasswordScreen(navigator::back) }
            composable<UpdateRoute> { UpdateScreen(navigator::back) }
            composable<AppearanceRoute> { AppearanceScreen(navigator::back) }
            composable<ServerStatsRoute> {
                ServerStatsScreen(
                    onBack = navigator::back,
                    onOpenMonitoring = { navigator.open(MonitoringRoute) },
                    onSignInAgain = navigator::signOut,
                )
            }
        }
    }
}
