package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.message.MessageToken

class GenerateReminderMessageUseCase {

    operator fun invoke(
        businessId: Long,
        businessTitle: String,
        businessAddress: String,
        visitorName: String,
        appointmentMillis: Long,
        reminderMinutes: String,
        serviceDuration: Int?,
        templateOverride: String? = null
    ): String {
        val template = templateOverride ?: PreferencesManager.getMessageTemplate(businessId)
        ?: getDefaultTemplate()

        val date = DateTimeUtils.formatMillisDateOnly(appointmentMillis)
        val time = DateTimeUtils.formatTime(appointmentMillis)
        val duration = serviceDuration?.let { "$it" } ?: "مشخص نشده"

        var message = template
        message = message.replace(MessageToken.Visitor.token, visitorName)
        message = message.replace(MessageToken.Business.token, businessTitle)
        message = message.replace(MessageToken.Address.token, businessAddress)
        message = message.replace(MessageToken.Date.token, date)
        message = message.replace(MessageToken.Time.token, time)
        message = message.replace(MessageToken.Minutes.token, reminderMinutes)
        message = message.replace(MessageToken.Duration.token, duration)

        return message
    }

    private fun getDefaultTemplate(): String {
        return "با سلام {visitor} عزیز 🌹؛ یادآوری نوبت شما در {business} برای ساعت {time}. مدت زمان خدمت به شما حدود {duration} است. لطفاً تا {minutes} دقیقه دیگر در محل حضور داشته باشید."
    }

    fun generatePreview(
        template: String,
        businessTitle: String,
        businessAddress: String,
        serviceDuration: Int?,
        reminderMinutes: Int
    ): String {
        return invoke(
            businessId = -1,
            businessTitle = businessTitle,
            businessAddress = businessAddress,
            visitorName = "سارا",
            appointmentMillis = DateTimeUtils.systemCurrentMilliseconds(),
            reminderMinutes = reminderMinutes.toString(),
            serviceDuration = serviceDuration,
            templateOverride = template
        )
    }
}
