package io.github.gitofleonardo.notisync.ui.filterapps

import android.graphics.drawable.Drawable
import io.github.gitofleonardo.coreservice.database.FilteredAppItem

data class AppItem(
    val appIcon: Drawable,
    val appName: String,
    val packageName: String,
    var filteredItem: FilteredAppItem?,
)
