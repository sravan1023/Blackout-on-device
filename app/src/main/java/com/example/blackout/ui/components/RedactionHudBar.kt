package com.example.blackout.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blackout.ui.ModelVariant

private val HudNavy = Color(0xFF0D1B2A)
private val HudGreen = Color(0xFF1FD18A)
private val HudWhite = Color(0xFFF5F5F4)
private val HudMuted = Color(0xFF8B97A6)
private val HudDivider = Color(0x33FFFFFF)

/**
 * Dark-navy HUD bar shown on the redaction screen. Renders backend chip + live pulse + stats:
 * latency · tokens · speed · fields. Backend chip is tappable to switch ModelVariant.
 */
@Composable
fun RedactionHudBar(
    backend: String,
    latencyMs: Long,
    tokenCount: Int,
    tokensPerSecond: Float,
    fieldsRedacted: Int,
    timeToFirstTokenMs: Long = 0L,
    modifier: Modifier = Modifier,
    selectedVariant: ModelVariant? = null,
    availableVariants: List<ModelVariant> = ModelVariant.entries,
    onSelectVariant: ((ModelVariant) -> Unit)? = null,
    isLive: Boolean = false,
) {
    val pulse by rememberInfiniteTransition(label = "hud_pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (isLive) 0.3f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha",
    )

    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = HudNavy,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Backend chip + green dot
            Box {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x331FD18A),
                    modifier = if (onSelectVariant != null) Modifier.clickable { menuOpen = true } else Modifier,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(pulse)
                                .background(HudGreen, CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = backend + if (onSelectVariant != null) " ▾" else "",
                            color = HudGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (onSelectVariant != null) {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        availableVariants.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(v.label) },
                                trailingIcon = {
                                    if (selectedVariant == v) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF1B8C5E))
                                    }
                                },
                                onClick = { menuOpen = false; onSelectVariant(v) },
                            )
                        }
                    }
                }
            }
            HudLabel("BACKEND")

            Spacer(Modifier.weight(1f))
            HudDividerCol()
            Spacer(Modifier.weight(1f))
            HudStat(value = formatLatency(latencyMs), label = "TOTAL")

            Spacer(Modifier.weight(1f))
            HudDividerCol()
            Spacer(Modifier.weight(1f))
            HudStat(value = formatLatency(timeToFirstTokenMs), label = "TTFT")

            Spacer(Modifier.weight(1f))
            HudDividerCol()
            Spacer(Modifier.weight(1f))
            HudStat(value = "%.0f".format(tokensPerSecond), label = "TOK/S")
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HudStat(value: String, label: String, suffix: String? = null) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = HudWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (suffix != null) {
                Text(suffix, color = HudMuted, fontSize = 9.sp, modifier = Modifier.padding(start = 1.dp, bottom = 2.dp))
            }
        }
        Text(label, color = HudMuted, fontSize = 8.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HudLabel(label: String) {
    Text(label, color = HudMuted, fontSize = 8.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun HudDividerCol() {
    Box(modifier = Modifier.width(1.dp).height(24.dp).background(HudDivider))
}

private fun formatLatency(ms: Long): String =
    if (ms <= 0) "—" else if (ms >= 1000) "%.1fs".format(ms / 1000f) else "${ms}ms"
