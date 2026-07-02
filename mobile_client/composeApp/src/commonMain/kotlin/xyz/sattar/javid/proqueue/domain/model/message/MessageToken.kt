package xyz.sattar.javid.proqueue.domain.model.message

enum class MessageToken(val label: String, val token: String) {
    Visitor("نام مشتری", "{visitor}"),
    Business("نام کسب‌وکار", "{business}"),
    Address("آدرس", "{address}"),
    Date("تاریخ", "{date}"),
    Time("ساعت", "{time}"),
    Minutes("دقیقه یادآوری", "{minutes}"),
    Duration("زمان سرویس", "{duration}");

    companion object {
        fun asList(): List<MessageToken> = entries.toList()
    }
}
