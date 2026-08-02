package xyz.sattar.javid.proqueue.feature.settings

import androidx.compose.runtime.Immutable

import xyz.sattar.javid.proqueue.core.utils.AppInfo
import xyz.sattar.javid.proqueue.domain.model.business.Business

@Immutable
data class SettingsState(
    val isLoading: Boolean = false,
    val businessName: String? = null,
    val currentBusiness: Business? = null,
    val appVersion: String = AppInfo.versionName,
    val message: String? = null,
    /** Draft of the public emergency notice; committed by [SettingsIntent.SaveNotice]. */
    val noticeEnabled: Boolean = false,
    val noticeMessage: String = "",
    val isSavingNotice: Boolean = false
) {
    /** True while the draft differs from what the server already has. */
    val noticeDirty: Boolean
        get() = currentBusiness != null &&
                (noticeEnabled != currentBusiness.noticeEnabled ||
                        noticeMessage != currentBusiness.noticeMessage)

    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class ShowMessage(val message: String) : PartialState()
        data class LoadSettings(val business: Business?) : PartialState()
        data class SetNoticeEnabled(val enabled: Boolean) : PartialState()
        data class SetNoticeMessage(val message: String) : PartialState()
        data class IsSavingNotice(val saving: Boolean) : PartialState()
    }
}
