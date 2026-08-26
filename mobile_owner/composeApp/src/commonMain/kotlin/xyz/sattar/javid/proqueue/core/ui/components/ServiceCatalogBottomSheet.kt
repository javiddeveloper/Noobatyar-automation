package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.service_catalog_add_new
import proqueue.composeapp.generated.resources.service_catalog_add_placeholder
import proqueue.composeapp.generated.resources.service_catalog_done
import proqueue.composeapp.generated.resources.service_catalog_empty
import proqueue.composeapp.generated.resources.service_catalog_sheet_hint
import proqueue.composeapp.generated.resources.service_catalog_sheet_title

/**
 * Chip picker for "which services did this client actually receive?",
 * scoped to the business's category and shared across every business in
 * that category (see business.models.ServiceCatalogItem on the backend —
 * an item one owner adds here becomes pickable for every other owner in the
 * same category, not just this business).
 *
 * Matches [ServiceDurationBottomSheet]'s look: same sheet chrome, title +
 * hint pattern, scrollable body capped at a fixed height so the keyboard
 * (needed for "add new") never pushes the confirm action off-screen.
 */
@Composable
fun ServiceCatalogBottomSheet(
    catalog: List<String>,
    selected: List<String>,
    isLoading: Boolean,
    onToggle: (String) -> Unit,
    onAddNew: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    var newItemText by remember { mutableStateOf("") }

    AdaptiveSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(Res.string.service_catalog_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.service_catalog_sheet_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else if (catalog.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.service_catalog_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    ServiceChipGroup(
                        items = catalog,
                        selected = selected,
                        onToggle = onToggle
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // "Add new" — deliberately a plain text field + button, not another
            // AppTextField with its own error/label chrome: this is a quick
            // inline add, not a form field.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = newItemText,
                    onValueChange = { newItemText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(Res.string.service_catalog_add_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                IconButton(
                    onClick = {
                        val trimmed = newItemText.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddNew(trimmed)
                            newItemText = ""
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(Res.string.service_catalog_add_new),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AppButton(
                text = stringResource(Res.string.service_catalog_done),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceChipGroup(
    items: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { name ->
            val isSelected = selected.contains(name)
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(name) },
                label = { Text(name) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/**
 * Read-only echo of the currently selected chips, rendered under the field
 * that opens [ServiceCatalogBottomSheet] — so a decision made inside the
 * sheet is still visible after it closes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectedServiceChipsRow(
    selected: List<String>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        selected.forEach { name ->
            FilterChip(
                selected = true,
                onClick = { onRemove(name) },
                label = { Text(name) },
                leadingIcon = {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
