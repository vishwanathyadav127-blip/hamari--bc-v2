package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BcCalculator
import com.example.model.Committee
import com.example.model.CommitteeMember
import com.example.model.RoundRecord
import com.example.ui.theme.LocalBcExtraColors
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun MembersAndInstallmentsScreen(
    committee: Committee,
    members: List<CommitteeMember>,
    rounds: List<RoundRecord>,
    isAdmin: Boolean,
    currentUserId: String,
    onAddMember: (name: String, mobile: String) -> Unit,
    onToggleInstallment: (CommitteeMember) -> Unit,
    onDeleteMember: (CommitteeMember) -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = LocalBcExtraColors.current
    val context = LocalContext.current
    val db = remember { Firebase.firestore }

    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newMobile by remember { mutableStateOf("") }

    // Admin Due Date & Late Fine Rules from Firestore
    var dueDayOfMonth by remember { mutableStateOf(10) }
    var dailyLateFine by remember { mutableStateOf(100L) }
    var paymentTimestamps by remember { mutableStateOf>(emptyMap()) }

    LaunchedEffect(Unit) {
        try {
            val rulesDoc = db.collection("group_settings").document("rules").get().await()
            if (rulesDoc.exists()) {
                dueDayOfMonth = (rulesDoc.getLong("dueDayOfMonth") ?: 10L).toInt().coerceIn(1, 28)
                dailyLateFine = (rulesDoc.getLong("dailyLateFine") ?: 100L).coerceAtLeast(0L)
            }
        } catch (_: Exception) {
        }
    }

    // Calculate overdue days if past dueDayOfMonth
    val overdueDays = remember(dueDayOfMonth) {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        if (today > dueDayOfMonth) (today - dueDayOfMonth) else 0
    }
    val currentLateFinePerUnpaid = overdueDays * dailyLateFine

    val installmentAmount = if (committee.perMemberInstallment > 0L) {
        committee.perMemberInstallment
    } else {
        val count = committee.totalMembers.coerceAtLeast(1L)
        committee.totalBcAmount / count
    }

    val paidCount = members.count { it.isPaid }
    val dateTimeFormatter = remember {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Summary & WhatsApp Share Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD700)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "किस्त व लेखा-जोखा • दौर #${committee.roundNumber}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD700)
                                )
                                Text(
                                    text = "प्रति सदस्य किस्त: ${BcCalculator.formatRupees(installmentAmount)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                            Surface(
                                color = Color(0xFF14532D),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "जमा: $paidCount / ${members.size}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4ADE80),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Due Date & Late Fine Status Strip
                        Surface(
                            color = if (overdueDays > 0) Color(0xFF7F1D1D) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Last Due Date: हर महीने की $dueDayOfMonth तारीख",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (overdueDays > 0) {
                                        "लेट फाइन: ${BcCalculator.formatRupees(currentLateFinePerUnpaid)} ($overdueDays दिन)"
                                    } else {
                                        "फाइन: ₹${dailyLateFine}/दिन"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (overdueDays > 0) Color(0xFFFCA5A5) else Color(0xFFFFD700)
                                )
                            }
                        }

                        // Admin Action Buttons: Add Member + Share Full Ledger on WhatsApp
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isAdmin) {
                                Button(
                                    onClick = { showAddDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFFD700),
                                        contentColor = Color(0xFF0F172A)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("मेंबर जोड़ें", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    val nowStr = dateTimeFormatter.format(Date())
                                    val sb = StringBuilder()
                                    sb.append("🦚 *${committee.name} - किस्त रिपोर्ट*\n")
                                    sb.append("📅 *तारीख व समय:* $nowStr\n")
                                    sb.append("🔄 *दौर:* #${committee.roundNumber} | *किस्त:* ${BcCalculator.formatRupees(installmentAmount)}\n")
                                    sb.append("⏰ *Last Due Date:* हर महीने की $dueDayOfMonth तारीख\n")
                                    sb.append("━━━━━━━━━━━━━━━\n")
                                    members.forEachIndexed { idx, m ->
                                        val timeInfo = paymentTimestamps[m.id]?.let {
                                            " (${dateTimeFormatter.format(Date(it))})"
                                        } ?: ""
                                        if (m.isPaid) {
                                            sb.append("${idx + 1}. ✅ *${m.name}* - जमा$timeInfo\n")
                                        } else {
                                            val fineText = if (currentLateFinePerUnpaid > 0L) {
                                                " + ₹$currentLateFinePerUnpaid फाइन"
                                            } else ""
                                            sb.append("${idx + 1}. ⏳ *${m.name}* - बाकी$fineText\n")
                                        }
                                    }
                                    sb.append("━━━━━━━━━━━━━━━\n")
                                    sb.append("कुल जमा: $paidCount / ${members.size} सदस्य")

                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, sb.toString())
                                        setPackage("com.whatsapp")
                                    }
                                    try {
                                        context.startActivity(sendIntent)
                                    } catch (_: Exception) {
                                        val fallback = Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, sb.toString())
                                            },
                                            "हिसाब शेयर करें"
                                        )
                                        context.startActivity(fallback)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF16A34A),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WhatsApp शेयर", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Members List with Date/Time & Late Fine Ledger
            items(members, key = { it.id }) { member ->
                val recordedTime = paymentTimestamps[member.id]
                val totalPayableForMember = if (member.isPaid) {
                    installmentAmount
                } else {
                    installmentAmount + currentLateFinePerUnpaid
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (member.isPaid) Color(0xFF16A34A).copy(alpha = 0.5f)
                        else if (overdueDays > 0) Color(0xFFDC2626).copy(alpha = 0.6f)
                        else Color.Gray.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (member.isPaid) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (member.isPaid) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = if (member.isPaid) Color(0xFF15803D) else Color(0xFFDC2626)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = member.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (member.mobileNumber.isNotBlank()) {
                                    Text(
                                        text = "📱 ${member.mobileNumber}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                if (member.isPaid) {
                                    val timeLabel = recordedTime?.let {
                                        dateTimeFormatter.format(Date(it))
                                    } ?: "समय दर्ज"
                                    Text(
                                        text = "✅ जमा: ${BcCalculator.formatRupees(installmentAmount)} • $timeLabel",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF15803D)
                                    )
                                } else {
                                    Text(
                                        text = if (currentLateFinePerUnpaid > 0L) {
                                            "बाकी: ${BcCalculator.formatRupees(installmentAmount)} + ₹$currentLateFinePerUnpaid फाइन = ${BcCalculator.formatRupees(totalPayableForMember)}"
                                        } else {
                                            "बाकी किस्त: ${BcCalculator.formatRupees(installmentAmount)}"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFDC2626)
                                    )
                                }
                            }
                        }

                        if (isAdmin) {
                            Column(horizontalAlignment = Alignment.End) {
                                Button(
                                    onClick = {
                                        if (!member.isPaid) {
                                            paymentTimestamps = paymentTimestamps + (member.id to System.currentTimeMillis())
                                        }
                                        onToggleInstallment(member)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (member.isPaid) Color(0xFF475569) else Color(0xFF16A34A),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = if (member.isPaid) "बाकी करें" else "जमा करें",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Member Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("नया सदस्य जोड़ें", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("सदस्य का नाम") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Outli
