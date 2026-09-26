package com.example.data.repository

import com.example.data.local.BatchScriptEntity
import com.example.data.local.CmdDao
import com.example.data.local.CommandHistoryEntity
import kotlinx.coroutines.flow.Flow

class CmdRepository(private val cmdDao: CmdDao) {
    val recentHistory: Flow<List<CommandHistoryEntity>> = cmdDao.getRecentHistory()
    val allScripts: Flow<List<BatchScriptEntity>> = cmdDao.getAllScripts()

    suspend fun recordCommand(command: String) {
        if (command.isNotBlank()) {
            cmdDao.insertCommand(CommandHistoryEntity(command = command.trim()))
        }
    }

    suspend fun clearHistory() {
        cmdDao.clearHistory()
    }

    suspend fun saveScript(name: String, content: String): Long {
        return cmdDao.insertScript(BatchScriptEntity(name = name, content = content))
    }

    suspend fun getScript(name: String): BatchScriptEntity? {
        return cmdDao.getScriptByName(name)
    }

    suspend fun deleteScript(name: String) {
        cmdDao.deleteScriptByName(name)
    }
}
