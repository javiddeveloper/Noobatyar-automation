package xyz.sattar.javid.proqueue.core.navigation.navHost

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import xyz.sattar.javid.proqueue.core.navigation.AppScreens
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.businessList.BusinessListScreen
import xyz.sattar.javid.proqueue.feature.createBusiness.CreateBusinessRoute

import androidx.navigation.toRoute

@Composable
fun BusinessNavHost(
    onBusinessSelected: (Business) -> Unit,
    onNavigateToAuth: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppScreens.BusinessList
    ) {
        composable<AppScreens.BusinessList> {
            BusinessListScreen(
                onNavigateToMain = { business ->
                    onBusinessSelected(business)
                },
                onNavigateToCreateBusiness = {
                    navController.navigate(AppScreens.CreateBusiness())
                },
                onNavigateToEditBusiness = { businessId ->
                    navController.navigate(AppScreens.CreateBusiness(businessId = businessId))
                },
                onNavigateToLogin = {
                    onNavigateToAuth()
                }
            )
        }

        composable<AppScreens.CreateBusiness> { backStackEntry ->
            val args = backStackEntry.toRoute<AppScreens.CreateBusiness>()
            CreateBusinessRoute(
                businessId = args.businessId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onContinue = {
                    navController.popBackStack()
                }
            )
        }
    }
}
