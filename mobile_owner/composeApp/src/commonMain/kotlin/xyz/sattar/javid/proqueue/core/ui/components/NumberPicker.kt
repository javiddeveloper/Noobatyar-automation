package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumberPicker(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    range: IntRange,
    initialValue: Int,
    onValueChange: (Int) -> Unit,
    itemHeight: Dp = 48.dp,
    visibleItems: Int = 3, // Must be an odd number for centering
    itemFormatter: (Int) -> String = { it.toString() }
) {
    val count = range.last - range.first + 1
    val centerOffset = visibleItems / 2
    
    // Scroll to initial value
    LaunchedEffect(Unit) {
        val index = initialValue - range.first
        if (index in 0 until count) {
            state.scrollToItem(index)
        }
    }

    // Detect changes
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                val value = range.first + index
                onValueChange(value)
            }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleItems),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = itemHeight * centerOffset)
        ) {
            items(count) { index ->
                val value = range.first + index
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = itemFormatter(value),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Dividers
        Column(Modifier.fillMaxWidth()) {
             Box(Modifier.weight(1f))
             HorizontalDivider(
                 modifier = Modifier.fillMaxWidth(),
                 color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                 thickness = 1.dp
             )
             Box(Modifier.height(itemHeight))
             HorizontalDivider(
                 modifier = Modifier.fillMaxWidth(),
                 color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                 thickness = 1.dp
             )
             Box(Modifier.weight(1f))
        }
    }
}
