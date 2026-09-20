package com.joaohouto.clusterplayer.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cluster_player_prefs")

data class PlaybackStateSnapshot(
    val lastTrackUri: String?,
    val lastFolderPath: String?,
    val lastPositionMs: Long,
    val isShuffleEnabled: Boolean,
    val repeatMode: Int
)

class PreferencesDataStore(private val context: Context) {

    companion object {
        val KEY_LAST_TRACK_URI = stringPreferencesKey("last_track_uri")
        val KEY_LAST_FOLDER_PATH = stringPreferencesKey("last_folder_path")
        val KEY_LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val KEY_SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
        val KEY_REPEAT_MODE = intPreferencesKey("repeat_mode")
        val KEY_ACCENT_THEME = stringPreferencesKey("accent_theme")
        val KEY_CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")

        @Volatile
        private var INSTANCE: PreferencesDataStore? = null

        fun getInstance(context: Context): PreferencesDataStore {
            return INSTANCE ?: synchronized(this) {
                val instance = PreferencesDataStore(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    val playbackStateFlow: Flow<PlaybackStateSnapshot> = context.dataStore.data.map { prefs ->
        PlaybackStateSnapshot(
            lastTrackUri = prefs[KEY_LAST_TRACK_URI],
            lastFolderPath = prefs[KEY_LAST_FOLDER_PATH],
            lastPositionMs = prefs[KEY_LAST_POSITION_MS] ?: 0L,
            isShuffleEnabled = prefs[KEY_SHUFFLE_MODE] ?: false,
            repeatMode = prefs[KEY_REPEAT_MODE] ?: 0
        )
    }

    suspend fun getPlaybackSnapshot(): PlaybackStateSnapshot {
        val prefs = context.dataStore.data.first()
        return PlaybackStateSnapshot(
            lastTrackUri = prefs[KEY_LAST_TRACK_URI],
            lastFolderPath = prefs[KEY_LAST_FOLDER_PATH],
            lastPositionMs = prefs[KEY_LAST_POSITION_MS] ?: 0L,
            isShuffleEnabled = prefs[KEY_SHUFFLE_MODE] ?: false,
            repeatMode = prefs[KEY_REPEAT_MODE] ?: 0
        )
    }

    suspend fun saveLastPlayback(
        trackUri: String,
        folderPath: String,
        positionMs: Long
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_TRACK_URI] = trackUri
            prefs[KEY_LAST_FOLDER_PATH] = folderPath
            prefs[KEY_LAST_POSITION_MS] = positionMs
        }
    }

    suspend fun savePosition(positionMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_POSITION_MS] = positionMs
        }
    }

    suspend fun saveShuffleMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHUFFLE_MODE] = enabled
        }
    }

    val accentThemeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCENT_THEME] ?: "needle_red"
    }

    val crossfadeSecondsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_CROSSFADE_SECONDS] ?: 3
    }

    suspend fun saveRepeatMode(repeatMode: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REPEAT_MODE] = repeatMode
        }
    }

    suspend fun saveAccentTheme(themeId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCENT_THEME] = themeId
        }
    }

    suspend fun saveCrossfadeSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CROSSFADE_SECONDS] = seconds
        }
    }
}
