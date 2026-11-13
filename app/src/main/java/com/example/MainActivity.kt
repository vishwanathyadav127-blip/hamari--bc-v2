package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.preferences.AppPreferences
import com.example.data.repository.CommitteeRepository
import com.example.model.AppThemeMode
import com.example.notifications.BiddingNotificationHelper
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.WinnerCelebrationDialog
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.LiveBiddingScreen
import com.example.ui.screens.MembersAndInstallmentsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupCommitteeScreen
import com.example.ui.theme.HamariBcTheme
import com.example.ui.viewmodel.BcCommitteeViewModel
import com.example.ui.viewmodel.BcEvent
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_HOME = "home"
const val ROUTE_INSTALLMENTS = "installments"
const val ROUTE_LIVE_BIDDING = "live_bidding"
const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {
  private var openLiveFromIntent by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    BiddingNotificationHelper.ensureChannel(this)
    openLiveFromIntent = intent?.getBooleanExtra(BiddingNotificationHelper.EXTRA_OPEN_LIVE_BIDDING, false) == true

    setContent {
      val context = LocalContext.current
      val preferences = remember(context) { AppPreferences(context.applicationContext) }
      val themeMode by preferences.themeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.ROYAL_BLUE)

      HamariBcTheme(themeMode = themeMode) {
        AppRoot(
          preferences = preferences,
          openLiveOnStart = openLiveFromIntent,
          onConsumedLiveIntent = { openLiveFromIntent = false }
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    if (intent.getBooleanExtra(BiddingNotificationHelper.EXTRA_OPEN_LIVE_BIDDING, false)) {
      openLiveFromIntent = true
    }
  }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth ->
    trySend(auth.currentUser)
  }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
private fun AppRoot(
  preferences: AppPreferences,
  openLiveOnStart: Boolean,
  onConsumedLiveIntent: () -> Unit,
  auth: FirebaseAuth = Firebase.auth
) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(
      onAuthSuccess = {}
    )
  } else {
    val uid = user.uid
    key(uid) {
      HamariBcAuthenticatedApp(
        currentUserId = uid,
        currentUserEmail = user.email.orEmpty(),
        currentUserName = user.displayName.orEmpty(),
        preferences = preferences,
        openLiveOnStart = openLiveOnStart,
        onConsumedLiveIntent = onConsumedLiveIntent
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HamariBcAuthenticatedApp(
  currentUserId: String,
  currentUserEmail: String,
  currentUserName: String,
  preferences: AppPreferences,
  openLiveOnStart: Boolean,
  onConsumedLiveIntent: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: BcCommitteeViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY])
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val repo = CommitteeRepository(db, Firebase.auth)
        BcCommitteeViewModel(
          repository = repo,
          preferences = preferences,
          appContext = app.applicationContext,
          currentUserId = currentUserId,
          currentUserEmail = currentUserEmail,
          currentUserDisplayName = currentUserName
        )
      }
    }
  )

  // Runtime notification permission on Android 13+
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { }
  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) {
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
  val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
  val activeCommittee by viewModel.activeCommittee.collectAsStateWithLifecycle()
  val members by viewModel.members.collectAsStateWithLifecycle()
  val bids by viewModel.bids.collectAsStateWithLifecycle()
  val rounds by viewModel.rounds.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val countdownSeconds by viewModel.countdownRemainingSeconds.collectAsStateWithLifecycle()
  val scheduleSeconds by viewModel.scheduleRemainingSeconds.collectAsStateWithLifecycle()
  val winnerModal by viewModel.showWinnerModal.collectAsStateWithLifecycle()

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME

  LaunchedEffect(openLiveOnStart, activeCommittee) {
    if (openLiveOnStart && activeCommittee != null) {
      onConsumedLiveIntent()
      navController.navigate(ROUTE_LIVE_BIDDING) {
        launchSingleTop = true
      }
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is BcEvent.OpenLiveBiddingRoom -> {
          navController.navigate(ROUTE_LIVE_BIDDING) {
            launchSingleTop = true
          }
        }
        is BcEvent.ShowWinnerCelebration -> {
          // Handled via StateFlow showWinnerModal
        }
        is BcEvent.ShowToast -> {
          Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  val committee = activeCommittee
  val isAdmin = committee != null && (
    committee.adminUid == currentUserId ||
      currentUserEmail.equals("VishwanathYadav127@gmail.com", ignoreCase = true)
    )

  if (committee == null && currentRoute != ROUTE_SETTINGS) {
    SetupCommitteeScreen(
      defaultName = userProfile?.displayName ?: currentUserName,
      defaultMobile = userProfile?.mobileNumber.orEmpty(),
      isBusy = isBusy,
      onCreateCommittee = viewModel::createCommittee,
      onJoinCommittee = viewModel::joinCommittee,
      onOpenSettings = {
        navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
      }
    )
  } else {
    val isFullScreen = currentRoute == ROUTE_LIVE_BIDDING || currentRoute == ROUTE_SETTINGS

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val useRail = maxWidth >= 700.dp

      Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
          if (!isFullScreen && committee != null) {
            TopAppBar(
              title = {
                Text(
                  text = committee.name,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold
                )
              },
              actions = {
                IconButton(
                  onClick = {
                    navController.navigate(ROUTE_LIVE_BIDDING) { launchSingleTop = true }
                  },
                  modifier = Modifier.testTag("header_live_button")
                ) {
                  BadgedBox(
                    badge = {
                      if (committee.isLive) {
                        Badge { Text("LIVE") }
                      }
                    }
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Gavel,
                      contentDescription = "लाइव बोली कक्ष"
                    )
                  }
                }
                IconButton(
                  onClick = {
                    navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                  },
                  modifier = Modifier.testTag("header_settings_button")
                ) {
                  Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "सेटिंग्स"
                  )
                }
              }
            )
          }
        },
        bottomBar = {
          if (!isFullScreen && !useRail && committee != null) {
            NavigationBar {
              NavigationBarItem(
                selected = currentRoute == ROUTE_HOME,
                onClick = {
                  navController.navigate(ROUTE_HOME) {
                    popUpTo(ROUTE_HOME) { inclusive = true }
                    launchSingleTop = true
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (currentRoute == ROUTE_HOME) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "होम"
                  )
                },
                label = { Text("होम") },
                modifier = Modifier.testTag("nav_home")
              )
              NavigationBarItem(
                selected = currentRoute == ROUTE_LIVE_BIDDING,
                onClick = {
                  navController.navigate(ROUTE_LIVE_BIDDING) { launchSingleTop = true }
                },
                icon = {
                  BadgedBox(
                    badge = {
                      if (committee.isLive) Badge()
                    }
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Gavel,
                      contentDescription = "लाइव बोली"
                    )
                  }
                },
                label = { Text("लाइव बोली") },
                modifier = Modifier.testTag("nav_live_bidding")
              )
              NavigationBarItem(
                selected = currentRoute == ROUTE_INSTALLMENTS,
                onClick = {
                  navController.navigate(ROUTE_INSTALLMENTS) {
                    popUpTo(ROUTE_HOME)
                    launchSingleTop = true
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (currentRoute == ROUTE_INSTALLMENTS) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet,
                    contentDescription = "किस्त व भुगतान"
                  )
                },
                label = { Text("किस्त व सदस्य") },
                modifier = Modifier.testTag("nav_installments")
              )
            }
          }
        }
      ) { innerPadding ->
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
          if (!isFullScreen && useRail && committee != null) {
            NavigationRail {
              NavigationRailItem(
                selected = currentRoute == ROUTE_HOME,
                onClick = {
                  navController.navigate(ROUTE_HOME) {
                    popUpTo(ROUTE_HOME) { inclusive = true }
                    launchSingleTop = true
                  }
                },
                icon = { Icon(Icons.Filled.Home, contentDescription = "होम") },
                label = { Text("होम") }
              )
              NavigationRailItem(
                selected = currentRoute == ROUTE_LIVE_BIDDING,
                onClick = {
                  navController.navigate(ROUTE_LIVE_BIDDING) { launchSingleTop = true }
                },
                icon = { Icon(Icons.Filled.Gavel, contentDescription = "लाइव बोली") },
                label = { Text("लाइव बोली") }
              )
              NavigationRailItem(
                selected = currentRoute == ROUTE_INSTALLMENTS,
                onClick = {
                  navController.navigate(ROUTE_INSTALLMENTS) {
                    popUpTo(ROUTE_HOME)
                    launchSingleTop = true
                  }
                },
                icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "किस्त व सदस्य") },
                label = { Text("किस्त व सदस्य") }
              )
            }
          }

          Box(modifier = Modifier.weight(1f)) {
            NavHost(
              navController = navController,
              startDestination = ROUTE_HOME
            ) {
              composable(ROUTE_HOME) {
                if (committee != null) {
                  HomeDashboardScreen(
                    committee = committee,
                    isAdmin = isAdmin,
                    countdownRemainingSeconds = countdownSeconds,
                    scheduleRemainingSeconds = scheduleSeconds,
                    onOpenLiveBidding = {
                      navController.navigate(ROUTE_LIVE_BIDDING) { launchSingleTop = true }
                    },
                    onStartLiveBidding = viewModel::startLiveBiddingNow,
                    onStartNextRound = viewModel::startNextRound,
                    onOpenSettings = {
                      navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                    }
                  )
                }
              }

              composable(ROUTE_INSTALLMENTS) {
                if (committee != null) {
                  MembersAndInstallmentsScreen(
                    committee = committee,
                    members = members,
                    rounds = rounds,
                    isAdmin = isAdmin,
                    currentUserId = currentUserId,
                    onAddMember = viewModel::addMember,
                    onToggleInstallment = viewModel::toggleInstallment,
                    onDeleteMember = viewModel::deleteMember
                  )
                }
              }

              composable(ROUTE_LIVE_BIDDING) {
                if (committee != null) {
                  LiveBiddingScreen(
                    committee = committee,
                    bids = bids,
                    currentUserId = currentUserId,
                    isAdmin = isAdmin,
                    countdownRemainingSeconds = countdownSeconds,
                    onStartLiveBidding = viewModel::startLiveBiddingNow,
                    onPlaceBid = viewModel::placeBid,
                    onSendChat = viewModel::sendChatMessage,
                    onEndAndDeclareWinner = { viewModel.endBiddingAndDeclareWinner() },
                    onBack = { navController.popBackStack() }
                  )
                }
              }

              composable(ROUTE_SETTINGS) {
                SettingsScreen(
                  themeMode = themeMode,
                  soundEnabled = soundEnabled,
                  committee = committee,
                  userProfile = userProfile,
                  isAdmin = isAdmin || committee == null,
                  onSelectTheme = viewModel::setThemeMode,
                  onToggleSound = viewModel::setSoundEnabled,
                  onTestSiren = viewModel::testChoonChoonSiren,
                  onSaveCommitteeSettings = viewModel::saveSettings,
                  onUpdateProfile = viewModel::updateMyProfile,
                  onSignOut = {
                    signOutUser(credentialManager, coroutineScope)
                  },
                  onBack = { navController.popBackStack() }
                )
              }
            }
          }
        }
      }
    }
  }

  val activeWinnerRecord = winnerModal
  if (activeWinnerRecord != null && committee != null) {
    WinnerCelebrationDialog(
      committeeName = committee.name,
      record = activeWinnerRecord,
      isAdmin = isAdmin,
      onStartNextRound = viewModel::startNextRound,
      onDismiss = viewModel::dismissWinnerModal
    )
  }
}
