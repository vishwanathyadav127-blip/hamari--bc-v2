package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.BcCalculator

object BiddingNotificationHelper {
  const val CHANNEL_ID = "hamari_bc_live_alerts"
  const val EXTRA_OPEN_LIVE_BIDDING = "extra_open_live_bidding"
  private const val ALARM_REQUEST_CODE = 4201

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "BC लाइव बोली और विजेता अलर्ट",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "लाइव बोली शुरू होने और विजेता घोषित होने की तुरंत सूचना"
        enableVibration(true)
      }
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  private fun canPostNotifications(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun showBiddingStartedNotification(context: Context, committeeName: String) {
    ensureChannel(context)
    if (!canPostNotifications(context)) return

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_OPEN_LIVE_BIDDING, true)
    }
    val pendingIntent = PendingIntent.getActivity(
      context,
      1001,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher_round)
      .setContentTitle("🔔 लाइव बोली शुरू हो गई है! ($committeeName)")
      .setContentText("सभी सदस्य ध्यान दें — बोली कक्ष खुल गया है। अभी जुड़ें!")
      .setStyle(
        NotificationCompat.BigTextStyle()
          .bigText("सभी सदस्य ध्यान दें — $committeeName का लाइव बोली कक्ष खुल गया है। अभी टैप करके बोली लगाएं!")
      )
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(pendingIntent)
      .build()

    runCatching {
      NotificationManagerCompat.from(context).notify(2001, notification)
    }
  }

  fun showWinnerDeclaredNotification(
    context: Context,
    committeeName: String,
    winnerName: String,
    perMemberInstallment: Long,
    sloganText: String
  ) {
    ensureChannel(context)
    if (!canPostNotifications(context)) return

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      context,
      1002,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val installmentStr = BcCalculator.formatRupees(perMemberInstallment)
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher_round)
      .setContentTitle("🏆 $winnerName विजेता बने! ($committeeName)")
      .setContentText("प्रति सदस्य किस्त: $installmentStr • $sloganText")
      .setStyle(
        NotificationCompat.BigTextStyle()
          .bigText("$sloganText\n\nप्रति सदस्य मासिक किस्त: $installmentStr")
      )
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(pendingIntent)
      .build()

    runCatching {
      NotificationManagerCompat.from(context).notify(2002, notification)
    }
  }

  fun scheduleBiddingAlarm(context: Context, scheduledAtMillis: Long, committeeName: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intent = Intent(context, BiddingAlarmReceiver::class.java).apply {
      putExtra("committee_name", committeeName)
    }
    val pendingIntent = PendingIntent.getBroadcast(
      context,
      ALARM_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    if (scheduledAtMillis > System.currentTimeMillis()) {
      runCatching {
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          scheduledAtMillis,
          pendingIntent
        )
      }
    } else {
      cancelBiddingAlarm(context)
    }
  }

  fun cancelBiddingAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intent = Intent(context, BiddingAlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
      context,
      ALARM_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    runCatching { alarmManager.cancel(pendingIntent) }
  }
}
