package xyz.sattar.javid.proqueue.feature.createVisitor

import androidx.compose.runtime.Immutable

import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

@Immutable
data class CreateVisitorState(
    val isLoading: Boolean = false,
    val visitorCreated: Boolean = false,
    val message: String? = null,
    val loadedVisitor: Visitor? = null
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class ShowMessage(val message: String) : PartialState()
        data class VisitorLoaded(val visitor: Visitor) : PartialState()
        object VisitorCreated : PartialState()
    }
}

data class CreateVisitorErrors(
    val fullName: String? = null,
    val phoneNumber: String? = null
)
