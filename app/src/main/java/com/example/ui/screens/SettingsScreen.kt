package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppThemeMode
import com.example.model.Committee
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: AppThemeMode,
    soundEnabled: Boolean,
    committee: Committee?,
    userProfile: UserProfile?,
    isAdmin: Boolean,
    onSelectTheme: (AppThemeMode) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onTestSiren: () -> Unit,
    onSaveCommitteeSettings: (String, Long, Long, Long, Long, String, String) -> Unit,
    onUpdateProfile: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { Firebase.firestore }

    // प्रोफाइल स्टेट्स
    var profileName by remember(userProfile) { mutableStateOf(userProfile?.displayName ?: "") }
    var profileMobile by remember(userProfile) { mutableStateOf(userProfile?.mobileNumber ?: "") }

    // मेंबर और कॉमन पासवर्ड/PIN स्टेट्स
    var newMemberName by remember { mutableStateOf("") }
    var newMemberMobile by remember { mutableStateOf("") }
    var newMemberPin by remember { mutableStateOf("1234") }
    var commonMemberPin by remember { mutableStateOf("1234") }
    var isSavingMember by remember { mutableStateOf(false) }

    // नियम, दो-चरणीय टाइमर और सायरन/बीप स्टेट्स
    var dueDay by remember { mutableStateOf("10") }
    var dailyFine by remember { mutableStateOf("100") }
    var stage1AlertSec by remember { mutableStateOf("15") }
    var stage2BidSec by remember { mutableStateOf("30") }
    var beepEnabled by remember { mutableStateOf(true) }
    var toneName by remember { mutableStateOf("डिफ़ॉल्ट तेज़ सायरन अलर्ट") }
    var isSavingRules by remember { mutableStateOf(false) }

    // UPI / QR पेमेंट स्टेट्स
    var adminUpiId by remember { mutableStateOf("") }
    var adminUpiName by remember { mutableStateOf("Vishwanath Yadav") }
    var qrImageUrl by remember { mutableStateOf("") }
    var isSavingPayment by remember { mutableStateOf(false) }

    // कमेटी मुख्य सेटिंग्स
    var cName by remember(committee) { mutableStateOf(committee?.name ?: "कृषिव ग्रुप BC") }
    var cPool by remember(committee) { mutableStateOf((committee?.totalBcAmount ?: 100000L).toString()) }
    var cMembers by remember(committee) { mutableStateOf((committee?.totalMembers ?: 10L).toString()) }
    var cTimer by remember(committee) { mutableStateOf((committee?.timerMinutes ?: 5L).toString()) }

    // Accordion कंट्रोल स्टेट (कौन सा सेक्शन खुला है)
    var expandedSection by remember { mutableStateOf("members") }

    // Firebase से मौजूदा सेटिंग्स लोड करें
    LaunchedEffect(Unit) {
        try {
            val doc = db.collection("group_settings").document("rules").get().await()
            if (doc.exists()) {
                dueDay = (doc.getLong("dueDayOfMonth") ?: 10L).toString()
                dailyFine = (doc.getLong("dailyLateFine") ?: 100L).toString()
                stage1AlertSec = (doc.getLong("stage1AlertSeconds") ?: 15L).toString()
                stage2BidSec = (doc.getLong("stage2BidSeconds") ?: 30L).toString()
                beepEnabled = doc.getBoolean("beepEnabled") ?: true
                toneName = doc.getString("sirenToneName") ?: "डिफ़ॉल्ट तेज़ सायरन अलर्ट"
                commonMemberPin = doc.getString("commonMemberPin") ?: "1234"
                adminUpiId = doc.getString("adminUpiId") ?: ""
                adminUpiName = doc.getString("adminUpiName") ?: "Vishwanath Yadav"
                qrImageUrl = doc.getString("qrImageUrl") ?: ""
            }
        } catch (_: Exception) {}
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                val title = RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "कस्टम सायरन टोन"
                toneName = title
                scope.launch {
                    try {
                        db.collection("group_settings").document("rules")
                            .set(mapOf("sirenToneUri" to uri.toString(), "sirenToneName" to title), SetOptions.merge()).await()
                        Toast.makeText(context, "नया सायरन टोन सेव: $title", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (isAdmin) "एडमिन कंट्रोल पैनल" else "प्रोफाइल व सेटिंग्स",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "कृषिव ग्रुप • हमारी BC कमेटी",
                            fontSize = 11.sp,
                            color = Color(0xFFD97706),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "वापस") }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ब्रांडिंग हेडर कार्ड
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFD700)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("KG", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF0F172A))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("KRISHIV GROUP BC", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFFFFD700))
                        Text(
                            if (isAdmin) "एडमिन मोड सक्रिय • पूरा नियंत्रण" else "सदस्य मोड • लाइव पारदर्शिता",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            if (isAdmin) {
                // 1. Accordion: नया सदस्य व कॉमन PIN सेटिंग
                AdminAccordionCard(
                    title = "1. सदस्य जोड़ें व कॉमन लॉगिन PIN",
                    subtitle = "नए मेंबर अप्रूवल व पासवर्ड कंट्रोल",
                    icon = Icons.Default.PersonAdd,
                    accentColor = Color(0xFFFFD700),
                    isDarkHeader = true,
                    expanded = expandedSection == "members",
                    onToggle = { expandedSection = if (expandedSection == "members") "" else "members" }
                ) {
                    OutlinedTextField(
                        value = newMemberName,
                        onValueChange = { newMemberName = it },
                        label = { Text("नए सदस्य का नाम") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newMemberMobile,
                            onValueChange = { newMemberMobile = it.filter { ch -> ch.isDigit() }.take(10) },
                            label = { Text("10-अंक मोबाइल") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1.4f)
                        )
                        OutlinedTextField(
                            value = newMemberPin,
                            onValueChange = { newMemberPin = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text("PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    Button(
                        onClick = {
                            val mob = newMemberMobile.filter { it.isDigit() }
                            val pin = newMemberPin.filter { it.isDigit() }
                            if (newMemberName.isBlank() || mob.length != 10 || pin.length != 4) {
                                Toast.makeText(context, "सही नाम, 10-अंक मोबाइल व 4-अंक PIN डालें", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSavingMember = true
                            scope.launch {
                                try {
                                    db.collection("approved_members").document(mob).set(
                                        mapOf(
                                            "name" to newMemberName.trim(),
                                            "mobile" to mob,
                                            "pin" to pin,
                                            "approved" to true,
                                            "addedAt" to System.currentTimeMillis()
                                        )
                                    ).await()
                                    Toast.makeText(context, "${newMemberName.trim()} जुड़ गए!", Toast.LENGTH_SHORT).show()
                                    newMemberName = ""; newMemberMobile = ""; newMemberPin = commonMemberPin
                                } catch (e: Exception) {
                                    Toast.makeText(context, "त्रुटि: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally { isSavingMember = false }
                            }
                        },
                        enabled = !isSavingMember,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color(0xFFFFD700)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("सदस्य को मंजूरी दें", fontWeight = FontWeight.ExtraBold) } 
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("ग्रुप कॉमन लॉगिन PIN (सभी मेंबर्स के लिए)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = commonMemberPin,
                            onValueChange = { commonMemberPin = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text("कॉमन 4-अंक PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (commonMemberPin.length != 4) {
                                    Toast.makeText(context, "4 अंकों का PIN डालें", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    try {
                                        db.collection("group_settings").document("rules")
                                            .set(mapOf("commonMemberPin" to commonMemberPin), SetOptions.merge()).await()
                                        newMemberPin = commonMemberPin
                                        Toast.makeText(context, "कॉमन PIN अपडेट हो गया!", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {}
                                }
                            }
                        ) { Text("PIN सेव") }
                    }
                }

                // 2. Accordion: दो-चरणीय बोली टाइमर, बीप/सायरन व लेट फाइन
                AdminAccordionCard(
                    title = "2. दो-चरणीय टाइमर, सायरन व लेट फाइन",
                    subtitle = "बोली अलर्ट, लाइव टाइमर, बीप व ड्यू डेट",
                    icon = Icons.Default.NotificationsActive,
                    accentColor = Color(0xFFDC2626),
                    expanded = expandedSection == "timer_rules",
                    onToggle = { expandedSection = if (expandedSection == "timer_rules") "" else "timer_rules" }
                ) {
                    Text("दो-चरणीय लाइव बोली टाइमर (Two-Stage Timer)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = stage1AlertSec,
                            onValueChange = { stage1AlertSec = it.filter { ch -> ch.isDigit() }.take(3) },
                            label = { Text("चरण 1 अलर्ट (सेकंड)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = stage2BidSec,
                            onValueChange = { stage2BidSec = it.filter { ch -> ch.isDigit() }.take(3) },
                            label = { Text("चरण 2 बोली (सेकंड)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("बोली सायरन अलार्म", fontWeight = FontWeight.SemiBold)
                            Text("टोन: $toneName", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(checked = soundEnabled, onCheckedChange = onToggleSound)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("काउंटडाउन टिक-टिक बीप", fontWeight = FontWeight.SemiBold)
                            Text("हर नई बोली और आखिरी 5 सेकंड में बीप", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(checked = beepEnabled, onCheckedChange = { beepEnabled = it })
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                }
                                ringtoneLauncher.launch(i)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("टोन बदलें") }
                        Button(
                            onClick = onTestSiren,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            modifier = Modifier.weight(1f)
                        ) { Text("सायरन टेस्ट") }
                    }

                    HorizontalDivider()

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = dueDay,
                            onValueChange = { dueDay = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Due तारीख (1-28)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dailyFine,
                            onValueChange = { dailyFine = it.filter { ch -> ch.isDigit() }.take(5) },
                            label = { Text("लेट फाइन (₹/दिन)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("100", "200", "500").forEach { p ->
                            FilterChip(selected = dailyFine == p, onClick = { dailyFine = p }, label = { Text("₹$p/दिन") })
                        }
                    }

                    Button(
                        onClick = {
                            val d = (dueDay.toLongOrNull() ?: 10L).coerceIn(1L, 28L)
                            val f = (dailyFine.toLongOrNull() ?: 100L).coerceAtLeast(0L)
                            val s1 = (stage1AlertSec.toLongOrNull() ?: 15L).coerceIn(5L, 120L)
                            val s2 = (stage2BidSec.toLongOrNull() ?: 30L).coerceIn(10L, 300L)
                            isSavingRules = true
                            scope.launch {
                                try {
                                    db.collection("group_settings").document("rules")
                                        .set(
                                            mapOf(
                                                "dueDayOfMonth" to d,
                                                "dailyLateFine" to f,
                                                "stage1AlertSeconds" to s1,
                                                "stage2BidSeconds" to s2,
                                                "beepEnabled" to beepEnabled,
                                                "sirenEnabled" to soundEnabled
                                            ),
                                            SetOptions.merge()
                                        ).await()
                                    Toast.makeText(context, "टाइमर, सायरन व नियम सेव हो गए!", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {} finally { isSavingRules = false }
                            }
                        },
                        enabled = !isSavingRules,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("टाइमर व नियम सेव करें", fontWeight = FontWeight.Bold) }
                }

                // 3. Accordion: UPI / QR पेमेंट सेटिंग्स
                AdminAccordionCard(
                    title = "3. UPI व QR पेमेंट मैनेजमेंट",
                    subtitle = "किस्त जमा करने के लिए एडमिन UPI व QR लिंक",
                    icon = Icons.Default.QrCode2,
                    accentColor = Color(0xFF16A34A),
                    expanded = expandedSection == "payment",
                    onToggle = { expandedSection = if (expandedSection == "payment") "" else "payment" }
                ) {
                    OutlinedTextField(
                        value = adminUpiId,
                        onValueChange = { adminUpiId = it },
                        label = { Text("एडमिन UPI ID (जैसे: name@okicici)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = adminUpiName,
                        onValueChange = { adminUpiName = it },
                        label = { Text("पेमेंट प्राप्तकर्ता का नाम") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = qrImageUrl,
                        onValueChange = { qrImageUrl = it },
                        label = { Text("QR कोड फोटो लिंक / पेमेंट नोट (वैकल्पिक)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            isSavingPayment = true
                            scope.launch {
                                try {
                                    db.collection("group_settings").document("rules")
                                        .set(
                                            mapOf(
                                                "adminUpiId" to adminUpiId.trim(),
                                                "adminUpiName" to adminUpiName.trim(),
                                                "qrImageUrl" to qrImageUrl.trim()
                                            ),
                                            SetOptions.merge()
                                        ).await()
                                    Toast.makeText(context, "UPI व QR जानकारी सेव हो गई!", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {} finally { isSavingPayment = false }
                            }
                        },
                        enabled = !isSavingPayment,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("UPI / QR सेटिंग्स सेव करें", fontWeight = FontWeight.Bold) }
                }

                // 4. Accordion: कमेटी की मुख्य सेटिंग्स
                AdminAccordionCard(
                    title = "4. कमेटी की मुख्य सेटिंग्स",
                    subtitle = "BC की कुल रकम, कुल सदस्य व मुख्य समय",
                    icon = Icons.Default.AccountBalanceWallet,
                    accentColor = Color(0xFF2563EB),
                    expanded = expandedSection == "committee",
                    onToggle = { expandedSection = if (expandedSection == "committee") "" else "committee" }
                ) {
                    OutlinedTextField(
                        value = cName,
                        onValueChange = { cName = it },
                        label = { Text("कमेटी का नाम") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cPool,
                            onValueChange = { cPool = it.filter { ch -> ch.isDigit() } },
                            label = { Text("कुल BC राशि") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = cMembers,
                            onValueChange = { cMembers = it.filter { ch -> ch.isDigit() } },
                            label = { Text("सदस्य") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    OutlinedTextField(
                        value = cTimer,
                        onValueChange = { cTimer = it.filter { ch -> ch.isDigit() } },
                        label = { Text("कुल बोली सत्र समय (मिनट)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onSaveCommitteeSettings(
                                cName.trim().ifBlank { "कृषिव ग्रुप BC" },
                                cPool.toLongOrNull() ?: 100000L,
                                (cMembers.toLongOrNull() ?: 10L).coerceAtLeast(1L),
                                (cTimer.toLongOrNull() ?: 5L).coerceAtLeast(1L),
                                committee?.scheduledAtMillis ?: 0L,
                                committee?.winnerSloganTemplate ?: Committee.DEFAULT_SLOGAN_TEMPLATE,
                                committee?.apkDownloadUrl ?: Committee.DEFAULT_APK_URL
                            )
                            Toast.makeText(context, "कमेटी सेटिंग्स सेव", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("कमेटी सेटिंग्स सेव करें", fontWeight = FontWeight.Bold) }
                }
            }

            // मेरी प्रोफाइल
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("मेरी प्रोफाइल", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = profileName, onValueChange = { profileName = it }, label = { Text("आपका नाम") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = profileMobile, onValueChange = { profileMobile = it.filter { ch -> ch.isDigit() }.take(10) }, label = { Text("मोबाइल नंबर") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onUpdateProfile(profileName.trim(), profileMobile.trim()); Toast.makeText(context, "प्रोफाइल अपडेट", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) { Text("प्रोफाइल सेव करें") }
                }
            }

            // ऐप थीम
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ऐप थीम", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    AppThemeMode.entries.forEach { mode ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onSelectTheme(mode) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = themeMode == mode, onClick = { onSelectTheme(mode) })
                            Spacer(Modifier.width(8.dp))
                            Text(mode.labelHindi)
                        }
                    }
                }
            }

            // लॉगआउट बटन
            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                border = BorderStroke(1.dp, Color(0xFFDC2626)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("लॉगआउट करें", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AdminAccordionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isDarkHeader: Boolean = false,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDarkHeader) Color(0xFF0F172A) else Color.Transparent)
                    .clickable { onToggle() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = accentColor)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isDarkHeader) Color(0xFFFFD700) else Color.Unspecified)
                    Text(subtitle, fontSize = 12.sp, color = if (isDarkHeader) Color(0xFF94A3B8) else Color.Gray)
                }
                Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = if (isDarkHeader) Color(0xFFFFD700) else Color.Gray)
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            }
        }
    }
}
                    
