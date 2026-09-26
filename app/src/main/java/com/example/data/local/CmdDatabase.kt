package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CommandHistoryEntity::class, BatchScriptEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CmdDatabase : RoomDatabase() {
    abstract fun cmdDao(): CmdDao

    companion object {
        @Volatile
        private var INSTANCE: CmdDatabase? = null

        fun getInstance(context: Context): CmdDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CmdDatabase::class.java,
                    "phone_cmd.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
