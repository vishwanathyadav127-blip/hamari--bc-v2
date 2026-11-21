package com.example.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BcCalculator
import com.example.model.BidMessage
import com.example.model.Committee
import com.example.ui.theme.LocalBcExtraColors
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
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
    val db = remember { Firebase.firestore }

    val roundBids = remember(bids, committee.roundNumber) {
        bids.filter { it.roundNumber == committee.roundNumber }
    }
    var bidInput by remember(committee.currentHighestBid) {
        mutableStateOf((committee.currentHighestBid + 500L).toString())
    }
    var chatInput by remember { mutableStateOf("") }
    var minsInput by remember(committee.timerMinutes) {
        mutableStateOf(committee.timerMinutes.coerceAtLeast(1L).toString())
    }

    // Firebase से दो-चरणीय टाइमर, बीप व ड्यू डेट नियम लोड करें
    var stage1AlertSec by remember { mutableStateOf(15) }
    var stage2BidSec by remember { mutableStateOf(30) }
    var beepEnabled by remember { mutableStateOf(true) }
    var dueDayOfMonth by remember { mutableStateOf(10) }

    LaunchedEffect(Unit) {
        try {
            val doc = db.collection("group_settings").document("rules").get().await()
            if (doc.exists()) {
                stage1AlertSec = (doc.getLong("stage1AlertSeconds") ?: 15L).toInt().coerceIn(5, 120)
                stage2BidSec = (doc.getLong("stage2BidSeconds") ?: 30L).toInt().coerceIn(10, 300)
                beepEnabled = doc.getBoolean("beepEnabled") ?: true
                dueDayOfMonth = (doc.getLong("dueDayOfMonth") ?: 10L).toInt().coerceIn(1, 28)
            }
        } catch (_: Exception) {}
    }

    // बीप साउंड जनरेटर (सुरक्षित)
    val toneGen = remember {
        try {
            ToneGenerator(AudioManager.STREAM_ALARM, 85)
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                toneGen?.release()
            } catch (_: Exception) {}
        }
    }

    // दो-चरणीय (2-Stage) काउंटडाउन लॉजिक
    val isStage2 = committee.isLive && committee.currentHighestBid > 0L
    var stageSecondsLeft by remember(committee.isLive, isStage2, committee.currentHighestBid, stage1AlertSec, stage2BidSec) {
        mutableStateOf(if (isStage2) stage2BidSec else stage1AlertSec)
    }

    // नई बोली आते ही बीप बजाएं और लिस्ट स्क्रॉल करें
    LaunchedEffect(roundBids.size) {
        if (roundBids.isNotEmpty()) {
            listState.animateScrollToItem(roundBids.size - 1)
            if (committee.isLive && beepEnabled) {
                try {
                    toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
                } catch (_: Exception) {}
            }
        }
    }

    // सेकंड-दर-सेकंड टाइमर और आखिरी 5 सेकंड बीप अलर्ट
    LaunchedEffect(committee.isLive, isStage2, committee.currentHighestBid) {
        if (committee.isLive) {
            while (stageSecondsLeft > 0) {
                delay(1000L)
                stageSecondsLeft -= 1
                if (beepEnabled && stageSecondsLeft in 1..5) {
                    try {
                        toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
                    } catch (_: Exception) {}
                }
            }
            if (stageSecondsLeft == 0 && beepEnabled) {
                try {
                    toneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                } catch (_: Exception) {}
            }
        }
    }

    val mins = (countdownRemainingSeconds.coerceAtLeast(0L)) / 60
    val secs = (countdownRemainingSeconds.coerceAtLeast(0L)) % 60
    val timerText = String.format(Locale.US, "%02d:%02d", mins, secs)

    Column(modifier = Modifier.fillMaxSize().background(extra.whatsappBg)) {
        Surface(color = extra.whatsappHeaderBg, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "वापस", tint = Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (committee.isLive) Color(0xFFB91C1C) else Color(0xFF1E3A8A))
                            .border(2.dp, if (committee.isLive) Color(0xFFFFD700) else Color.White.copy(0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (committee.isLive) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LIVE", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD700))
                                Text(timerText, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        } else {
                            Text("🦚 BC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(committee.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = if (committee.isLive) "दौर #${committee.roundNumber} • लाइव बोली चालू" else "दौर #${committee.roundNumber} • बोली बंद",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    // बीप ऑन/ऑफ टॉगल बटन
                    IconButton(onClick = { beepEnabled = !beepEnabled }) {
                        Icon(
                            imageVector = if (beepEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "बीप कंट्रोल",
                            tint = if (beepEnabled) Color(0xFFFFD700) else Color.White.copy(alpha = 0.5f)
                        )
                    }
                    if (isAdmin && committee.isLive) {
                        Button(
                            onClick = onEndAndDeclareWinner,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Stop, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("बोली बंद", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // दो-चरणीय (Two-Stage) लाइव स्टेटस और टाइमर पट्टी
                if (committee.isLive) {
                    Surface(
                        color = if (isStage2) Color(0xFF7F1D1D) else Color(0xFF1E3A8A),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isStage2) "🔥 चरण 2: मुख्य लाइव बोली" else "🔔 चरण 1: बोली अलर्ट (पहली बोली लगाएं)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFFD700)
                                )
                                Surface(
                                    color = if (stageSecondsLeft <= 5) Color(0xFFDC2626) else Color.Black.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (stageSecondsLeft > 0) "⏱ ${stageSecondsLeft}s" else "⏰ समय पूरा!",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (committee.currentHighestBid > 0L) "ऊँची बोली: ${BcCalculator.formatRupees(committee.currentHighestBid)}" else "प्रारंभिक बोली: ₹0",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (committee.currentHighestBidderName.isNotBlank()) {
                                    Text("लीडर: ${committee.currentHighestBidderName}", fontSize = 12.sp, color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!committee.isLive && committee.currentHighestBid > 0L) {
                item {
                    val totalPool = committee.totalBcAmount
                    val highestBid = committee.currentHighestBid
                    val memberCount = committee.totalMembers.coerceAtLeast(1L)
                    val winnerPayout = (totalPool - highestBid).coerceAtLeast(0L)
                    val dividend = highestBid / memberCount
                    val netInstallment = ((totalPool / memberCount) - dividend).coerceAtLeast(0L)

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.5.dp, Color(0xFFFFD700)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🏆 दौर #${committee.roundNumber} बोली परिणाम", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD700))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                            if (committee.currentHighestBidderName.isNotBlank()) {
                                ResultRow("बोली विजेता:", committee.currentHighestBidderName, Color.White, true)
                            }
                            ResultRow("कुल कमेटी राशि:", BcCalculator.formatRupees(totalPool), Color.White)
                            ResultRow("फाइनल बोली (छूट):", BcCalculator.formatRupees(highestBid), Color(0xFFFBBF24))
                            ResultRow("विजेता को मिलने वाली राशि:", BcCalculator.formatRupees(winnerPayout), Color(0xFF38BDF8), true)
                            ResultRow("हर मेंबर का फायदा:", "+ ${BcCalculator.formatRupees(dividend)}", Color(0xFF4ADE80))
                            ResultRow("इस महीने की किस्त:", BcCalculator.formatRupees(netInstallment), Color(0xFFFFD700), true)
                            ResultRow("आखिरी तारीख:", "हर महीने की $dueDayOfMonth तारीख", Color(0xFFF87171), true)
                        }
                    }
                }
            }

            if (roundBids.isEmpty() && committee.isLive) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "🔔 लाइव बोली शुरू हो चुकी है! नीचे से पहली बोली लगाएं या चैट में संदेश भेजें।",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            items(roundBids, key = { it.id }) { msg ->
                val isMine = msg.senderUid == currentUserId
                val isBid = msg.type == "bid" || msg.amount > 0L
                val timeStr = remember(msg.createdAt) {
                    msg.createdAt?.toDate()?.let { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it) } ?: ""
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = when {
                            isBid && isMine -> Color(0xFFDCF8C6)
                            isBid -> Color.White
                            isMine -> Color(0xFFE7FFDB)
                            else -> Color.White
                        },
                        shape = RoundedCornerShape(14.dp),
                        shadowElevation = 2.dp,
                        border = if (isBid) BorderStroke(1.dp, Color(0xFF16A34A)) else null,
                        modifier = Modifier.widthIn(max = 290.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            if (!isMine && msg.senderName.isNotBlank()) {
                                Text(msg.senderName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF075E54))
                            }
                            if (isBid) {
                                Text("💰 बोली: ${BcCalculator.formatRupees(msg.amount)}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF15803D))
                            } else {
                                Text(msg.message, fontSize = 14.sp, color = Color(0xFF111827))
                            }
                            if (timeStr.isNotBlank()) {
                                Text(timeStr, fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            if (committee.isLive) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(500L, 1000L, 2000L, 5000L)) { inc ->
                            val target = committee.currentHighestBid + inc
                            SuggestionChip(
                                onClick = { if (target > committee.currentHighestBid) onPlaceBid(target) },
                                label = { Text("+${BcCalculator.formatRupees(inc)}", fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = bidInput,
                            onValueChange = { bidInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("बोली राशि (₹)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { val amt = bidInput.toLongOrNull() ?: 0L; if (amt > committee.currentHighestBid) onPlaceBid(amt) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Icon(Icons.Filled.Gavel, null)
                            Spacer(Modifier.width(6.dp))
                            Text("बोली लगाएं", fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = chatInput, onValueChange = { chatInput = it }, placeholder = { Text("संदेश लिखें...") }, singleLine = true, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (chatInput.isNotBlank()) { onSendChat(chatInput.trim()); chatInput = "" } }) {
                            Icon(Icons.AutoMirrored.Filled.Send, "भेजें", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else if (isAdmin) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = minsInput,
                        onValueChange = { minsInput = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("मिनट") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(100.dp)
                    )
                    Button(
                        onClick = { onStartLiveBidding((minsInput.toLongOrNull() ?: 5L).coerceIn(1L, 60L)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(54.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("नई बोली शुरू करें", fontWeight = FontWeight.ExtraBold)
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = chatInput, onValueChange = { chatInput = it }, placeholder = { Text("चैट संदेश लिखें...") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (chatInput.isNotBlank()) { onSendChat(chatInput.trim()); chatInput = "" } }) {
                        Icon(Icons.AutoMirrored.Filled.Send, "भेजें", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, valueColor: Color, isBold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = if (isBold) 13.sp else 12.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal, color = Color(0xFFCBD5E1))
        Text(value, fontSize = if (isBold) 14.sp else 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
