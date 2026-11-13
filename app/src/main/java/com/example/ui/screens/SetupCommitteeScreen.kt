package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

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
  var selectedTab by remember { mutableIntStateOf(0) }
  var committeeName by remember { mutableStateOf("हमारी BC कमेटी") }
  var totalAmountText by remember { mutableStateOf("100000") }
  var totalMembersText by remember { mutableStateOf("10") }
  var timerMinutesText by remember { mutableStateOf("5") }
  var yourName by remember(defaultName) { mutableStateOf(defaultName.ifBlank { "" }) }
  var yourMobile by remember(defaultMobile) { mutableStateOf(defaultMobile) }
  var inviteCode by remember { mutableStateOf("") }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "हमारी BC कमेटी",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
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
        Text(
          text = "शुरू करने के लिए अपनी BC कमेटी बनाएं या इनवाइट कोड से जुड़ें:",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = { Icon(Icons.Filled.AddBusiness, contentDescription = null) }
          ) {
            Text("नई कमेटी बनाएं")
          }
          SegmentedButton(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = { Icon(Icons.Filled.GroupAdd, contentDescription = null) }
          ) {
            Text("कोड से जुड़ें")
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
              onValueChange = { yourMobile = it.filter { ch -> ch.isDigit() || ch == '+' }.take(15) },
              label = { Text("मोबाइल नंबर (Mobile Number)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
                label = { Text("बोली टाइमर (कोई भी मिनट, जैसे 1, 3, 7, 15)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )

              Spacer(modifier = Modifier.height(4.dp))

              Button(
                onClick = {
                  onCreateCommittee(
                    committeeName,
                    totalAmountText.toLongOrNull() ?: 100_000L,
                    totalMembersText.toLongOrNull() ?: 10L,
                    timerMinutesText.toLongOrNull() ?: 5L,
                    yourName,
                    yourMobile
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

              Spacer(modifier = Modifier.height(4.dp))

              OutlinedButton(
                onClick = { onJoinCommittee(inviteCode, yourName, yourMobile) },
                enabled = !isBusy && inviteCode.isNotBlank() && yourName.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(54.dp)
                  .testTag("join_committee_button")
              ) {
                Text(
                  text = "कमेटी में जुड़ें",
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
