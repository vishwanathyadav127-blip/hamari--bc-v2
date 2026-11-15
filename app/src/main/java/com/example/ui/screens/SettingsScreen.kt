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
    onSaveCommitteeSettings: (String, Long, Long, Long, Long, String, String) -> Unit,
    onUpdateProfile: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { Firebase.firestore }

    var profileName by remember(userProfile) { mutableStateOf(userProfile?.displayName ?: "") }
    var profileMobile by remember(userProfile) { mutableStateOf(userProfile?.mobileNumber ?: "") }

    var newMemberName by remember { mutableStateOf("") }
    var newMemberMobile by remember { mutableStateOf("") }
    var newMemberPin by remember { mutableStateOf("1234") }
    var isSavingMember by remember { mutableStateOf(false) }

    var dueDay by remember { mutableStateOf("10") }
    var dailyFine by remember { mutableStateOf("100") }
    var toneName by remember { mutableStateOf("डिफ़ॉल्ट तेज़ सायरन अलर्ट") }
    var isSavingRules by remember { mutableStateOf(false) }

    var cName by remember(committee) { mutableStateOf(committee?.name ?: "कृषिव ग्रुप BC") }
    var cPool by remember(committee) { mutableStateOf((committee?.totalBcAmount ?: 100000L).toString()) }
    var cMembers by remember(committee) { mutableStateOf((committee?.totalMembers ?: 10L).toString()) }
    var cTimer by remember(committee) { mutableStateOf((committee?.timerMinutes ?: 5L).toString()) }

    LaunchedEffect(Unit) {
        try {
            val doc = db.collection("group_settings").document("rules").get().await()
            if (doc.exists()) {
                dueDay = (doc.getLong("dueDayOfMonth") ?: 10L).toString()
                dailyFine = (doc.getLong("dailyLateFine") ?: 100L).toString()
                toneName = doc.getString("sirenToneName") ?: "डिफ़ॉल्ट तेज़ सायरन अलर्ट"
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
                title = { Text(if (isAdmin) "एडमिन कंट्रोल व सेटिंग्स" else "मेरी प्रोफाइल व सेटिंग्स", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "वापस") }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isAdmin) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PersonAdd, null, tint = Color(0xFFFFD700))
                            Spacer(Modifier.width(8.dp))
                            Text("नया सदस्य जोड़ें (Member Approval)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                        }
                        OutlinedTextField(
                            value = newMemberName,
                            onValueChange = { newMemberName = it },
                            label = { Text("सदस्य का नाम", color = Color(0xFF94A3B8)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newMemberMobile,
                                onValueChange = { newMemberMobile = it.filter { ch -> ch.isDigit() }.take(10) },
                                label = { Text("10-अंक मोबाइल", color = Color(0xFF94A3B8)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                modifier = Modifier.weight(1.4f)
                            )
                            OutlinedTextField(
                                value = newMemberPin,
                                onValueChange = { newMemberPin = it.filter { ch -> ch.isDigit() }.take(4) },
                                label = { Text("PIN", color = Color(0xFF94A3B8)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
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
                                            mapOf("name" to newMemberName.trim(), "mobile" to mob, "pin" to pin, "approved" to true, "addedAt" to System.currentTimeMillis())
                                        ).await()
                                        Toast.makeText(context, "${newMemberName.trim()} जुड़ गए!", Toast.LENGTH_SHORT).show()
                                        newMemberName = ""; newMemberMobile = ""; newMemberPin = "1234"
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "त्रुटि: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally { isSavingMember = false }
                                }
                            },
                            enabled = !isSavingMember,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("सदस्य को मंजूरी दें", fontWeight = FontWeight.ExtraBold) }
                    }
                }

                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ड्यू डेट, लेट फाइन व सायरन अलर्ट", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(value = dueDay, onValueChange = { dueDay = it.filter { ch -> ch.isDigit() }.take(2) }, label = { Text("Due तारीख (1-28)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(value = dailyFine, onValueChange = { dailyFine = it.filter { ch -> ch.isDigit() }.take(5) }, label = { Text("लेट फाइन (₹/दिन)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("100", "200", "500").forEach { p ->
                                FilterChip(selected = dailyFine == p, onClick = { dailyFine = p }, label = { Text("₹$p/दिन") })
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("बोली सायरन अलार्म", fontWeight = FontWeight.SemiBold)
                                Text("टोन: $toneName", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(checked = soundEnabled, onCheckedChange = onToggleSound)
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
                            Button(onClick = onTestSiren, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)), modifier = Modifier.weight(1f)) { Text("सायरन टेस्ट") }
                        }
                        Button(
                            onClick = {
                                val d = (dueDay.toLongOrNull() ?: 10L).coerceIn(1L, 28L)
                                val f = (dailyFine.toLongOrNull() ?: 100L).coerceAtLeast(0L)
                                isSavingRules = true
                                scope.launch {
                                    try {
                                        db.collection("group_settings").document("rules")
                                            .set(mapOf("dueDayOfMonth" to d, "dailyLateFine" to f, "sirenEnabled" to soundEnabled), SetOptions.merge()).await()
                                        Toast.makeText(context, "नियम सेव हो गए!", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {} finally { isSavingRules = false }
                                }
                            },
                            enabled = !isSavingRules,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ड्यू डेट व फाइन सेव करें", fontWeight = FontWeight.Bold) }
                    }
                }

                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("कमेटी की मुख्य सेटिंग्स", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = cName, onValueChange = { cName = it }, label = { Text("कमेटी का नाम") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = cPool, onValueChange = { cPool = it.filter { ch -> ch.isDigit() } }, label = { Text("कुल BC राशि") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1.2f))
                            OutlinedTextField(value = cMembers, onValueChange = { cMembers = it.filter { ch -> ch.isDigit() } }, label = { Text("सदस्य") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(0.8f))
                        }
                        OutlinedTextField(value = cTimer, onValueChange = { cTimer = it.filter { ch -> ch.isDigit() } }, label = { Text("टाइमर (मिनट)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
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
            }

            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("मेरी प्रोफाइल", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = profileName, onValueChange = { profileName = it }, label = { Text("आपका नाम") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = profileMobile, onValueChange = { profileMobile = it.filter { ch -> ch.isDigit() }.take(10) }, label = { Text("मोबाइल नंबर") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onUpdateProfile(profileName.trim(), profileMobile.trim()); Toast.makeText(context, "प्रोफाइल अपडेट", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) { Text("प्रोफाइल सेव करें") }
                }
            }

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
