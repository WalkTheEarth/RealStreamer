package com.irlstream.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.irlstream.app.ui.screens.DestinationsScreen
import com.irlstream.app.ui.screens.OverlaysScreen
import com.irlstream.app.ui.screens.SettingsScreen
import com.irlstream.app.ui.screens.StudioScreen
import com.irlstream.app.ui.screens.WelcomeScreen
import com.irlstream.app.ui.theme.IRL

private data class PermissionSnapshot(
    val camera: Boolean,
    val microphone: Boolean,
    val notifications: Boolean,
)

private fun snapshot(context: Context): PermissionSnapshot {
    fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    return PermissionSnapshot(
        camera = granted(Manifest.permission.CAMERA),
        microphone = granted(Manifest.permission.RECORD_AUDIO),
        notifications = if (Build.VERSION.SDK_INT >= 33) {
            granted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        },
    )
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.engineState.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissions by remember { mutableStateOf(snapshot(context)) }
    val snackbar = remember { SnackbarHostState() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissions = snapshot(context)
        vm.startPreview()
    }

    val requestPermissions = {
        val requested = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        launcher.launch(requested)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = snapshot(context)
                if (permissions.camera) vm.startPreview()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        vm.clearError()
        snackbar.showSnackbar(message)
    }
    LaunchedEffect(toast) {
        val message = toast ?: return@LaunchedEffect
        vm.consumeToast()
        snackbar.showSnackbar(message)
    }

    BackHandler(enabled = screen != Screen.Studio) {
        vm.back()
    }

    Box(Modifier.fillMaxSize()) {
        if (!settings.onboardingComplete) {
            WelcomeScreen(vm)
        } else {
            when (screen) {
                Screen.Studio -> StudioScreen(
                    vm = vm,
                    settings = settings,
                    state = state,
                    cameraGranted = permissions.camera,
                    micGranted = permissions.microphone,
                    onRequestPermissions = requestPermissions,
                )
                Screen.Destinations -> DestinationsScreen(vm, settings, state)
                Screen.Overlays -> OverlaysScreen(vm, settings, state)
                Screen.Settings -> SettingsScreen(vm, settings, state)
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp),
        ) { data ->
            Snackbar(
                containerColor = IRL.PanelHigh,
                contentColor = IRL.Text,
                shape = RoundedCornerShape(12.dp),
                actionContentColor = IRL.Neon,
            ) {
                Text(data.visuals.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
