package xyz.sattar.javid.proqueue.feature.businessList

import xyz.sattar.javid.proqueue.domain.model.business.Business

sealed interface BusinessListIntent {
    data object ObserveBusinesses : BusinessListIntent
    data object LoadNextPage : BusinessListIntent
    data object RetryFetch : BusinessListIntent
    data class OnBusinessClick(val business: Business) : BusinessListIntent
    data object OnCreateBusinessClick : BusinessListIntent
    data class OnEditBusinessClick(val businessId: Long) : BusinessListIntent
    data class OnDeleteBusinessClick(val businessId: Long) : BusinessListIntent
}
