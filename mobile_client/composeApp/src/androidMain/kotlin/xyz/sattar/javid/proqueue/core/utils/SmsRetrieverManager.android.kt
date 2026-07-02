package xyz.sattar.javid.proqueue.core.utils

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

actual class SmsRetrieverManager(private val context: Context) {
    private var onOtpReceivedCallback: ((String) -> Unit)? = null
    private var isListening = false

    private val smsVerificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                val extras = intent.extras
                val status = extras?.get(SmsRetriever.EXTRA_STATUS) as? Status

                when (status?.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        // Get SMS message contents
                        val message = extras.get(SmsRetriever.EXTRA_SMS_MESSAGE) as? String
                        message?.let {
                            // Extract 6-digit OTP code using Regex
                            val regex = Regex("\\d{6}")
                            val matchResult = regex.find(it)
                            matchResult?.value?.let { otp ->
                                onOtpReceivedCallback?.invoke(otp)
                                stopListening()
                            }
                        }
                    }
                    CommonStatusCodes.TIMEOUT -> {
                        // Waiting for SMS timed out (5 minutes)
                        stopListening()
                    }
                }
            }
        }
    }

    actual fun startListening(onOtpReceived: (String) -> Unit) {
        if (isListening) return
        onOtpReceivedCallback = onOtpReceived
        
        val client = SmsRetriever.getClient(context)
        val task = client.startSmsRetriever()

        task.addOnSuccessListener {
            // Successfully started retriever, expect broadcast intent
            val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    context,
                    smsVerificationReceiver,
                    intentFilter,
                    SmsRetriever.SEND_PERMISSION,
                    null,
                    ContextCompat.RECEIVER_EXPORTED
                )
            } else {
                ContextCompat.registerReceiver(
                    context,
                    smsVerificationReceiver,
                    intentFilter,
                    SmsRetriever.SEND_PERMISSION,
                    null,
                    ContextCompat.RECEIVER_EXPORTED
                )
            }
            isListening = true
        }

        task.addOnFailureListener {
            // Failed to start retriever, inspect Exception for more details
            isListening = false
        }
    }

    actual fun stopListening() {
        if (isListening) {
            try {
                context.unregisterReceiver(smsVerificationReceiver)
            } catch (e: Exception) {
                // Ignore if not registered
            }
            isListening = false
            onOtpReceivedCallback = null
        }
    }
}
