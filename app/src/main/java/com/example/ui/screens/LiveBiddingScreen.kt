package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.BcCalculator
import com.example.model.BidMessage
import com.example.model.Committee
import com.example.ui.theme.LocalBcExtraColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveBiddingScreen(
  committee: Committee,
  bids: List<BidMessage>,
  currentUserId: String,
  isAdmin: Boolean,
  countdownRemainingSeconds: Long,
  onStartLiveBidding: (Long) -> Unit,
  onPlaceBid: (Long) -> Unit,
  onSendChat: (String) -> Unit,
  onEndAndDeclareWinner: () -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)

  val extra = LocalBcExtraColors.current
  val listState = rememberLazyListState()
  val roundBids = remember(bids, committee.roundNumber) {
    bids.filter { it.roundNumber == committee.roundNumber || it.roundNumber == 0L }
  }

  var bidAmountInput by remember(committee.currentHighestBid) {
    val suggested = (committee.currentHighestBid + 1000L).coerceAtMost(committee.totalBcAmount)
    mutableStateOf(suggested.toString())
  }
  var chatMessageInput by remember { mutableStateOf("") }
  var startMinutesInput by remember(committee.timerMinutes) {
    mutableStateOf(committee.timerMinutes.toString())
  }

  LaunchedEffect(roundBids.size) {
    if (roundBids.isNotEmpty()) {
      listState.animateScrollToItem(roundBids.lastIndex)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(extra.whatsappBg)
  ) {
    // WhatsApp-Style Header
    Surface(
      color = extra.whatsappHeaderBg,
      shadowElevation = 4.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .statusBarsPadding()
          .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "वापस जाएं",
              tint = MaterialTheme.colorScheme.onPrimary
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "लाइव बोली कक्ष • दौर #${committee.roundNumber}",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
              text = "${committee.name} • ${committee.memberUids.size} ऑनलाइन",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
            )
          }

          Surface(
            color = if (committee.isLive) extra.liveRed else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
            shape = RoundedCornerShape(12.dp)
          ) {
            val mins = countdownRemainingSeconds / 60
            val secs = countdownRemainingSeconds % 60
            Text(
              text = if (committee.isLive) {
                String.format(Locale.US, "● %02d:%02d", mins, secs)
              } else {
                "🔒 बंद"
              },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
          }
        }

        // Highest Bid Banner + Admin End/Start Control
        Surface(
          color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (committee.currentHighestBid > 0) {
                  "सबसे ऊँची बोली: ${BcCalculator.formatRupees(committee.currentHighestBid)}"
                } else {
                  "अभी कोई बोली नहीं लगी"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
              )
              if (committee.currentHighestBidderName.isNotBlank()) {
                Text(
                  text = "लीडर: ${committee.currentHighestBidderName} ${if (committee.currentHighestBidderMobile.isNotBlank()) "(${committee.currentHighestBidderMobile})" else ""}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                )
              }
            }

            if (isAdmin && committee.isLive) {
              Button(
                onClick = onEndAndDeclareWinner,
                colors = ButtonDefaults.buttonColors(
                  containerColor = extra.celebrationGold,
                  contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                  .height(40.dp)
                  .testTag("end_bidding_button")
              ) {
                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("बोली बंद व विजेता", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }

    // WhatsApp-style Bidding & Chat Stream
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      if (roundBids.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
          ) {
            Surface(
              color = extra.whatsappSystemChip,
              shape = RoundedCornerShape(16.dp)
            ) {
              Text(
                text = "💬 लाइव बोली और ग्रुप चैट यहाँ दिखाई देगी। पहली बोली लगाकर शुरुआत करें!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
              )
            }
          }
        }
      }

      items(roundBids, key = { it.id.ifBlank { "${it.senderUid}_${it.amount}_${it.message}" } }) { item ->
        if (item.type == "system") {
          SystemEventBubble(item)
        } else {
          WhatsAppBidBubble(
            item = item,
            isOwn = item.senderUid == currentUserId
          )
        }
      }
    }

    // Bottom Action Bar
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      shadowElevation = 8.dp,
      modifier = Modifier
        .fillMaxWidth()
        .imePadding()
        .navigationBarsPadding()
    ) {
      if (committee.isLive) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // 1-Tap Quick Bid Chips
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            val increments = listOf(500L, 1000L, 2000L, 5000L)
            items(increments) { inc ->
              val target = (committee.currentHighestBid + inc).coerceAtMost(committee.totalBcAmount)
              SuggestionChip(
                onClick = {
                  if (target > committee.currentHighestBid) {
                    onPlaceBid(target)
                  }
                },
                label = {
                  Text(
                    text = "+${BcCalculator.formatRupees(inc)} (${BcCalculator.formatRupees(target)})",
                    fontWeight = FontWeight.Bold
                  )
                }
              )
            }
          }

          // Custom Bid Input + Bid Button
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedTextField(
              value = bidAmountInput,
              onValueChange = { bidAmountInput = it.filter { ch -> ch.isDigit() } },
              label = { Text("बोली राशि (₹)") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier
                .weight(1f)
                .testTag("bid_amount_input")
            )
            Button(
              onClick = {
                val amt = bidAmountInput.toLongOrNull() ?: 0L
                if (amt > committee.currentHighestBid) {
                  onPlaceBid(amt)
                }
              },
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = extra.successGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary
              ),
              modifier = Modifier
                .height(54.dp)
                .testTag("place_bid_button")
            ) {
              Icon(Icons.Filled.Gavel, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("बोली लगाएं", fontWeight = FontWeight.Bold)
            }
          }

          // Quick Chat Message Input
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedTextField(
              value = chatMessageInput,
              onValueChange = { chatMessageInput = it },
              placeholder = { Text("ग्रुप संदेश लिखें...") },
              singleLine = true,
              shape = RoundedCornerShape(24.dp),
              colors = OutlinedTextFieldDefaults.colors(),
              modifier = Modifier.weight(1f)
            )
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(48.dp)
            ) {
              IconButton(
                onClick = {
                  if (chatMessageInput.isNotBlank()) {
                    onSendChat(chatMessageInput)
                    chatMessageInput = ""
                  }
                }
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Send,
                  contentDescription = "संदेश भेजें",
                  tint = MaterialTheme.colorScheme.onPrimary
                )
              }
            }
          }
        }
      } else {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
            Text(
              text = if (committee.winnerDeclared) {
                "🏆 दौर #${committee.roundNumber} समाप्त! विजेता: ${committee.winnerName} (${BcCalculator.formatRupees(committee.winningBid)})"
              } else {
                "बोली कक्ष अभी बंद है। शेड्यूल समय पर या एडमिन द्वारा शुरू होने पर खुलेगा।"
              },
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface
            )
          }

          if (isAdmin && !committee.winnerDeclared) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = startMinutesInput,
                onValueChange = { startMinutesInput = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("मिनट") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.4f)
              )
              Button(
                onClick = {
                  val mins = (startMinutesInput.toLongOrNull() ?: committee.timerMinutes).coerceIn(1L, 180L)
                  onStartLiveBidding(mins)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(52.dp)
              ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("लाइव बोली शुरू करें", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SystemEventBubble(item: BidMessage) {
  val extra = LocalBcExtraColors.current
  Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      color = extra.whatsappSystemChip,
      shape = RoundedCornerShape(16.dp),
      tonalElevation = 1.dp
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.EmojiEvents,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = item.message,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
    }
  }
}

@Composable
private fun WhatsAppBidBubble(
  item: BidMessage,
  isOwn: Boolean
) {
  val extra = LocalBcExtraColors.current
  val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
  val timestampText = remember(item.createdAt) {
    val date = item.createdAt?.toDate() ?: Date()
    timeFormat.format(date)
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
    verticalAlignment = Alignment.Bottom
  ) {
    if (!isOwn) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(36.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = if (item.senderMobile.isNotBlank()) Icons.Filled.PhoneAndroid else Icons.Filled.Person,
            contentDescription = item.senderName,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
          )
        }
      }
      Spacer(modifier = Modifier.width(6.dp))
    }

    Surface(
      shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isOwn) 16.dp else 4.dp,
        bottomEnd = if (isOwn) 4.dp else 16.dp
      ),
      color = if (isOwn) extra.whatsappOwnBubble else extra.whatsappOtherBubble,
      shadowElevation = 2.dp,
      modifier = Modifier.widthIn(min = 160.dp, max = 310.dp)
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        val headerText = if (item.senderMobile.isNotBlank()) {
          "${item.senderName} • ${item.senderMobile}"
        } else {
          item.senderName
        }
        Text(
          text = headerText,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )

        if (item.type == "bid") {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = BcCalculator.formatRupees(item.amount),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
              color = extra.successGreenContainer,
              shape = RoundedCornerShape(8.dp)
            ) {
              Text(
                text = "बोली",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = extra.successGreen,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
        } else {
          Text(
            text = item.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          Text(
            text = timestampText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}
