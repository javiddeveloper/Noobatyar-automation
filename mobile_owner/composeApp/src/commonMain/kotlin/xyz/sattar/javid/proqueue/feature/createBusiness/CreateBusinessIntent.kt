package xyz.sattar.javid.proqueue.feature.createBusiness

import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory
import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery

sealed class CreateBusinessIntent {
    data class CreateBusiness(
        val title: String,
        val category: BusinessCategory,
        val phone: String,
        val address: String,
        val defaultProgress: String,
        val workStartHour: Int,
        val workEndHour: Int,
        val allowAnonymousView: Boolean,
        val notifyOwnerBySms: Boolean,
        val bio: String,
        val logoBytes: ByteArray? = null,
        val maxAppointmentsPerHour: Int? = null,
        val depositMode: String? = null,
        val depositAmount: Int?,
        val acceptedPaymentMethods: String,
        val cardNumber: String,
        val cardOwnerName: String,
        val merchantId: String,
        val paymentLink: String,
        val noticeEnabled: Boolean = false,
        val noticeMessage: String = "",
        val reminderDelivery: String = ReminderDelivery.MANUAL.value,
        val services: List<String> = emptyList(),
        val allowClientAddService: Boolean = false
    ) : CreateBusinessIntent()
    object BackPress : CreateBusinessIntent()
    object ClearMessage : CreateBusinessIntent()
    object BusinessCreated : CreateBusinessIntent()
    data class LoadBusiness(val businessId: Long) : CreateBusinessIntent()
    object LoadEntitlements : CreateBusinessIntent()
    data class UpgradePlan(val planId: Int) : CreateBusinessIntent()
    /** Chips to offer in the service-menu picker, for the picked category. */
    data class LoadServiceCatalog(val category: BusinessCategory) : CreateBusinessIntent()
    /** Adds a brand-new chip to the category's shared catalog. */
    data class AddServiceCatalogItem(
        val category: BusinessCategory,
        val name: String
    ) : CreateBusinessIntent()
}
