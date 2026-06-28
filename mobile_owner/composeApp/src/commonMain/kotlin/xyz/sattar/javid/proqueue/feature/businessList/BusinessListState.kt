package xyz.sattar.javid.proqueue.feature.businessList

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.domain.model.business.Business

@Immutable
data class BusinessListState(
    val isLoading: Boolean = false,
    val isPaginating: Boolean = false,
    val currentPage: Int = 1,
    val businesses: List<Business> = emptyList(),
    val message: String? = null
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class IsPaginating(val isPaginating: Boolean) : PartialState()
        data class ShowMessage(val message: String) : PartialState()
        data class LoadBusinesses(val businesses: List<Business>) : PartialState()
        data class PageIncremented(val newPage: Int) : PartialState()
    }
}
