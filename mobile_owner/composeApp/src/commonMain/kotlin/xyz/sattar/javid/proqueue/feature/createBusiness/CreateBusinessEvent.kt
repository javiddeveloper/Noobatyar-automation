package xyz.sattar.javid.proqueue.feature.createBusiness

sealed class CreateBusinessEvent {
    object NavigateToBusiness : CreateBusinessEvent()
    object BackPressed : CreateBusinessEvent()
}