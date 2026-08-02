package xyz.sattar.javid.proqueue.feature.createBusiness

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.usecase.BusinessUpsertUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMyEntitlementsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetPlansUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.CreatePaymentUseCase

class CreateBusinessViewModel(
    initialState: CreateBusinessState,
    private val businessUpsertUseCase: BusinessUpsertUseCase,
    private val businessRepository: xyz.sattar.javid.proqueue.domain.BusinessRepository,
    private val getMyEntitlementsUseCase: GetMyEntitlementsUseCase,
    private val getPlansUseCase: GetPlansUseCase,
    private val createPaymentUseCase: CreatePaymentUseCase
) : BaseViewModel<CreateBusinessState, CreateBusinessState.PartialState, CreateBusinessEvent, CreateBusinessIntent>(
    initialState
) {
    override fun handleIntent(intent: CreateBusinessIntent): Flow<CreateBusinessState.PartialState> {
        return when (intent) {
            is CreateBusinessIntent.CreateBusiness -> {
                createBusiness(
                    intent.title,
                    intent.category,
                    intent.phone,
                    intent.address,
                    intent.defaultProgress,
                    intent.workStartHour,
                    intent.workEndHour,
                    intent.allowAnonymousView,
                    intent.notifyOwnerBySms,
                    intent.maxAppointmentsPerHour,
                    intent.depositMode,
                    intent.depositAmount,
                    intent.acceptedPaymentMethods,
                    intent.cardNumber,
                    intent.cardOwnerName,
                    intent.merchantId,
                    intent.paymentLink,
                    intent.bio,
                    intent.logoBytes,
                    intent.noticeEnabled,
                    intent.noticeMessage,
                    intent.reminderDelivery
                )
            }

            CreateBusinessIntent.BackPress -> sendEvent(CreateBusinessEvent.BackPressed)
            CreateBusinessIntent.ClearMessage -> flow { emit(CreateBusinessState.PartialState.ClearMessage) }
            CreateBusinessIntent.BusinessCreated -> sendEvent(CreateBusinessEvent.NavigateToBusiness)
            is CreateBusinessIntent.LoadBusiness -> loadBusiness(intent.businessId)
            CreateBusinessIntent.LoadEntitlements -> loadEntitlements()
            is CreateBusinessIntent.UpgradePlan -> upgradePlan(intent.planId)
        }
    }

    private fun loadEntitlements(): Flow<CreateBusinessState.PartialState> = flow {
        try {
            when (val response = getMyEntitlementsUseCase()) {
                is ApiResponse.Success -> emit(CreateBusinessState.PartialState.LoadEntitlements(response.data))
                is ApiResponse.Error -> {}
            }
        } catch (e: Exception) {}

        try {
            when (val response = getPlansUseCase()) {
                is ApiResponse.Success -> emit(CreateBusinessState.PartialState.LoadPlans(response.data))
                is ApiResponse.Error -> {}
            }
        } catch (e: Exception) {}
    }

    private fun upgradePlan(planId: Int): Flow<CreateBusinessState.PartialState> = flow {
        try {
            when (val response = createPaymentUseCase(planId)) {
                is ApiResponse.Success -> sendEvent(CreateBusinessEvent.OpenUrl(response.data.paymentUrl))
                is ApiResponse.Error -> emit(CreateBusinessState.PartialState.ShowMessage(response.message))
            }
        } catch (e: Exception) {
            emit(CreateBusinessState.PartialState.ShowMessage(e.message ?: "خطا در برقراری ارتباط"))
        }
    }


    override fun reduceState(
        currentState: CreateBusinessState,
        partialState: CreateBusinessState.PartialState
    ): CreateBusinessState {
        return when (partialState) {
            CreateBusinessState.PartialState.BusinessCreated ->
                currentState.copy(businessCreated = true, isLoading = false, message = null)

            is CreateBusinessState.PartialState.IsLoading ->
                currentState.copy(isLoading = partialState.isLoading, message = null)

            is CreateBusinessState.PartialState.ShowMessage ->
                currentState.copy(
                    businessCreated = false,
                    isLoading = false,
                    message = partialState.message
                )
            CreateBusinessState.PartialState.ClearMessage ->
                currentState.copy(message = null)
            is CreateBusinessState.PartialState.LogoSelected ->
                currentState.copy(logoBytes = partialState.bytes, isLoading = false)

            is CreateBusinessState.PartialState.BusinessLoaded ->
                currentState.copy(
                    businessId = partialState.business.id,
                    business = partialState.business,
                    isLoading = false
                )

            is CreateBusinessState.PartialState.LoadEntitlements ->
                currentState.copy(entitlements = partialState.entitlements)

            is CreateBusinessState.PartialState.LoadPlans ->
                currentState.copy(plans = partialState.plans)
        }
    }

    override fun createErrorState(message: String): CreateBusinessState.PartialState =
        CreateBusinessState.PartialState.ShowMessage(message)

    private fun loadBusiness(businessId: Long): Flow<CreateBusinessState.PartialState> = flow {
        emit(CreateBusinessState.PartialState.IsLoading(true))
        val business = businessRepository.getBusinessById(businessId)
        if (business != null) {
            emit(CreateBusinessState.PartialState.BusinessLoaded(business))
        } else {
            emit(CreateBusinessState.PartialState.ShowMessage("بیزینس یافت نشد"))
        }
    }

    private fun createBusiness(
        businessName: String,
        category: xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory,
        phone: String,
        address: String,
        defaultProgress: String,
        workStartHour: Int,
        workEndHour: Int,
        allowAnonymousView: Boolean,
        notifyOwnerBySms: Boolean,
        maxAppointmentsPerHour: Int?,
        depositMode: String?,
        depositAmount: Int?,
        acceptedPaymentMethods: String,
        cardNumber: String,
        cardOwnerName: String,
        merchantId: String,
        paymentLink: String,
        bio: String,
        logoBytes: ByteArray?,
        noticeEnabled: Boolean,
        noticeMessage: String,
        reminderDelivery: String
    ): Flow<CreateBusinessState.PartialState> = flow {
        emit(CreateBusinessState.PartialState.IsLoading(true))
        val updatedBusiness = businessUpsertUseCase.invoke(
            Business(
                title = businessName,
                category = category,
                phone = phone,
                address = address,
                logoPath = uiState.value.business?.logoPath ?: "Sample_path.jpg",
                id = uiState.value.businessId,
                defaultServiceDuration = defaultProgress.toIntOrNull() ?: 15,
                workStartHour = workStartHour,
                workEndHour = workEndHour,
                notificationEnabled = uiState.value.business?.notificationEnabled ?: true,
                notificationTypes = uiState.value.business?.notificationTypes ?: "SMS,WHATSAPP",
                allowAnonymousView = allowAnonymousView,
                notifyOwnerBySms = notifyOwnerBySms,
                maxAppointmentsPerHour = maxAppointmentsPerHour,
                depositMode = depositMode,
                depositAmount = depositAmount,
                acceptedPaymentMethods = acceptedPaymentMethods.split(",").filter { it.isNotEmpty() },
                cardNumber = cardNumber,
                cardOwnerName = cardOwnerName,
                merchantId = merchantId,
                paymentLink = paymentLink,
                bio = bio,
                logoBytes = logoBytes,
                noticeEnabled = noticeEnabled,
                noticeMessage = noticeMessage,
                reminderDelivery = reminderDelivery
            )
        )
        if (updatedBusiness != null) {
            // Update global state if this is the currently selected business
            val selectedBusiness = xyz.sattar.javid.proqueue.core.state.BusinessStateHolder.selectedBusiness.value
            if (selectedBusiness != null && selectedBusiness.id == updatedBusiness.id) {
                xyz.sattar.javid.proqueue.core.state.BusinessStateHolder.selectBusiness(updatedBusiness)
            }
            emit(CreateBusinessState.PartialState.BusinessCreated)
        } else {
            emit(CreateBusinessState.PartialState.ShowMessage("خطا در عملیات"))
        }
    }
}
