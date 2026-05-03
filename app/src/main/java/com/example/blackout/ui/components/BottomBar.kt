package com.example.blackout.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.blackout.ui.theme.CardWhite
import com.example.blackout.ui.theme.TealAccent

enum class BottomBarDestination { HOME, MANAGE }

@Composable
fun BlackoutFab(
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = TealAccent,
        contentColor = CardWhite,
        shape = CircleShape,
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(24.dp))
    }
}
