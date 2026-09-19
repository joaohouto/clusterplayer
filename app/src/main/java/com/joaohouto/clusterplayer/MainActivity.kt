package com.joaohouto.clusterplayer

import android.Manifest
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

enum class Screen {
    Home,
    Player
}

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            homeViewModel.rescan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        PlayerController.getInstance(this).initialize()
        checkAndRequestPermissions()

        setContent {
            ClusterPlayerTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DeepMetallicBackground),
                    color = DeepMetallicBackground
                ) {
                    ClusterApp(
                        homeViewModel = homeViewModel,
                        playerViewModel = playerViewModel
                    )
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
}

@Composable
fun ClusterApp(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel
) {
    var currentScreen by remember { mutableStateOf(Screen.Player) }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ScreenTransition"
    ) { screen ->
        when (screen) {
            Screen.Player -> {
                PlayerScreen(
                    viewModel = playerViewModel,
                    onNavigateToHome = { currentScreen = Screen.Home }
                )
            }
            Screen.Home -> {
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToPlayer = { currentScreen = Screen.Player }
                )
            }
        }
    }
}