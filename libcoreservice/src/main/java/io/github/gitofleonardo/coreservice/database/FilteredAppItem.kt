package io.github.gitofleonardo.coreservice.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "filtered_apps", indices = [Index(value = ["package_name"], unique = true)])
data class FilteredAppItem(
    @PrimaryKey(autoGenerate = true) var uid: Long,
    @ColumnInfo(name = "package_name") var packageName: String
)
