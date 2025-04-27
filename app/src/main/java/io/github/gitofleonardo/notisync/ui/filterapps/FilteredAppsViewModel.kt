package io.github.gitofleonardo.notisync.ui.filterapps

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.gitofleonardo.coreservice.database.FilteredAppItem
import io.github.gitofleonardo.coreservice.util.getServiceDatabase
import io.github.gitofleonardo.notisync.NotiSyncApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FilteredAppsViewModel : ViewModel() {

    private val _appItemsState = MutableLiveData<AppItemsState>()

    val appItemsState: LiveData<AppItemsState>
        get() = _appItemsState

    private val context: Context
        get() = NotiSyncApplication.appContext

    private val appsDao by lazy {
        NotiSyncApplication.appContext.getServiceDatabase()
            .filteredAppsDao()
    }

    fun loadAllApps() {
        _appItemsState.value = AppItemsState.LoadingState
        viewModelScope.launch {
            _appItemsState.postValue(
                AppItemsState.AllAppsState(loadAllAppsInner())
            )
        }
    }

    fun setAppFiltered(app: AppItem, filtered: Boolean) {
        val filteredItem = app.filteredItem ?: if (!filtered) {
            return
        } else {
            FilteredAppItem(
                0,
                app.packageName
            )
        }
        if (filtered) {
            app.filteredItem = filteredItem
        } else {
            app.filteredItem = null
        }
        viewModelScope.launch {
            setAppFiltered(filteredItem, filtered)
        }
    }

    private suspend fun setAppFiltered(
        filteredItem: FilteredAppItem,
        filtered: Boolean
    ) = withContext(Dispatchers.IO) {
        if (filtered) {
            filteredItem.uid = appsDao.insert(filteredItem)
        } else {
            appsDao.delete(filteredItem)
        }
    }

    private suspend fun loadAllAppsInner(): List<AppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val allFilteredItems = appsDao
            .getFilteredApps()
            .associate { it.packageName to it }
        val allAppItems = pm.getInstalledApplications(0)
            .filter {
                (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }
            .map {
                AppItem(
                    it.loadIcon(pm),
                    it.loadLabel(pm).toString(),
                    it.packageName,
                    allFilteredItems[it.packageName]
                )
            }
            .sortedWith { a1, a2 ->
                if (a1.filteredItem == null) {
                    return@sortedWith 1
                } else if (a2.filteredItem == null) {
                    return@sortedWith -1
                } else {
                    return@sortedWith a1.appName.compareTo(a2.appName)
                }
            }
        return@withContext allAppItems
    }
}
