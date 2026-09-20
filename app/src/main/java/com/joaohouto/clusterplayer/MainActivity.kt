package com.joaohouto.clusterplayer

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.joaohouto.clusterplayer.player.PlayerController
import com.joaohouto.clusterplayer.ui.home.HomeScreen
import com.joaohouto.clusterplayer.ui.home.HomeViewModel
import com.joaohouto.clusterplayer.ui.player.PlayerScreen
import com.joaohouto.clusterplayer.ui.player.PlayerViewModel
import com.joaohouto.clusterplayer.ui.theme.ClusterPlayerTheme
import com.joaohouto.clusterplayer.ui.theme.DeepMetallicBackground

import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.joaohouto.clusterplayer.data.local.PreferencesDataStore
import com.joaohouto.clusterplayer.ui.settings.SettingsDialog
import com.joaohouto.clusterplayer.ui.theme.getAccentThemeById

enum class Screen {
    Home,
    Player
}

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private lateinit var preferencesDataStore: PreferencesDataStore

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions handled; do not automatically scan storage
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.setBackgroundDrawableResource(R.color.deep_metallic_background)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        preferencesDataStore = PreferencesDataStore.getInstance(this)
        PlayerController.getInstance(this).initialize()
        checkAndRequestPermissions()

        setContent {
            val accentThemeId by preferencesDataStore.accentThemeFlow.collectAsState(initial = "needle_red")
            val crossfadeSeconds by preferencesDataStore.crossfadeSecondsFlow.collectAsState(initial = 0)
            val appVolumePercent by preferencesDataStore.appVolumePercentFlow.collectAsState(initial = 100)
            val isLoudnessEnabled by preferencesDataStore.loudnessBoostFlow.collectAsState(initial = false)
            val isKeepScreenOn by preferencesDataStore.keepScreenOnFlow.collectAsState(initial = true)
            val isShowLyrics by preferencesDataStore.showLyricsFlow.collectAsState(initial = true)
            val isAutoplayOnStart by preferencesDataStore.autoplayOnStartFlow.collectAsState(initial = true)

            val currentAccent = remember(accentThemeId) { getAccentThemeById(accentThemeId) }
            val coroutineScope = rememberCoroutineScope()
            var showSettingsDialog by remember { mutableStateOf(false) }

            LaunchedEffect(isKeepScreenOn) {
                if (isKeepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            ClusterPlayerTheme(accentTheme = currentAccent) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DeepMetallicBackground)
                        .safeDrawingPadding(),
                    color = DeepMetallicBackground
                ) {
                    ClusterApp(
                        homeViewModel = homeViewModel,
                        playerViewModel = playerViewModel,
                        showLyrics = isShowLyrics,
                        onOpenSettings = { showSettingsDialog = true }
                    )

                    if (showSettingsDialog) {
                        SettingsDialog(
                            currentAccentTheme = currentAccent,
                            currentCrossfadeSeconds = crossfadeSeconds,
                            currentAppVolumePercent = appVolumePercent,
                            isLoudnessEnabled = isLoudnessEnabled,
                            isKeepScreenOn = isKeepScreenOn,
                            isShowLyrics = isShowLyrics,
                            isAutoplayOnStart = isAutoplayOnStart,
                            onSelectAccent = { newThemeId ->
                                coroutineScope.launch {
                                    preferencesDataStore.saveAccentTheme(newThemeId)
                                }
                            },
                            onSelectCrossfade = { newSeconds ->
                                coroutineScope.launch {
                                    preferencesDataStore.saveCrossfadeSeconds(newSeconds)
                                }
                            },
                            onSelectAppVolume = { newVolume ->
                                coroutineScope.launch {
                                    preferencesDataStore.saveAppVolumePercent(newVolume)
                                }
                            },
                            onToggleLoudness = { enabled ->
                                coroutineScope.launch {
                                    preferencesDataStore.saveLoudnessBoost(enabled)
                                }
                            },
                            onToggleKeepScreenOn = { enabled ->
                                coroutineScope.launch {
                                    preferencesDataStore.saveKeepScreenOn(enabled)
                                }
                            },
                            onToggleShowLyrics = { enabled ->
                                coroutineScope.launch {
                                    preferencesDataStore.saveShowLyrics(enabled)
                                }
                            },
                            onToggleAutoplayOnStart = { enabled ->
                                coroutineScope.launch {
                                    preferencesDataStore.saveAutoplayOnStart(enabled)
                                }
                            },
                            onDismiss = { showSettingsDialog = false }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do not stop playback on activity destroy to allow background audio
    }
}

@Composable
fun ClusterApp(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    showLyrics: Boolean,
    onOpenSettings: () -> Unit
) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
    var userNavigatedScreen by remember { mutableStateOf<Screen?>(null) }

    // Always start on Player screen (do not automatically open folder selector if no music is playing)
    val currentScreen = userNavigatedScreen ?: Screen.Player

    androidx.activity.compose.BackHandler(enabled = currentScreen == Screen.Home) {
        userNavigatedScreen = Screen.Player
    }

    androidx.activity.compose.BackHandler(enabled = currentScreen == Screen.Player) {
        activity?.moveTaskToBack(true)
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ScreenTransition"
    ) { screen ->
        when (screen) {
            Screen.Player -> {
                PlayerScreen(
                    viewModel = playerViewModel,
                    showLyrics = showLyrics,
                    onNavigateToHome = { userNavigatedScreen = Screen.Home },
                    onOpenSettings = onOpenSettings
                )
            }
            Screen.Home -> {
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToPlayer = { userNavigatedScreen = Screen.Player },
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}