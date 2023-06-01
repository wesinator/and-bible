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

package net.bible.service.db

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import net.bible.android.activity.R
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.SyncableRoomDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.migrations.getColumnNames
import net.bible.android.database.migrations.getColumnNamesJoined
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.page.application
import net.bible.service.common.CommonUtils
import net.bible.service.common.forEach
import net.bible.service.common.getFirst
import java.io.File

class TableDef(val tableName: String, val idField1: String = "id", val idField2: String? = null)

enum class DatabaseCategory {
    BOOKMARKS, WORKSPACES, READINGPLANS;
    val contentDescription: Int get() = when(this) {
        READINGPLANS -> R.string.reading_plans_content
        BOOKMARKS -> R.string.bookmarks_contents
        WORKSPACES -> R.string.workspaces_contents
    }

    val tables get() = when(this) {
        BOOKMARKS -> listOf(
            TableDef("Label"),
            TableDef("Bookmark"),
            TableDef("BookmarkToLabel", "bookmarkId", "labelId"),
            TableDef("StudyPadTextEntry"),
        )
        WORKSPACES -> listOf(
            TableDef("Workspace"),
            TableDef("Window"),
            TableDef("PageManager", "windowId"),
        )
        READINGPLANS -> listOf(
            TableDef("ReadingPlan"),
            TableDef("ReadingPlanStatus"),
        )
    }

    companion object {
        val ALL = arrayOf(BOOKMARKS, WORKSPACES, READINGPLANS)
    }
}

