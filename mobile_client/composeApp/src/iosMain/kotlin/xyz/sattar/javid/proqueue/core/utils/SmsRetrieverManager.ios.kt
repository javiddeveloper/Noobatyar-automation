package xyz.sattar.javid.proqueue.core.utils

actual class SmsRetrieverManager {
    actual fun startListening(onOtpReceived: (String) -> Unit) {
        // No-op for iOS. iOS handles OTP autofill natively via textContentType = UITextContentTypeOneTimeCode
    }

    actual fun stopListening() {
        // No-op for iOS
    }
}
