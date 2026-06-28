package xyz.sattar.javid.proqueue.feature.visitorSelection

sealed interface VisitorSelectionIntent {
    data object LoadVisitors : VisitorSelectionIntent
    data object LoadMore : VisitorSelectionIntent
    data class SearchVisitors(val query: String) : VisitorSelectionIntent
    data class SelectVisitor(val visitorId: Long) : VisitorSelectionIntent
    data object CreateNewVisitor : VisitorSelectionIntent
    data class DeleteVisitor(val visitorId: Long) : VisitorSelectionIntent
    data class EditVisitor(val visitorId: Long) : VisitorSelectionIntent
    data object BackPress : VisitorSelectionIntent
}
