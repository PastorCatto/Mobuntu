package org.mobuntu.chroot.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mobuntu.chroot.MainActivity
import org.mobuntu.chroot.MobutuApp
import org.mobuntu.chroot.R
import org.mobuntu.chroot.engine.ChrootEngine
import org.mobuntu.chroot.engine.SessionManager
import org.mobuntu.chroot.engine.SessionState
import org.mobuntu.chroot.engine.TermuxX11Launcher
import org.mobuntu.chroot.settings.SettingsRepository

class MobuntuForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settings: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START   -> startSession()
            ACTION_STOP    -> stopSession()
            ACTION_RESTART -> restartSession()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Session lifecycle ────────────────────────────────────────────────────

    private fun startSession() {
        startForeground(NOTIF_ID, buildNotification("Mobuntu starting…"))
        scope.launch {
            try {
                val prefs = settings.settings.first()

                SessionManager.setState(SessionState.MOUNTING)
                updateNotification("Mounting rootfs…")
                val mountResult = ChrootEngine.mount()
                if (!mountResult.success) {
                    SessionManager.setState(
                        SessionState.ERROR,
                        mountResult.output.joinToString("\n")
                    )
                    stopSelf()
                    return@launch
                }

                SessionManager.setState(SessionState.STARTING_DISPLAY)
                updateNotification("Starting display…")
                val launchResult = TermuxX11Launcher.launch(prefs)
                if (!launchResult.success) {
                    SessionManager.setState(
                        SessionState.ERROR,
                        launchResult.output.joinToString("\n")
                    )
                    ChrootEngine.unmount()
                    stopSelf()
                    return@launch
                }

                SessionManager.setState(SessionState.RUNNING)
                updateNotification("Mobuntu running")

            } catch (e: Exception) {
                SessionManager.setState(SessionState.ERROR, e.message)
                stopSelf()
            }
        }
    }

    private fun stopSession() {
        scope.launch {
            SessionManager.setState(SessionState.STOPPING)
            updateNotification("Stopping…")
            ChrootEngine.unmount()
            SessionManager.setState(SessionState.STOPPED)
            stopSelf()
        }
    }

    private fun restartSession() {
        scope.launch {
            SessionManager.setState(SessionState.STOPPING)
            updateNotification("Restarting…")
            ChrootEngine.unmount()
            startSession()
        }
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = pendingServiceIntent(ACTION_STOP, 1)
        val restartIntent = pendingServiceIntent(ACTION_RESTART, 2)

        return NotificationCompat.Builder(this, MobutuApp.CHANNEL_ID)
            .setContentTitle("Mobuntu")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_play, "Restart", restartIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun pendingServiceIntent(action: String, requestCode: Int): PendingIntent {
        val i = Intent(this, MobuntuForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_START   = "org.mobuntu.START"
        const val ACTION_STOP    = "org.mobuntu.STOP"
        const val ACTION_RESTART = "org.mobuntu.RESTART"
        const val NOTIF_ID = 1001

        fun start(context: Context) {
            val i = Intent(context, MobuntuForegroundService::class.java)
                .apply { action = ACTION_START }
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, MobuntuForegroundService::class.java)
                .apply { action = ACTION_STOP }
            context.startService(i)
        }
    }
}
