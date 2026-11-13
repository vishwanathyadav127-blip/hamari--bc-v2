package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.BcCalculator
import com.example.model.Committee
import com.example.ui.components.WinnerBreakdownCard
import com.example.ui.components.shareToWhatsApp
import com.example.ui.theme.LocalBcExtraColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeDashboardScreen(
  committee: Committee,
  isAdmin: Boolean,
  countdownRemainingSeconds: Long,
  scheduleRemainingSeconds: Long,
  onOpenLiveBidding: () -> Unit,
  onStartLiveBidding: (Long) -> Unit,
  onStartNextRound: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val extra = LocalBcExtraColors.current
  var customMinutesInput by remember(committee.timerMinutes) {
    mutableStateOf(committee.timerMinutes.toString())
  }

  val scheduleDateFormatter = remember {
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.TopCenter
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 640.dp)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Live Bidding Hero Card
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
          containerColor = if (committee.isLive) extra.whatsappHeaderBg else MaterialTheme.colorScheme.primary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Surface(
              color = if (committee.isLive) extra.liveRed else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
              shape = RoundedCornerShape(50)
            ) {
              Text(
                text = if (committee.isLive) "● LIVE बोली चालू है" else "दौर #${committee.roundNumber}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              )
            }

            if (committee.isLive) {
              val mins = countdownRemainingSeconds / 60
              val secs = countdownRemainingSeconds % 60
              Text(
                text = String.format(Locale.US, "%02d:%02d", mins, secs),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimary
              )
            } else {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.Timer,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "${committee.timerMinutes} मिनट टाइमर",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onPrimary
                )
              }
            }
          }

          if (committee.isLive) {
            Text(
              text = if (committee.currentHighestBid > 0) {
                "सबसे ऊँची बोली: ${BcCalculator.formatRupees(committee.currentHighestBid)} (${committee.currentHighestBidderName})"
              } else {
                "बोली कक्ष खुला है — सबसे पहले बोली लगाएं!"
              },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimary
            )
          } else if (committee.scheduledAtMillis > System.currentTimeMillis()) {
            val formattedTime = scheduleDateFormatter.format(Date(committee.scheduledAtMillis))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
              )
              Column {
                Text(
                  text = "अगली लाइव बोली: $formattedTime",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimary
                )
                val hrs = scheduleRemainingSeconds / 3600
                val remM = (scheduleRemainingSeconds % 3600) / 60
                val remS = scheduleRemainingSeconds % 60
                Text(
                  text = String.format(
                    Locale.US,
                    "ऑटो-स्टार्ट व चूँ-चूँ सायरन में शेष: %02d:%02d:%02d",
                    hrs,
                    remM,
                    remS
                  ),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                )
              }
            }
          } else {
            Text(
              text = "लाइव बोली कक्ष (WhatsApp जैसा चैट और बोली)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onPrimary
            )
          }

          Button(
            onClick = onOpenLiveBidding,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.surface,
              contentColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("open_live_bidding_button")
          ) {
            Icon(imageVector = Icons.Filled.Gavel, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (committee.isLive) "लाइव बोली कक्ष में जाएं" else "बोली कक्ष खोलें (Live Bidding)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }

          if (isAdmin && !committee.isLive) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = customMinutesInput,
                onValueChange = { customMinutesInput = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("मिनट", color = MaterialTheme.colorScheme.onPrimary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                  focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                  unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                  focusedBorderColor = MaterialTheme.colorScheme.onPrimary,
                  unfocusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                ),
                modifier = Modifier.weight(0.45f)
              )
              Button(
                onClick = {
                  val mins = (customMinutesInput.toLongOrNull() ?: committee.timerMinutes).coerceIn(1L, 180L)
                  onStartLiveBidding(mins)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = extra.celebrationGold,
                  contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier
                  .weight(1f)
                  .height(52.dp)
                  .testTag("start_live_bidding_button")
              ) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "अभी बोली शुरू करें",
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }

      // 2. Quick Metric Grid
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        MetricTile(
          icon = Icons.Filled.AccountBalanceWallet,
          label = "कुल BC राशि",
          value = BcCalculator.formatRupees(committee.totalBcAmount),
          modifier = Modifier.weight(1f)
        )
        MetricTile(
          icon = Icons.Filled.Groups,
          label = "कुल सदस्य",
          value = "${committee.totalMembers} सदस्य",
          modifier = Modifier.weight(1f)
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        MetricTile(
          icon = Icons.Filled.EmojiEvents,
          label = "प्रति सदस्य किस्त",
          value = if (committee.winnerDeclared && committee.perMemberInstallment > 0) {
            BcCalculator.formatRupees(committee.perMemberInstallment)
          } else {
            "बोली के बाद"
          },
          modifier = Modifier.weight(1f)
        )
        MetricTile(
          icon = Icons.Filled.Key,
          label = "इनवाइट कोड",
          value = committee.inviteCode,
          modifier = Modifier.weight(1f)
        )
      }

      // 3. Winner & Installment Calculation Card
      if (committee.winnerDeclared) {
        WinnerBreakdownCard(
          committeeName = committee.name,
          roundNumber = committee.roundNumber,
          totalBcAmount = committee.totalBcAmount,
          totalMembers = committee.totalMembers,
          winnerName = committee.winnerName,
          winnerMobile = committee.winnerMobile,
          winningBid = committee.winningBid,
          netPayableToWinner = committee.netPayableToWinner,
          perMemberInstallment = committee.perMemberInstallment,
          sloganText = BcCalculator.formatSlogan(committee.winnerSloganTemplate, committee.winnerName)
        )

        if (isAdmin) {
          OutlinedButton(
            onClick = onStartNextRound,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
          ) {
            Text("अगला दौर (#${committee.roundNumber + 1}) शुरू करें", fontWeight = FontWeight.Bold)
          }
        }
      } else {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = "विजेता एवं किस्त गणना",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "जैसे ही दौर #${committee.roundNumber} की बोली समाप्त होगी, विजेता को मिलने वाली राशि और प्रति सदस्य किस्त यहाँ स्वतः दिख जाएगी।",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
              onClick = onOpenSettings,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.height(48.dp)
            ) {
              Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("अगली बोली का समय सेट करें")
            }
          }
        }
      }

      // 4. WhatsApp Invite & Direct APK Share Card
      OutlinedButton(
        onClick = {
          val text = BcCalculator.buildInviteShareText(
            committeeName = committee.name,
            inviteCode = committee.inviteCode,
            totalBcAmount = committee.totalBcAmount,
            totalMembers = committee.totalMembers,
            apkUrl = committee.apkDownloadUrl
          )
          shareToWhatsApp(context, text)
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("home_share_invite_button")
      ) {
        Icon(imageVector = Icons.Filled.Share, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "WhatsApp पर Invite Link / APK Share करें",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold
        )
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

@Composable
private fun MetricTile(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  value: String,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp)
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}
