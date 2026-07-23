package xyz.sattar.javid.proqueue.feature.addons

sealed interface AddonsIntent {
    data object Load : AddonsIntent
    data class Buy(val packId: Int) : AddonsIntent
}
