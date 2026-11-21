package com.wallhaven.browser.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wallhaven.browser.ui.theme.Pal

/** 轻量触感反馈：iOS 上按钮的回弹手感很大程度来自这一下。 */
class Haptics internal constructor(private val view: View) {
    fun light() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun select() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            light()
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/**
 * 按压缩放：按下略缩小，松手用弹簧回弹（带一点点过冲），iOS 的手感就来自这个曲线。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.95f,
    dampingRatio: Float = 0.5f,
    stiffness: Float = 900f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = dampingRatio, stiffness = stiffness),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** 去水波纹的点击（iOS 没有 Material 的涟漪，靠缩放 + 变色表达按下）。 */
@Composable
fun Modifier.iosClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    haptics: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    onClick = {
        haptics?.invoke()
        onClick()
    },
)

enum class IosButtonStyle { Filled, Tinted, Plain }

@Composable
fun IosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IosButtonStyle = IosButtonStyle.Filled,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 48.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberHaptics()

    val containerColor = when (style) {
        IosButtonStyle.Filled -> Pal.Accent
        IosButtonStyle.Tinted -> Pal.ChipBackground
        IosButtonStyle.Plain -> Color.Transparent
    }
    val pressedColor = when (style) {
        IosButtonStyle.Filled -> Pal.Accent.copy(alpha = 0.82f)
        IosButtonStyle.Tinted -> Pal.ChipBorder
        IosButtonStyle.Plain -> PaletteContainerPressed
    }
    val contentColor = when (style) {
        IosButtonStyle.Filled -> Color.White
        IosButtonStyle.Tinted, IosButtonStyle.Plain -> Pal.Accent
    }
    val background by animateColorAsState(
        targetValue = if (pressed) pressedColor else containerColor,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 1200f),
        label = "iosButtonBackground",
    )

    Row(
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.955f, dampingRatio = 0.45f, stiffness = 1000f)
            .height(height)
            .clip(CircleShape)
            .background(background)
            .then(
                if (style == IosButtonStyle.Tinted) {
                    Modifier.border(0.8.dp, Pal.ChipBorder, CircleShape)
                } else {
                    Modifier
                },
            )
            .iosClickable(interaction, enabled, { haptics.light() }, onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
        )
    }
}

private val PaletteContainerPressed = Color(0x1A007AFF)
