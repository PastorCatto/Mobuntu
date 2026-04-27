package org.mobuntu.chroot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.topjohnwu.superuser.Shell

class MobutuApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialise libsu — request root shell on first use, 10s timeout
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mobuntu Session",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active Mobuntu chroot session controls"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "mobuntu_session"
    }
}
