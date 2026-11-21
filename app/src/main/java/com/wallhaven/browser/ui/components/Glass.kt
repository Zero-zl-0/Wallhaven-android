package com.wallhaven.browser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wallhaven.browser.ui.theme.Pal
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** 由 App 根节点提供，供所有玻璃组件取用；为 null 时退化为半透明面板。 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * iOS 27 风格的“液态玻璃”面板：真实背景模糊 + 半透明着色 + 高光 + 细描边。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(30.dp),
    blurRadius: Dp = 34.dp,
    tint: Color = Pal.GlassTint,
    backgroundColor: Color = Pal.GlassBackground,
    overlay: Color = Pal.GlassOverlay,
    shadowElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeState = LocalHazeState.current
    Box(
        modifier = modifier
            .then(
                if (shadowElevation > 0.dp) {
                    Modifier.shadow(
                        elevation = shadowElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.18f),
                        spotColor = Color.Black.copy(alpha = 0.22f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = backgroundColor,
                            tint = HazeTint(tint),
                            blurRadius = blurRadius,
                            noiseFactor = 0.04f,
                            fallbackTint = HazeTint(Color(0xF014141A)),
                        ),
                    )
                } else {
                    Modifier.background(backgroundColor)
                },
            )
            .background(overlay)
            .border(
                width = 0.9.dp,
                brush = Brush.linearGradient(
                    listOf(Pal.BorderTop, Pal.BorderMid, Pal.BorderBottom),
                ),
                shape = shape,
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Pal.SpecularTop, Color.Transparent, Color(0x0AFFFFFF)),
                    ),
                ),
        )
        content()
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 19.dp,
    tint: Color = Pal.Label,
    enabled: Boolean = true,
    backgroundColor: Color = Pal.ChipBackground,
    borderColor: Color = Pal.ChipBorder,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberHaptics()
    val pressedColor = backgroundColor.copy(
        alpha = (backgroundColor.alpha * 2.2f).coerceIn(0f, 1f),
    )
    val background by animateColorAsState(
        targetValue = if (pressed) pressedColor else backgroundColor,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 1400f),
        label = "iconButtonBackground",
    )
    Box(
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.88f, dampingRatio = 0.42f, stiffness = 850f)
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(0.8.dp, borderColor, CircleShape)
            .iosClickable(interaction, enabled, { haptics.light() }, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (enabled) tint else Pal.LabelTertiary,
        )
    }
}

@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberHaptics()

    val background by animateColorAsState(
        if (selected) Pal.Accent.copy(alpha = 0.24f) else Pal.ChipBackground,
        label = "chipBackground",
    )
    val foreground by animateColorAsState(
        if (selected) Pal.AccentSoft else Pal.LabelSecondary,
        label = "chipForeground",
    )
    val borderColor by animateColorAsState(
        if (selected) Pal.Accent.copy(alpha = 0.55f) else Pal.ChipBorder,
        label = "chipBorder",
    )
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 1300f),
        label = "chipPress",
    )

    Row(
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.94f, dampingRatio = 0.45f, stiffness = 1000f)
            .clip(CircleShape)
            .background(
                if (press > 0f) lerp(background, foreground.copy(alpha = 0.12f), press) else background,
            )
            .border(0.9.dp, borderColor, CircleShape)
            .iosClickable(interaction, true, { haptics.light() }, onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = foreground)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = foreground,
            maxLines = 1,
        )
    }
}

data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * 悬浮胶囊玻璃标签栏。
 * 选中指示块会随选中项用弹簧滑过去，按下时整格轻微缩小并回弹。
 */
@Composable
fun GlassTabBar(
    tabs: List<TabItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = CircleShape,
        blurRadius = 36.dp,
        tint = Pal.GlassTint,
        shadowElevation = 18.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val inset = 8.dp
            val itemWidth = (maxWidth - inset * 2) / tabs.size
            val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
            val pillOffset by animateDpAsState(
                targetValue = inset + itemWidth * selectedIndex,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
                label = "tabPillOffset",
            )
            Box(
                modifier = Modifier
                    .offset(x = pillOffset, y = 8.dp)
                    .width(itemWidth)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(Pal.TabPill),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = inset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    val selected = tab.route == currentRoute
                    val interaction = remember(tab.route) { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(
                        targetValue = if (pressed) 0.9f else 1f,
                        animationSpec = spring(dampingRatio = 0.42f, stiffness = 900f),
                        label = "tabPress",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) Pal.Accent else Pal.LabelSecondary,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
                        label = "tabContentColor",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 6.dp)
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                            .clip(CircleShape)
                            .iosClickable(
                                interactionSource = interaction,
                                enabled = true,
                                haptics = { haptics.select() },
                                onClick = {
                                    if (!selected) onSelect(tab.route)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier
                                    .size(21.dp)
                                    .graphicsLayer {
                                        val scale = if (selected) 1.06f else 1f
                                        scaleX = scale
                                        scaleY = scale
                                    },
                                tint = contentColor,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = contentColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 让滚动内容给底部玻璃栏让出的高度（含系统手势条）。 */
@Composable
fun tabBarContentPadding(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 92.dp

@Composable
fun LargeTitleHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                size = 38.dp,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = Pal.Label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Pal.LabelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
