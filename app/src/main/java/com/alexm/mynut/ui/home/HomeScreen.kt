package com.alexm.mynut.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexm.mynut.data.NutItem

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToCalendar: () -> Unit,
    onNavigateToForm: (Long?) -> Unit
) {
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val nutItems by viewModel.nutItems.collectAsStateWithLifecycle()
    val dailyEntries by viewModel.dailyEntries.collectAsStateWithLifecycle()
    val dailySummary by viewModel.dailySummary.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var portionDialog by remember { mutableStateOf<NutItem?>(null) }

    val filteredItems = remember(nutItems, searchQuery) {
        if (searchQuery.isBlank()) nutItems
        else nutItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToForm(null) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un item")
            }
        },
        bottomBar = {
            NutrientSummaryBar(summary = dailySummary)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Date header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedDate,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onNavigateToCalendar) {
                        Icon(Icons.Default.DateRange, contentDescription = "Calendrier")
                    }
                }
            }

            // Catalogue section
            item {
                Text(
                    text = "Catalogue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Rechercher") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            items(filteredItems, key = { it.id }) { item ->
                NutItemCard(
                    item = item,
                    onClick = { portionDialog = item },
                    onLongClick = { onNavigateToForm(item.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Divider
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }

            // Consumed today section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Consommés aujourd'hui",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${dailyEntries.size} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (dailyEntries.isEmpty()) {
                item {
                    Text(
                        text = "Aucun item consommé ce jour",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            items(dailyEntries, key = { it.entry.id }) { entryWithItem ->
                DailyEntryRow(
                    entryWithItem = entryWithItem,
                    onDelete = { viewModel.removeEntry(entryWithItem.entry) }
                )
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Portion count dialog
    portionDialog?.let { item ->
        PortionDialog(
            itemName = item.name,
            onConfirm = { portionCount ->
                viewModel.addEntry(item.id, portionCount)
                portionDialog = null
            },
            onDismiss = { portionDialog = null }
        )
    }
}

@Composable
private fun PortionDialog(
    itemName: String,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var portionText by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter $itemName") },
        text = {
            Column {
                Text("Nombre de portions :")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = portionText,
                    onValueChange = { portionText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    portionText.toDoubleOrNull()?.let { onConfirm(it) }
                }
            ) { Text("Ajouter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}