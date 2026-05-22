package com.pimenov.main.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pimenov.main.R

/**
 * Icon left of the input that opens a menu with two sections: the DM's
 * suggested actions (clickable) and read-only «Зацепки» the player uncovered.
 */
@Composable
fun ActionPicker(
    options: List<String>,
    leads: List<String>,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = options.isNotEmpty() || leads.isNotEmpty()
    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (active) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .clickable(enabled = active) { expanded = true }
                .padding(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_actions),
                contentDescription = "Действия и зацепки",
                tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (options.isNotEmpty()) {
                MenuSectionHeader("Действия")
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        enabled = enabled,
                        onClick = {
                            expanded = false
                            onPick(opt)
                        },
                    )
                }
            }
            if (leads.isNotEmpty()) {
                MenuSectionHeader("Зацепки")
                leads.forEach { lead ->
                    Text(
                        text = "• $lead",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
