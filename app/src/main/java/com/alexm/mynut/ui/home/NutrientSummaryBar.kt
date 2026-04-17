package com.alexm.mynut.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.alexm.mynut.data.DailySummary

@Composable
fun NutrientSummaryBar(
    summary: DailySummary,
    modifier: Modifier = Modifier
) {
    var showDetail by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDetail = true }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryItem("Calories", "${summary.totalCalories.toInt()}", "kcal")
            SummaryItem("Lipides", "%.1f".format(summary.totalFats), "g")
            SummaryItem("Glucides", "%.1f".format(summary.totalCarbs), "g")
            SummaryItem("Protéines", "%.1f".format(summary.totalProteins), "g")
        }
    }

    if (showDetail) {
        NutrientDetailDialog(summary = summary, onDismiss = { showDetail = false })
    }
}

@Composable
private fun SummaryItem(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = "$label ($unit)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun NutrientDetailDialog(summary: DailySummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Détail nutritionnel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow("Calories", "${summary.totalCalories.toInt()} kcal")
                DetailRow("Lipides", "%.1f g".format(summary.totalFats))
                DetailRow("  dont saturés", "%.1f g".format(summary.totalSaturatedFats))
                DetailRow("Glucides", "%.1f g".format(summary.totalCarbs))
                DetailRow("  dont sucres", "%.1f g".format(summary.totalSugars))
                DetailRow("Fibres", "%.1f g".format(summary.totalFiber))
                DetailRow("Protéines", "%.1f g".format(summary.totalProteins))
                DetailRow("Sodium", "%.1f mg".format(summary.totalSodium))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}