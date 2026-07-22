package xyz.sattar.javid.proqueue.feature.addons

sealed interface AddonsEvent {
    data class OpenUrl(val url: String) : AddonsEvent
}
