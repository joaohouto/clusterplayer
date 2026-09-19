package com.joaohouto.clusterplayer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.joaohouto.clusterplayer.data.local.dao.FolderDao
import com.joaohouto.clusterplayer.data.local.dao.TrackDao
import com.joaohouto.clusterplayer.data.local.entity.FolderEntity
import com.joaohouto.clusterplayer.data.local.entity.TrackEntity

@Database(
    entities = [FolderEntity::class, TrackEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun trackDao(): TrackDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cluster_player.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
