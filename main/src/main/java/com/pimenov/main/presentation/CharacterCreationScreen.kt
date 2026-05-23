package com.pimenov.main.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pimenov.feature.game.player.CharacterProfile
import com.pimenov.feature.game.world.ClassDef

private val AVATARS = listOf("avatar_wanderer", "avatar_warrior", "avatar_rogue", "avatar_mage")

@Composable
fun CharacterCreationScreen(
    classes: List<ClassDef>,
    onCreate: (CharacterProfile) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf(classes.firstOrNull()) }
    var avatar by remember { mutableStateOf(classes.firstOrNull()?.avatar ?: AVATARS.first()) }
    val scroll = rememberScrollState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Кто ты?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Имя") },
                placeholder = { Text("Как тебя звать?") },
            )

            SectionLabel("Облик")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AVATARS.forEach { id ->
                    AvatarChoice(
                        avatar = id,
                        selected = id == avatar,
                        onClick = { avatar = id },
                    )
                }
            }

            SectionLabel("Класс")
            classes.forEach { def ->
                ClassCard(
                    def = def,
                    selected = def.id == selectedClass?.id,
                    onClick = {
                        selectedClass = def
                        avatar = def.avatar ?: avatar
                    },
                )
            }

            Button(
                onClick = {
                    val cls = selectedClass ?: return@Button
                    onCreate(CharacterProfile(name = name.trim(), classId = cls.id, avatar = avatar))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && selectedClass != null,
            ) {
                Text("В путь")
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun AvatarChoice(avatar: String, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(2.dp, border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(avatarDrawable(avatar)),
            contentDescription = avatar,
            tint = tint,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun ClassCard(def: ClassDef, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(2.dp, border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = def.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = onContainer,
        )
        Text(
            text = def.description,
            style = MaterialTheme.typography.bodySmall,
            color = onContainer,
        )
        Text(
            text = "HP ${def.hp}  ·  атака +${def.attackBonus}  ·  навык +${def.skillBonus}  ·  защита ${def.baseAc}",
            style = MaterialTheme.typography.labelMedium,
            color = onContainer,
        )
    }
}
