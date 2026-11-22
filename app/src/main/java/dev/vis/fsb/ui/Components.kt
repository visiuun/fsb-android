@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.fsb.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil3.compose.SubcomposeAsyncImage
import dev.vis.fsb.ui.theme.StatusColors
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

// ------------------------------------------------------------------ formatting

fun compact(n: Long): String {
    val a = abs(n)
    return when {
        a >= 1_000_000_000 -> trim(n / 1_000_000_000.0) + "B"
        a >= 1_000_000 -> trim(n / 1_000_000.0) + "M"
        a >= 10_000 -> trim(n / 1_000.0) + "K"
        a >= 1_000 -> String.format(Locale.US, "%,d", n)
        else -> n.toString()
    }
}

fun compact(d: Double): String = if (d >= 1000) compact(d.toLong()) else trim(d)

private fun trim(d: Double): String {
    val s = String.format(Locale.US, if (d >= 100) "%.0f" else "%.1f", d)
    return s.removeSuffix(".0")
}

fun ago(epochSec: Long): String {
    if (epochSec <= 0) return ""
    val s = System.currentTimeMillis() / 1000 - epochSec
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60}m ago"
        s < 86400 -> "${s / 3600}h ago"
        s < 86400 * 7 -> "${s / 86400}d ago"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(epochSec * 1000)
    }
}

fun shortDate(epochSec: Long): String {
    if (epochSec <= 0) return ""
    val now = java.util.Calendar.getInstance()
    val c = java.util.Calendar.getInstance().apply { timeInMillis = epochSec * 1000 }
    val s = System.currentTimeMillis() / 1000 - epochSec
    return when {
        s < 86400 -> ago(epochSec)
        c.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) -> SimpleDateFormat("d MMM", Locale.getDefault()).format(c.time)
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(c.time)
    }
}

/** "Mon Dec 23 03:35:24 +0000 2019" → "December 2019". */
fun joinedDate(twitterDate: String): String = runCatching {
    val d = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH).parse(twitterDate)!!
    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(d)
}.getOrDefault("")

fun parseColor(hex: String?): Color? = runCatching {
    if (hex.isNullOrBlank()) return null
    Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
}.getOrNull()

// ------------------------------------------------------------------ shapes

/** Segmented list shape: big outer corners, small inner corners (M3 Expressive lists). */
fun segmentShape(index: Int, count: Int, big: Dp = 24.dp, small: Dp = 6.dp): Shape = when {
    count <= 1 -> RoundedCornerShape(big)
    index == 0 -> RoundedCornerShape(topStart = big, topEnd = big, bottomStart = small, bottomEnd = small)
    index == count - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = big, bottomEnd = big)
    else -> RoundedCornerShape(small)
}

// ------------------------------------------------------------------ building blocks

@Composable
fun ShapeBadge(
    icon: ImageVector,
    shape: RoundedPolygon,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        modifier.size(size).clip(shape.toShape()).background(container),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(size * 0.5f), tint = content) }
}

@Composable
fun Avatar(url: String?, modifier: Modifier = Modifier, size: Dp = 48.dp, shape: Shape = CircleShape, fallback: String? = null) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    SubcomposeAsyncImage(
        model = url ?: fallback,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(shape).background(bg),
        error = {
            if (fallback != null && url != null && url != fallback) {
                // Stored twimg URLs go stale when users change avatars; FSB's proxy always resolves the current one.
                SubcomposeAsyncImage(
                    model = fallback, contentDescription = null, contentScale = ContentScale.Crop,
                    error = { PersonPlaceholder(size, bg) },
                )
            } else {
                PersonPlaceholder(size, bg)
            }
        },
    )
}

@Composable
private fun PersonPlaceholder(size: Dp, bg: Color) {
    Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Person, null, Modifier.size(size * 0.55f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun VerifiedMark(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Icon(Icons.Rounded.Verified, "Verified", modifier.size(size), tint = Color(0xFF1D9BF0))
}

@Composable
fun NameLine(name: String, verified: Boolean, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, maxLines: Int = 1) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, color = color, modifier = Modifier.weight(1f, fill = false))
        if (verified) {
            Spacer(Modifier.width(4.dp))
            VerifiedMark()
        }
    }
}

enum class Level { OK, WARN, BAD, UNKNOWN }

fun levelColor(l: Level) = when (l) {
    Level.OK -> StatusColors.clean
    Level.WARN -> StatusColors.warn
    Level.BAD -> StatusColors.bad
    Level.UNKNOWN -> StatusColors.unknown
}

fun levelOf(sb: dev.vis.fsb.data.Shadowban) = when (sb.verdict) {
    dev.vis.fsb.data.Verdict.CLEAN -> Level.OK
    dev.vis.fsb.data.Verdict.SENSITIVE -> Level.WARN
    dev.vis.fsb.data.Verdict.SHADOWBANNED -> Level.BAD
    dev.vis.fsb.data.Verdict.UNKNOWN -> Level.UNKNOWN
}

@Composable
fun StatusPill(text: String, level: Level, modifier: Modifier = Modifier) {
    val c = levelColor(level)
    Surface(modifier, shape = CircleShape, color = c.copy(alpha = 0.16f), contentColor = c) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Connected toggle-button group (M3 Expressive) for single choice. */
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                if (icon != null) {
                    Icon(icon(option), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label(option), maxLines = 1, style = MaterialTheme.typography.labelLarge, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Morphs between M3 Expressive shapes while slowly rotating. */
@Composable
fun MorphingBlob(shapes: List<RoundedPolygon>, color: Color, modifier: Modifier = Modifier, holdMs: Int = 2400, morphMs: Int = 900) {
    val morphs = remember(shapes) { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val path = remember { android.graphics.Path() }
    val t = rememberInfiniteTransition(label = "blob")
    val step = holdMs + morphMs
    val p by t.animateFloat(0f, shapes.size.toFloat(), infiniteRepeatable(tween(step * shapes.size, easing = LinearEasing)), label = "p")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(50_000, easing = LinearEasing)), label = "rot")
    Canvas(modifier) {
        val i = p.toInt().coerceIn(0, shapes.lastIndex)
        val local = ((p - i) * step - holdMs) / morphMs
        val eased = FastOutSlowInEasing.transform(local.coerceIn(0f, 1f))
        path.rewind()
        morphs[i].toPath(eased, path)
        val cp = path.asComposePath()
        val s = size.minDimension
        rotate(rot, pivot = center) { scale(s, s, pivot = Offset.Zero) { drawPath(cp, color) } }
    }
}

/** Centered empty / error / locked state with an expressive shape badge. */
@Composable
fun EmptyState(
    icon: ImageVector,
    shape: RoundedPolygon,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapeBadge(
            icon, shape, size = 96.dp,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
