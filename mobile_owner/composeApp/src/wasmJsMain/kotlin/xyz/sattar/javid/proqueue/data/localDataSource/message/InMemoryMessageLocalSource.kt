package xyz.sattar.javid.proqueue.data.localDataSource.message

import kotlinx.coroutines.flow.MutableStateFlow
import xyz.sattar.javid.proqueue.domain.model.message.Message

// See InMemoryBusinessLocalSource for the rationale.
class InMemoryMessageLocalSource : MessageLocalSource {
    private val state = MutableStateFlow<Map<Long, Message>>(emptyMap())
    private var nextId = 1L

    override suspend fun insertMessage(message: Message) {
        val id = if (message.id != 0L) message.id else nextId++
        state.value = state.value + (id to message.copy(id = id))
    }

    override suspend fun getAppointmentMessages(appointmentId: Long): List<Message> =
        state.value.values.filter { it.appointmentId == appointmentId }.sortedBy { it.sentAt }

    override suspend fun getMessagesForVisitorAndBusiness(visitorId: Long, businessId: Long): List<Message> =
        // The local cache has no visitor/business columns of its own — see
        // RoomMessageLocalSource's equivalent query, which joins through
        // Appointment. Nothing on the wasmJs path populates this cache with
        // enough data to reproduce that join, so this intentionally returns
        // an empty list rather than guessing; callers already treat "no
        // local messages" as a normal, fall-through-to-server state.
        emptyList()

    override suspend fun deleteMessage(id: Long): Int {
        val existed = state.value.containsKey(id)
        state.value = state.value - id
        return if (existed) 1 else 0
    }

    override suspend fun deleteMessagesByVisitorId(visitorId: Long): Int = 0
}
