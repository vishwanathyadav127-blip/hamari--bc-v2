package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BcCalculator
import com.example.model.BidMessage
import com.example.model.Committee
import com.example.ui.theme.LocalBcExtraColors
import java.text.SimpleDateFormat
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
        bids.filter { it.roundNumber == committee.roundNumber }
    }

    var bidAmountInput by remember(committee.currentHighestBid) {
        val suggested = (committee.currentHighestBid + 500L)
        mutableStateOf(suggested.toString())
    }
    var chatMessageInput by remember { mutableStateOf("") }
    var startMinutesInput by remember(committee.timerMinutes) {
        mutableStateOf(committee.timerMinutes.coerceAtLeast(1L).toString())
    }

    LaunchedEffect(roundBids.size) {
        if (roundBids.isNotEmpty()) {
            listState.animateScrollToItem(roundBids.size - 1)
        }
    }

    val mins = (countdownRemainingSeconds.coerceAtLeast(0L)) / 60
    val secs = (countdownRemainingSeconds.coerceAtLeast(0L)) % 60
    val timerText = String.format(Locale.US, "%02d:%02d", mins, secs)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(extra.whatsappBg)
    ) {
        Surface(
            color = extra.whatsappHeaderBg,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "वापस",
                            tint = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (committee.isLive) Color(0xFFB91C1C) else Color(0xFF1E3A8A)
                            )
                            .border(
                                width = 2.dp,
                                color = if (committee.isLive) Color(0xFFFFD700) else Color.White.copy(alpha = 0.6f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (committee.isLive) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "LIVE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFFD700),
                                    lineHeight = 9.sp
                                )
                                Text(
                                    text = timerText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    lineHeight = 14.sp
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "🦚", fontSize = 14.sp, lineHeight = 15.sp)
                                Text(
                                    text = "BC",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD700),
                                    lineHeight = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = committee.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (committee.isLive) {
                                "दौर #${committee.roundNumber} • बोली चालू है"
                            } else {
                                "दौर #${committee.roundNumber} • बोली बंद"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    if (isAdmin && committee.isLive) {
                        Button(
                            onClick = onEndAndDeclareWinner,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("end_bidding_button")
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("बोली बंद", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (committee.isLive) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, start = 6.dp, end = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (committee.currentHighestBid > 0L) {
                                    "ऊँची बोली: ${BcCalculator.formatRupees(committee.currentHighestBid)}"
                                } else {
                                    "पहली बोली लगाएं"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                            if (committee.currentHighestBidderName.isNotBlank()) {
                                Text(
                                    text = "लीडर: ${committee.currentHighestBidderName}",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!committee.isLive && committee.currentHighestBid > 0L) {
                item {
                    val totalPool = committee.totalBcAmount
                    val highestBid = committee.currentHighestBid
                    val memberCount = committee.totalMembers.coerceAtLeast(1L)
                    val perMemberDividend = highestBid / memberCount
                    val baseInstallment = totalPool / memberCount
                    val netPayableInstallment = (baseInstallment - perMemberDividend).coerceAtLeast(0L)

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.5.dp, Color(0xFFFFD700)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🏆 दौर #${committee.roundNumber} बोली परिणाम व हिसाब",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFD700)
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                            if (committee.currentHighestBidderName.isNotBlank()) {
                                ResultRow(
                                    label = "बोली विजेता:",
                                    value = committee.currentHighestBidderName,
                                    valueColor = Color.White
                                )
                            }
                            ResultRow(
                                label = "कुल कमेटी राशि:",
                                value = BcCalculator.formatRupees(totalPool),
                                valueColor = Color.White
                            )
                            ResultRow(
                                label = "फाइनल बोली (छूट):",
                                value = BcCalculator.formatRupees(highestBid),
                                valueColor = Color(0xFFFBBF24)
                            )
                            ResultRow(
                                label = "हर मेंबर का फायदा (बचत):",
                                value = "+ ${BcCalculator.formatRupees(perMemberDividend)}",
                                valueColor = Color(0xFF4ADE80)
                            )
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    ResultRow(
                                        label = "इस महीने भरने वाली किस्त:",
                                        value = BcCalculator.formatRupees(netPayableInstallment),
                                        valueColor = Color(0xFFFFD700),
                                        isBold = true
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    ResultRow(
                                        label = "आखिरी तारीख (Last Due Date):",
                                        value = "हर महीने की 10 तारीख",
                                        valueColor = Color(0xFFF87171),
                                        isBold = true
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(roundBids, key = { it.id }) { msg ->
                val isMine = msg.senderUid == currentUserId
                val isBidMsg = msg.type == "bid" || msg.amount > 0L
                val timeStr = remember(msg.createdAt) {
                    msg.createdAt?.toDate()?.let {
                        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it)
                    } ?: ""
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = when {
                            isBidMsg && isMine -> Color(0xFFDCF8C6)
                            isBidMsg -> Color.White
                            isMine -> Color(0xFFE7FFDB)
                            else -> Color.White
                        },
                        shape = RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isMine) 14.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 14.dp
                        ),
                        shadowElevation = 2.dp,
                        border = if (isBidMsg) BorderStroke(1.dp, Color(0xFF16A34A)) else null,
                        modifier = Modifier.widthIn(max = 290.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            if (!isMine && msg.senderName.isNotBlank()) {
                                Text(
                                    text = msg.senderName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF075E54)
                                )
                            }
                            if (isBidMsg) {
                                Text(
                                    text = "💰 बोली: ${BcCalculator.formatRupees(msg.amount)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF15803D)
                                )
                            } else {
                                Text(
                                    text = msg.message,
                                    fontSize = 14.sp,
                                    color = Color(0xFF111827)
                                )
                            }
                            if (timeStr.isNotBlank()) {
                                Text(
                                    text = timeStr,
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

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
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val increments = listOf(500L, 1000L, 2000L, 5000L)
                        items(increments) { inc ->
                            val target = (committee.currentHighestBid + inc)
                            SuggestionChip(
                                onClick = {
                                    if (target > committee.currentHighestBid) {
                                        onPlaceBid(target)
                                    }
                                },
                                label = {
                                    Text(
                                        text = "+${BcCalculator.formatRupees(inc)}",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )
                        }
                    }

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
             
