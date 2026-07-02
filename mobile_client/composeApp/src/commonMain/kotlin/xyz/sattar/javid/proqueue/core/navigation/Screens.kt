package xyz.sattar.javid.proqueue.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.rounded.Event
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.home_menu_item
import proqueue.composeapp.generated.resources.last_visitors_menu_item
import proqueue.composeapp.generated.resources.settings_menu_item
import proqueue.composeapp.generated.resources.appointments_menu_item

object AppNavHost {
    @Serializable
    data object MessageNavHost

    @Serializable
    data object MainNavHost

    @Serializable
    data object BusinessNavHost
}

sealed class AppScreens {
    @Serializable
    object OnBoarding : AppScreens()
    @Serializable
    object Register : AppScreens()
    @Serializable
    object Login : AppScreens()
    @Serializable
    data class SendOTP(val phone: String) : AppScreens()
    @Serializable
    data class ResetPassword(val phone: String, val resetToken: String) : AppScreens()
    @Serializable
    object Home : AppScreens()
    @Serializable
    object Appointments : AppScreens()
    @Serializable
    object Settings : AppScreens()
    @Serializable
    object Notifications : AppScreens()

    @Serializable
    data class CreateAppointment(
        val visitorId: Long? = null, 
        val appointmentId: Long? = null,
        val date: Long? = null,
        val time: String? = null
    ) : AppScreens()
    @Serializable
    object Messages : AppScreens()
    @Serializable
    data class Calendar(val isPicker: Boolean = false) : AppScreens()
    @Serializable
    data class PaymentResult(
        val success: Int,
        val ref: String? = null,
        val amount: String? = null,
        val txn: String? = null
    ) : AppScreens()
    @Serializable
    object AboutUs : AppScreens()
    @Serializable
    object BusinessList : AppScreens()
    @Serializable
    data class BusinessDetail(val businessId: Long) : AppScreens()
}


sealed interface MainTab {
    val title: StringResource
    val iconSelected: ImageVector
    val iconUnSelected: ImageVector
    val route: AppScreens

    @Serializable
    data object Home : MainTab {
        override val title = Res.string.home_menu_item
        override val iconSelected = Icons.Rounded.Home
        override val iconUnSelected = Icons.Outlined.Home
        override val route = AppScreens.Home
    }

    @Serializable
    data object Appointments : MainTab {
        override val title = Res.string.appointments_menu_item
        override val iconSelected = Icons.Rounded.Event
        override val iconUnSelected = Icons.Outlined.Event
        override val route = AppScreens.Appointments
    }

    @Serializable
    data object Settings : MainTab {
        override val title = Res.string.settings_menu_item
        override val iconSelected = Icons.Rounded.Menu
        override val iconUnSelected = Icons.Outlined.Menu
        override val route = AppScreens.Settings
    }
}
