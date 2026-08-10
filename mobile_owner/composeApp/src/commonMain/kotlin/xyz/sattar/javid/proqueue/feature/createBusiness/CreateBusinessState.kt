package xyz.sattar.javid.proqueue.feature.createBusiness

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementsResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto
import xyz.sattar.javid.proqueue.domain.model.business.Business

@Immutable
data class CreateBusinessState (
    val businessId: Long = 0,
    val isLoading: Boolean = false,
    val businessCreated: Boolean = false,
    val business: Business? = null,
    val message: String? = null,
    val logoBytes: ByteArray? = null,
    // Commitment-ladder gating for the "advanced settings" tabs.
    val entitlements: EntitlementsResponseDto? = null,
    val plans: List<PlanDto> = emptyList(),
    // Category-wide chips the owner picks their own service menu from.
    val serviceCatalog: List<String> = emptyList(),
    val isServiceCatalogLoading: Boolean = false
){
    sealed class PartialState{
        data class IsLoading(val isLoading: Boolean): PartialState()
        data class ShowMessage(val message: String): PartialState()
        object ClearMessage : PartialState()
        object BusinessCreated: PartialState()
        data class LogoSelected(val bytes: ByteArray): PartialState()
        data class BusinessLoaded(val business: Business): PartialState()
        data class LoadEntitlements(val entitlements: EntitlementsResponseDto?): PartialState()
        data class LoadPlans(val plans: List<PlanDto>): PartialState()
        data class LoadServiceCatalog(val items: List<String>): PartialState()
        data class ServiceCatalogLoading(val isLoading: Boolean): PartialState()
    }
}
