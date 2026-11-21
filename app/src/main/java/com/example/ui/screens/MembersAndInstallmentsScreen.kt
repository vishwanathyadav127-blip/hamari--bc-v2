package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    val context = LocalContext.current
    val db = remember { Firebase.firestore }

    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newMobile by remember { mutableStateOf("") }

    // नियम व UPI / QR स्टेट्स
    var dueDayOfMonth by remember { mutableStateOf(10) }
    var dailyLateFine by remember { mutableStateOf(100L) }
    var adminUpiId by remember { mutableStateOf("") }
    var adminUpiName by remember { mutableStateOf("Krishiv Group") }
    var qrImageUrl by remember { mutableStateOf("") }

    // लोकल पेमेंट मोड और टाइमस्टैम्प ट्रैकिंग
    var paymentTimestamps by remember { mutableStateOf(mapOf<String, Long>()) }
    var paymentModes by remember { mutableStateOf(mapOf<String, String>()) }
    var memberForModeDialog by remember { mutableStateOf<CommitteeMember?>(null) }

    // टैब स्विचर: 0 = सदस्य व किस्त, 1 = 1 साल का लेजर (12 महीने)
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val doc = db.collection("group_settings").document("rules").get().await()
            if (doc.exists()) {
                dueDayOfMonth = (doc.getLong("dueDayOfMonth") ?: 10L).toInt().coerceIn(1, 28)
                dailyLateFine = (doc.getLong("dailyLateFine") ?: 100L).coerceAtLeast(0L)
                adminUpiId = doc.getString("adminUpiId") ?: ""
                adminUpiName = doc.getString("adminUpiName") ?: "Krishiv Group"
                qrImageUrl = doc.getString("qrImageUrl") ?: ""
            }
        } catch (_: Exception) {}
    }

    val overdueDays = remember(dueDayOfMonth) {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        if (today > dueDayOfMonth) (today - dueDayOfMonth) else 0
    }
    val currentLateFine = overdueDays * dailyLateFine

    val installmentAmount = if (committee.perMemberInstallment > 0L) {
        committee.perMemberInstallment
    } else {
        committee.totalBcAmount / committee.totalMembers.coerceAtLeast(1L)
    }

    val paidCount = members.count { it.isPaid }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    // 1 साल (अधिकतम 12 दौर) का सुरक्षित लेजर
    val yearlyRounds = remember(rounds) {
        rounds.takeLast(12).reversed()
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
            // मुख्य हेडर कार्ड
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD700)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "किस्त व लेखा-जोखा • दौर #${committee.roundNumber}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD700)
                                )
                                Text(
                                    "प्रति सदस्य किस्त: ${BcCalculator.formatRupees(installmentAmount)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                            Surface(color = Color(0xFF14532D), shape = RoundedCornerShape(12.dp)) {
                                Text(
                                    "जमा: $paidCount / ${members.size}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4ADE80),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Surface(
                            color = if (overdueDays > 0) Color(0xFF7F1D1D) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Last Due Date: हर महीने की $dueDayOfMonth तारीख",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (overdueDays > 0) "लेट फाइन: ${BcCalculator.formatRupees(currentLateFine)} ($overdueDays दिन)" else "फाइन: ₹${dailyLateFine}/दिन",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (overdueDays > 0) Color(0xFFFCA5A5) else Color(0xFFFFD700)
                                )
                            }
                        }

                        // UPI / QR पेमेंट पट्टी (यदि एडमिन ने सेट किया हो)
                        if (adminUpiId.isNotBlank() || qrImageUrl.isNotBlank()) {
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("ऑनलाइन किस्त जमा (UPI / QR)", fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                        if (adminUpiId.isNotBlank()) {
                                            Text("UPI: $adminUpiId ($adminUpiName)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        }
                                        if (qrImageUrl.isNotBlank()) {
                                            Text("QR नोट: $qrImageUrl", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                                        }
                                    }
                                    if (adminUpiId.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", adminUpiId))
                                                Toast.makeText(context, "UPI ID कॉपी हो गया: $adminUpiId", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "UPI कॉपी", tint = Color(0xFFFFD700))
                                        }
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isAdmin) {
                                Button(
                                    onClick = { showAddDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color(0xFF0F172A)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("मेंबर जोड़ें", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                            Button(
                                onClick = {
                                    val nowStr = dateFormatter.format(Date())
                                    val sb = StringBuilder()
                                    sb.append("🦚 *${committee.name} - किस्त रिपोर्ट*\n")
                                    sb.append("📅 *तारीख:* $nowStr\n")
                                    sb.append("🔄 *दौर:* #${committee.roundNumber} | *किस्त:* ${BcCalculator.formatRupees(installmentAmount)}\n")
                                    sb.append("⏰ *Last Due Date:* हर महीने की $dueDayOfMonth तारीख\n")
                                    if (adminUpiId.isNotBlank()) {
                                        sb.append("💳 *UPI ID:* $adminUpiId\n")
                                    }
                                    sb.append("━━━━━━━━━━━━━━━\n")
                                    members.forEachIndexed { idx, m ->
                                        val ts = paymentTimestamps[m.id]
                                        val mode = paymentModes[m.id]?.let { " [$it]" } ?: ""
                                        val tInfo = if (ts != null) " (${dateFormatter.format(Date(ts))})$mode" else mode
                                        if (m.isPaid) {
                                            sb.append("${idx + 1}. ✅ *${m.name}* - जमा$tInfo\n")
                                        } else {
                                            val fTxt = if (currentLateFine > 0L) " + ₹$currentLateFine फाइन" else ""
                                            sb.append("${idx + 1}. ⏳ *${m.name}* - बाकी$fTxt\n")
                                        }
                                    }
                                    sb.append("━━━━━━━━━━━━━━━\nकुल जमा: $paidCount / ${members.size} सदस्य")
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, sb.toString())
                                    }
                                    context.startActivity(Intent.createChooser(intent, "हिसाब शेयर करें"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("WhatsApp शेयर", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // टैब स्विचर: सदस्य किस्त बनाम 1 साल लेजर
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("सदस्य व किस्त (${members.size})", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.Group, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("1 साल लेजर (${yearlyRounds.size} दौर)", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (selectedTab == 0) {
                // क्रैश सुरक्षा: यदि अभी कोई मेंबर नहीं जुड़ा है
                if (members.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.GroupAdd, null, modifier = Modifier.size(40.dp), tint = Color.Gray)
                                Text("अभी इस ग्रुप में कोई सदस्य नहीं जुड़ा है", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("ऊपर 'मेंबर जोड़ें' बटन से सदस्यों को जोड़ें", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                } else {
                    items(members, key = { it.id }) { member ->
                        val ts = paymentTimestamps[member.id]
                        val modeLabel = paymentModes[member.id]
                        val totalDue = installmentAmount + currentLateFine

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                1.2.dp,
                                if (member.isPaid) Color(0xFF16A34A).copy(alpha = 0.6f)
                                else if (overdueDays > 0) Color(0xFFDC2626).copy(alpha = 0.7f)
                                else Color(0xFFDC2626).copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(if (member.isPaid) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (member.isPaid) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = if (member.isPaid) Color(0xFF15803D) else Color(0xFFDC2626)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(member.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            if (member.mobileNumber.isNotBlank()) {
                                                Text("📱 ${member.mobileNumber}", fontSize = 12.sp, color = Color.Gray)
                                            }
                                            if (member.isPaid) {
                                                val tLabel = if (ts != null) dateFormatter.format(Date(ts)) else "जमा दर्ज"
                                                val mTag = if (modeLabel != null) " • $modeLabel" else ""
                                                Text(
                                                    "✅ जमा: ${BcCalculator.formatRupees(installmentAmount)}$mTag • $tLabel",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF15803D)
                                                )
                                            } else {
                                                Text(
                                                    text = if (currentLateFine > 0L) "बाकी: ${BcCalculator.formatRupees(installmentAmount)} + ₹$currentLateFine फाइन = ${BcCalculator.formatRupees(totalDue)}"
                                                    else "बाकी किस्त: ${BcCalculator.formatRupees(installmentAmount)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFFDC2626)
                                                )
                                            }
                                        }
                                    }

                                    if (isAdmin) {
                                        Button(
                                            onClick = {
                                                if (!member.isPaid) {
                                                    memberForModeDialog = member
                                                } else {
                                                    onToggleInstallment(member)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (member.isPaid) Color(0xFF475569) else Color(0xFF16A34A),
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text(if (member.isPaid) "बाकी करें" else "जमा करें", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // यदि किस्त बाकी है और एडमिन UPI ID सेट है, तो सीधा UPI भुगतान बटन दिखाएं
                                if (!member.isPaid && adminUpiId.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val upiUri = Uri.parse(
                                                    "upi://pay?pa=${Uri.encode(adminUpiId)}&pn=${Uri.encode(adminUpiName)}&am=$totalDue&cu=INR&tn=${Uri.encode("${committee.name} Round ${committee.roundNumber} - ${member.name}")}"
                                                )
                                                val intent = Intent(Intent.ACTION_VIEW, upiUri)
                                                context.startActivity(Intent.createChooser(intent, "UPI ऐप चुनें"))
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "कोई UPI ऐप नहीं मिला", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFF2563EB)),
                                        modifier = Modifier.fillMaxWidth().height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.QrCode, null, modifier = Modifier.size(16.dp), tint = Color(0xFF2563EB))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "UPI / QR से जमा करें (${BcCalculator.formatRupees(totalDue)})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2563EB)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 1 साल (12 महीने) का लेजर व राउंड इतिहास
                if (yearlyRounds.isEmpty()) {
                    item {
                        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.EventNote, null, modifier = Modifier.size(40.dp), tint = Color.Gray)
                                Text("अभी तक कोई पिछला दौर दर्ज नहीं है", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("जैसे-जैसे हर महीने बोली पूरी होगी, 12 महीनों का पूरा रिकॉर्ड यहाँ सुरक्षित रहेगा", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                } else {
                    items(yearlyRounds) { round ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("दौर #${round.roundNumber}", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFFD97706))
                                    Text(
                                        dateFormatter.format(round.createdAt?.toDate() ?: java.util.Date()),
                                        
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Text("🏆 विजेता: ${round.winnerName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("बोली राशि: ${BcCalculator.formatRupees(round.winningBid)}", fontSize = 12.sp, color = Color(0xFFDC2626))
                        Text("विजेता को मिले: ${BcCalculator.formatRupees(round.netPayableToWinner)}", fontSize = 12.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                    }

                            }
                        }
                    }
                }
            }
        }
    }

    // पेमेंट मोड चुनने का डायलॉग (Cash / Online UPI / QR)
    memberForModeDialog?.let { targetMember ->
        AlertDialog(
            onDismissRequest = { memberForModeDialog = null },
            title = { Text("पेमेंट का तरीका चुनें", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${targetMember.name} की किस्त किस माध्यम से जमा हुई है?")
                    listOf("Cash (नकद)", "UPI / Online", "QR Scan").forEach { mode ->
                        Button(
                            onClick = {
                                paymentTimestamps = paymentTimestamps + (targetMember.id to System.currentTimeMillis())
                                paymentModes = paymentModes + (targetMember.id to mode)
                                onToggleInstallment(targetMember)
                                memberForModeDialog = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(mode, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { memberForModeDialog = null }) { Text("रद्द करें") }
            }
        )
    }

    // नया सदस्य जोड़ने का डायलॉग
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("नया सदस्य जोड़ें", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("सदस्य का नाम") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = newMobile,
                        onValueChange = { if (it.length <= 10) newMobile = it.filter { ch -> ch.isDigit() } },
                        label = { Text("मोबाइल नंबर (10 अंक)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        onAddMember(newName.trim(), newMobile.trim())
                        newName = ""
                        newMobile = ""
                        showAddDialog = false
                        Toast.makeText(context, "नया सदस्य जुड़ गया", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("जोड़ें") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("रद्द करें") }
            }
        )
    }
}
