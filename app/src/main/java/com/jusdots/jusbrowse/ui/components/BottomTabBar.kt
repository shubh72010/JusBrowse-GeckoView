package com.jusdots.jusbrowse.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jusdots.jusbrowse.data.models.BrowserTab
import com.jusdots.jusbrowse.ui.theme.GlassBorderLight
import com.jusdots.jusbrowse.ui.theme.GlowPrimary
import com.jusdots.jusbrowse.ui.theme.PrivatePurple

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BottomTabBar(
    tabs: SnapshotStateList<BrowserTab>,
    activeTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onNewTab: (String) -> Unit,
    onGroupTabs: (String, String) -> Unit = { _, _ -> },
    onUngroupTab: (String) -> Unit = {},
    onOpenTabGroup: (String?) -> Unit = {},
    showIcons: Boolean = false,
    showNewTabButton: Boolean = true,
    groupIdToShow: String? = null,
    activeGroupId: String? = null,
    modifier: Modifier = Modifier
) {
    var showContainerMenu by remember { mutableStateOf(false) }
    val tabBounds = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val primary = MaterialTheme.colorScheme.primary

    // Filter to only show root tabs or tabs for the specified group
    // Filter logic:
    // If we are looking at a specific group (groupIdToShow), show its children.
    // Otherwise, if an activeGroupId is set GLOBALLY, show that group's children.
    // Otherwise, show root tabs.
    val effectiveGroupId = groupIdToShow ?: activeGroupId
    val currentTabs = if (effectiveGroupId == null) {
        tabs.filter { it.parentGroupId == null }
    } else {
        tabs.filter { it.parentGroupId == effectiveGroupId }
    }

    // Glass surface — NO BACKGROUND
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // Divider removed for transparency

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Tab chips ────────────────────────────────────────────────────
            
            // Apply custom 900ms delay for Drag & Drop long press
            val currentViewConfig = androidx.compose.ui.platform.LocalViewConfiguration.current
            val customViewConfig = remember(currentViewConfig) {
                object : androidx.compose.ui.platform.ViewConfiguration by currentViewConfig {
                    override val longPressTimeoutMillis: Long
                        get() = 900L
                }
            }
            
            CompositionLocalProvider(androidx.compose.ui.platform.LocalViewConfiguration provides customViewConfig) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (activeGroupId != null) {
                        // Back to Root button
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                                .clickable { onOpenTabGroup(null) }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    currentTabs.forEach { tab ->
                        val originalIndex = tabs.indexOf(tab)
                        var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                        var isDragging by remember { mutableStateOf(false) }
                        
                        Box(
                            modifier = Modifier
                                .onGloballyPositioned { coords ->
                                    tabBounds[tab.id] = coords.boundsInWindow()
                                }
                                .offset { androidx.compose.ui.unit.IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt()) }
                                .zIndex(if (isDragging) 1f else 0f)
                                .pointerInput(tab.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { isDragging = true },
                                        onDragCancel = {
                                            isDragging = false
                                            dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                        },
                                        onDragEnd = {
                                            isDragging = false
                                            // Swipe up to remove threshold
                                            if (dragOffset.y < -150f && tab.parentGroupId != null) {
                                                onUngroupTab(tab.id)
                                            } else {
                                                val myBounds = tabBounds[tab.id]
                                                if (myBounds != null) {
                                                    val dropPoint = myBounds.center + dragOffset
                                                    // Use 1D horizontal intersection since users scroll
                                                    val target = tabBounds.entries.find { 
                                                        it.key != tab.id && 
                                                        dropPoint.x >= it.value.left && 
                                                        dropPoint.x <= it.value.right &&
                                                        // Require y to just be within a generous overlapping box
                                                        dropPoint.y >= it.value.top - 100f &&
                                                        dropPoint.y <= it.value.bottom + 100f
                                                    }?.key
                                                    
                                                    if (target != null) {
                                                        onGroupTabs(tab.id, target)
                                                    }
                                                }
                                            }
                                            dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                        },
                                        onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                                            change.consume()
                                            dragOffset += dragAmount
                                        }
                                    )
                                }
                        ) {
                            val isDragTarget = tabBounds.entries.any {
                                it.key != tab.id &&
                                (tabBounds[tab.id]?.center?.plus(dragOffset) ?: androidx.compose.ui.geometry.Offset.Unspecified).let { p ->
                                    p.x >= it.value.left && p.x <= it.value.right &&
                                    p.y >= it.value.top - 50f && p.y <= it.value.bottom + 50f
                                } && it.key == tab.id // Wait, logic checked against all entries
                            }

                            // Corrected drag target logic: we are rendering 'tab'. 
                            // We want to know if SOME OTHER dragging tab is currently over US.
                            // This requires external state or a shared map.
                            // For simplicity, let's just use the dragOffset if WE are being dragged, 
                            // and a visual feedback on the chip itself.
                            
                            TabChip(
                                tab = tab,
                                isActive = originalIndex == activeTabIndex,
                                onClick = { 
                                    if (tab.isGroupMaster) {
                                        onOpenTabGroup(tab.id)
                                    } else {
                                        onTabSelected(originalIndex)
                                    }
                                },
                                onLongClick = { }, // Empty for pure drag & drop
                                onClose = { onTabClosed(originalIndex) },
                                onUngroup = { onUngroupTab(tab.id) },
                                isInsideGroup = effectiveGroupId != null,
                                showIcon = showIcons
                            )
                        }
                    }
                }
            }

            // ── New Tab button ───────────────────────────────────────────────
            if (showNewTabButton) {
                Box {
                    FilledTonalIconButton(
                        onClick = { onNewTab("default") },
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Tab",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Tiny arrow for container sub-menu
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .padding(end = 1.dp, bottom = 1.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(0.6f))
                            .clickable { showContainerMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Containers",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showContainerMenu,
                        onDismissRequest = { showContainerMenu = false }
                    ) {
                        com.jusdots.jusbrowse.security.ContainerManager.AVAILABLE_CONTAINERS.forEach { container ->
                            DropdownMenuItem(
                                text = { Text(com.jusdots.jusbrowse.security.ContainerManager.getContainerName(container)) },
                                onClick = {
                                    onNewTab(container)
                                    showContainerMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (container == "default") Icons.Filled.Public else Icons.Filled.Layers,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TabChip(
    tab: BrowserTab,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onClose: () -> Unit,
    onUngroup: () -> Unit = {},
    isInsideGroup: Boolean = false,
    showIcon: Boolean = false,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary

    // Animate between active and inactive styles
    AnimatedContent(
        targetState = isActive,
        transitionSpec = {
            (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                    scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)))
                .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                        scaleOut(targetScale = 0.92f))
        },
        label = "tabChip"
    ) { active ->
        val chipBackground = if (active)
            Brush.linearGradient(
                colors = listOf(
                    primary.copy(alpha = 0.85f),
                    primary.copy(alpha = 0.75f)
                )
            )
        else
            Brush.linearGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.65f),
                    Color.Black.copy(alpha = 0.55f)
                )
            )

        val borderColor = if (active) primary.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f)
        val borderWidth = if (active) 1.5.dp else 1.dp

        Row(
            modifier = modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(chipBackground)
                .border(borderWidth, borderColor, CircleShape)
                .then(
                    if (active) Modifier.drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primary.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                radius = size.width
                            )
                        )
                    } else Modifier
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Group Master indicator
            if (tab.isGroupMaster) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Group",
                    modifier = Modifier.size(16.dp),
                    tint = primary.copy(alpha = 1.0f)
                )
            }

            // Private tab indicator
            if (tab.isPrivate) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = "Private",
                    modifier = Modifier.size(14.dp),
                    tint = PrivatePurple
                )
            }

            // Container color dot
            val currentContainerId = tab.containerId ?: "default"
            if (currentContainerId != "default") {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when (currentContainerId) {
                                "work"     -> Color(0xFF4285F4)
                                "personal" -> Color(0xFF34A853)
                                "banking"  -> Color(0xFFFBBC05)
                                "sandbox"  -> Color(0xFFEA4335)
                                else       -> primary
                            }
                        )
                )
            }

            if (showIcon) {
                val initial = try {
                    val url = if (tab.url == "about:blank") "N" else tab.url
                    android.net.Uri.parse(url).host?.firstOrNull()?.uppercase() ?: "N"
                } catch (e: Exception) { "N" }

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = primary
                    )
                }
            } else {
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .widthIn(max = 110.dp)
                        .graphicsLayer(
                            compositingStrategy = CompositingStrategy.Offscreen,
                            blendMode = BlendMode.Difference
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Ungroup button (visible if inside a group)
            if (isInsideGroup) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                        .clickable { onUngroup() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldLess,
                        contentDescription = "Ungroup",
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Close button
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Tab",
                    modifier = Modifier.size(11.dp),
                    tint = if (active) primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

