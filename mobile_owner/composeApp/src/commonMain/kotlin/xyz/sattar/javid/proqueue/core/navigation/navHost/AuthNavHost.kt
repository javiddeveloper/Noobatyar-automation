package xyz.sattar.javid.proqueue.core.navigation.navHost

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import xyz.sattar.javid.proqueue.core.navigation.AppScreens
import xyz.sattar.javid.proqueue.core.ui.components.AppScaffold
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import xyz.sattar.javid.proqueue.feature.forgetPassword.resetPassword.ResetPasswordScreen
import xyz.sattar.javid.proqueue.feature.forgetPassword.sendOTP.SendOTPScreen
import xyz.sattar.javid.proqueue.feature.login.LoginScreen
import xyz.sattar.javid.proqueue.feature.register.RegisterScreen

@Composable
fun AuthNavHost(
    onNavigateToHome: () -> Unit,
    onRegisterComplete: () -> Unit
) {
    val navController = rememberNavController()
    // No width cap at this level. LoginScreen builds its own full-viewport
    // two-column layout on web, and a cap here would squeeze that split into a
    // 420dp strip instead. The three screens that are still plain phone forms
    // are wrapped individually below.
    NavHost(
        navController = navController,
        startDestination = AppScreens.Login
    ) {
        composable<AppScreens.Register> {
            AppScaffold(maxWidth = ContentWidth.Form) {
            RegisterScreen(
                onRegisterComplete = onRegisterComplete,
                onBackPress = {
                    navController.popBackStack(
                        route = AppScreens.Login,
                        inclusive = false
                    )
                },
            )
            }
        }
        composable<AppScreens.Login> {
            LoginScreen(
                onNavigateToHome = onNavigateToHome,
                onNavigateToForgetPassword = { phone ->
                    navController.navigate(AppScreens.SendOTP(phone))
                },
                onNavigateToRegister = {
                    navController.navigate(AppScreens.Register)
                },
            )
        }
        composable<AppScreens.SendOTP> {
            val args = it.toRoute<AppScreens.SendOTP>()
            AppScaffold(maxWidth = ContentWidth.Form) {
            SendOTPScreen(
                phone = args.phone,
                onNavigateToResetPassword = { phone, resetToken ->
                    navController.navigate(AppScreens.ResetPassword(phone, resetToken))
                },
                onNavigateToLogin = {
                    navController.popBackStack(
                        route = AppScreens.Login,
                        inclusive = false
                    )
                }
            )
            }
        }

        composable<AppScreens.ResetPassword> {
            val args = it.toRoute<AppScreens.ResetPassword>()
            AppScaffold(maxWidth = ContentWidth.Form) {
            ResetPasswordScreen(phone = args.phone, resetToken = args.resetToken) {
                navController.popBackStack(
                    route = AppScreens.Login,
                    inclusive = false
                )
            }
            }
        }
    }
}
