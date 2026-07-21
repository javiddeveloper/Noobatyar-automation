package xyz.sattar.javid.proqueue.core.ui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.math.max

class CurrencyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text.filter { it.isDigit() }
        var formattedText = ""
        var counter = 0
        for (i in originalText.length - 1 downTo 0) {
            formattedText = originalText[i] + formattedText
            counter++
            if (counter == 3 && i > 0) {
                formattedText = ",$formattedText"
                counter = 0
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (originalText.isEmpty()) return 0
                val safeOffset = offset.coerceIn(0, originalText.length)
                val commasBeforeOffset = max(0, (safeOffset - 1) / 3)
                return safeOffset + commasBeforeOffset
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (formattedText.isEmpty()) return 0
                val safeOffset = offset.coerceIn(0, formattedText.length)
                var commasCount = 0
                for (i in 0 until safeOffset) {
                    if (formattedText[i] == ',') commasCount++
                }
                return safeOffset - commasCount
            }
        }
        return TransformedText(AnnotatedString(formattedText), offsetMapping)
    }
}

class CardNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text.filter { it.isDigit() }
        var formattedText = ""
        for (i in originalText.indices) {
            formattedText += originalText[i]
            if ((i + 1) % 4 == 0 && i != originalText.lastIndex) {
                formattedText += "-"
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (originalText.isEmpty()) return 0
                val safeOffset = offset.coerceIn(0, originalText.length)
                val dashesBeforeOffset = max(0, (safeOffset - 1) / 4)
                return safeOffset + dashesBeforeOffset
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (formattedText.isEmpty()) return 0
                val safeOffset = offset.coerceIn(0, formattedText.length)
                var dashesCount = 0
                for (i in 0 until safeOffset) {
                    if (formattedText[i] == '-') dashesCount++
                }
                return safeOffset - dashesCount
            }
        }
        return TransformedText(AnnotatedString(formattedText), offsetMapping)
    }
}
