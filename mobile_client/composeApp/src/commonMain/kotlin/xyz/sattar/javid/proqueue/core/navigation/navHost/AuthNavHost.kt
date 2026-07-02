package xyz.sattar.javid.proqueue.core.navigation.navHost

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import xyz.sattar.javid.proqueue.core.navigation.AppScreens
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
    NavHost(
        navController = navController,
        startDestination = AppScreens.Login
    ) {
        composable<AppScreens.Register> {
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

        composable<AppScreens.ResetPassword> {
            val args = it.toRoute<AppScreens.ResetPassword>()
            ResetPasswordScreen(phone = args.phone, resetToken = args.resetToken) {
                navController.popBackStack(
                    route = AppScreens.Login,
                    inclusive = false
                )
            }
        }
    }
}
