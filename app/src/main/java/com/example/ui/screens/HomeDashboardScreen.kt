package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BcCalculator
import com.example.model.Committee
import com.example.ui.components.WinnerBreakdownCard
import com.example.ui.components.shareToWhatsApp
import com.example.ui.theme.LocalBcExtraColors
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class BcGroupTab(
    val id: String,
    val title: String,
    val amount: Long,
    val members: Long
)

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
    val db = remember { Firebase.firestore }
    val scope = rememberCoroutineScope()

    var customMinutesInput by remember(committee.timerMinutes) {
        mutableStateOf(committee.timerMinutes.coerceAtLeast(1L).toString())
    }

    // मल्टी-BC ग्रुप स्विचर स्टेट्स (डिफ़ॉल्ट + Firebase से लोड)
    val defaultGroups = remember(committee.id, committee.name, committee.totalBcAmount, committee.totalMembers) {
        listOf(
            BcGroupTab(committee.id.ifBlank { "main" }, committee.name.ifBlank { "मुख्य BC" }, committee.totalBcAmount, committee.totalMembers),
            BcGroupTab("bc_60k", "₹60,000 BC", 60000L, 12L),
            BcGroupTab("bc_70k", "₹70,000 BC", 70000L, 14L),
            BcGroupTab("bc_1l", "₹1,00,000 BC", 100000L, 10L),
            BcGroupTab("bc_1_2l", "₹1,20,000 BC", 120000L, 12L),
            BcGroupTab("bc_2l_dec15", "₹2,00,000 BC (15 Dec)", 200000L, 20L)
        ).distinctBy { it.title }
    }
    var groupTabs by remember(defaultGroups) { mutableStateOf(defaultGroups) }
    var selectedGroupTab by remember(groupTabs) { mutableStateOf(groupTabs.first()) }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var newGroupTitle by remember { mutableStateOf("") }
    var newGroupAmount by remember { mutableStateOf("200000") }
    var newGroupMembers by remember { mutableStateOf("20") }

    // Firebase से अतिरिक्त BC ग्रुप लोड करें (क्रैश-सुरक्षित)
    LaunchedEffect(Unit) {
        try {
            val snap = db.collection("bc_groups").get().await()
            if (!snap.isEmpty) {
                val loaded = snap.documents.mapNotNull { d ->
                    val t = d.getString("title") ?: return@mapNotNull null
                    val amt = d.getLong("amount") ?: 100000L
                    val mem = d.getLong("members") ?: 10L
                    BcGroupTab(d.id, t, amt, mem)
                }
                if (loaded.isNotEmpty()) {
                    groupTabs = (defaultGroups + loaded).distinctBy { it.title }
                }
            }
        } catch (_: Exception) {}
    }

    val activeBcAmount = if (selectedGroupTab.id == committee.id || selectedGroupTab.id == "main") {
        committee.totalBcAmount
    } else {
        selectedGroupTab.amount
    }
    val activeBcMembers = if (selectedGroupTab.id == committee.id || selectedGroupTab.id == "main") {
        committee.totalMembers.coerceAtLeast(1L)
    } else {
        selectedGroupTab.members.coerceAtLeast(1L)
    }
    val activeBcTitle = if (selectedGroupTab.id == committee.id || selectedGroupTab.id == "main") {
        committee.name
    } else {
        selectedGroupTab.title
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0. कस्टम ब्रांडिंग हेडर व मल्टी-BC ग्रुप स्विचर
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFD700)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🦚", fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("KRISHIV GROUP • हमारी BC कमेटी", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD700))
                                Text("5 वर्षों का अटूट विश्वास • 0% प्लेटफॉर्म शुल्क", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                            }
                        }
                        if (isAdmin) {
                            IconButton(
                                onClick = { showNewGroupDialog = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = "नया BC ग्रुप", tint = Color(0xFFFFD700))
                            }
                        }
                    }

                    // मल्टी-BC ग्रुप स्विचर चिप्स
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groupTabs, key = { it.title }) { tab ->
                            val isSelected = selectedGroupTab.title == tab.title
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedGroupTab = tab },
                                label = {
                                    Text(
                                        tab.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFD700),
                                    selectedLabelColor = Color(0xFF0F172A),
                                    containerColor = Color(0xFF1E293B),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

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
                                text = if (committee.isLive) "● LIVE बोली चालू है" else "$activeBcTitle • दौर #${committee.roundNumber}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        if (committee.isLive) {
                            val mins = countdownRemainingSeconds.coerceAtLeast(0L) / 60
                            val secs = countdownRemainingSeconds.coerceAtLeast(0L) % 60
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
                                val hrs = scheduleRemainingSeconds.coerceAtLeast(0L) / 3600
                                val remM = (scheduleRemainingSeconds.coerceAtLeast(0L) % 3600) / 60
                                val remS = scheduleRemainingSeconds.coerceAtLeast(0L) % 60
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "ऑटो-स्टार्ट व सायरन में शेष: %02d:%02d:%02d",
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
                                colors = OutlinedTextFieldDefaults.colors(
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

            // 2. Quick Metric Grid (चयनित BC ग्रुप के अनुसार सुरक्षित गणना)
            val baseInstallment = activeBcAmount / activeBcMembers.coerceAtLeast(1L)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    icon = Icons.Filled.AccountBalanceWallet,
                    label = "कुल BC राशि",
                    value = BcCalculator.formatRupees(activeBcAmount),
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    icon = Icons.Filled.Groups,
                    label = "कुल सदस्य",
                    value = "$activeBcMembers सदस्य",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    icon = Icons.Filled.EmojiEvents,
                    label = "मासिक किस्त",
                    value = if (selectedGroupTab.id == committee.id && committee.winnerDeclared && committee.perMemberInstallment > 0) {
                        BcCalculator.formatRupees(committee.perMemberInstallment)
                    } else {
                        BcCalculator.formatRupees(baseInstallment)
                    },
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    icon = Icons.Filled.Key,
                    label = "इनवाइट कोड",
                    value = committee.inviteCode.ifBlank { "KRISHIV" },
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Winner & Installment Calculation Card
            if (committee.winnerDeclared) {
                WinnerBreakdownCard(
                    committeeName = committee.name,
                    roundNumber = committee.roundNumber,
                    totalBcAmount = committee.totalBcAmount,
                    totalMembers = committee.totalMembers.coerceAtLeast(1L),
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
                            Text("एडमिन सेटिंग्स व समय बदलें")
                        }
                    }
                }
            }

            // 4. WhatsApp Invite & Direct APK Share Card
            OutlinedButton(
                onClick = {
                    val text = BcCalculator.buildInviteShareText(
                        committeeName = activeBcTitle,
                        inviteCode = committee.inviteCode,
                        totalBcAmount = activeBcAmount,
                        totalMembers = activeBcMembers,
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

    // नया BC ग्रुप जोड़ने का डायलॉग (Admin के लिए)
    if (showNewGroupDialog) {
        AlertDialog(
            onDismissRequest = { showNewGroupDialog = false },
            title = { Text("नया BC ग्रुप जोड़ें", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newGroupTitle,
                        onValueChange = { newGroupTitle = it },
                        label = { Text("ग्रुप का नाम (जैसे: ₹2,00,000 BC)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newGroupAmount,
                        onValueChange = { newGroupAmount = it.filter { ch -> ch.isDigit() } },
                        label = { Text("कुल BC राशि (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newGroupMembers,
                        onValueChange = { newGroupMembers = it.filter { ch -> ch.isDigit() } },
                        label = { Text("कुल सदस्य संख्या") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val title = newGroupTitle.trim()
                    val amt = newGroupAmount.toLongOrNull() ?: 200000L
                    val mem = (newGroupMembers.toLongOrNull() ?: 20L).coerceAtLeast(1L)
                    if (title.isNotBlank()) {
                        scope.launch {
                            try {
                                val docRef = db.collection("bc_groups").document()
                                docRef.set(mapOf("title" to title, "amount" to amt, "members" to mem)).await()
                                val newTab = BcGroupTab(docRef.id, title, amt, mem)
                                groupTabs = (groupTabs + newTab).distinctBy { it.title }
                                selectedGroupTab = newTab
                                Toast.makeText(context, "$title जुड़ गया!", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {}
                        }
                        newGroupTitle = ""
                        showNewGroupDialog = false
                    }
                }) { Text("जोड़ें") }
            },
            dismissButton = {
                TextButton(onClick = { showNewGroupDialog = false }) { Text("रद्द करें") }
            }
        )
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
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
