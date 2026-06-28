package xyz.sattar.javid.proqueue.feature.createBusiness

sealed class CreateBusinessIntent {
    data class CreateBusiness(
        val title: String,
        val phone: String,
        val address: String,
        val defaultProgress: String,
        val workStartHour: Int,
        val workEndHour: Int
    ) : CreateBusinessIntent()
    object BackPress : CreateBusinessIntent()
    object BusinessCreated : CreateBusinessIntent()
    data class LoadBusiness(val businessId: Long) : CreateBusinessIntent()
}
