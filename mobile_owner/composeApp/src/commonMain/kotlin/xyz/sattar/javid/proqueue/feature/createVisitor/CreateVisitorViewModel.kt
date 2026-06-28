package xyz.sattar.javid.proqueue.feature.createVisitor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor
import xyz.sattar.javid.proqueue.domain.usecase.GetVisitorByIdUseCase
import xyz.sattar.javid.proqueue.domain.usecase.VisitorUpsertUseCase

class CreateVisitorViewModel(
    initialState: CreateVisitorState,
    private val visitorUpsertUseCase: VisitorUpsertUseCase,
    private val getVisitorByIdUseCase: GetVisitorByIdUseCase
) : BaseViewModel<CreateVisitorState, CreateVisitorState.PartialState, CreateVisitorEvent, CreateVisitorIntent>(
    initialState
) {
    override fun handleIntent(intent: CreateVisitorIntent): Flow<CreateVisitorState.PartialState> {
        return when (intent) {
            is CreateVisitorIntent.CreateVisitor -> createVisitor(
                intent.fullName,
                intent.phoneNumber,
                intent.id
            )

            is CreateVisitorIntent.EditVisitor -> editVisitor(
                intent.fullName,
                intent.phoneNumber,
                intent.visitorId
            )

            is CreateVisitorIntent.LoadVisitor -> loadVisitor(intent.visitorId)
            CreateVisitorIntent.BackPress -> {
                sendEvent(CreateVisitorEvent.BackPressed)
            }
        }
    }

    override fun reduceState(
        currentState: CreateVisitorState,
        partialState: CreateVisitorState.PartialState
    ): CreateVisitorState {
        return when (partialState) {
            CreateVisitorState.PartialState.VisitorCreated ->
                currentState.copy(visitorCreated = true, isLoading = false, message = null)

            is CreateVisitorState.PartialState.IsLoading ->
                currentState.copy(visitorCreated = false, isLoading = partialState.isLoading, message = null)

            is CreateVisitorState.PartialState.ShowMessage ->
                currentState.copy(
                    visitorCreated = false,
                    isLoading = false,
                    message = partialState.message
                )

            is CreateVisitorState.PartialState.VisitorLoaded ->
                currentState.copy(
                    loadedVisitor = partialState.visitor,
                    isLoading = false
                )
        }
    }

    override fun createErrorState(message: String): CreateVisitorState.PartialState =
        CreateVisitorState.PartialState.ShowMessage(message)

    private fun loadVisitor(visitorId: Long): Flow<CreateVisitorState.PartialState> = flow {
        emit(CreateVisitorState.PartialState.IsLoading(true))
        try {
            val visitor = getVisitorByIdUseCase(visitorId)
            if (visitor != null) {
                emit(CreateVisitorState.PartialState.VisitorLoaded(visitor))
            } else {
                emit(CreateVisitorState.PartialState.ShowMessage("مراجع یافت نشد"))
            }
        } catch (e: Exception) {
            emit(CreateVisitorState.PartialState.ShowMessage(e.message ?: "خطا در بارگذاری مراجع"))
        }
    }

    private fun createVisitor(
        fullName: String,
        phoneNumber: String,
        id: Long
    ): Flow<CreateVisitorState.PartialState> = flow {
        emit(CreateVisitorState.PartialState.IsLoading(true))

        val currentTime = DateTimeUtils.systemCurrentMilliseconds()
        val visitorId = visitorUpsertUseCase.invoke(
            Visitor(
                id = id,
                fullName = fullName,
                phoneNumber = phoneNumber,
                createdAt = currentTime
            )
        )

        emit(CreateVisitorState.PartialState.VisitorCreated)
        if (visitorId != -1L) {
            sendEvent(CreateVisitorEvent.VisitorCreated(visitorId))
        } else {
            emit(CreateVisitorState.PartialState.ShowMessage("خطا در ذخیره مراجع"))
        }
    }

    private fun editVisitor(
        fullName: String,
        phoneNumber: String,
        visitorId: Long?
    ): Flow<CreateVisitorState.PartialState> = flow {
        emit(CreateVisitorState.PartialState.IsLoading(true))
        if (visitorId != null) {
            val originalCreatedAt = uiState.value.loadedVisitor?.createdAt ?: DateTimeUtils.systemCurrentMilliseconds()
            visitorUpsertUseCase.invoke(
                Visitor(
                    id = visitorId,
                    fullName = fullName,
                    phoneNumber = phoneNumber,
                    createdAt = originalCreatedAt
                )
            )
            emit(CreateVisitorState.PartialState.IsLoading(false))
            sendEvent(CreateVisitorEvent.VisitorUpdated(visitorId))
        } else {
            emit(CreateVisitorState.PartialState.ShowMessage("خطا در بروزرسانی مراجع"))
        }
    }
}
