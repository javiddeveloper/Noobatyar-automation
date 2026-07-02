package xyz.sattar.javid.proqueue.core.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberNotificationPermissionLauncher(onResult: (Boolean) -> Unit): PermissionLauncher {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onResult
    )

    return remember(launcher) {
        object : PermissionLauncher {
            override fun launch() {
                if (Build.VERSION.SDK_INT >= 33) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        onResult(true)
                    } else {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    onResult(true)
                }
            }
        }
    }
}
