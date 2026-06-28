package xyz.sattar.javid.proqueue.feature.createBusiness

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.domain.model.business.Business

@Immutable
data class CreateBusinessState (
    val businessId: Long = 0,
    val isLoading: Boolean = false,
    val businessCreated: Boolean = false,
    val business: Business? = null,
    val message: String? = null,
    val logoPath: String? = null
){
    sealed class PartialState{
        data class IsLoading(val isLoading: Boolean): PartialState()
        data class ShowMessage(val message: String): PartialState()
        object BusinessCreated: PartialState()
        data class LogoSelected(val path: String): PartialState()
        data class BusinessLoaded(val business: Business): PartialState()
    }
}
