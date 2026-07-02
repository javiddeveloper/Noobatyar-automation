package xyz.sattar.javid.proqueue.core.utils

expect class SmsRetrieverManager {
    fun startListening(onOtpReceived: (String) -> Unit)
    fun stopListening()
}
