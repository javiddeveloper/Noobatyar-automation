package xyz.sattar.javid.proqueue.feature.home

sealed interface HomeEvent {
    data object NavigateToLogin : HomeEvent
    data class OpenUrl(val url: String) : HomeEvent
    data class ShowError(val message: String) : HomeEvent
}
