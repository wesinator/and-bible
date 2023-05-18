/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.database

import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.bible.android.database.bookmarks.BookmarkDao
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.android.database.readingplan.ReadingPlanEntities
import java.util.Date

const val BOOKMARK_DATABASE_VERSION = 1

enum class EditType {
    INSERT,
    UPDATE,
    DELETE
}

@Entity(indices = [Index(value = ["tableName", "entityId1", "entityId2"], unique = true)])
class Edit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String,
    val entityId1: String,
    val entityId2: String?,
    val editType: EditType,
    @ColumnInfo(defaultValue = "0") val lastUpdated: Date = Date(System.currentTimeMillis()),
)

@Database(
    entities = [
        BookmarkEntities.Bookmark::class,
        BookmarkEntities.Label::class,
        BookmarkEntities.StudyPadTextEntry::class,
        BookmarkEntities.BookmarkToLabel::class,
        Edit::class,
    ],
    version = BOOKMARK_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class BookmarkDatabase: RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    companion object {
        const val dbFileName = "bookmarks.sqlite3"
    }
}

const val READING_PLAN_DATABASE_VERSION = 1

@Database(
    entities = [
        ReadingPlanEntities.ReadingPlan::class,
        ReadingPlanEntities.ReadingPlanStatus::class,
        Edit::class,
    ],
    version = READING_PLAN_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class ReadingPlanDatabase: RoomDatabase() {
    abstract fun readingPlanDao(): ReadingPlanDao
    companion object {
        const val dbFileName = "readingplans.sqlite3"
    }
}

const val WORKSPACE_DATABASE_VERSION = 1

@Database(
    entities = [
        WorkspaceEntities.Workspace::class,
        WorkspaceEntities.Window::class,
        WorkspaceEntities.HistoryItem::class,
        WorkspaceEntities.PageManager::class,
        Edit::class,
    ],
    version = WORKSPACE_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class WorkspaceDatabase: RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    companion object {
        const val dbFileName = "workspaces.sqlite3"
    }
}

const val TEMPORARY_DATABASE_VERSION = 1

@Database(
    entities = [
        DocumentSearch::class,
    ],
    version = TEMPORARY_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class TemporaryDatabase: RoomDatabase() {
    abstract fun documentSearchDao(): DocumentSearchDao
    companion object {
        const val dbFileName = "temporary.sqlite3"
    }
}

const val REPO_DATABASE_VERSION = 1

@Database(
    entities = [
        CustomRepository::class,
        SwordDocumentInfo::class,
    ],
    version = REPO_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class RepoDatabase: RoomDatabase() {
    abstract fun swordDocumentInfoDao(): SwordDocumentInfoDao
    abstract fun customRepositoryDao(): CustomRepositoryDao
    companion object {
        const val dbFileName = "repositories.sqlite3"
    }
}

const val SETTINGS_DATABASE_VERSION = 1

@Database(
    entities = [
        BooleanSetting::class,
        StringSetting::class,
        LongSetting::class,
        DoubleSetting::class,
    ],
    version = SETTINGS_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class SettingsDatabase: RoomDatabase() {
    abstract fun booleanSettingDao(): BooleanSettingDao
    abstract fun stringSettingDao(): StringSettingDao
    abstract fun longSettingDao(): LongSettingDao
    abstract fun doubleSettingDao(): DoubleSettingDao
    companion object {
        const val dbFileName = "settings.sqlite3"
    }    
}
