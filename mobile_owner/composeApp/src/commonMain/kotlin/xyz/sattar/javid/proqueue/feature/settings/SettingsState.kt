package xyz.sattar.javid.proqueue.feature.settings

import androidx.compose.runtime.Immutable

import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.utils.AppInfo
import xyz.sattar.javid.proqueue.domain.model.business.Business

@Immutable
data class SettingsState(
    val isLoading: Boolean = false,
    val businessName: String? = null,
    val currentBusiness: Business? = null,
    val appVersion: String = AppInfo.versionName,
    val message: UiMessage? = null
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class ShowMessage(val message: UiMessage) : PartialState()
        data class LoadSettings(val business: Business?) : PartialState()
        data object ClearMessage : PartialState()
    }
}
