package xyz.sattar.javid.proqueue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

import android.content.Intent
import xyz.sattar.javid.proqueue.core.navigation.NotificationNavigationManager
import xyz.sattar.javid.proqueue.core.navigation.NavigationEvent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        handleNotificationIntent(intent)

        setContent {
            App()
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("from_notification", false) == true) {
            val businessId = intent.getLongExtra("businessId", -1)
            val visitorId = intent.getLongExtra("visitorId", -1)
            val openMessageDialog = intent.getBooleanExtra("openMessageDialog", false)
            
            if (visitorId != -1L) {
                NotificationNavigationManager.navigate(
                    NavigationEvent.ToVisitorDetails(
                        visitorId = visitorId,
                        openMessageDialog = openMessageDialog
                    )
                )
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}