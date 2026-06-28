package xyz.sattar.javid.proqueue.feature.visitorSelection

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

@Immutable
data class VisitorSelectionState(
    val isLoading: Boolean = false,
    val visitors: List<Visitor> = emptyList(),
    val filteredVisitors: List<Visitor> = emptyList(),
    val searchQuery: String = "",
    val message: String? = null,
    val currentPage: Int = 1,
    val canLoadMore: Boolean = true
) {
    sealed interface PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState
        data class LoadVisitors(val visitors: List<Visitor>, val page: Int, val canLoadMore: Boolean) : PartialState
        data class UpdateSearchQuery(val query: String) : PartialState
        data class ShowMessage(val message: String) : PartialState
    }
}
