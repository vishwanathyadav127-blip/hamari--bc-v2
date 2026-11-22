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
                            onValueChange = { dailyFine = it.filter { ch -> ch.isDigit() }.take
