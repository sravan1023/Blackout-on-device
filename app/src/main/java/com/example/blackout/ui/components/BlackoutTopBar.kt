package com.example.blackout.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blackout.ui.theme.NavyPrimary
import com.example.blackout.ui.theme.TealAccent

@Composable
fun BlackoutLogo(modifier: Modifier = Modifier, fontSize: androidx.compose.ui.unit.TextUnit = 28.sp) {
    val logo = buildAnnotatedString {
        withStyle(SpanStyle(color = NavyPrimary, fontWeight = FontWeight.Bold, fontSize = fontSize)) {
            append("Black")
        }
        withStyle(SpanStyle(
            color = TealAccent,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            fontStyle = FontStyle.Italic,
        )) {
            append("out")
        }
    }
    Text(text = logo, modifier = modifier)
}

@Composable
fun BlackoutTopBar(
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BlackoutLogo()
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}
