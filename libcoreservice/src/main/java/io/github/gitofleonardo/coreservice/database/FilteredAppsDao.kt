package io.github.gitofleonardo.coreservice.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FilteredAppsDao {

    @Query("SELECT * FROM filtered_apps")
    fun getFilteredApps(): List<FilteredAppItem>

    @Query("SELECT * FROM filtered_apps")
    fun getFilteredAppsFlow(): Flow<List<FilteredAppItem>>

    @Insert
    fun insert(app: FilteredAppItem): Long

    @Delete
    fun delete(vararg apps: FilteredAppItem)
}
