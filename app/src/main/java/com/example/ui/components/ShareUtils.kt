package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast

fun shareToWhatsApp(context: Context, messageText: String) {
  val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, messageText)
    setPackage("com.whatsapp")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
  try {
    context.startActivity(whatsappIntent)
  } catch (_: ActivityNotFoundException) {
    val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, messageText)
    }
    val chooser = Intent.createChooser(fallbackIntent, "WhatsApp या अन्य ऐप पर शेयर करें").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
      context.startActivity(chooser)
    }.onFailure {
      Toast.makeText(context, "शेयर करने के लिए कोई ऐप नहीं मिला।", Toast.LENGTH_SHORT).show()
    }
  }
}
