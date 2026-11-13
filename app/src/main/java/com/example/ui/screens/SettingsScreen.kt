package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.AppThemeMode
import com.example.model.BcCalculator
import com.example.model.Committee
import com.example.model.UserProfile
import com.example.ui.components.shareToWhatsApp
import com.example.ui.theme.DarkPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalBcExtraColors
import com.example.ui.theme.RoyalBluePrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
  val extra = LocalBcExtraColors.current

  var commName by remember(committee?.id, committee?.name) { mutableStateOf(committee?.name ?: "हमारी BC कमेटी") }
  var totalAmountText by remember(committee?.id, committee?.totalBcAmount) {
    mutableStateOf((committee?.totalBcAmount ?: 100_000L).toString())
  }
  var totalMembersText by remember(committee?.id, committee?.totalMembers) {
    mutableStateOf((committee?.totalMembers ?: 10L).toString())
  }
  var timerMinutesText by remember(committee?.id, committee?.timerMinutes) {
    mutableStateOf((committee?.timerMinutes ?: 5L).toString())
  }
  var scheduledMillis by remember(committee?.id, committee?.scheduledAtMillis) {
    mutableLongStateOf(committee?.scheduledAtMillis ?: 0L)
  }
  var sloganTemplate by remember(committee?.id, committee?.winnerSloganTemplate) {
    mutableStateOf(committee?.winnerSloganTemplate ?: Committee.DEFAULT_SLOGAN_TEMPLATE)
  }
  var apkDownloadUrl by remember(committee?.id, committee?.apkDownloadUrl) {
    mutableStateOf(committee?.apkDownloadUrl ?: Committee.DEFAULT_APK_URL)
  }

  var myName by remember(userProfile?.displayName) { mutableStateOf(userProfile?.displayName ?: "") }
  var myMobile by remember(userProfile?.mobileNumber) { mutableStateOf(userProfile?.mobileNumber ?: "") }

  var showDatePicker by remember { mutableStateOf(false) }
  var showTimePicker by remember { mutableStateOf(false) }
  var pickedDateUtcMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

  val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "सेटिंग्स (Settings)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "वापस जाएं"
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
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 640.dp)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // 1. Theme Switcher
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Text(
                text = "ऐप थीम चुनें (Theme Switcher)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
            }

            AppThemeMode.entries.forEach { mode ->
              val swatchColor = when (mode) {
                AppThemeMode.ROYAL_BLUE -> RoyalBluePrimary
                AppThemeMode.EMERALD_GREEN -> EmeraldPrimary
                AppThemeMode.DARK_MODE -> DarkPrimary
              }
              val selected = mode == themeMode
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onSelectTheme(mode) }
                  .testTag("theme_option_${mode.storageKey}")
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Surface(
                    shape = CircleShape,
                    color = swatchColor,
                    modifier = Modifier.size(28.dp)
                  ) {}
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = mode.labelHindi,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = mode.subtitle,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  if (selected) {
                    Icon(
                      imageVector = Icons.Filled.CheckCircle,
                      contentDescription = "चयनित",
                      tint = MaterialTheme.colorScheme.primary
                    )
                  }
                }
              }
            }
          }
        }

        // 2. Siren Test & Sound Toggle
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
              ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                  Text(
                    text = "सायरन और अलर्ट साउंड",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = if (soundEnabled) "चालू (Choon-Choon और End Siren)" else "बंद (Mute)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              Switch(
                checked = soundEnabled,
                onCheckedChange = onToggleSound,
                modifier = Modifier.testTag("sound_toggle_switch")
              )
            }

            Button(
              onClick = onTestSiren,
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = extra.celebrationGold,
                contentColor = MaterialTheme.colorScheme.onSecondary
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("test_siren_button")
            ) {
              Icon(Icons.Filled.Campaign, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("सायरन टेस्ट करें (Test Siren)", fontWeight = FontWeight.Bold)
            }
          }
        }

        // 3. Admin Committee, Custom Timer, Schedule, Slogan & APK Share
        if (committee != null) {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Text(
                text = "बोली टाइमर, शेड्यूल व बधाई संदेश",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )

              OutlinedTextField(
                value = timerMinutesText,
                onValueChange = { timerMinutesText = it.filter { c -> c.isDigit() }.take(3) },
                enabled = isAdmin,
                label = { Text("कस्टम बोली टाइमर (कोई भी मिनट, जैसे 1, 3, 7, 15, 25)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("custom_timer_minutes_input")
              )

              // Schedule Date & Time Picker Row
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Text(
                    text = "अगली लाइव बोली का समय (Auto-Start Schedule)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = if (scheduledMillis > 0L) {
                      "📅 निर्धारित समय: ${dateFormat.format(Date(scheduledMillis))}"
                    } else {
                      "अभी कोई समय निर्धारित नहीं है"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  if (isAdmin) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      OutlinedButton(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                          .weight(1f)
                          .height(48.dp)
                          .testTag("pick_schedule_datetime_button")
                      ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("तारीख व समय चुनें")
                      }
                      if (scheduledMillis > 0L) {
                        IconButton(onClick = { scheduledMillis = 0L }) {
                          Icon(Icons.Filled.Clear, contentDescription = "शेड्यूल हटाएं")
                        }
                      }
                    }
                  }
                }
              }

              OutlinedTextField(
                value = sloganTemplate,
                onValueChange = { sloganTemplate = it },
                enabled = isAdmin,
                label = { Text("विजेता बधाई संदेश ('You Are Great' टेम्पलेट)") },
                supportingText = { Text("[Winner Name] लिखने पर विजेता का नाम अपने-आप जुड़ जाएगा") },
                minLines = 2,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("winner_slogan_input")
              )

              if (isAdmin) {
                TextButton(
                  onClick = { sloganTemplate = Committee.DEFAULT_SLOGAN_TEMPLATE }
                ) {
                  Text("डिफ़ॉल्ट बधाई संदेश वापस लाएं")
                }
              }

              OutlinedTextField(
                value = commName,
                onValueChange = { commName = it },
                enabled = isAdmin,
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
                  enabled = isAdmin,
                  label = { Text("कुल BC राशि (₹)") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  singleLine = true,
                  modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                  value = totalMembersText,
                  onValueChange = { totalMembersText = it.filter { c -> c.isDigit() } },
                  enabled = isAdmin,
                  label = { Text("कुल सदस्य") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  singleLine = true,
                  modifier = Modifier.weight(0.75f)
                )
              }

              OutlinedTextField(
                value = apkDownloadUrl,
                onValueChange = { apkDownloadUrl = it },
                enabled = isAdmin,
                label = { Text("डायरेक्ट APK डाउनलोड लिंक (Direct APK Link)") },
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("apk_link_input")
              )

              if (isAdmin) {
                Button(
                  onClick = {
                    onSaveCommitteeSettings(
                      commName,
                      totalAmountText.toLongOrNull() ?: committee.totalBcAmount,
                      totalMembersText.toLongOrNull() ?: committee.totalMembers,
                      (timerMinutesText.toLongOrNull() ?: committee.timerMinutes).coerceIn(1L, 180L),
                      scheduledMillis,
                      sloganTemplate,
                      apkDownloadUrl
                    )
                  },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_settings_button")
                ) {
                  Icon(Icons.Filled.Save, contentDescription = null)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("सेटिंग्स सहेजें (Save)", fontWeight = FontWeight.Bold)
                }
              }

              OutlinedButton(
                onClick = {
                  val inviteMsg = BcCalculator.buildInviteShareText(
                    committeeName = commName,
                    inviteCode = committee.inviteCode,
                    totalBcAmount = totalAmountText.toLongOrNull() ?: committee.totalBcAmount,
                    totalMembers = totalMembersText.toLongOrNull() ?: committee.totalMembers,
                    apkUrl = apkDownloadUrl
                  )
                  shareToWhatsApp(context, inviteMsg)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(52.dp)
                  .testTag("settings_share_apk_button")
              ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("WhatsApp पर Invite Link / APK Share करें", fontWeight = FontWeight.Bold)
              }
            }
          }
        }

        // 4. My Profile & Sign Out
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Text(
              text = "मेरा प्रोफ़ाइल (My Profile)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
              value = myName,
              onValueChange = { myName = it },
              label = { Text("आपका नाम") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = myMobile,
              onValueChange = { myMobile = it.filter { c -> c.isDigit() || c == '+' }.take(15) },
              label = { Text("मोबाइल नंबर (बोली बबल में दिखेगा)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            Button(
              onClick = { onUpdateProfile(myName, myMobile) },
              enabled = myName.isNotBlank(),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
            ) {
              Text("प्रोफ़ाइल अपडेट करें", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
              onClick = onSignOut,
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("sign_out_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("साइन आउट करें (Sign Out)", fontWeight = FontWeight.Bold)
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState(
      initialSelectedDateMillis = if (scheduledMillis > 0L) scheduledMillis else System.currentTimeMillis()
    )
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            pickedDateUtcMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
            showDatePicker = false
            showTimePicker = true
          }
        ) {
          Text("आगे समय चुनें")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) {
          Text("रद्द करें")
        }
      }
    ) {
      DatePicker(state = datePickerState)
    }
  }

  if (showTimePicker) {
    val calNow = Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
      initialHour = calNow.get(Calendar.HOUR_OF_DAY),
      initialMinute = (calNow.get(Calendar.MINUTE) + 2) % 60
    )
    AlertDialog(
      onDismissRequest = { showTimePicker = false },
      title = { Text("बोली शुरू होने का समय चुनें", fontWeight = FontWeight.Bold) },
      text = {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          TimePicker(state = timePickerState)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
              timeInMillis = pickedDateUtcMillis
            }
            val localCal = Calendar.getInstance().apply {
              set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
              set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
              set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
              set(Calendar.HOUR_OF_DAY, timePickerState.hour)
              set(Calendar.MINUTE, timePickerState.minute)
              set(Calendar.SECOND, 0)
              set(Calendar.MILLISECOND, 0)
            }
            scheduledMillis = localCal.timeInMillis
            showTimePicker = false
          }
        ) {
          Text("समय सेट करें")
        }
      },
      dismissButton = {
        TextButton(onClick = { showTimePicker = false }) {
          Text("रद्द करें")
        }
      }
    )
  }
}
