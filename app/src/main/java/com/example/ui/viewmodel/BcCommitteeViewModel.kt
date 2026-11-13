package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SirenSynthesizer
import com.example.data.preferences.AppPreferences
import com.example.data.repository.CommitteeRepository
import com.example.model.AppThemeMode
import com.example.model.BcCalculator
import com.example.model.BidMessage
import com.example.model.Committee
import com.example.model.CommitteeMember
import com.example.model.RoundRecord
import com.example.model.UserProfile
import com.example.notifications.BiddingNotificationHelper
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface BcEvent {
  data object OpenLiveBiddingRoom : BcEvent
  data class ShowWinnerCelebration(val roundRecord: RoundRecord) : BcEvent
  data class ShowToast(val message: String) : BcEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class BcCommitteeViewModel(
  private val repository: CommitteeRepository,
  private val preferences: AppPreferences,
  private val appContext: Context,
  val currentUserId: String,
  private val currentUserEmail: String,
  private val currentUserDisplayName: String
) : ViewModel() {

  private val _events = MutableSharedFlow<BcEvent>(extraBufferCapacity = 8)
  val events = _events.asSharedFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _countdownRemainingSeconds = MutableStateFlow(0L)
  val countdownRemainingSeconds: StateFlow<Long> = _countdownRemainingSeconds.asStateFlow()

  private val _scheduleRemainingSeconds = MutableStateFlow(0L)
  val scheduleRemainingSeconds: StateFlow<Long> = _scheduleRemainingSeconds.asStateFlow()

  private val _showWinnerModal = MutableStateFlow<RoundRecord?>(null)
  val showWinnerModal: StateFlow<RoundRecord?> = _showWinnerModal.asStateFlow()

  val themeMode: StateFlow<AppThemeMode> = preferences.themeModeFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppThemeMode.ROYAL_BLUE)

  val soundEnabled: StateFlow<Boolean> = preferences.soundEnabledFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

  val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
    .catch { error ->
      val fErr = unwrapFirestoreError(error)
      if (fErr == null && error is CancellationException) throw error
      Log.w(TAG, "Error observing user profile", fErr ?: error)
      emit(null)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val committees: StateFlow<List<Committee>> = repository.observeMyCommittees(currentUserId)
    .catch { error ->
      val fErr = unwrapFirestoreError(error)
      if (fErr == null && error is CancellationException) throw error
      Log.w(TAG, "Error observing committees", fErr ?: error)
      emit(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  private val _selectedCommitteeId = MutableStateFlow("")

  val activeCommittee: StateFlow<Committee?> = combine(
    committees,
    userProfile,
    _selectedCommitteeId
  ) { list, profile, selectedId ->
    val targetId = selectedId.ifBlank { profile?.activeCommitteeId.orEmpty() }
    list.firstOrNull { it.id == targetId } ?: list.firstOrNull()
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val members: StateFlow<List<CommitteeMember>> = activeCommittee
    .map { it?.id.orEmpty() }
    .distinctUntilChanged()
    .flatMapLatest { committeeId ->
      if (committeeId.isBlank()) flowOf(emptyList())
      else repository.observeMembers(committeeId, currentUserId)
        .catch { error ->
          val fErr = unwrapFirestoreError(error)
          if (fErr == null && error is CancellationException) throw error
          Log.w(TAG, "Error observing members", fErr ?: error)
          emit(emptyList())
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val bids: StateFlow<List<BidMessage>> = activeCommittee
    .map { it?.id.orEmpty() }
    .distinctUntilChanged()
    .flatMapLatest { committeeId ->
      if (committeeId.isBlank()) flowOf(emptyList())
      else repository.observeBids(committeeId, currentUserId)
        .catch { error ->
          val fErr = unwrapFirestoreError(error)
          if (fErr == null && error is CancellationException) throw error
          Log.w(TAG, "Error observing bids", fErr ?: error)
          emit(emptyList())
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val rounds: StateFlow<List<RoundRecord>> = activeCommittee
    .map { it?.id.orEmpty() }
    .distinctUntilChanged()
    .flatMapLatest { committeeId ->
      if (committeeId.isBlank()) flowOf(emptyList())
      else repository.observeRounds(committeeId, currentUserId)
        .catch { error ->
          val fErr = unwrapFirestoreError(error)
          if (fErr == null && error is CancellationException) throw error
          Log.w(TAG, "Error observing rounds", fErr ?: error)
          emit(emptyList())
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  private var lastAutoStartedRoundKey: String = ""
  private var lastAutoEndedRoundKey: String = ""
  private var lastObservedWinnerRoundKey: String = ""
  private var lastObservedLiveKey: String = ""
  private var lastSyncedMembersHash: Int = 0

  init {
    ensureUserProfileExists()
    startCountdownAndSchedulerLoop()
    observeRemoteStateTransitions()
  }

  private fun ensureUserProfileExists() {
    viewModelScope.launch {
      if (userProfile.value == null) {
        val fallbackName = currentUserDisplayName.ifBlank {
          currentUserEmail.substringBefore("@").ifBlank { "सदस्य" }
        }
        repository.saveUserProfile(
          displayName = fallbackName,
          mobileNumber = "",
          email = currentUserEmail
        )
      }
    }
  }

  private fun observeRemoteStateTransitions() {
    viewModelScope.launch {
      activeCommittee.collect { comm ->
        if (comm == null) return@collect

        // Sync subcollection memberUids if admin detects new joined members
        val uidHash = comm.memberUids.hashCode()
        if (comm.adminUid == currentUserId && uidHash != lastSyncedMembersHash) {
          lastSyncedMembersHash = uidHash
          repository.syncSubcollectionMemberUids(comm)
        }

        // Sync AlarmManager for scheduled time
        if (comm.scheduledAtMillis > System.currentTimeMillis() && !comm.isLive) {
          BiddingNotificationHelper.scheduleBiddingAlarm(appContext, comm.scheduledAtMillis, comm.name)
        } else {
          BiddingNotificationHelper.cancelBiddingAlarm(appContext)
        }

        // Detect live room transition on any member device
        val liveKey = "${comm.id}_${comm.roundNumber}_${comm.isLive}_${comm.biddingStartedAtMillis}"
        if (comm.isLive && lastObservedLiveKey.isNotBlank() && lastObservedLiveKey != liveKey) {
          if (soundEnabled.value) {
            SirenSynthesizer.playChoonChoonSiren(cycles = 5)
          }
          BiddingNotificationHelper.showBiddingStartedNotification(appContext, comm.name)
          _events.tryEmit(BcEvent.OpenLiveBiddingRoom)
        }
        lastObservedLiveKey = liveKey

        // Detect winner declared transition on every member device
        val winnerKey = "${comm.id}_${comm.roundNumber}_${comm.winnerDeclared}_${comm.winnerName}"
        if (comm.winnerDeclared && lastObservedWinnerRoundKey.isNotBlank() && lastObservedWinnerRoundKey != winnerKey) {
          if (soundEnabled.value) {
            SirenSynthesizer.playEndSiren()
          }
          val slogan = BcCalculator.formatSlogan(comm.winnerSloganTemplate, comm.winnerName)
          val record = RoundRecord(
            id = "round_${comm.roundNumber}",
            committeeId = comm.id,
            roundNumber = comm.roundNumber,
            totalBcAmount = comm.totalBcAmount,
            totalMembers = comm.totalMembers,
            winnerUid = comm.winnerUid,
            winnerName = comm.winnerName,
            winnerMobile = comm.winnerMobile,
            winningBid = comm.winningBid,
            netPayableToWinner = comm.netPayableToWinner,
            perMemberInstallment = comm.perMemberInstallment,
            sloganText = slogan,
            memberUids = comm.memberUids
          )
          _showWinnerModal.value = record
          BiddingNotificationHelper.showWinnerDeclaredNotification(
            context = appContext,
            committeeName = comm.name,
            winnerName = comm.winnerName,
            perMemberInstallment = comm.perMemberInstallment,
            sloganText = slogan
          )
        }
        lastObservedWinnerRoundKey = winnerKey
      }
    }
  }

  private fun startCountdownAndSchedulerLoop() {
    viewModelScope.launch {
      while (isActive) {
        val comm = activeCommittee.value
        val now = System.currentTimeMillis()
        if (comm != null) {
          if (comm.isLive && comm.biddingEndsAtMillis > 0L) {
            val remainingSec = ((comm.biddingEndsAtMillis - now) / 1000L).coerceAtLeast(0L)
            _countdownRemainingSeconds.value = remainingSec
            val endKey = "${comm.id}_${comm.roundNumber}_${comm.biddingEndsAtMillis}"
            if (remainingSec == 0L && lastAutoEndedRoundKey != endKey) {
              lastAutoEndedRoundKey = endKey
              onTimerExpired(comm)
            }
          } else {
            _countdownRemainingSeconds.value = 0L
          }

          if (!comm.isLive && comm.scheduledAtMillis > 0L) {
            val schedRemSec = ((comm.scheduledAtMillis - now) / 1000L).coerceAtLeast(0L)
            _scheduleRemainingSeconds.value = schedRemSec
            val startKey = "${comm.id}_${comm.roundNumber}_${comm.scheduledAtMillis}"
            if (now >= comm.scheduledAtMillis && lastAutoStartedRoundKey != startKey) {
              lastAutoStartedRoundKey = startKey
              onScheduledTimeArrived(comm)
            }
          } else {
            _scheduleRemainingSeconds.value = 0L
          }
        } else {
          _countdownRemainingSeconds.value = 0L
          _scheduleRemainingSeconds.value = 0L
        }
        delay(500L)
      }
    }
  }

  private fun onScheduledTimeArrived(comm: Committee) {
    viewModelScope.launch {
      repository.triggerScheduledAutoStart(comm).onSuccess {
        if (soundEnabled.value) {
          SirenSynthesizer.playChoonChoonSiren(cycles = 6)
        }
        BiddingNotificationHelper.showBiddingStartedNotification(appContext, comm.name)
        _events.tryEmit(BcEvent.OpenLiveBiddingRoom)
      }
    }
  }

  private fun onTimerExpired(comm: Committee) {
    viewModelScope.launch {
      if (soundEnabled.value) {
        SirenSynthesizer.playEndSiren()
      }
      repository.lockAndDeclareWinner(comm, members.value).onSuccess { record ->
        _showWinnerModal.value = record
        BiddingNotificationHelper.showWinnerDeclaredNotification(
          context = appContext,
          committeeName = comm.name,
          winnerName = record.winnerName,
          perMemberInstallment = record.perMemberInstallment,
          sloganText = record.sloganText
        )
      }
    }
  }

  fun dismissWinnerModal() {
    _showWinnerModal.value = null
  }

  fun openLatestWinnerModal() {
    val comm = activeCommittee.value ?: return
    if (!comm.winnerDeclared) return
    val slogan = BcCalculator.formatSlogan(comm.winnerSloganTemplate, comm.winnerName)
    _showWinnerModal.value = RoundRecord(
      id = "round_${comm.roundNumber}",
      committeeId = comm.id,
      roundNumber = comm.roundNumber,
      totalBcAmount = comm.totalBcAmount,
      totalMembers = comm.totalMembers,
      winnerUid = comm.winnerUid,
      winnerName = comm.winnerName,
      winnerMobile = comm.winnerMobile,
      winningBid = comm.winningBid,
      netPayableToWinner = comm.netPayableToWinner,
      perMemberInstallment = comm.perMemberInstallment,
      sloganText = slogan,
      memberUids = comm.memberUids
    )
  }

  fun setThemeMode(mode: AppThemeMode) {
    viewModelScope.launch {
      preferences.setThemeMode(mode)
    }
  }

  fun setSoundEnabled(enabled: Boolean) {
    viewModelScope.launch {
      preferences.setSoundEnabled(enabled)
      if (!enabled) SirenSynthesizer.stop()
    }
  }

  fun testChoonChoonSiren() {
    SirenSynthesizer.playChoonChoonSiren(cycles = 3)
    _events.tryEmit(BcEvent.ShowToast("🔊 चूँ-चूँ सायरन बज रहा है!"))
  }

  fun selectCommittee(committeeId: String) {
    _selectedCommitteeId.value = committeeId
  }

  fun createCommittee(
    name: String,
    totalBcAmount: Long,
    totalMembers: Long,
    timerMinutes: Long,
    creatorName: String,
    creatorMobile: String
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.createCommittee(
        name = name,
        totalBcAmount = totalBcAmount,
        totalMembers = totalMembers,
        timerMinutes = timerMinutes,
        creatorName = creatorName,
        creatorMobile = creatorMobile,
        creatorEmail = currentUserEmail
      ).onSuccess { id ->
        _selectedCommitteeId.value = id
        _events.tryEmit(BcEvent.ShowToast("🎉 BC कमेटी सफलतापूर्वक बन गई!"))
      }.onFailure { err ->
        _events.tryEmit(BcEvent.ShowToast(err.localizedMessage ?: "कमेटी बनाने में त्रुटि"))
      }
      _isBusy.value = false
    }
  }

  fun joinCommittee(inviteCode: String, memberName: String, memberMobile: String) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.joinCommitteeByInviteCode(
        inviteCode = inviteCode,
        memberName = memberName,
        memberMobile = memberMobile,
        memberEmail = currentUserEmail
      ).onSuccess { id ->
        _selectedCommitteeId.value = id
        _events.tryEmit(BcEvent.ShowToast("✅ आप सफलतापूर्वक कमेटी में जुड़ गए!"))
      }.onFailure { err ->
        _events.tryEmit(BcEvent.ShowToast(err.localizedMessage ?: "जुड़ने में त्रुटि"))
      }
      _isBusy.value = false
    }
  }

  fun updateMyProfile(name: String, mobile: String) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.saveUserProfile(
        displayName = name,
        mobileNumber = mobile,
        email = currentUserEmail,
        activeCommitteeId = activeCommittee.value?.id.orEmpty()
      ).onSuccess {
        _events.tryEmit(BcEvent.ShowToast("प्रोफ़ाइल सहेजी गई!"))
      }.onFailure { err ->
        _events.tryEmit(BcEvent.ShowToast(err.localizedMessage ?: "प्रोफ़ाइल सहेजने में त्रुटि"))
      }
      _isBusy.value = false
    }
  }

  fun addMember(name: String, mobile: String) {
    val comm = activeCommittee.value ?: return
    viewModelScope.launch {
      repository.addMember(comm, name, mobile).onSuccess {
        _events.tryEmit(BcEvent.ShowToast("✅ सदस्य जोड़ दिया गया"))
      }.onFailure { err ->
        _events.tryEmit(BcEvent.ShowToast(err.localizedMessage ?: "सदस्य जोड़ने में त्रुटि"))
      }
    }
  }

  fun toggleInstallment(member: CommitteeMember) {
    val comm = activeCommittee.value ?: return
    viewModelScope.launch {
      repository.toggleMemberInstallment(comm, member).onFailure { err ->
        _events.tryEmit(BcEvent.ShowToast(err.localizedMessage ?: "किस्त अपडेट करने में त्रुटि"))
      }
    }
  }

  fun deleteMember(member: CommitteeMember) {
    val comm = activeCommittee.value ?: return
    viewModelScope.launch {
      repository.deleteMember(comm.id, member.id).onSuccess {
        _events.tryEmit(BcEvent.ShowToast("सदस्य हटाया गया"))
      }
    }
  }

  fun saveSettings(
    name: String,
    totalBcAmount: Long,
    totalMembers: Long,
    timerMinutes: Long,
    scheduledAtMillis: Long,
    sloganTemplate: String,
    apkDownloadUrl: String
  ) {
    val comm = activeCommittee.value ?: return
    viewModelScope.launch {
      _isBusy.value = true
      repository.updateCommitteeSettings(
        committee = comm,
        name = name,
        totalBcAmount = totalBcAmount,
        totalMembers = totalMembers,
        timerMinutes = timerMinutes,
        scheduledAtMillis = scheduledAtMillis,
        winnerSloganTemplate = sloganTemplate,
        apkDownloadUrl = apkDownloadUrl
      ).onSuccess {
        if (scheduledAtMillis > System.currentTimeMillis()) {
          BiddingNotificationHelper.scheduleBiddingAlarm(appContext, scheduledAtMillis, name)
        } else {
          BiddingNotificationHelper.cancelBiddingAlarm(appContext)
        }
        _events.tryEmit(BcEvent.ShowToast("⚙️ सेटिंग्स सफलतापूर्वक सहेजी गईं!"))
      }.onFailure { err ->
        _events.tryEmit(BcEvent.ShowToast(err.localizedMessage ?: "सेटिंग्स सहेजने में त्रुटि"))
      }
      _isBusy.value = false
    }
  }

  fun startLiveBiddingNow(customMinutes: Long) {
    val comm = activeCommittee.value ?: return
    viewModelScope.launch {
      _isBusy.value = true
      repository.startLiveBidding(comm, customMinutes).onSuccess {
        if (soundEnabled.value) {
          SirenSynthesizer.playChoonChoonSiren(cycles = 5)
        }
        BiddingNotificationHelper.showBiddingStartedNotification(appContext, comm.name)
        _events.tryEmit(BcEvent.OpenLiveBiddingRoom)
      }.onFailure { err ->
        _events.tryEmit(BcEvent.ShowToast(err.localizedMessage ?: "बोली शुरू करने में त्रुटि"))
      }
      _isBusy.value = false
    }
  }

  fun placeBid(amount: Long) {
    val comm = activeCommittee.value ?: return
    val profile = userProfile.value
    val name = profile?.displayName?.ifBlank { currentUserDisplayName } ?: currentUserDisplayName.ifBlank { "सदस्य" }
    val mobile = profile?.mobileNumber.orEmpty()
    viewModelScope.launch {
      repository.placeBid(comm, amount, name, mobile).onSuccess {
        if (soundEnabled.value) SirenSynthesizer.playBidPop()
      }.onFailure { err ->
        _events.tryEmit(BcEvent.ShowToast(err.localizedMessage ?: "बोली लगाने में त्रुटि"))
      }
    }
  }

  fun sendChatMessage(message: String) {
    val comm = activeCommittee.value ?: return
    val profile = userProfile.value
    val name = profile?.displayName?.ifBlank { currentUserDisplayName } ?: currentUserDisplayName.ifBlank { "सदस्य" }
    val mobile = profile?.mobileNumber.orEmpty()
    viewModelScope.launch {
      repository.sendChatMessage(comm, message, name, mobile).onFailure { err ->
        _events.tryEmit(BcEvent.ShowToast(err.localizedMessage ?: "संदेश भेजने में त्रुटि"))
      }
    }
  }

  fun endBiddingAndDeclareWinner(manualWinnerName: String? = null, manualBid: Long? = null) {
    val comm = activeCommittee.value ?: return
    viewModelScope.launch {
      _isBusy.value = true
      if (soundEnabled.value) {
        SirenSynthesizer.playEndSiren()
      }
      repository.lockAndDeclareWinner(
        committee = comm,
        members = members.value,
        manualWinnerName = manualWinnerName,
        manualWinningBid = manualBid
      ).onSuccess { record ->
        _showWinnerModal.value = record
        BiddingNotificationHelper.showWinnerDeclaredNotification(
          context = appContext,
          committeeName = comm.name,
          winnerName = record.winnerName,
          perMemberInstallment = record.perMemberInstallment,
          sloganText = record.sloganText
        )
      }.onFailure { err ->
        _events.tryEmit(BcEvent.ShowToast(err.localizedMessage ?: "विजेता घोषित करने में त्रुटि"))
      }
      _isBusy.value = false
    }
  }

  fun startNextRound() {
    val comm = activeCommittee.value ?: return
    viewModelScope.launch {
      repository.startNextRound(comm).onSuccess {
        _showWinnerModal.value = null
        _events.tryEmit(BcEvent.ShowToast("🔄 नया दौर #${comm.roundNumber + 1} तैयार है!"))
      }
    }
  }

  private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
    generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()

  private companion object {
    const val TAG = "BcCommitteeVM"
  }
}
