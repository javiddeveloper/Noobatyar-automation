package xyz.sattar.javid.proqueue.feature.businessList

import xyz.sattar.javid.proqueue.domain.model.business.Business

sealed interface BusinessListEvent {
    data class NavigateToMain(val business: Business) : BusinessListEvent
    data class ShowMessage(val message: String) : BusinessListEvent
    data object NavigateToCreateBusiness : BusinessListEvent
    data class NavigateToEditBusiness(val businessId: Long) : BusinessListEvent
    data object NavigateToLogin : BusinessListEvent
}
