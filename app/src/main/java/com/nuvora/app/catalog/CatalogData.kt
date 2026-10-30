package com.nuvora.app.catalog

import com.nuvora.designsystem.components.NavItem
import com.nuvora.designsystem.icons.NuvoraIcons

internal val CATEGORIES = listOf("Todos", "Filmes", "Séries", "Ao vivo", "Documentários")

internal val TV_MENU = listOf(
    NavItem("search", "Buscar", NuvoraIcons.Search),
    NavItem("home", "Início", NuvoraIcons.Home),
    NavItem("favorites", "Favoritos", NuvoraIcons.Heart),
    NavItem("history", "Histórico", NuvoraIcons.History),
    NavItem("categories", "Categorias", NuvoraIcons.Categories),
)

internal val TV_MENU_BOTTOM = listOf(
    NavItem("settings", "Ajustes", NuvoraIcons.Settings),
)
