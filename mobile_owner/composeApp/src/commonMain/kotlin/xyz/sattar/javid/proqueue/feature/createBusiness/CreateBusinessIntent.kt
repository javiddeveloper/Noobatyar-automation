package xyz.sattar.javid.proqueue.feature.createBusiness

import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory

sealed class CreateBusinessIntent {
    data class CreateBusiness(
        val title: String,
        val category: BusinessCategory,
        val phone: String,
        val address: String,
        val defaultProgress: String,
        val workStartHour: Int,
        val workEndHour: Int,
        val allowAnonymousView: Boolean
    ) : CreateBusinessIntent()
    object BackPress : CreateBusinessIntent()
    object BusinessCreated : CreateBusinessIntent()
    data class LoadBusiness(val businessId: Long) : CreateBusinessIntent()
}