class DatabaseDefinition<T: SyncableRoomDatabase>(
    var localDb: T,
    val dbFactory: (filename: String) -> T,
    private val _resetLocalDb: () -> T,
    val localDbFile: File,
    val category: DatabaseCategory,
    val deviceId: String = CommonUtils.deviceIdentifier
) {
    fun resetLocalDb() {
        localDb = _resetLocalDb()
    }
    val categoryName get() = category.name.lowercase()
    val dao get() = localDb.syncDao()
    val writableDb get() = localDb.openHelper.writableDatabase
    val tableDefinitions get() = category.tables
}
object DatabasePatching {
    private fun createTriggersForTable(
        dbDef: DatabaseDefinition<*>,
        tableDef: TableDef,
    ) = tableDef.run {
        fun where(prefix: String): String =
            if(idField2 == null) {
                "entityId1 = $prefix.$idField1"
            } else {
                "entityId1 = $prefix.$idField1 AND entityId2 = $prefix.$idField2"
            }
        fun insert(prefix: String): String =
            if(idField2 == null) {
                "$prefix.$idField1,''"
            } else {
                "$prefix.$idField1,$prefix.$idField2"
            }
        val timeStampFunc = "CAST(UNIXEPOCH('subsec') * 1000 AS INTEGER)"

        val db = dbDef.writableDb
        val deviceId = dbDef.deviceId

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS ${tableName}_inserts AFTER INSERT ON $tableName 
            BEGIN DELETE FROM LogEntry WHERE ${where("NEW")} AND tableName = '$tableName';
            INSERT INTO LogEntry VALUES ('$tableName', ${insert("NEW")}, 'UPSERT', $timeStampFunc, '$deviceId'); 
            END;
        """.trimIndent()
        )
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS ${tableName}_updates AFTER UPDATE ON $tableName 
            BEGIN DELETE FROM LogEntry WHERE ${where("OLD")} AND tableName = '$tableName';
            INSERT INTO LogEntry VALUES ('$tableName', ${insert("OLD")}, 'UPSERT', $timeStampFunc, '$deviceId'); 
            END;
        """.trimIndent()
        )
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS ${tableName}_deletes AFTER DELETE ON $tableName 
            BEGIN DELETE FROM LogEntry WHERE ${where("OLD")} AND tableName = '$tableName';
            INSERT INTO LogEntry VALUES ('$tableName', ${insert("OLD")}, 'DELETE', $timeStampFunc, '$deviceId'); 
            END;
        """.trimIndent()
        )
    }

    private fun dropTriggersForTable(dbDef: DatabaseDefinition<*>, tableDef: TableDef) = dbDef.writableDb.run {
        execSQL("DROP TRIGGER IF EXISTS ${tableDef.tableName}_inserts")
        execSQL("DROP TRIGGER IF EXISTS ${tableDef.tableName}_updates")
        execSQL("DROP TRIGGER IF EXISTS ${tableDef.tableName}_deletes")
    }


    fun createTriggers(dbDef: DatabaseDefinition<*>) {
        for(tableDef in dbDef.tableDefinitions) {
            createTriggersForTable(dbDef, tableDef)
        }
    }

    private fun writePatchData(db: SupportSQLiteDatabase, tableDef: TableDef, lastPatchWritten: Long) = db.run {
        val table = tableDef.tableName
        val idField1 = tableDef.idField1
        val idField2 = tableDef.idField2
        val cols = getColumnNamesJoined(db, table, "patch")

        var where = idField1
        var select = "pe.entityId1"
        if (idField2 != null) {
            where = "($idField1,$idField2)"
            select = "pe.entityId1,pe.entityId2"
        }
        execSQL("""
            INSERT INTO patch.$table ($cols) SELECT $cols FROM $table WHERE $where IN 
            (SELECT $select FROM LogEntry pe WHERE tableName = '$table' AND type = 'UPSERT' 
            AND lastUpdated > $lastPatchWritten)
            """.trimIndent())
        execSQL("""
            INSERT INTO patch.LogEntry SELECT * FROM LogEntry 
            WHERE tableName = '$table' AND lastUpdated > $lastPatchWritten
            """.trimIndent())
    }

    private fun readPatchData(
        db: SupportSQLiteDatabase,
        table: String,
        idField1: String = "id",
        idField2: String? = null
    ) = db.run {
        val colList = getColumnNames(this, table)
        val cols = colList.joinToString(",") { "`$it`" }
        val setValues = colList.filterNot {it == idField1 || it == idField2}.joinToString(",\n") { "`$it`=excluded.`$it`" }
        val amount = query("SELECT COUNT(*) FROM patch.LogEntry WHERE tableName = '$table'")
            .getFirst { it.getInt(0)}

        Log.i(TAG, "Reading patch data for $table: $amount log entries")
        var idFields = idField1
        var select = "pe.entityId1"
        if (idField2 != null) {
            idFields = "($idField1,$idField2)"
            select = "pe.entityId1,pe.entityId2"
        }

        // Insert all rows from patch table that don't have more recent entry in LogEntry table
        execSQL("""
            INSERT INTO $table ($cols)
            SELECT $cols FROM patch.$table WHERE $idFields IN
            (SELECT $select FROM patch.LogEntry pe
             OUTER LEFT JOIN LogEntry me
             ON pe.entityId1 = me.entityId1 AND pe.entityId2 = me.entityId2 AND pe.tableName = me.tableName
             WHERE pe.tableName = '$table' AND pe.type = 'UPSERT' AND 
             (me.lastUpdated IS NULL OR pe.lastUpdated > me.lastUpdated)
            ) ON CONFLICT DO UPDATE SET $setValues;
            """.trimIndent())

        // Let's fix all foreign key violations. Those will result if target object has been deleted here,
        // but patch still adds references
        execSQL("""
            DELETE FROM $table WHERE rowId in (SELECT rowid FROM pragma_foreign_key_check('$table'));
            """.trimIndent()
        )

        // Delete all marked deletions from patch LogEntry table
        // TODO CHECK DATES TOO BECAUSE BookmarkToLabel CAN HAVE SAME PRIMARY KEY AGAIN
        execSQL("""
            DELETE FROM $table 
            WHERE $idFields IN 
            (SELECT $select FROM patch.LogEntry pe WHERE tableName = '$table' AND type = 'DELETE')
            """.trimIndent()
        )

        // Let's fix LogEntry table timestamps (all above insertions have created new entries)

        // TODO CHECK DATES TOO BECAUSE BookmarkToLabel CAN HAVE SAME PRIMARY KEY AGAIN
        execSQL("""
            INSERT OR REPLACE INTO LogEntry SELECT * FROM patch.LogEntry WHERE tableName = '$table'
            """.trimIndent()
        )
    }

    fun createPatchForDatabase(dbDef: DatabaseDefinition<*>): File? {
        val lastPatchWritten = dbDef.dao.getLong("lastPatchWritten")?: 0
        val patchDbFile = File.createTempFile("created-patch-${dbDef.categoryName}-", ".sqlite3", CommonUtils.tmpDir)

        dbDef.localDb.openHelper.writableDatabase.run {
            val amountUpdated = dbDef.dao.countNewLogEntries(lastPatchWritten, dbDef.deviceId)
            if(amountUpdated == 0L) {
                Log.i(TAG, "No new entries ${dbDef.categoryName}")
                return null
            }
            // let's create empty database with correct schema first.
            val patchDb = dbDef.dbFactory(patchDbFile.absolutePath)
            patchDb.openHelper.writableDatabase.use {}
            Log.i(TAG, "Creating patch for ${dbDef.categoryName}: $amountUpdated updated")
            execSQL("ATTACH DATABASE '${patchDbFile.absolutePath}' AS patch")
            execSQL("PRAGMA patch.foreign_keys=OFF;")
            beginTransaction()
            for (tableDef in dbDef.tableDefinitions) {
                writePatchData(this, tableDef, lastPatchWritten)
            }
            setTransactionSuccessful()
            endTransaction()
            execSQL("PRAGMA patch.foreign_keys=ON;")
            execSQL("DETACH DATABASE patch")
            dbDef.dao.setConfig("lastPatchWritten", System.currentTimeMillis())
        }

        val gzippedOutput = CommonUtils.tmpFile
        Log.i(TAG, "Saving patch file ${dbDef.categoryName}")
        CommonUtils.gzipFile(patchDbFile, gzippedOutput)

        if(!CommonUtils.isDebugMode) {
            patchDbFile.delete()
        }
        return gzippedOutput
    }

    fun applyPatchesForDatabase(dbDef: DatabaseDefinition<*>, vararg patchFiles: File) {
        for(gzippedPatchFile in patchFiles) {
            val patchDbFile = File.createTempFile("downloaded-patch-${dbDef.categoryName}-", ".sqlite3", CommonUtils.tmpDir)
            Log.i(TAG, "Applying patch file ${patchDbFile.name}")
            CommonUtils.gunzipFile(gzippedPatchFile, patchDbFile)
            gzippedPatchFile.delete()
            dbDef.localDb.openHelper.writableDatabase.run {
                execSQL("ATTACH DATABASE '${patchDbFile.absolutePath}' AS patch")
                execSQL("PRAGMA foreign_keys=OFF;")
                beginTransaction()
                for (tableDef in dbDef.tableDefinitions) {
                    readPatchData(this, tableDef.tableName, tableDef.idField1, tableDef.idField2)
                }
                setTransactionSuccessful()
                endTransaction()
                execSQL("PRAGMA foreign_keys=ON;")
                execSQL("DETACH DATABASE patch")
                if(CommonUtils.isDebugMode) {
                    checkForeignKeys(this)
                }
            }
            if(!CommonUtils.isDebugMode) {
                patchDbFile.delete()
            }
        }
    }

    private fun checkForeignKeys(db: SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check;").forEach {c->
            val tableName = c.getString(0)
            val rowId = c.getLong(1)
            val parent = c.getString(2)
            Log.w(TAG, "Foreign key check failure: $tableName:$rowId (<- $parent)")
            Dialogs.showErrorMsg("Foreign key check failure: $tableName:$rowId ($parent)")
        }
    }
}
