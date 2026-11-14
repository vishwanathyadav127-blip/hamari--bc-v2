package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
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
    onSaveCommitteeSettings: (
        name: String,
        totalBcAmount: Long,
        totalMembers: Long,
        timerMinutes: Long,
        scheduledAtMillis: Long,
        sloganTemplate: String,
        apkDownloadUrl: String
    ) -> Unit,
    onUpdateProfile: (name: String, mobile: String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { Firebase.firestore }

    // User Profile States
    var profileName by remember(userProfile) { mutableStateOf(userProfile?.name ?: "") }
    var profileMobile by remember(userProfile) { mutableStateOf(userProfile?.mobile ?: "") }

    // Admin: Add Approved Member States
    var newMemberName by remember { mutableStateOf("") }
    var newMemberMobile by remember { mutableStateOf("") }
    var newMemberPin by remember { mutableStateOf("1234") }
    var isSavingMember by remember { mutableStateOf(false) }

    // Admin: Due Date, Late Fine & Siren Ringtone States
    var dueDayOfMonth by remember { mutableStateOf("10") }
    var dailyLateFine by remember { mutableStateOf("100") }
    var selectedToneName by remember { mutableStateOf("डिफ़ॉल्ट तेज़ सायरन अलर्ट") }
    var isSavingRules by remember { mutableStateOf(false) }

    // Admin: Committee Basic Settings
    var committeeName by remember(committee) { mutableStateOf(committee?.name ?: "कृषिव ग्रुप BC") }
    var totalPoolText by remember(committee) {
        mutableStateOf((committee?.totalAmount ?: 100000L).toString())
    }
    var totalMembersText by remember(committee) {
        mutableStateOf((committee?.totalMembers ?: 10).toString())
    }
    var timerMinutesText by remember(committee) {
        mutableStateOf((committee?.timerMinutes ?: 5).toString())
    }

    // Load saved admin rules from Firestore
    LaunchedEffect(Unit) {
        try {
            val rulesDoc = db.collection("group_settings").document("rules").get().await()
            if (rulesDoc.exists()) {
                dueDayOfMonth = (rulesDoc.getLong("dueDayOfMonth") ?: 10L).toString()
                dailyLateFine = (rulesDoc.getLong("dailyLateFine") ?: 100L).toString()
                selectedToneName = rulesDoc.getString("sirenToneName") ?: "डिफ़ॉल्ट तेज़ सायरन अलर्ट"
            }
        } catch (_: Exception) {
        }
    }

    // System Ringtone Picker Launcher for Admin
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(context, uri)
                val title = ringtone?.getTitle(context) ?: "कस्टम सायरन टोन"
                selectedToneName = title
                scope.launch {
                    try {
                        db.collection("group_settings").document("rules").set(
                            mapOf(
                                "sirenToneUri" to uri.toString(),
                                "sirenToneName" to title
                            ),
                            SetOptions.merge()
                        ).await()
                        Toast.makeText(context, "नया सायरन टोन सेव हो गया: $title", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isAdmin) "एडमिन कंट्रोल व सेटिंग्स" else "मेरी प्रोफाइल व सेटिंग्स",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "वापस")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. ADMIN ONLY SECTION
            if (isAdmin) {
                // Card A: Add Approved Member (Whitelist for Login)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFFFFD700))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "नया सदस्य जोड़ें (Member Approval)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                        }
                        Text(
                            text = "यहाँ जोड़े गए मोबाइल नंबर और PIN से ही सदस्य ऐप में लॉगिन कर पाएंगे।",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )

                        OutlinedTextField(
                            value = newMemberName,
                            onValueChange = { newMemberName = it },
                            label = { Text("सदस्य का पूरा नाम", color = Color(0xFF94A3B8)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFFD700)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newMemberMobile,
                                onValueChange = { newMemberMobile = it.filter { ch -> ch.isDigit() }.take(10) },
                                label = { Text("10 अंकों का मोबाइल", color = Color(0xFF94A3B8)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFFFD700)
                                ),
                                modifier = Modifier.weight(1.4f)
                            )
                            OutlinedTextField(
                                value = newMemberPin,
                                onValueChange = { newMemberPin = it.filter { ch -> ch.isDigit() }.take(4) },
                                label = { Text("4-अंक PIN", color = Color(0xFF94A3B8)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFFFD700)
                                ),
                                modifier = Modifier.weight(0.8f)
                            )
                        }

                        Button(
                            onClick = {
                                val cleanMob = newMemberMobile.filter { it.isDigit() }
                                val cleanPin = newMemberPin.filter { it.isDigit() }
                                if (newMemberName.isBlank() || cleanMob.length != 10 || cleanPin.length != 4) {
                                    Toast.makeText(context, "सही नाम, 10 अंकों का नंबर और 4-अंक का PIN डालें", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSavingMember = true
                                scope.launch {
                                    try {
                                        db.collection("approved_members").document(cleanMob).set(
                                            mapOf(
                                                "name" to newMemberName.trim(),
                                                "mobile" to cleanMob,
                                                "pin" to cleanPin,
                                                "approved" to true,
                                                "addedAt" to System.currentTimeMillis()
                                            )
                                        ).await()
                                        Toast.makeText(context, "${newMemberName.trim()} ग्रुप में जुड़ गए!", Toast.LENGTH_SHORT).show()
                                        newMemberName = ""
                                        newMemberMobile = ""
                                        newMemberPin = "1234"
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "सेव करने में त्रुटि: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSavingMember = false
                                    }
                                }
                            },
                            enabled = !isSavingMember,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD700),
                                contentColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("सदस्य को मंजूरी दें (Approve Member)", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                // Card B: Due Date, Late Fine & Loud Siren Ringtone Controls
                Card(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFDC2626))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ड्यू डेट, लेट फाइन व सायरन अलर्ट",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = dueDayOfMonth,
                                onValueChange = { dueDayOfMonth = it.filter { ch -> ch.isDigit() }.take(2) },
                                label = { Text("Last Due तारीख (1-28)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = dailyLateFine,
                                onValueChange = { dailyLateFine = it.filter { ch -> ch.isDigit() }.take(5) },
                                label = { Text("प्रतिदिन लेट फाइन (₹)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Quick Fine Preset Chips
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("100", "200", "500").forEach { finePreset ->
                                FilterChip(
                                    selected = dailyLateFine == finePreset,
                                    onClick = { dailyLateFine = finePreset },
                                    label = { Text("₹$finePreset / दिन") }
                                )
                            }
                        }

                        // Siren / Ringtone Controls (Admin Only)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("बोली शुरू होने का सायरन अलार्म", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "चुनी हुई टोन: $selectedToneName",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = soundEnabled,
                                onCheckedChange = onToggleSound
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "कड़क सायरन / रिंगटोन चुनें")
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    }
                                    ringtonePickerLauncher.launch(intent)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("टोन बदलें", fontSize = 13.sp)
                            }

                            Button(
                                onClick = onTestSiren,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("सायरन टेस्ट", fontSize = 13.sp)
                            }
                        }

                        Button(
                            onClick = {
                                val day = (dueDayOfMonth.toLongOrNull() ?: 10L).coerceIn(1L, 28L)
                                val fine = (dailyLateFine.toLongOrNull() ?: 100L).coerceAtLeast(0L)
                                isSavingRules = true
                                scope.launch {
                                    try {
                                        db.collection("group_settings").document("rules").set(
                                            mapOf(
                                                "dueDayOfMonth" to day,
                                                "dailyLateFine" to fine,
                                                "sirenEnabled" to soundEnabled
                                            ),
                                            SetOptions.merge()
                                        ).await()
                                        Toast.makeText(context, "ड्यू डेट (तारीख $day) और लेट फाइन (₹$fine) सेव हो गया!", Toast.LENGTH_SHORT).show()
                                   
