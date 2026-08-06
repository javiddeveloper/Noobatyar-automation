package xyz.sattar.javid.proqueue.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.People
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.home_menu_item
import proqueue.composeapp.generated.resources.last_visitors_menu_item
import proqueue.composeapp.generated.resources.settings_menu_item

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
    object Settings : AppScreens()
    @Serializable
    object Notifications : AppScreens()
    // A single shared tab destination — MainTab.LastVisitors.route. Reached
    // either via the bottom bar or via a Home stat card / queue row / chart
    // tap; the latter carries its filter through
    // [xyz.sattar.javid.proqueue.core.navigation.PendingVisitorsFilter]
    // rather than as route arguments, so every arrival lands on this exact
    // same tab instance (see PendingVisitorsFilter's doc for why).
    @Serializable
    object Visitors : AppScreens()
    @Serializable
    data class CreateBusiness(val businessId: Long? = null) : AppScreens()
    @Serializable
    data class AdvancedSettings(val businessId: Long) : AppScreens()
    @Serializable
    object BusinessList : AppScreens()
    @Serializable
    data class VisitorSelection(val returnResult: Boolean = false) : AppScreens()
    @Serializable
    object CreateVisitor : AppScreens()
    @Serializable
    data class EditVisitor(val visitorId: Long) : AppScreens()
    @Serializable
    data class VisitorDetails(val visitorId: Long, val openMessageDialog: Boolean = false) : AppScreens()
    @Serializable
    data class CreateAppointment(
        val visitorId: Long? = null, 
        val appointmentId: Long? = null,
        val date: Long? = null,
        val time: String? = null
    ) : AppScreens()
    @Serializable
    object Messages : AppScreens()
    /** Server-side SMS log for the selected business. */
    @Serializable
    object SmsReport : AppScreens()
    /** Public emergency notice banner shown on the booking page. */
    @Serializable
    object EmergencyNotice : AppScreens()
    @Serializable
    data class Calendar(
        val isPicker: Boolean = false,
        // When picking a new date/time for an appointment that already occupies
        // a slot, that appointment must not count as "occupied" against itself.
        val excludeAppointmentId: Long? = null
    ) : AppScreens()
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
    object AddOns : AppScreens()
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
    data object LastVisitors : MainTab {
        override val title = Res.string.last_visitors_menu_item
        override val iconSelected = Icons.Rounded.People
        override val iconUnSelected = Icons.Outlined.People
        override val route = AppScreens.Visitors
    }

    @Serializable
    data object Settings : MainTab {
        override val title = Res.string.settings_menu_item
        override val iconSelected = Icons.Rounded.Menu
        override val iconUnSelected = Icons.Outlined.Menu
        override val route = AppScreens.Settings
    }
}
