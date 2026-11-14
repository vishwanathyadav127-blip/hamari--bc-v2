package com.example.ui.auth

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope
) {
  if (Firebase.auth.currentUser != null) {
    onAuthSuccess()
  } else {
    onUnauthenticated()
  }
}

fun signOutUser(
  credentialManager: CredentialManager,
  scope: CoroutineScope,
  onSignOutComplete: () -> Unit = {}
) {
  scope.launch {
    withContext(NonCancellable) {
      try {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        Log.w("Auth", "Failed to clear credential state: ${e.message}")
      } finally {
        Firebase.auth.signOut()
        onSignOutComplete()
      }
    }
  }
}

private fun loginOrRegisterWithMobilePin(
  mobileNumber: String,
  pin: String,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope
) {
  val cleanMobile = mobileNumber.filter { it.isDigit() }
  val cleanPin = pin.filter { it.isDigit() }

  if (cleanMobile.length != 10) {
    onAuthError("कृपया 10 अंकों का सही मोबाइल नंबर दर्ज करें।")
    return
  }
  if (cleanPin.length != 4) {
    onAuthError("कृपया 4 अंकों का सुरक्षा PIN दर्ज करें।")
    return
  }

  val email = "91$cleanMobile@hamaribc.app"
  val password = "bcpin_$cleanPin"

  scope.launch {
    try {
      // पहले मौजूदा अकाउंट से लॉगिन करने की कोशिश करें
      Firebase.auth.signInWithEmailAndPassword(email, password).await()
      onAuthSuccess()
    } catch (signInException: Exception) {
      // यदि यूज़र नया है, तो नया अकाउंट बनाएं
      if (signInException is FirebaseAuthInvalidUserException ||
        signInException is FirebaseAuthInvalidCredentialsException
      ) {
        try {
          val result = Firebase.auth.createUserWithEmailAndPassword(email, password).await()
          result.user?.updateProfile(
            userProfileChangeRequest {
              displayName = cleanMobile
            }
          )?.await()
          onAuthSuccess()
        } catch (createException: Exception) {
          val msg = createException.message ?: ""
          if (msg.contains("email address is already in use", ignoreCase = true)) {
            onAuthError("गलत 4-डिजिट PIN! कृपया सही PIN दर्ज करें।")
          } else {
            Log.e("Auth", "Account creation failed", createException)
            onAuthError("लॉगिन विफल: ${createException.localizedMessage ?: "कृपया पुनः प्रयास करें।"}")
          }
        }
      } else {
        Log.e("Auth", "Sign-In failed", signInException)
        onAuthError("लॉगिन करने में समस्या आई। इंटरनेट कनेक्शन जांचें।")
      }
    }
  }
}

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var mobileNumber by remember { mutableStateOf("") }
  var pin by remember { mutableStateOf("") }
  var pinVisible by remember { mutableStateOf(false) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = coroutineScope
    )
  }

  Scaffold { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(innerPadding)
        .padding(20.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 520.dp)
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(24.dp),
          tonalElevation = 4.dp,
          shadowElevation = 6.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
              painter = painterResource(id = R.drawable.img_winner_banner),
              contentDescription = "हमारी BC कमेटी बैनर",
              modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
              contentScale = ContentScale.Crop
            )

            Column(
              modifier = Modifier.padding(18.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = "हमारी BC कमेटी",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
              )
              Text(
                text = "मोबाइल नंबर और 4-डिजिट PIN से तुरंत लॉगिन या नया खाता शुरू करें।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          }
        }

        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Text(
              text = "सदस्य लॉगिन / पंजीकरण",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
              value = mobileNumber,
              onValueChange = { input ->
                val digits = input.filter { it.isDigit() }
                if (digits.length <= 10) {
                  mobileNumber = digits
                  errorMessage = null
                }
              },
              label = { Text("मोबाइल नंबर (10 अंक)") },
              placeholder = { Text("उदा. 9876543210") },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.PhoneAndroid,
                  contentDescription = null
                )
              },
              prefix = { Text("+91 ") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
              value = pin,
              onValueChange = { input ->
                val digits = input.filter { it.isDigit() }
                if (digits.length <= 4) {
                  pin = digits
                  errorMessage = null
                }
              },
              label = { Text("4-डिजिट सुरक्षा PIN") },
              placeholder = { Text("****") },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.Lock,
                  contentDescription = null
                )
              },
              trailingIcon = {
                IconButton(onClick = { pinVisible = !pinVisible }) {
                  Icon(
                    imageVector = if (pinVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (pinVisible) "PIN छिपाएं" else "PIN दिखाएं"
                  )
                }
              },
              visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth()
            )

            Text(
              text = "• नए सदस्य पहली बार अपना नंबर और मनपसंद 4-अंकों का PIN डालकर सीधे जुड़ सकते हैं।",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (errorMessage != null) {
              Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = errorMessage!!,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.padding(12.dp),
                  textAlign = TextAlign.Center
                )
              }
            }

            Button(
              onClick = {
                errorMessage = null
                isLoading = true
                loginOrRegisterWithMobilePin(
                  mobileNumber = mobileNumber,
                  pin = pin,
                  onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                  },
                  onAuthError = { msg ->
                    isLoading = false
                    errorMessage = msg
                  },
                  scope = coroutineScope
                )
              },
              enabled = !isLoading,
              shape = RoundedCornerShape(16.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("mobile_pin_sign_in_button")
            ) {
              if (isLoading) {
                CircularProgressIndicator(
                  modifier = Modifier.size(24.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp
                )
              } else {
                Text(
                  text = "आगे बढ़ें / लॉगिन करें",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }

        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            AuthHighlightRow(
              icon = Icons.Filled.Gavel,
              title = "व्हाट्सएप जैसा लाइव बोली कक्ष",
              subtitle = "नाम, मोबाइल नंबर और रियल-टाइम बोली"
            )
            AuthHighlightRow(
              icon = Icons.Filled.NotificationsActive,
              title = "चूँ-चूँ सायरन व स्वतः समय अलर्ट",
              subtitle = "कस्टम टाइमर और तय समय पर ऑटो-स्टार्ट"
            )
            AuthHighlightRow(
              icon = Icons.Filled.AccountBalanceWallet,
              title = "संपूर्ण BC हिसाब व किस्त गणना",
              subtitle = "विजेता राशि और प्रति सदस्य किस्त स्वतः अपडेट"
            )
            AuthHighlightRow(
              icon = Icons.Filled.Groups,
              title = "1-क्लिक WhatsApp शेयर व APK लिंक",
              subtitle = "पूरा हिसाब और आमंत्रण तुरंत भेजें"
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))
      }
    }
  }
}

@Composable
private fun AuthHighlightRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.primaryContainer,
      modifier = Modifier.size(38.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(20.dp)
        )
      }
    }
    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
