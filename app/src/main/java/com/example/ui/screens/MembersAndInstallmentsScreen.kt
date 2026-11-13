package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.BcCalculator
import com.example.model.Committee
import com.example.model.CommitteeMember
import com.example.model.RoundRecord
import com.example.ui.theme.LocalBcExtraColors

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
  var showAddDialog by remember { mutableStateOf(false) }

  val installmentAmount = if (committee.winnerDeclared && committee.perMemberInstallment > 0L) {
    committee.perMemberInstallment
  } else {
    committee.totalBcAmount / committee.totalMembers.coerceAtLeast(1L)
  }
  val paidCount = members.count { it.isPaid }

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
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 92.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = "किस्त व भुगतान • दौर #${committee.roundNumber}",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = if (committee.winnerDeclared) "विजेता हिसाब से प्रति किस्त:" else "अनुमानित मासिक किस्त:",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                  text = BcCalculator.formatRupees(installmentAmount),
                  style = MaterialTheme.typography.headlineMedium,
                  fontWeight = FontWeight.ExtraBold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer
                )
              }
              Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
              ) {
                Text(
                  text = "जमा: $paidCount / ${members.size}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = extra.successGreen,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
              }
            }
            if (committee.winnerDeclared) {
              Text(
                text = "🏆 विजेता: ${committee.winnerName} • बोली: ${BcCalculator.formatRupees(committee.winningBid)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
            }
          }
        }
      }

      if (members.isEmpty()) {
        item {
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
              )
              Text(
                text = "अभी कोई सदस्य सूची में नहीं है",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "नीचे '+' बटन दबाकर सदस्य जोड़ें या इनवाइट कोड (${committee.inviteCode}) शेयर करें।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      items(members, key = { it.id }) { member ->
        val canToggle = isAdmin || member.linkedUid == currentUserId
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth()
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
              color = if (member.isPaid) extra.successGreenContainer else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.size(44.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = member.name.take(1).uppercase().ifBlank { "स" },
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = if (member.isPaid) extra.successGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = member.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              if (member.mobileNumber.isNotBlank()) {
                Text(
                  text = "📱 ${member.mobileNumber}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Text(
                text = "किस्त: ${BcCalculator.formatRupees(installmentAmount)}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
              )
              if (member.hasWonRound > 0L) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = extra.celebrationGold,
                    modifier = Modifier.size(14.dp)
                  )
                  Text(
                    text = "दौर #${member.hasWonRound} विजेता",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = extra.warningAmber
                  )
                }
              }
            }

            Button(
              onClick = { if (canToggle) onToggleInstallment(member) },
              enabled = canToggle,
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = if (member.isPaid) extra.successGreenContainer else extra.warningAmberContainer,
                contentColor = if (member.isPaid) extra.successGreen else extra.warningAmber
              ),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              modifier = Modifier.height(48.dp)
            ) {
              Icon(
                imageVector = if (member.isPaid) Icons.Filled.CheckCircle else Icons.Filled.HourglassTop,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (member.isPaid) "जमा" else "बाकी",
                fontWeight = FontWeight.Bold
              )
            }

            if (isAdmin && member.linkedUid != currentUserId) {
              IconButton(onClick = { onDeleteMember(member) }) {
                Icon(
                  imageVector = Icons.Filled.Delete,
                  contentDescription = "सदस्य हटाएं",
                  tint = MaterialTheme.colorScheme.error
                )
              }
            }
          }
        }
      }

      if (rounds.isNotEmpty()) {
        item {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "पिछले दौर का इतिहास (Round History)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
        }
        items(rounds, key = { it.id }) { round ->
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = "दौर #${round.roundNumber} • 🏆 ${round.winnerName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "बोली: ${BcCalculator.formatRupees(round.winningBid)} • विजेता को मिला: ${BcCalculator.formatRupees(round.netPayableToWinner)} • किस्त: ${BcCalculator.formatRupees(round.perMemberInstallment)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }

    if (isAdmin) {
      FloatingActionButton(
        onClick = { showAddDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
          .testTag("add_member_fab")
      ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = "सदस्य जोड़ें")
      }
    }
  }

  if (showAddDialog) {
    AddMemberDialog(
      onConfirm = { name, mobile ->
        onAddMember(name, mobile)
        showAddDialog = false
      },
      onDismiss = { showAddDialog = false }
    )
  }
}

@Composable
private fun AddMemberDialog(
  onConfirm: (name: String, mobile: String) -> Unit,
  onDismiss: () -> Unit
) {
  var name by remember { mutableStateOf("") }
  var mobile by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("नया सदस्य जोड़ें", fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("सदस्य का नाम") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = mobile,
          onValueChange = { mobile = it.filter { c -> c.isDigit() || c == '+' }.take(15) },
          label = { Text("मोबाइल नंबर") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(name, mobile) },
        enabled = name.isNotBlank()
      ) {
        Text("जोड़ें")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("रद्द करें")
      }
    }
  )
}
