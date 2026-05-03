package com.example.blackout.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.blackout.ui.theme.NavyPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onPdf: () -> Unit,
    onText: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp),
                shape = RoundedCornerShape(100.dp),
                color = Color(0xFFD8D8D8),
            ) {}
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = "Add document",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = NavyPrimary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Choose how to bring in your document",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF888888),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(20.dp))

            UploadRow(
                icon = Icons.Outlined.Image,
                title = "Choose from gallery",
                subtitle = "Pick from your photo library",
                iconBg = Color(0xFFDFF5EC),
                iconTint = Color(0xFF0F6E56),
                enabled = true,
                onClick = { onDismiss(); onGallery() },
            )
            Spacer(Modifier.height(10.dp))
            UploadRow(
                icon = Icons.Outlined.CameraAlt,
                title = "Take a photo",
                subtitle = "Scan document with camera",
                iconBg = Color(0xFFE3EEFB),
                iconTint = Color(0xFF185FA5),
                enabled = true,
                onClick = { onDismiss(); onCamera() },
            )
            Spacer(Modifier.height(10.dp))
            UploadRow(
                icon = Icons.Outlined.TextFields,
                title = "Type or paste text",
                subtitle = "Enter text manually",
                iconBg = Color(0xFFEDEAFB),
                iconTint = Color(0xFF534AB7),
                enabled = true,
                onClick = { onDismiss(); onText() },
            )

            Spacer(Modifier.height(20.dp))
            Surface(
                color = Color(0xFFEFEFEF),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() },
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancel", color = Color(0xFF666666), fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UploadRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconBg: Color,
    iconTint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val titleColor = if (enabled) NavyPrimary else Color(0xFF999999)
    val subtitleColor = if (enabled) Color(0xFF888888) else Color(0xFFAAAAAA)
    val effectiveIconBg = if (enabled) iconBg else Color(0xFFF0F0F0)
    val effectiveIconTint = if (enabled) iconTint else Color(0xFFAAAAAA)
    Surface(
        color = Color(0xFFFAFAFA),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(effectiveIconBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = title, tint = effectiveIconTint, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = titleColor)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = subtitleColor)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (enabled) Color(0xFFBBBBBB) else Color(0xFFDDDDDD),
            )
        }
    }
}
