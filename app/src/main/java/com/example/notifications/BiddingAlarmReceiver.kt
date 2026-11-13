package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BiddingAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val committeeName = intent?.getStringExtra("committee_name").orEmpty().ifBlank { "हमारी BC कमेटी" }
    BiddingNotificationHelper.showBiddingStartedNotification(context, committeeName)
  }
}
