package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.PrefsManager
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigate: (isLoggedIn: Boolean) -> Unit
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    LaunchedEffect(Unit) {
        delay(1800)
        val prefs = PrefsManager.getInstance(context)
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val loggedIn = prefs.isLoggedIn || (firebaseUser != null)
        onNavigate(loggedIn)
    }

    val isDarkSplash = androidx.compose.material3.MaterialTheme.colorScheme.background == CommandNavyDark
    val splashText = if (isDarkSplash) Color.White else Color(0xFF111118)
    val splashAccent = if (isDarkSplash) CyanNeon else CommandLightPrimary
    val splashMuted = if (isDarkSplash) TextMuted else TextDarkMuted
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isDarkSplash) listOf(Color(0xFF14141C), CommandNavyDark, Color(0xFF101018))
                    else listOf(Color(0xFFFFFFFF), CommandLightBg, Color(0xFFEBE8F0))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Exact Logo as Provided
            Image(
                painter = painterResource(id = R.drawable.img_logo),
                contentDescription = "SafeGuard Logo",
                modifier = Modifier
                    .size(160.dp)
                    .scale(glowScale),
                contentScale = ContentScale.Fit
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "SafeGuard",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = splashText,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "SMART SAFETY TELEMETRY SYSTEM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = splashAccent,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "ESP32 • LoRa 915MHz • RTDB Link",
                    fontSize = 11.sp,
                    color = splashMuted
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = splashAccent,
                strokeWidth = 2.5.dp
            )

            Text(
                text = "Establishing Firebase LoRa Bridge...",
                fontSize = 11.sp,
                color = splashMuted.copy(alpha = 0.8f)
            )
        }

        Text(
            text = "Version 1.3 • Industrial Safety Division",
            fontSize = 10.sp,
            color = splashMuted.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}
