package xyz.sattar.javid.proqueue.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
inline fun <reified T> Flow<T>.collectWithLifecycleAware(
    key: Any = Unit,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    noinline action: suspend (T) -> Unit
) {
    /**
     * Collects values from this [Flow] within a Composable function, automatically cancelling
     * the collection when the LifecycleOwner's state is less than the specified [minActiveState].
     *
     * @param key An optional key to restart the collection when changed.
     * @param lifecycleOwner The LifecycleOwner that controls the lifecycle of the collection.
     * @param minActiveState The minimum active state in which the collection should occur.
     * @param action The action to perform with each value collected from the flow.
     */
    val lifecycleAwareFlow = remember(this, lifecycleOwner) {
        this.flowWithLifecycle(lifecycleOwner.lifecycle, minActiveState)
    }

    LaunchedEffect(key) {
        lifecycleAwareFlow.collect(action)
    }
}

@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED
): State<T> = collectAsStateWithLifecycle(
    initialValue = remember { this.value },
    lifecycle = lifecycle,
    minActiveState = minActiveState
)

@Composable
fun <T> Flow<T>.collectAsStateWithLifecycleAware(
    initialValue: T,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED
): State<T> {
    /**
     * Collects values from this [Flow] as a [State] within a Composable function,
     * initializing with [initialValue] and automatically stopping the collection
     * when the LifecycleOwner's state is less than the specified [minActiveState].
     *
     * @param initialValue The initial value to start with.
     * @param lifecycle The Lifecycle that controls the lifecycle of the collection.
     * @param minActiveState The minimum active state in which the collection should occur.
     * @return A [State] object containing the latest value collected from this [Flow].
     */
    val currentValue = remember(this) { initialValue }
    return produceState(
        initialValue = currentValue,
        key1 = this,
        key2 = lifecycle,
        key3 = minActiveState
    ) {
        lifecycle.repeatOnLifecycle(minActiveState) {
            this@collectAsStateWithLifecycleAware.collect {
                this@produceState.value = it
            }
        }
    }
}

fun Modifier.animatePlacement(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var targetOffset by remember { mutableStateOf(IntOffset.Zero) }
    var animatable by remember {
        mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null)
    }
    this
        .onPlaced {
            targetOffset = it
                .positionInParent()
                .round()
        }
        .offset {
            val anim = animatable ?: Animatable(targetOffset, IntOffset.VectorConverter)
                .also {
                    animatable = it
                }
            if (anim.targetValue != targetOffset) {
                scope.launch {
                    anim.animateTo(targetOffset, spring(stiffness = Spring.StiffnessMediumLow))
                }
            }
            animatable?.let { it.value - targetOffset } ?: IntOffset.Zero
        }
}

fun String.iSValidForSearch(): Boolean = this.trim().length > 2

fun String.iSValidForSearchHashtag(): Boolean = this.trim().length > 1

val hashtagRegex = "#[\\p{L}0-9_\\p{M}]+".toRegex()
val stringListRegex = "(\\S+|\\s)".toRegex()
val emojiRegex = """[\uD83C\uDF00-\uD83D\uDFFF\uD83E\uDD00-\uD83E\uDFFF]+""".toRegex()

fun Int.toTimerFormat(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

