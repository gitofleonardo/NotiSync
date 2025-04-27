package io.github.gitofleonardo.notisync.ui.filterapps

sealed class AppItemsState {

    object LoadingState : AppItemsState()

    class AllAppsState(val allApps: List<AppItem>) : AppItemsState()
}
