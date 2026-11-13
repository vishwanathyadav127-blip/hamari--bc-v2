package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.model.BcCalculator
import com.example.model.BidMessage
import com.example.model.Committee
import com.example.model.CommitteeMember
import com.example.model.RoundRecord
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CommitteeRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth
) {
  constructor(context: Context) : this(
    db = FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    auth = Firebase.auth
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("Google से साइन-इन करना आवश्यक है।")

  private fun <T> Flow<T>.handleSnapshotErrors(
    userId: String,
    operationType: OperationType,
    path: String
  ): Flow<T> = catch { error ->
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
      handleFirestoreError(exceptionToLog, operationType, path, auth)
    }
    throw error
  }

  fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
    val docRef = db.collection("users").document(userId)
    emitAll(
      docRef.snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .handleSnapshotErrors(userId, OperationType.GET, docRef.path)
    )
  }

  suspend fun saveUserProfile(
    displayName: String,
    mobileNumber: String,
    email: String,
    activeCommitteeId: String = ""
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    val existing = docRef.get().await()
    if (existing.exists()) {
      val updates = mutableMapOf<String, Any>(
        "displayName" to displayName.trim().take(60).ifBlank { "सदस्य" },
        "mobileNumber" to mobileNumber.trim().take(20),
        "email" to email.trim().take(120),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      if (activeCommitteeId.isNotBlank()) {
        updates["activeCommitteeId"] = activeCommitteeId
      }
      docRef.update(updates).await()
    } else {
      val payload = mapOf(
        "userId" to uid,
        "displayName" to displayName.trim().take(60).ifBlank { "सदस्य" },
        "mobileNumber" to mobileNumber.trim().take(20),
        "email" to email.trim().take(120),
        "activeCommitteeId" to activeCommitteeId,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.set(payload).await()
    }
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}", auth)
  }

  fun observeMyCommittees(userId: String): Flow<List<Committee>> = flow {
    val path = "committees"
    emitAll(
      db.collection(path)
        .whereArrayContains("memberUids", userId)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(Committee::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
        }
        .handleSnapshotErrors(userId, OperationType.LIST, path)
    )
  }

  fun observeMembers(committeeId: String, userId: String): Flow<List<CommitteeMember>> = flow {
    val path = "committees/$committeeId/members"
    emitAll(
      db.collection("committees").document(committeeId).collection("members")
        .whereArrayContains("memberUids", userId)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(CommitteeMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.createdAt ?: Timestamp(0, 0) }
        }
        .handleSnapshotErrors(userId, OperationType.LIST, path)
    )
  }

  fun observeBids(committeeId: String, userId: String): Flow<List<BidMessage>> = flow {
    val path = "committees/$committeeId/bids"
    emitAll(
      db.collection("committees").document(committeeId).collection("bids")
        .whereArrayContains("memberUids", userId)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(BidMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.createdAt ?: Timestamp(0, 0) }
        }
        .handleSnapshotErrors(userId, OperationType.LIST, path)
    )
  }

  fun observeRounds(committeeId: String, userId: String): Flow<List<RoundRecord>> = flow {
    val path = "committees/$committeeId/rounds"
    emitAll(
      db.collection("committees").document(committeeId).collection("rounds")
        .whereArrayContains("memberUids", userId)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(RoundRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.roundNumber }
        }
        .handleSnapshotErrors(userId, OperationType.LIST, path)
    )
  }

  suspend fun createCommittee(
    name: String,
    totalBcAmount: Long,
    totalMembers: Long,
    timerMinutes: Long,
    creatorName: String,
    creatorMobile: String,
    creatorEmail: String,
    apkDownloadUrl: String = Committee.DEFAULT_APK_URL
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val committeeId = "comm_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val inviteCode = "BC${(1000..9999).random()}"
    val safeName = name.trim().take(80).ifBlank { "हमारी BC कमेटी" }
    val safeAmount = totalBcAmount.coerceIn(1000L, 100_000_000L)
    val safeMembers = totalMembers.coerceIn(2L, 50L)
    val safeTimer = timerMinutes.coerceIn(1L, 180L)
    val cleanCreatorName = creatorName.trim().take(60).ifBlank { "एडमिन" }
    val cleanCreatorMobile = creatorMobile.trim().take(20)

    val committeePayload = mapOf(
      "name" to safeName,
      "adminUid" to uid,
      "adminEmail" to creatorEmail.trim().take(120),
      "inviteCode" to inviteCode,
      "memberUids" to listOf(uid),
      "totalBcAmount" to safeAmount,
      "totalMembers" to safeMembers,
      "timerMinutes" to safeTimer,
      "scheduledAtMillis" to 0L,
      "isLive" to false,
      "biddingStartedAtMillis" to 0L,
      "biddingEndsAtMillis" to 0L,
      "currentHighestBid" to 0L,
      "currentHighestBidderUid" to "",
      "currentHighestBidderName" to "",
      "currentHighestBidderMobile" to "",
      "roundNumber" to 1L,
      "winnerDeclared" to false,
      "winnerName" to "",
      "winnerUid" to "",
      "winnerMobile" to "",
      "winningBid" to 0L,
      "netPayableToWinner" to 0L,
      "perMemberInstallment" to 0L,
      "winnerSloganTemplate" to Committee.DEFAULT_SLOGAN_TEMPLATE,
      "apkDownloadUrl" to apkDownloadUrl.trim().take(500),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )

    val invitePayload = mapOf(
      "committeeId" to committeeId,
      "committeeName" to safeName,
      "adminUid" to uid,
      "createdAt" to FieldValue.serverTimestamp()
    )

    val memberPayload = mapOf(
      "committeeId" to committeeId,
      "adminUid" to uid,
      "linkedUid" to uid,
      "name" to cleanCreatorName,
      "mobileNumber" to cleanCreatorMobile,
      "currentInstallmentStatus" to "pending",
      "paidRounds" to emptyList<Long>(),
      "hasWonRound" to 0L,
      "memberUids" to listOf(uid),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )

    val commRef = db.collection("committees").document(committeeId)
    val inviteRef = db.collection("invites").document(inviteCode)
    val memberRef = commRef.collection("members").document(uid)

    commRef.set(committeePayload).await()
    inviteRef.set(invitePayload).await()
    memberRef.set(memberPayload).await()

    saveUserProfile(
      displayName = cleanCreatorName,
      mobileNumber = cleanCreatorMobile,
      email = creatorEmail,
      activeCommitteeId = committeeId
    ).getOrThrow()

    committeeId
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.CREATE, "committees", auth)
  }

  suspend fun joinCommitteeByInviteCode(
    inviteCode: String,
    memberName: String,
    memberMobile: String,
    memberEmail: String
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val cleanCode = inviteCode.trim().uppercase()
    require(cleanCode.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) { "सही इनवाइट कोड दर्ज करें।" }
    val inviteDoc = db.collection("invites").document(cleanCode).get().await()
    require(inviteDoc.exists()) { "यह इनवाइट कोड नहीं मिला।" }

    val committeeId = inviteDoc.getString("committeeId")
      ?: throw IllegalStateException("कमेटी ID नहीं मिली।")
    val commRef = db.collection("committees").document(committeeId)
    val commDoc = commRef.get().await()
    require(commDoc.exists()) { "कमेटी मौजूद नहीं है।" }

    val currentUids = (commDoc.get("memberUids") as? List<*>)
      ?.filterIsInstance<String>()
      ?.toMutableList() ?: mutableListOf()

    if (!currentUids.contains(uid)) {
      currentUids.add(uid)
      commRef.update(
        mapOf(
          "memberUids" to currentUids,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }

    val cleanName = memberName.trim().take(60).ifBlank { "सदस्य" }
    val cleanMobile = memberMobile.trim().take(20)
    val adminUid = commDoc.getString("adminUid") ?: ""
    val memberRef = commRef.collection("members").document(uid)
    val existingMember = memberRef.get().await()
    if (!existingMember.exists()) {
      memberRef.set(
        mapOf(
          "committeeId" to committeeId,
          "adminUid" to adminUid,
          "linkedUid" to uid,
          "name" to cleanName,
          "mobileNumber" to cleanMobile,
          "currentInstallmentStatus" to "pending",
          "paidRounds" to emptyList<Long>(),
          "hasWonRound" to 0L,
          "memberUids" to currentUids,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }

    saveUserProfile(
      displayName = cleanName,
      mobileNumber = cleanMobile,
      email = memberEmail,
      activeCommitteeId = committeeId
    ).getOrThrow()

    committeeId
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.WRITE, "invites/$inviteCode", auth)
  }

  suspend fun syncSubcollectionMemberUids(committee: Committee): Result<Unit> = runCatching {
    val uid = requireUserId()
    if (committee.adminUid != uid) return@runCatching
    val commRef = db.collection("committees").document(committee.id)
    val targetUids = committee.memberUids
    val batch = db.batch()
    var count = 0

    val members = commRef.collection("members").whereArrayContains("memberUids", uid).get().await()
    members.documents.forEach { doc ->
      val docUids = (doc.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
      if (docUids != targetUids) {
        batch.update(doc.reference, mapOf("memberUids" to targetUids, "updatedAt" to FieldValue.serverTimestamp()))
        count++
      }
    }

    val rounds = commRef.collection("rounds").whereArrayContains("memberUids", uid).get().await()
    rounds.documents.forEach { doc ->
      val docUids = (doc.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
      if (docUids != targetUids) {
        batch.update(doc.reference, mapOf("memberUids" to targetUids))
        count++
      }
    }

    val bids = commRef.collection("bids").whereArrayContains("memberUids", uid).get().await()
    bids.documents.forEach { doc ->
      val docUids = (doc.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
      if (docUids != targetUids) {
        batch.update(doc.reference, mapOf("memberUids" to targetUids))
        count++
      }
    }

    if (count > 0) batch.commit().await()
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "committees/${committee.id}", auth)
  }

  suspend fun addMember(committee: Committee, name: String, mobileNumber: String): Result<Unit> = runCatching {
    requireUserId()
    val memberId = "mem_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val commRef = db.collection("committees").document(committee.id)
    commRef.collection("members").document(memberId).set(
      mapOf(
        "committeeId" to committee.id,
        "adminUid" to committee.adminUid,
        "linkedUid" to "",
        "name" to name.trim().take(60),
        "mobileNumber" to mobileNumber.trim().take(20),
        "currentInstallmentStatus" to "pending",
        "paidRounds" to emptyList<Long>(),
        "hasWonRound" to 0L,
        "memberUids" to committee.memberUids,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
    ).await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.CREATE, "committees/${committee.id}/members", auth)
  }

  suspend fun toggleMemberInstallment(committee: Committee, member: CommitteeMember): Result<Unit> = runCatching {
    requireUserId()
    val newStatus = if (member.isPaid) "pending" else "paid"
    val updatedRounds = member.paidRounds.toMutableList()
    if (newStatus == "paid" && !updatedRounds.contains(committee.roundNumber)) {
      updatedRounds.add(committee.roundNumber)
    } else if (newStatus == "pending") {
      updatedRounds.remove(committee.roundNumber)
    }
    val docRef = db.collection("committees").document(committee.id)
      .collection("members").document(member.id)
    docRef.update(
      mapOf(
        "currentInstallmentStatus" to newStatus,
        "paidRounds" to updatedRounds.take(60),
        "updatedAt" to FieldValue.serverTimestamp()
      )
    ).await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "committees/${committee.id}/members/${member.id}", auth)
  }

  suspend fun deleteMember(committeeId: String, memberId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection("committees").document(committeeId)
      .collection("members").document(memberId)
    docRef.delete().await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.DELETE, "committees/$committeeId/members/$memberId", auth)
  }

  suspend fun updateCommitteeSettings(
    committee: Committee,
    name: String,
    totalBcAmount: Long,
    totalMembers: Long,
    timerMinutes: Long,
    scheduledAtMillis: Long,
    winnerSloganTemplate: String,
    apkDownloadUrl: String
  ): Result<Unit> = runCatching {
    requireUserId()
    val safeName = name.trim().take(80).ifBlank { committee.name }
    val safeAmount = totalBcAmount.coerceIn(1000L, 100_000_000L)
    val safeMembers = totalMembers.coerceIn(2L, 50L)
    val safeTimer = timerMinutes.coerceIn(1L, 180L)
    val safeSlogan = winnerSloganTemplate.trim().take(300).ifBlank { Committee.DEFAULT_SLOGAN_TEMPLATE }
    val safeUrl = apkDownloadUrl.trim().take(500)

    val updates = mutableMapOf<String, Any>(
      "name" to safeName,
      "totalBcAmount" to safeAmount,
      "totalMembers" to safeMembers,
      "timerMinutes" to safeTimer,
      "scheduledAtMillis" to scheduledAtMillis.coerceAtLeast(0L),
      "winnerSloganTemplate" to safeSlogan,
      "apkDownloadUrl" to safeUrl,
      "updatedAt" to FieldValue.serverTimestamp()
    )

    if (committee.winnerDeclared && committee.winningBid >= 0) {
      val (netPayable, perMember) = BcCalculator.calculateBreakdown(safeAmount, safeMembers, committee.winningBid)
      updates["netPayableToWinner"] = netPayable
      updates["perMemberInstallment"] = perMember
    }

    db.collection("committees").document(committee.id).update(updates).await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "committees/${committee.id}", auth)
  }

  suspend fun startLiveBidding(committee: Committee, customMinutes: Long): Result<Unit> = runCatching {
    val uid = requireUserId()
    val safeMinutes = customMinutes.coerceIn(1L, 180L)
    val now = System.currentTimeMillis()
    val endsAt = now + safeMinutes * 60_000L
    val commRef = db.collection("committees").document(committee.id)

    commRef.update(
      mapOf(
        "timerMinutes" to safeMinutes,
        "scheduledAtMillis" to 0L,
        "isLive" to true,
        "biddingStartedAtMillis" to now,
        "biddingEndsAtMillis" to endsAt,
        "winnerDeclared" to false,
        "currentHighestBid" to 0L,
        "currentHighestBidderUid" to "",
        "currentHighestBidderName" to "",
        "currentHighestBidderMobile" to "",
        "updatedAt" to FieldValue.serverTimestamp()
      )
    ).await()

    val bidId = "sys_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    commRef.collection("bids").document(bidId).set(
      mapOf(
        "committeeId" to committee.id,
        "roundNumber" to committee.roundNumber,
        "senderUid" to uid,
        "senderName" to "सिस्टम",
        "senderMobile" to "",
        "type" to "system",
        "amount" to 0L,
        "message" to "🔔 दौर #${committee.roundNumber} की लाइव बोली शुरू हो गई है! (समय: $safeMinutes मिनट)",
        "memberUids" to committee.memberUids,
        "createdAt" to FieldValue.serverTimestamp()
      )
    ).await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "committees/${committee.id}", auth)
  }

  suspend fun triggerScheduledAutoStart(committee: Committee): Result<Unit> = runCatching {
    val uid = requireUserId()
    if (committee.isLive || committee.scheduledAtMillis <= 0L) return@runCatching
    val now = System.currentTimeMillis()
    val endsAt = now + committee.timerMinutes.coerceIn(1L, 180L) * 60_000L
    val commRef = db.collection("committees").document(committee.id)

    commRef.update(
      mapOf(
        "isLive" to true,
        "scheduledAtMillis" to 0L,
        "biddingStartedAtMillis" to now,
        "biddingEndsAtMillis" to endsAt,
        "winnerDeclared" to false,
        "updatedAt" to FieldValue.serverTimestamp()
      )
    ).await()

    val bidId = "sys_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    commRef.collection("bids").document(bidId).set(
      mapOf(
        "committeeId" to committee.id,
        "roundNumber" to committee.roundNumber,
        "senderUid" to uid,
        "senderName" to "सिस्टम",
        "senderMobile" to "",
        "type" to "system",
        "amount" to 0L,
        "message" to "⏰ निर्धारित समय पर लाइव बोली अपने-आप शुरू हुई! (${committee.timerMinutes} मिनट)",
        "memberUids" to committee.memberUids,
        "createdAt" to FieldValue.serverTimestamp()
      )
    ).await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "committees/${committee.id}", auth)
  }

  suspend fun placeBid(
    committee: Committee,
    amount: Long,
    bidderName: String,
    bidderMobile: String
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(committee.isLive) { "बोली कक्ष अभी बंद है।" }
    require(amount > committee.currentHighestBid) {
      "बोली वर्तमान उच्चतम बोली (${BcCalculator.formatRupees(committee.currentHighestBid)}) से अधिक होनी चाहिए।"
    }
    require(amount <= committee.totalBcAmount) {
      "बोली कुल BC राशि (${BcCalculator.formatRupees(committee.totalBcAmount)}) से अधिक नहीं हो सकती।"
    }

    val safeName = bidderName.trim().take(60).ifBlank { "सदस्य" }
    val safeMobile = bidderMobile.trim().take(20)
    val commRef = db.collection("committees").document(committee.id)

    commRef.update(
      mapOf(
        "currentHighestBid" to amount,
        "currentHighestBidderUid" to uid,
        "currentHighestBidderName" to safeName,
        "currentHighestBidderMobile" to safeMobile,
        "updatedAt" to FieldValue.serverTimestamp()
      )
    ).await()

    val bidId = "bid_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    commRef.collection("bids").document(bidId).set(
      mapOf(
        "committeeId" to committee.id,
        "roundNumber" to committee.roundNumber,
        "senderUid" to uid,
        "senderName" to safeName,
        "senderMobile" to safeMobile,
        "type" to "bid",
        "amount" to amount,
        "message" to "बोली लगाई",
        "memberUids" to committee.memberUids,
        "createdAt" to FieldValue.serverTimestamp()
      )
    ).await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "committees/${committee.id}", auth)
  }

  suspend fun sendChatMessage(
    committee: Committee,
    text: String,
    senderName: String,
    senderMobile: String
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val cleanMsg = text.trim().take(500)
    require(cleanMsg.isNotEmpty()) { "संदेश खाली नहीं हो सकता।" }

    val bidId = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    db.collection("committees").document(committee.id).collection("bids").document(bidId).set(
      mapOf(
        "committeeId" to committee.id,
        "roundNumber" to committee.roundNumber,
        "senderUid" to uid,
        "senderName" to senderName.trim().take(60).ifBlank { "सदस्य" },
        "senderMobile" to senderMobile.trim().take(20),
        "type" to "chat",
        "amount" to 0L,
        "message" to cleanMsg,
        "memberUids" to committee.memberUids,
        "createdAt" to FieldValue.serverTimestamp()
      )
    ).await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.CREATE, "committees/${committee.id}/bids", auth)
  }

  suspend fun lockAndDeclareWinner(
    committee: Committee,
    members: List<CommitteeMember>,
    manualWinnerName: String? = null,
    manualWinningBid: Long? = null
  ): Result<RoundRecord> = runCatching {
    val uid = requireUserId()
    val winningBid = manualWinningBid ?: committee.currentHighestBid
    val winnerName = (manualWinnerName ?: committee.currentHighestBidderName)
      .trim()
      .ifBlank { "कोई बोली नहीं (अनिर्णीत)" }
    val winnerUid = if (manualWinnerName == null) committee.currentHighestBidderUid else ""
    val winnerMobile = if (manualWinnerName == null) committee.currentHighestBidderMobile else ""

    val (netPayable, perMemberInstallment) = BcCalculator.calculateBreakdown(
      totalBcAmount = committee.totalBcAmount,
      totalMembers = committee.totalMembers,
      winningBid = winningBid
    )
    val sloganText = BcCalculator.formatSlogan(committee.winnerSloganTemplate, winnerName)
    val commRef = db.collection("committees").document(committee.id)

    commRef.update(
      mapOf(
        "isLive" to false,
        "biddingEndsAtMillis" to 0L,
        "winnerDeclared" to true,
        "winnerName" to winnerName.take(60),
        "winnerUid" to winnerUid.take(128),
        "winnerMobile" to winnerMobile.take(20),
        "winningBid" to winningBid,
        "netPayableToWinner" to netPayable,
        "perMemberInstallment" to perMemberInstallment,
        "updatedAt" to FieldValue.serverTimestamp()
      )
    ).await()

    val roundId = "round_${committee.roundNumber}"
    val roundDocRef = commRef.collection("rounds").document(roundId)
    if (!roundDocRef.get().await().exists()) {
      roundDocRef.set(
        mapOf(
          "committeeId" to committee.id,
          "roundNumber" to committee.roundNumber,
          "totalBcAmount" to committee.totalBcAmount,
          "totalMembers" to committee.totalMembers,
          "winnerUid" to winnerUid.take(128),
          "winnerName" to winnerName.take(60),
          "winnerMobile" to winnerMobile.take(20),
          "winningBid" to winningBid,
          "netPayableToWinner" to netPayable,
          "perMemberInstallment" to perMemberInstallment,
          "sloganText" to sloganText.take(400),
          "memberUids" to committee.memberUids,
          "createdAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }

    val sysBidId = "sys_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    commRef.collection("bids").document(sysBidId).set(
      mapOf(
        "committeeId" to committee.id,
        "roundNumber" to committee.roundNumber,
        "senderUid" to uid,
        "senderName" to "सिस्टम",
        "senderMobile" to "",
        "type" to "system",
        "amount" to winningBid,
        "message" to "🏆 बोली समाप्त! विजेता: $winnerName (${BcCalculator.formatRupees(winningBid)}) • प्रति किस्त: ${BcCalculator.formatRupees(perMemberInstallment)}",
        "memberUids" to committee.memberUids,
        "createdAt" to FieldValue.serverTimestamp()
      )
    ).await()

    if (committee.adminUid == uid && members.isNotEmpty()) {
      val batch = db.batch()
      members.forEach { member ->
        val mRef = commRef.collection("members").document(member.id)
        val isWinner = (winnerUid.isNotBlank() && member.linkedUid == winnerUid) ||
          (member.name.equals(winnerName, ignoreCase = true))
        batch.update(
          mRef,
          mapOf(
            "currentInstallmentStatus" to "pending",
            "hasWonRound" to if (isWinner) committee.roundNumber else member.hasWonRound,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        )
      }
      batch.commit().await()
    }

    RoundRecord(
      id = roundId,
      committeeId = committee.id,
      roundNumber = committee.roundNumber,
      totalBcAmount = committee.totalBcAmount,
      totalMembers = committee.totalMembers,
      winnerUid = winnerUid,
      winnerName = winnerName,
      winnerMobile = winnerMobile,
      winningBid = winningBid,
      netPayableToWinner = netPayable,
      perMemberInstallment = perMemberInstallment,
      sloganText = sloganText,
      memberUids = committee.memberUids
    )
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "committees/${committee.id}", auth)
  }

  suspend fun startNextRound(committee: Committee): Result<Unit> = runCatching {
    requireUserId()
    db.collection("committees").document(committee.id).update(
      mapOf(
        "roundNumber" to (committee.roundNumber + 1L),
        "isLive" to false,
        "winnerDeclared" to false,
        "currentHighestBid" to 0L,
        "currentHighestBidderUid" to "",
        "currentHighestBidderName" to "",
        "currentHighestBidderMobile" to "",
        "winnerName" to "",
        "winnerUid" to "",
        "winnerMobile" to "",
        "winningBid" to 0L,
        "biddingStartedAtMillis" to 0L,
        "biddingEndsAtMillis" to 0L,
        "updatedAt" to FieldValue.serverTimestamp()
      )
    ).await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "committees/${committee.id}", auth)
  }
}
