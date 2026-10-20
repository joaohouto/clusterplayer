package com.joaohouto.clusterplayer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.joaohouto.clusterplayer.data.local.dao.FolderDao
import com.joaohouto.clusterplayer.data.local.dao.TrackDao
import com.joaohouto.clusterplayer.data.local.dao.TrackLoudnessDao
import com.joaohouto.clusterplayer.data.local.entity.FolderEntity
import com.joaohouto.clusterplayer.data.local.entity.TrackEntity
import com.joaohouto.clusterplayer.data.local.entity.TrackLoudnessEntity

@Database(
    entities = [FolderEntity::class, TrackEntity::class, TrackLoudnessEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun trackDao(): TrackDao
    abstract fun trackLoudnessDao(): TrackLoudnessDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `track_loudness` (
                        `uri` TEXT NOT NULL,
                        `fileSize` INTEGER NOT NULL,
                        `lastModified` INTEGER NOT NULL,
                        `integratedLufs` REAL NOT NULL,
                        `truePeak` REAL NOT NULL,
                        `algorithmVersion` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`uri`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cluster_player.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
