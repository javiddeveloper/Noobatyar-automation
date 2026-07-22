package xyz.sattar.javid.proqueue.feature.addons

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.user.BuyAddonUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetAddonsUseCase

class AddonsViewModel(
    private val getAddonsUseCase: GetAddonsUseCase,
    private val buyAddonUseCase: BuyAddonUseCase
) : BaseViewModel<AddonsState, AddonsState.PartialState, AddonsEvent, AddonsIntent>(
    initialState = AddonsState()
) {
    override fun handleIntent(intent: AddonsIntent): Flow<AddonsState.PartialState> {
        return when (intent) {
            AddonsIntent.Load -> load()
            is AddonsIntent.Buy -> buy(intent.packId)
        }
    }

    private fun load(): Flow<AddonsState.PartialState> = flow {
        emit(AddonsState.PartialState.IsLoading(true))
        try {
            when (val response = getAddonsUseCase()) {
                is ApiResponse.Success -> emit(AddonsState.PartialState.LoadPacks(response.data))
                is ApiResponse.Error -> emit(AddonsState.PartialState.ShowMessage(response.message))
            }
        } catch (e: Exception) {
            emit(AddonsState.PartialState.ShowMessage(e.message ?: "خطا در دریافت بسته‌ها"))
        }
        emit(AddonsState.PartialState.IsLoading(false))
    }

    private fun buy(packId: Int): Flow<AddonsState.PartialState> = flow {
        emit(AddonsState.PartialState.SetPurchasing(packId))
        try {
            when (val response = buyAddonUseCase(packId)) {
                is ApiResponse.Success -> sendEvent(AddonsEvent.OpenUrl(response.data.paymentUrl))
                is ApiResponse.Error -> emit(AddonsState.PartialState.ShowMessage(response.message))
            }
        } catch (e: Exception) {
            emit(AddonsState.PartialState.ShowMessage(e.message ?: "خطا در برقراری ارتباط"))
        }
        emit(AddonsState.PartialState.SetPurchasing(null))
    }

    override fun reduceState(
        currentState: AddonsState,
        partialState: AddonsState.PartialState
    ): AddonsState {
        return when (partialState) {
            is AddonsState.PartialState.IsLoading -> currentState.copy(isLoading = partialState.loading)
            is AddonsState.PartialState.LoadPacks -> currentState.copy(packs = partialState.packs)
            is AddonsState.PartialState.ShowMessage -> currentState.copy(message = partialState.text)
            is AddonsState.PartialState.SetPurchasing -> currentState.copy(purchasingPackId = partialState.packId)
        }
    }

    override fun createErrorState(message: String): AddonsState.PartialState =
        AddonsState.PartialState.ShowMessage(message)
}
