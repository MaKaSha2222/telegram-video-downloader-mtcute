package expo.modules.localbotapi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class LocalBotApiForegroundService : Service() {
  override fun onCreate() {
    super.onCreate()
    val manager = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Telegram download", NotificationManager.IMPORTANCE_LOW)
      )
    }
    startForeground(NOTIFICATION_ID, notification())
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    stopForeground(STOP_FOREGROUND_REMOVE)
    super.onDestroy()
  }

  private fun notification(): Notification {
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(this, CHANNEL_ID)
    } else {
      Notification.Builder(this)
    }
    return builder
      .setContentTitle("Telegram video download")
      .setContentText("Download လုပ်နေပါတယ်…")
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setOngoing(true)
      .build()
  }

  companion object {
    private const val CHANNEL_ID = "telegram_downloads"
    private const val NOTIFICATION_ID = 19081
  }
}
