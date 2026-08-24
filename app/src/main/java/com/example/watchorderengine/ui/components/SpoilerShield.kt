package com.example.watchorderengine.ui.components

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchorderengine.ui.theme.LocalAppTheme

/**
 * Progress-aware "Spoiler Shield" — a state-driven wrapper that blurs/conceals
 * [content] whenever [isBlurred] is true, and reveals it automatically once
 * [isBlurred] flips to false (e.g. the caller determined the user has caught
 * up on the show).
 *
 * [isBlurred] is expected to be computed upstream from the user's real watch
 * progress (see [com.example.watchorderengine.data.repository.MediaRepository.observeSpoilerShieldActive]) —
 * this composable only owns the *presentation* of that state: the blur
 * animation, the lock overlay, and an optional tap-to-reveal escape hatch for
 * users who don't mind spoiling themselves on this specific piece of content.
 *
 * @param isBlurred Whether this content should currently be shielded.
 * @param allowManualReveal If true, tapping the overlay reveals content for
 *   this composition (mirrors the existing "tap to reveal" lore card UX).
 *   Resets back to blurred if [isBlurred] becomes true again after having
 *   been false (i.e. a new, later spoiler entered the shielded window).
 * @param label Overlay caption, e.g. "SPOILER PROTECTED" or "Ahead of your progress".
 * @param blurRadius Blur strength when shielded.
 */
@Composable
fun SpoilerShield(
    isBlurred: Boolean,
    modifier: Modifier = Modifier,
    allowManualReveal: Boolean = true,
    label: String = "SPOILER PROTECTED",
    blurRadius: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    val theme = LocalAppTheme.current
    var manuallyRevealed by rememberSaveable(isBlurred) { mutableStateOf(false) }

    val effectivelyBlurred = isBlurred && !manuallyRevealed

    val animatedBlur: Dp by animateDpAsState(
        targetValue = if (effectivelyBlurred) blurRadius else 0.dp,
        animationSpec = tween(durationMillis = 350),
        label = "spoiler_shield_blur"
    )

    val shieldModifier = if (effectivelyBlurred) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(radius = animatedBlur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        } else {
            Modifier.alpha(0.12f)
        }
    } else Modifier

    Box(modifier = modifier) {
        Box(modifier = shieldModifier) { content() }

        if (effectivelyBlurred) {
            Surface(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(enabled = allowManualReveal) { manuallyRevealed = true },
                color = theme.background.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, theme.statusMixed.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = theme.textPrimary.copy(alpha = 0.6f),
                        modifier = Modifier.height(22.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        color = theme.textPrimary.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                    if (allowManualReveal) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap to reveal anyway",
                            color = theme.accent,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}
