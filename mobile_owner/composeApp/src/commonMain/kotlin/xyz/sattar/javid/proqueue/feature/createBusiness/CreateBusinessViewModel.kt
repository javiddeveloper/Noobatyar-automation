package xyz.sattar.javid.proqueue.feature.createBusiness

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.usecase.BusinessUpsertUseCase

class CreateBusinessViewModel(
    initialState: CreateBusinessState,
    private val businessUpsertUseCase: BusinessUpsertUseCase,
    private val businessRepository: xyz.sattar.javid.proqueue.domain.BusinessRepository
) : BaseViewModel<CreateBusinessState, CreateBusinessState.PartialState, CreateBusinessEvent, CreateBusinessIntent>(
    initialState
) {
    override fun handleIntent(intent: CreateBusinessIntent): Flow<CreateBusinessState.PartialState> {
        return when (intent) {
            is CreateBusinessIntent.CreateBusiness -> {
                createBusiness(
                    intent.title,
                    intent.phone,
                    intent.address,
                    intent.defaultProgress,
                    intent.workStartHour,
                    intent.workEndHour
                )
            }

            CreateBusinessIntent.BackPress -> sendEvent(CreateBusinessEvent.BackPressed)
            CreateBusinessIntent.BusinessCreated -> sendEvent(CreateBusinessEvent.NavigateToBusiness)
            is CreateBusinessIntent.LoadBusiness -> loadBusiness(intent.businessId)
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
            is CreateBusinessState.PartialState.LogoSelected ->
                currentState.copy(logoPath = partialState.path, isLoading = false)

            is CreateBusinessState.PartialState.BusinessLoaded ->
                currentState.copy(
                    businessId = partialState.business.id,
                    business = partialState.business,
                    logoPath = partialState.business.logoPath,
                    isLoading = false
                )
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
        phone: String,
        address: String,
        defaultProgress: String,
        workStartHour: Int,
        workEndHour: Int
    ): Flow<CreateBusinessState.PartialState> = flow {
        emit(CreateBusinessState.PartialState.IsLoading(true))
        val updatedBusiness = businessUpsertUseCase.invoke(
            Business(
                title = businessName,
                phone = phone,
                address = address,
                logoPath = uiState.value.logoPath ?: "Sample_path.jpg",
                id = uiState.value.businessId,
                defaultServiceDuration = defaultProgress.toIntOrNull() ?: 15,
                workStartHour = workStartHour,
                workEndHour = workEndHour,
                notificationEnabled = uiState.value.business?.notificationEnabled ?: true,
                notificationTypes = uiState.value.business?.notificationTypes ?: "SMS,WHATSAPP",
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
