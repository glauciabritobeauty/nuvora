package com.nuvora.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nuvora.designsystem.theme.NuvoraTheme

@Immutable
data class NavItem(val key: String, val label: String, val icon: ImageVector)

/** Menu inferior do celular e do tablet. */
@Composable
fun BottomNavBar(
    items: List<NavItem>,
    selectedKey: String,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NuvoraTheme.colors
    Column(modifier.fillMaxWidth().background(colors.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.surfaceRaised))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEach { item ->
                val selected = item.key == selectedKey
                val tint = if (selected) colors.accent else colors.textSecondary
                NuvoraClickable(
                    onClick = { onSelect(item) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.semantics {
                        this.selected = selected
                        role = Role.Tab
                    },
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NuvoraIcon(icon = item.icon, tint = tint)
                        NuvoraText(text = item.label, style = NuvoraTheme.typography.caption.copy(fontSize = NuvoraTheme.typography.overline.fontSize), color = tint, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Menu lateral da TV: só ícones, navegado pelas setas.
 * O item da tela atual fica com fundo elevado e ícone ciano; o item em foco ganha a borda ciano.
 */
@Composable
fun TvSideMenu(
    items: List<NavItem>,
    bottomItems: List<NavItem>,
    selectedKey: String,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NuvoraTheme.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(80.dp)
            .background(colors.surface)
            .padding(vertical = 27.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NuvoraMark(Modifier.size(30.dp))
        Spacer(Modifier.height(18.dp))
        items.forEach { TvMenuItem(it, it.key == selectedKey, onSelect) }
        Spacer(Modifier.weight(1f))
        bottomItems.forEach { TvMenuItem(it, it.key == selectedKey, onSelect) }
    }
}

@Composable
private fun TvMenuItem(item: NavItem, selected: Boolean, onSelect: (NavItem) -> Unit) {
    val colors = NuvoraTheme.colors
    NuvoraClickable(
        onClick = { onSelect(item) },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = item.label
                this.selected = selected
            },
        containerColor = if (selected) colors.surfaceRaised else colors.surface,
        focusedContainerColor = colors.surfaceRaised,
    ) {
        NuvoraIcon(
            icon = item.icon,
            tint = if (selected) colors.accent else colors.textSecondary,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
