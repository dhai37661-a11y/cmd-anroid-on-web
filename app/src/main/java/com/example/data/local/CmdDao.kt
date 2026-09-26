package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CmdDao {
    @Query("SELECT * FROM command_history ORDER BY id DESC LIMIT 100")
    fun getRecentHistory(): Flow<List<CommandHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: CommandHistoryEntity)

    @Query("DELETE FROM command_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM batch_scripts ORDER BY name ASC")
    fun getAllScripts(): Flow<List<BatchScriptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: BatchScriptEntity): Long

    @Query("DELETE FROM batch_scripts WHERE name = :name")
    suspend fun deleteScriptByName(name: String)

    @Query("SELECT * FROM batch_scripts WHERE name = :name LIMIT 1")
    suspend fun getScriptByName(name: String): BatchScriptEntity?
}
