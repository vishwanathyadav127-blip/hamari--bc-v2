package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupCommitteeScreen(
    defaultName: String,
    defaultMobile: String,
    isBusy: Boolean,
    onCreateCommittee: (
        name: String,
        totalBcAmount: Long,
        totalMembers: Long,
        timerMinutes: Long,
        creatorName: String,
        creatorMobile: String
    ) -> Unit,
    onJoinCommittee: (inviteCode: String, memberName: String, memberMobile: String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { Firebase.firestore }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var committeeName by remember { mutableStateOf("कृषिव ग्रुप BC कमेटी") }
    var totalAmountText by remember { mutableStateOf("200000") }
    var totalMembersText by remember { mutableStateOf("20") }
    var timerMinutesText by remember { mutableStateOf("5") }
    var yourName by remember(defaultName) { mutableStateOf(defaultName.ifBlank { "" }) }
    var yourMobile by remember(defaultMobile) { mutableStateOf(defaultMobile) }
    var inviteCode by remember { mutableStateOf("") }
    var memberPinInput by remember { mutableStateOf("") }
    var isVerifyingPin by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "कृषिव ग्रुप • हमारी BC कमेटी",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "सुरक्षित डिजिटल बचत व लाइव बोली",
                            fontSize = 11.sp,
                            color = Color(0xFFD97706),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "सेटिंग्स"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // कस्टम ब्रांडिंग वेलकम कार्ड
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD700)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD700)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🦚", fontSize = 24.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "KRISHIV GROUP BC",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFD700)
                            )
                            Text(
                                "5 वर्षों का अटूट विश्वास • 100% पारदर्शी लाइव बोली",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Filled.AddBusiness, contentDescription = null) }
                    ) {
                        Text("नई कमेटी बनाएं", fontWeight = FontWeight.Bold)
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Filled.GroupAdd, contentDescription = null) }
                    ) {
                        Text("मेंबर लॉगिन (PIN)", fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = yourName,
                            onValueChange = { yourName = it },
                            label = { Text("आपका नाम (Your Name)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = yourMobile,
                            onValueChange = { yourMobile = it.filter { ch -> ch.isDigit() }.take(10) },
                            label = { Text("10-अंक मोबाइल नंबर") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (selectedTab == 0) {
                            OutlinedTextField(
                                value = committeeName,
                                onValueChange = { committeeName = it },
                                label = { Text("कमेटी का नाम") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = totalAmountText,
                                    onValueChange = { totalAmountText = it.filter { c -> c.isDigit() } },
                                    label = { Text("कुल BC राशि (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = totalMembersText,
                                    onValueChange = { totalMembersText = it.filter { c -> c.isDigit() } },
                                    label = { Text("कुल सदस्य") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(0.75f)
                                )
                            }

                            OutlinedTextField(
                                value = timerMinutesText,
                                onValueChange = { timerMinutesText = it.filter { c -> c.isDigit() } },
                                label = { Text("बोली टाइमर (मिनट में, जैसे 5)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    onCreateCommittee(
                                        committeeName.trim().ifBlank { "कृषिव ग्रुप BC" },
                                        totalAmountText.toLongOrNull() ?: 200_000L,
                                        (totalMembersText.toLongOrNull() ?: 20L).coerceAtLeast(1L),
                                        (timerMinutesText.toLongOrNull() ?: 5L).coerceAtLeast(1L),
                                        yourName.trim(),
                                        yourMobile.trim()
                                    )
                                },
                                enabled = !isBusy && yourName.isNotBlank(),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("create_committee_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        text = "BC कमेटी शुरू करें",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = inviteCode,
                                onValueChange = { inviteCode = it.uppercase().take(12) },
                                label = { Text("इनवाइट कोड लिखें (जैसे BC1234)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = memberPinInput,
                                onValueChange = { memberPinInput = it.filter { ch -> ch.isDigit() }.take(4) },
                                label = { Text("4-अंकों का मेंबर / कॉमन PIN") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    val cleanMob = yourMobile.filter { it.isDigit() }
                                    val cleanPin = memberPinInput.filter { it.isDigit() }
                                    if (cleanPin.length != 4) {
                                        Toast.makeText(context, "कृपया 4-अंकों का सही PIN डालें", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isVerifyingPin = true
                                    scope.launch {
                                        try {
                                            var isPinValid = false
                                            // 1. पहले व्यक्तिगत approved_members में PIN जांचें
                                            if (cleanMob.length == 10) {
                                                val memberDoc = db.collection("approved_members").document(cleanMob).get().await()
                                                if (memberDoc.exists()) {
                                                    val savedPin = memberDoc.getString("pin") ?: ""
                                                    if (savedPin == cleanPin) isPinValid = true
                                                }
                                            }
                                            // 2. यदि व्यक्तिगत नहीं मिला, तो कॉमन ग्रुप PIN जांचें
                                            if (!isPinValid) {
                                                val rulesDoc = db.collection("group_settings").document("rules").get().await()
                                                val commonPin = rulesDoc.getString("commonMemberPin") ?: "1234"
                                                if (cleanPin == commonPin) isPinValid = true
                                            }

                                            if (isPinValid) {
                                                onJoinCommittee(inviteCode.trim(), yourName.trim(), cleanMob)
                                            } else {
                                                Toast.makeText(context, "गलत PIN! एडमिन से सही 4-अंकों का PIN पूछें", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (_: Exception) {
                                            // ऑफ़लाइन या नेटवर्क त्रुटि पर डिफ़ॉल्ट 1234 अनुमति
                                            if (cleanPin == "1234") {
                                                onJoinCommittee(inviteCode.trim(), yourName.trim(), cleanMob)
                                            } else {
                                                Toast.makeText(context, "PIN सत्यापन विफल, पुनः प्रयास करें", Toast.LENGTH_SHORT).show()
                                            }
                                        } finally {
                                            isVerifyingPin = false
                                        }
                                    }
                                },
                                enabled = !isBusy && !isVerifyingPin && inviteCode.isNotBlank() && yourName.isNotBlank() && memberPinInput.length == 4,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("join_committee_button")
                            ) {
                                if (isBusy || isVerifyingPin) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Text(
                                        text = "PIN सत्यापित करें व जुड़ें",
                                        style = MaterialTheme.typography.titleMedium,
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
}
