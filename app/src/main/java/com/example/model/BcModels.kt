package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class AppThemeMode(val storageKey: String, val labelHindi: String, val subtitle: String) {
  ROYAL_BLUE("ROYAL_BLUE", "रॉयल ब्लू (Royal Blue)", "डिफ़ॉल्ट शाही नीला थीम"),
  EMERALD_GREEN("EMERALD_GREEN", "एमरल्ड ग्रीन (Emerald Green)", "ताज़ा हरा और सुनहरा थीम"),
  DARK_MODE("DARK_MODE", "डार्क मोड (Dark Mode)", "रात के लिए आरामदायक गहरा थीम");

  companion object {
    fun fromKey(key: String?): AppThemeMode =
      entries.firstOrNull { it.storageKey == key } ?: ROYAL_BLUE
  }
}

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val mobileNumber: String = "",
  val email: String = "",
  val activeCommitteeId: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Committee(
  @DocumentId val id: String = "",
  val name: String = "हमारी BC कमेटी",
  val adminUid: String = "",
  val adminEmail: String = "",
  val inviteCode: String = "",
  val memberUids: List<String> = emptyList(),
  val totalBcAmount: Long = 100_000L,
  val totalMembers: Long = 10L,
  val timerMinutes: Long = 5L,
  val scheduledAtMillis: Long = 0L,
  @JvmField val isLive: Boolean = false,
  val biddingStartedAtMillis: Long = 0L,
  val biddingEndsAtMillis: Long = 0L,
  val currentHighestBid: Long = 0L,
  val currentHighestBidderUid: String = "",
  val currentHighestBidderName: String = "",
  val currentHighestBidderMobile: String = "",
  val roundNumber: Long = 1L,
  val winnerDeclared: Boolean = false,
  val winnerName: String = "",
  val winnerUid: String = "",
  val winnerMobile: String = "",
  val winningBid: Long = 0L,
  val netPayableToWinner: Long = 0L,
  val perMemberInstallment: Long = 0L,
  val winnerSloganTemplate: String = DEFAULT_SLOGAN_TEMPLATE,
  val apkDownloadUrl: String = DEFAULT_APK_URL,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  companion object {
    const val DEFAULT_SLOGAN_TEMPLATE =
      "🏆 बधाई हो [Winner Name]! YOU ARE GREAT! 🎉 इस दौर के BC विजेता बनने पर हार्दिक शुभकामनाएँ!"
    const val DEFAULT_APK_URL =
      "https://ais-pre-3tdzdizru7y43oxyrhnzrr-762124234781.asia-east1.run.app"
  }
}

@IgnoreExtraProperties
data class CommitteeMember(
  @DocumentId val id: String = "",
  val committeeId: String = "",
  val adminUid: String = "",
  val linkedUid: String = "",
  val name: String = "",
  val mobileNumber: String = "",
  val currentInstallmentStatus: String = "pending",
  val paidRounds: List<Long> = emptyList(),
  val hasWonRound: Long = 0L,
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  @get:Exclude
  val isPaid: Boolean get() = currentInstallmentStatus == "paid"
}

@IgnoreExtraProperties
data class BidMessage(
  @DocumentId val id: String = "",
  val committeeId: String = "",
  val roundNumber: Long = 1L,
  val senderUid: String = "",
  val senderName: String = "",
  val senderMobile: String = "",
  val type: String = "bid", // "bid", "chat", "system"
  val amount: Long = 0L,
  val message: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RoundRecord(
  @DocumentId val id: String = "",
  val committeeId: String = "",
  val roundNumber: Long = 1L,
  val totalBcAmount: Long = 100_000L,
  val totalMembers: Long = 10L,
  val winnerUid: String = "",
  val winnerName: String = "",
  val winnerMobile: String = "",
  val winningBid: Long = 0L,
  val netPayableToWinner: Long = 0L,
  val perMemberInstallment: Long = 0L,
  val sloganText: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null
)

object BcCalculator {
  fun calculateBreakdown(
    totalBcAmount: Long,
    totalMembers: Long,
    winningBid: Long
  ): Pair<Long, Long> {
    val safeBid = winningBid.coerceIn(0L, totalBcAmount)
    val netPayable = (totalBcAmount - safeBid).coerceAtLeast(0L)
    val perMember = if (totalMembers > 0L) netPayable / totalMembers else 0L
    return netPayable to perMember
  }

  fun formatSlogan(template: String, winnerName: String): String {
    val safeName = winnerName.ifBlank { "विजेता" }
    val base = template.ifBlank { Committee.DEFAULT_SLOGAN_TEMPLATE }
    return if (base.contains("[Winner Name]")) {
      base.replace("[Winner Name]", safeName)
    } else {
      "$base — $safeName"
    }
  }

  fun formatRupees(amount: Long): String {
    val absVal = kotlin.math.abs(amount)
    val sign = if (amount < 0) "-" else ""
    val str = absVal.toString()
    if (str.length <= 3) return "${sign}₹$str"
    val lastThree = str.takeLast(3)
    val rest = str.dropLast(3)
    val sb = StringBuilder()
    var i = rest.length
    var count = 0
    while (i > 0) {
      i--
      sb.append(rest[i])
      count++
      if (count == 2 && i > 0) {
        sb.append(',')
        count = 0
      }
    }
    return "${sign}₹${sb.reverse()},$lastThree"
  }

  fun buildWinnerShareText(
    committeeName: String,
    roundNumber: Long,
    totalBcAmount: Long,
    totalMembers: Long,
    winnerName: String,
    winningBid: Long,
    netPayableToWinner: Long,
    perMemberInstallment: Long,
    sloganText: String
  ): String {
    return buildString {
      appendLine(sloganText)
      appendLine()
      appendLine("📋 *${committeeName} — दौर #$roundNumber संपूर्ण बीसी हिसाब*")
      appendLine("━━━━━━━━━━━━━━━━━━━━")
      appendLine("💰 कुल BC कमेटी राशि: *${formatRupees(totalBcAmount)}*")
      appendLine("🏆 विजेता का नाम: *$winnerName*")
      appendLine("🔨 बोली राशि (कितने में तोड़ा): *${formatRupees(winningBid)}*")
      appendLine("💵 विजेता को मिलने वाली राशि: *${formatRupees(netPayableToWinner)}*")
      appendLine("👥 कुल सदस्य: *$totalMembers*")
      appendLine("📅 प्रति सदस्य मासिक किस्त: *${formatRupees(perMemberInstallment)}*")
      appendLine("━━━━━━━━━━━━━━━━━━━━")
      appendLine("कृपया अपनी किस्त समय पर जमा करें। धन्यवाद! 🙏")
    }
  }

  fun buildInviteShareText(
    committeeName: String,
    inviteCode: String,
    totalBcAmount: Long,
    totalMembers: Long,
    apkUrl: String
  ): String {
    return buildString {
      appendLine("🙏 नमस्ते! आपको *${committeeName}* में जुड़ने का निमंत्रण है।")
      appendLine("💰 कुल BC राशि: *${formatRupees(totalBcAmount)}* ($totalMembers सदस्य)")
      appendLine("🔑 इनवाइट कोड (Invite Code): *$inviteCode*")
      if (apkUrl.isNotBlank()) {
        appendLine()
        appendLine("📲 ऐप डाउनलोड / लिंक खोलें:")
        appendLine(apkUrl.trim())
      }
    }
  }
}
