package com.example.ui.auth

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.firestore
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
                Log.w("Auth", "Failed to clear credential state", e)
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

private fun loginApprovedMemberWithPin(
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
            val db = Firebase.firestore
            val isMasterAdmin = (cleanMobile == "7021711472")

            // 1. पहले चेक करें कि क्या एडमिन ने यह नंबर ग्रुप में जोड़ा है
            val memberDoc = db.collection("approved_members").document(cleanMobile).get().await()

            if (!isMasterAdmin && !memberDoc.exists()) {
                onAuthError("यह मोबाइल नंबर ग्रुप में नहीं जुड़ा है। कृपया एडमिन से नंबर जुड़वाएं।")
                return@launch
            }

            // यदि एडमिन ने कोई खास PIN सेट किया है तो उसे जांचें
            val adminSetPin = memberDoc.getString("pin")
            if (!adminSetPin.isNullOrBlank() && adminSetPin != cleanPin) {
                onAuthError("गलत 4-अंकों का PIN! एडमिन द्वारा दिया गया सही PIN डालें।")
                return@launch
            }

            val memberName = if (isMasterAdmin && !memberDoc.exists()) {
                "विश्वनाथ यादव"
            } else {
                memberDoc.getString("name") ?: cleanMobile
            }

            // 2. Firebase Auth से साइन-इन या पहली बार अकाउंट एक्टिवेट करें
            try {
                Firebase.auth.signInWithEmailAndPassword(email, password).await()
                onAuthSuccess()
            } catch (signInEx: Exception) {
                try {
                    val result = Firebase.auth.createUserWithEmailAndPassword(email, password).await()
                    result.user?.updateProfile(
                        userProfileChangeRequest {
                            displayName = memberName
                        }
                    )?.await()
                    onAuthSuccess()
                } catch (createEx: Exception) {
                    val msg = createEx.message ?: ""
                    if (msg.contains("email address is already in use", ignoreCase = true)) {
                        onAuthError("गलत 4-डिजिट PIN! कृपया सही PIN दर्ज करें।")
                    } else {
                        onAuthError("लॉगिन विफल: कृपया इंटरनेट जांचें और पुनः प्रयास करें।")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Auth", "Login check failed", e)
            onAuthError("सर्वर से जुड़ने में समस्या आई। कृपया पुनः प्रयास करें।")
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

    // 3D Floating & Royal Glow Animation for Krishiv Group
    val infiniteTransition = rememberInfiniteTransition(label = "krishiv3d")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val goldBrush = Brush.linearGradient(
        colors = listOf(Color(0xFFFFD700), Color(0xFFFFA000), Color(0xFFFFECB3))
    )

    Scaffold(containerColor = Color(0xFF060B19)) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF060B19),
                            Color(0xFF0D1B3E),
                            Color(0xFF060B19)
                        )
                    )
                )
                .padding(innerPadding)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 3D Royal Emblem ("Krishiv Group")
                Box(
                    modifier = Modifier
                        .scale(pulseScale)
                        .size(108.dp)
                        .shadow(24.dp, CircleShape, ambientColor = Color(0xFFFFD700), spotColor = Color(0xFFFFD700))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF1E3A8A), Color(0xFF090F24))
                            ),
                            CircleShape
                        )
                        .border(2.5.dp, goldBrush, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🦚", fontSize = 22.sp)
                        Text(
                            text = "ॐ",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFD700)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Krishiv",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFD700),
                    letterSpacing = 2.sp
                )

                Text(
                    text = "WELCOME TO KRISHIV GROUP",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFE082),
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "वेलकम टू कृषिव ग्रुप • Hamari BC Committee",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 4.dp, bottom = 22.dp)
                )

                // Single Unified Member Login Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF111827).copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.35f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "सदस्य लॉगिन (Member Login)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "केवल ग्रुप एडमिन द्वारा जोड़े गए मेंबर्स के लिए",
                            fontSize = 11.sp,
                            color = Color(0xFF9CA3AF),
                            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                        )

                        OutlinedTextField(
                            value = mobileNumber,
                            onValueChange = { input ->
                                mobileNumber = input.filter { it.isDigit() }.take(10)
                                errorMessage = null
                            },
                            label = { Text("10 अंकों का मोबाइल नंबर", color = Color(0xFF9CA3AF)) },
                            leadingIcon = {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFFFFD700))
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFFD700),
                                unfocusedBorderColor = Color(0xFF374151)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = pin,
                            onValueChange = { input ->
                                pin = input.filter { it.isDigit() }.take(4)
                                errorMessage = null
                            },
                            label = { Text("4-अंकों का PIN", color = Color(0xFF9CA3AF)) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFFD700))
                            },
                            trailingIcon = {
                                IconButton(onClick = { pinVisible = !pinVisible }) {
                                    Icon(
                                        imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = Color(0xFF9CA3AF)
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFFD700),
                                unfocusedBorderColor = Color(0xFF374151)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFF87171),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                isLoading = true
                                errorMessage = null
                                loginApprovedMemberWithPin(
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
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD700),
                                contentColor = Color(0xFF0F172A)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color(0xFF0F172A),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "लॉगिन करें (Login)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
