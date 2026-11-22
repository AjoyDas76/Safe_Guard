package com.example.ui.screens

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.example.MainActivity
import com.example.R
import com.example.data.PrefsManager
import com.example.ui.components.*
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { PrefsManager.getInstance(context) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current

    val emailAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.EmailAddress, AutofillType.Username),
            onFill = {
                email = it
                errorMessage = null
            }
        )
    }

    val passwordAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = {
                password = it
                errorMessage = null
            }
        )
    }

    fun requestGoogleAutofill() {
        val activity = context as? Activity ?: return
        coroutineScope.launch {
            try {
                val credentialManager = CredentialManager.create(activity)
                val getPasswordOption = GetPasswordOption()
                val getCredRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(getPasswordOption)
                    .build()
                val response = credentialManager.getCredential(activity, getCredRequest)
                val cred = response.credential
                if (cred is PasswordCredential) {
                    email = cred.id
                    password = cred.password
                    errorMessage = null
                    Toast.makeText(context, "Filled saved credentials from Google", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Throwable) {
                Log.d("LoginScreen", "Google autofill request dismissed or no credentials: ${e.message}")
            }
        }
    }

    // Automatically prompt to fill saved credentials from Google if available
    LaunchedEffect(Unit) {
        if (email.isBlank() && password.isBlank()) {
            val activity = context as? Activity
            if (activity != null) {
                try {
                    val credentialManager = CredentialManager.create(activity)
                    val getPasswordOption = GetPasswordOption()
                    val getCredRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(getPasswordOption)
                        .build()
                    val response = credentialManager.getCredential(activity, getCredRequest)
                    val cred = response.credential
                    if (cred is PasswordCredential) {
                        email = cred.id
                        password = cred.password
                        errorMessage = null
                    }
                } catch (_: Throwable) {
                    // Ignored if user has no saved passwords or dismissed prompt
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            (context as? MainActivity)?.dismissActiveActionMode()
        }
    }

    fun doLogin(isDemo: Boolean = false) {
        keyboardController?.hide()
        (context as? MainActivity)?.dismissActiveActionMode()
        errorMessage = null

        if (isDemo) {
            prefs.isLoggedIn = true
            prefs.userEmail = "demo.operator@safeguard.com"
            Toast.makeText(context, "Logged in as Industrial Safety Monitor", Toast.LENGTH_SHORT).show()
            onLoginSuccess()
            return
        }

        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Please provide both email and password."
            return
        }

        val userEmail = email.trim()
        val userPass = password

        isLoading = true
        try {
            FirebaseAuth.getInstance().signInWithEmailAndPassword(userEmail, userPass)
                .addOnSuccessListener {
                    prefs.isLoggedIn = true
                    prefs.userEmail = userEmail
                    Toast.makeText(context, "Authentication successful!", Toast.LENGTH_SHORT).show()

                    // 1. Commit Android OS Autofill session (Google Autofill Framework)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            val autofillManager = context.getSystemService(AutofillManager::class.java)
                            autofillManager?.commit()
                        } catch (e: Throwable) {
                            Log.e("LoginScreen", "Autofill commit error: ${e.message}")
                        }
                    }

                    // 2. Launch Google Credential Manager "Save password to Google" dialog bottom sheet
                    coroutineScope.launch {
                        val activity = context as? Activity
                        if (activity != null) {
                            try {
                                val credentialManager = CredentialManager.create(activity)
                                val saveRequest = CreatePasswordRequest(
                                    id = userEmail,
                                    password = userPass
                                )
                                kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                    credentialManager.createCredential(activity, saveRequest)
                                }
                            } catch (e: Throwable) {
                                Log.d("LoginScreen", "Save password dialog completed/dismissed: ${e.message}")
                            }
                        }
                        isLoading = false
                        focusManager.clearFocus(force = true)
                        onLoginSuccess()
                    }
                }
                .addOnFailureListener { exc ->
                    isLoading = false
                    val msg = exc.localizedMessage ?: "Authentication failed. Check credentials or network."
                    errorMessage = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            isLoading = false
            errorMessage = e.message ?: "Firebase Auth error"
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    (context as? MainActivity)?.dismissActiveActionMode()
                })
            }
    ) {
        // 1. High-Tech Industrial Safety Vest & Hardhat Background
        Image(
            painter = painterResource(id = R.drawable.img_login_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Dark gradient vignette overlay for optimal text contrast and sci-fi atmosphere
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x99050A14),
                            Color(0x6608101E),
                            Color(0xB304070D)
                        )
                    )
                )
        )

        // 2. Futuristic Corner HUD Decors
        // Top-Left Circuit Lines
        CircuitLinesHud(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 16.dp)
                .size(100.dp, 40.dp)
                .align(Alignment.TopStart)
        )

        // Top-Right Dot Matrix
        DotMatrixHud(
            modifier = Modifier
                .statusBarsPadding()
                .padding(end = 20.dp, top = 20.dp)
                .size(54.dp, 48.dp)
                .align(Alignment.TopEnd)
        )

        // Bottom-Left Circular Radar Reticle
        RadarReticleHud(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 20.dp)
                .size(68.dp)
                .align(Alignment.BottomStart)
        )

        // Bottom-Right Tech Telemetry Bar
        TelemetryLinesHud(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 24.dp)
                .size(110.dp, 30.dp)
                .align(Alignment.BottomEnd)
        )

        // 3. Left-Side Vertical HUD Category Badges
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HudCategoryBadge(icon = Icons.Default.Shield, label = "SAFETY")
            HudCategoryBadge(icon = Icons.Default.Favorite, label = "HEALTH")
            HudCategoryBadge(icon = Icons.Default.LocationOn, label = "TRACKING")
            HudCategoryBadge(icon = Icons.Default.Notifications, label = "ALERTS")
            HudCategoryBadge(icon = Icons.Default.Sensors, label = "CONNECTIVITY")
        }

        // 4. Center Glassmorphic Login Card
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 68.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.16f),
                                Color.White.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.12f)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.25f),
                                Color(0xFF6FA8FF).copy(alpha = 0.45f)
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 22.dp, vertical = 28.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Exact User Logo as Provided
                    Image(
                        painter = painterResource(id = R.drawable.img_logo),
                        contentDescription = "SafeGuard Logo",
                        modifier = Modifier.size(96.dp),
                        contentScale = ContentScale.Fit
                    )

                    // Titles
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SafeGuard",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "REAL-TIME SAFETY MONITORING",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                            letterSpacing = 1.2.sp
                        )
                    }

                    if (errorMessage != null) {
                        Surface(
                            color = Color(0x33FF1744),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x88FF1744))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = errorMessage!!,
                                    fontSize = 11.5.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Email Field - Capsule Pill
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        placeholder = {
                            Text(
                                text = "Email",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Email",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (email.isBlank()) {
                                IconButton(onClick = { requestGoogleAutofill() }) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = "Auto-fill with Google",
                                        tint = Color(0xFF6FA8FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                IconButton(onClick = { email = ""; errorMessage = null }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear email",
                                        tint = Color.White.copy(alpha = 0.65f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.White.copy(alpha = 0.20f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.14f),
                            focusedBorderColor = Color(0xFF6FA8FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                            cursorColor = Color(0xFF6FA8FF)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .onGloballyPositioned { coordinates ->
                                autofillTree += emailAutofillNode
                                emailAutofillNode.boundingBox = coordinates.boundsInWindow()
                            }
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    autofill?.requestAutofillForNode(emailAutofillNode)
                                } else {
                                    autofill?.cancelAutofillForNode(emailAutofillNode)
                                }
                            },
                        shape = RoundedCornerShape(50)
                    )

                    // Password Field - Capsule Pill
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        placeholder = {
                            Text(
                                text = "Password",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Password Visibility",
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { doLogin(false) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.White.copy(alpha = 0.20f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.14f),
                            focusedBorderColor = Color(0xFF6FA8FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                            cursorColor = Color(0xFF6FA8FF)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .onGloballyPositioned { coordinates ->
                                autofillTree += passwordAutofillNode
                                passwordAutofillNode.boundingBox = coordinates.boundsInWindow()
                            }
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    autofill?.requestAutofillForNode(passwordAutofillNode)
                                } else {
                                    autofill?.cancelAutofillForNode(passwordAutofillNode)
                                }
                            },
                        shape = RoundedCornerShape(50)
                    )

                    // Quick Google Autofill helper row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6FA8FF).copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { requestGoogleAutofill() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = Color(0xFF6FA8FF),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Auto-fill with Google",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }

                        if (email.isNotBlank() || password.isNotBlank()) {
                            Text(
                                text = "Clear",
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .clickable {
                                        email = ""
                                        password = ""
                                        errorMessage = null
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Vibrant Cyan/Electric Blue Login Button
                    Button(
                        onClick = { doLogin(false) },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2F6FD6),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = "LOGIN",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = Color.White,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }
        }
    }
}


