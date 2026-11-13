package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.BcCalculator
import com.example.model.RoundRecord
import com.example.ui.theme.LocalBcExtraColors

@Composable
fun WinnerBreakdownCard(
  committeeName: String,
  roundNumber: Long,
  totalBcAmount: Long,
  totalMembers: Long,
  winnerName: String,
  winnerMobile: String,
  winningBid: Long,
  netPayableToWinner: Long,
  perMemberInstallment: Long,
  sloganText: String,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val extra = LocalBcExtraColors.current

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    modifier = modifier.fillMaxWidth()
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(126.dp)
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_winner_banner),
          contentDescription = "विजेता ट्रॉफी बैनर",
          modifier = Modifier
            .fillMaxWidth()
            .height(126.dp)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
          contentScale = ContentScale.Crop
        )
        Surface(
          color = extra.celebrationGold,
          shape = RoundedCornerShape(50),
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(12.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.EmojiEvents,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "दौर #$roundNumber विजेता घोषित",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSecondary
            )
          }
        }
      }

      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Surface(
          color = extra.warningAmberContainer,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = sloganText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = extra.warningAmber,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(12.dp)
          )
        }

        Text(
          text = "संपूर्ण बीसी हिसाब (Auto Breakdown)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )

        BreakdownRow("कुल BC कमेटी राशि", BcCalculator.formatRupees(totalBcAmount))
        BreakdownRow(
          "विजेता का नाम",
          if (winnerMobile.isNotBlank()) "$winnerName ($winnerMobile)" else winnerName
        )
        BreakdownRow("बोली राशि (कितने में तोड़ा)", BcCalculator.formatRupees(winningBid))
        BreakdownRow(
          "विजेता को मिलने वाली राशि",
          BcCalculator.formatRupees(netPayableToWinner),
          highlight = true
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Surface(
          color = extra.successGreenContainer,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "प्रति सदस्य मासिक किस्त",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = extra.successGreen
              )
              Text(
                text = "($totalMembers सदस्यों में बराबर बँटी)",
                style = MaterialTheme.typography.labelMedium,
                color = extra.successGreen
              )
            }
            Text(
              text = BcCalculator.formatRupees(perMemberInstallment),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = extra.successGreen
            )
          }
        }

        Button(
          onClick = {
            val text = BcCalculator.buildWinnerShareText(
              committeeName = committeeName,
              roundNumber = roundNumber,
              totalBcAmount = totalBcAmount,
              totalMembers = totalMembers,
              winnerName = winnerName,
              winningBid = winningBid,
              netPayableToWinner = netPayableToWinner,
              perMemberInstallment = perMemberInstallment,
              sloganText = sloganText
            )
            shareToWhatsApp(context, text)
          },
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = extra.successGreen,
            contentColor = MaterialTheme.colorScheme.onPrimary
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("share_winner_whatsapp_button")
        ) {
          Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "WhatsApp पर पूरा हिसाब व बधाई शेयर करें",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@Composable
private fun BreakdownRow(
  label: String,
  value: String,
  highlight: Boolean = false
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f)
    )
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.Bold,
      color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
fun WinnerCelebrationDialog(
  committeeName: String,
  record: RoundRecord,
  isAdmin: Boolean,
  onStartNextRound: () -> Unit,
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface
          ) {
            IconButton(onClick = onDismiss) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "बंद करें",
                tint = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        WinnerBreakdownCard(
          committeeName = committeeName,
          roundNumber = record.roundNumber,
          totalBcAmount = record.totalBcAmount,
          totalMembers = record.totalMembers,
          winnerName = record.winnerName,
          winnerMobile = record.winnerMobile,
          winningBid = record.winningBid,
          netPayableToWinner = record.netPayableToWinner,
          perMemberInstallment = record.perMemberInstallment,
          sloganText = record.sloganText
        )

        if (isAdmin) {
          TextButton(onClick = onStartNextRound) {
            Text(
              text = "अगले दौर (#${record.roundNumber + 1}) की तैयारी करें",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimary
            )
          }
        }
      }
    }
  }
}
