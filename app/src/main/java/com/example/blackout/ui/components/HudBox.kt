package com.example.blackout.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import com.example.blackout.engine.InferenceMetrics
import com.example.blackout.ui.ModelVariant

private val BackendNpu  = Color(0xFF1B8C5E)   // teal-green
private val BackendGpu  = Color(0xFF1565C0)   // blue
private val BackendCpu  = Color(0xFF5D5F63)   // gray

private val StatLatency = Color(0xFF854F0B)   // amber
private val StatTokens  = Color(0xFF534AB7)   // purple
private val StatSpeed   = Color(0xFF1B8C5E)   // teal-green
@Suppress("unused") private val StatEnergy  = Color(0xFF993556)
private val HudBg       = Color(0xFFEDEDEB)   // warm light gray

@Composable
fun HudBox(
    metrics: InferenceMetrics?,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
    elapsedMs: Long = 0,
    backend: String = metrics?.backend ?: "CPU",
    selectedVariant: ModelVariant? = null,
    onSelectVariant: ((ModelVariant) -> Unit)? = null,
    availableVariants: List<ModelVariant> = ModelVariant.entries,
) {
    val chipColor = when {
        backend.contains("NPU", ignoreCase = true) -> BackendNpu
        backend.contains("GPU", ignoreCase = true) -> BackendGpu
        else -> BackendCpu
    }

    val pulse by rememberInfiniteTransition(label = "hud_pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (isLive) 0.4f else 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse_alpha",
    )

    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = HudBg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Live pulse dot
            if (isLive) {
                Surface(
                    modifier = Modifier.size(7.dp).alpha(pulse),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = chipColor,
                ) {}
            }

            // Backend chip — tappable for variant selection when callback provided.
            Box {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = chipColor.copy(alpha = 0.15f),
                    modifier = if (onSelectVariant != null) {
                        Modifier.clickable { menuOpen = true }
                    } else Modifier,
                ) {
                    Text(
                        text = backend + if (onSelectVariant != null) " ▾" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = chipColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (onSelectVariant != null) {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        availableVariants.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(v.label) },
                                trailingIcon = {
                                    if (selectedVariant == v) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = BackendNpu)
                                    }
                                },
                                onClick = {
                                    menuOpen = false
                                    onSelectVariant(v)
                                },
                            )
                        }
                    }
                }
            }

            if (isLive) {
                HudStat(value = formatElapsed(elapsedMs), label = "elapsed", valueColor = StatLatency)
            } else if (metrics != null) {
                HudStat(value = "%.1f".format(metrics.decodeTokensPerSec), label = "tok/s", valueColor = StatSpeed)
                HudStat(value = formatLatency(metrics.latencyMs), label = "total", valueColor = StatLatency)
                HudStat(value = formatLatency(metrics.timeToFirstTokenMs), label = "TTFT", valueColor = StatTokens)
            }
        }
    }
}

@Composable
private fun HudStat(value: String, label: String, valueColor: Color = Color(0xFF333333)) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = valueColor,
        )
        Spacer(Modifier.width(1.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF888888),
        )
    }
}

private fun formatLatency(ms: Long): String =
    if (ms >= 1000) "%.1fs".format(ms / 1000f) else "${ms}ms"

private fun formatElapsed(ms: Long): String =
    if (ms >= 60_000) "%dm %ds".format(ms / 60_000, (ms % 60_000) / 1000)
    else if (ms >= 1000) "%.1fs".format(ms / 1000f)
    else "${ms}ms"
