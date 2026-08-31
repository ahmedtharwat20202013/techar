package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PinStorage
import com.example.ui.theme.PrimaryDarkGreen
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.LightBgGreen

@Composable
fun PinLockScreen(
    onAuthenticated: () -> Unit,
    onSetupPin: () -> Unit = {},
    onSkip: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val pinStorage = remember { PinStorage(context) }

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSetupMode by remember { mutableStateOf(!pinStorage.hasPin()) }
    var confirmPin by remember { mutableStateOf("") }

    val pinLength = 4

    fun onDigitClick(digit: String) {
        if (enteredPin.length < pinLength) {
            enteredPin += digit
            errorMessage = null
        }
    }

    fun onBackspaceClick() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    fun onSkipSetup() {
        pinStorage.setSetupSkipped(true)
        pinStorage.setPinEnabled(false)
        pinStorage.setAuthenticated(true)
        if (onSkip != null) {
            onSkip()
        } else {
            onAuthenticated()
        }
    }

    fun onPinComplete() {
        if (enteredPin.length != pinLength) return

        if (isSetupMode) {
            if (confirmPin.isEmpty()) {
                confirmPin = enteredPin
                enteredPin = ""
            } else {
                if (enteredPin == confirmPin) {
                    pinStorage.setPin(enteredPin)
                    pinStorage.setPinEnabled(true)
                    pinStorage.setAuthenticated(true)
                    onAuthenticated()
                } else {
                    errorMessage = "الرقمين غير متطابقين"
                    enteredPin = ""
                    confirmPin = ""
                }
            }
        } else {
            if (pinStorage.verifyPin(enteredPin)) {
                pinStorage.setAuthenticated(true)
                onAuthenticated()
            } else {
                errorMessage = "رقم خاطئ"
                enteredPin = ""
            }
        }
    }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == pinLength) {
            onPinComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBgGreen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Premium Styled Logo Icon Placeholder
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(PrimaryDarkGreen),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "م",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Techar",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryDarkGreen
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Title
        Text(
            text = when {
                isSetupMode && confirmPin.isEmpty() -> "أنشئ رقم PIN (4 أرقام)"
                isSetupMode -> "أكد رقم PIN"
                else -> "أدخل رقم PIN للدخول"
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryDarkGreen
        )

        Spacer(modifier = Modifier.height(24.dp))

        // PIN dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pinLength) { index ->
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < enteredPin.length)
                                PrimaryGreen
                            else
                                PrimaryDarkGreen.copy(alpha = 0.15f)
                        )
                )
            }
        }

        // Error message
        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = it,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Keypad
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "del")
            )

            keys.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { key ->
                        when (key) {
                            "" -> {
                                if (isSetupMode) {
                                    TextButton(
                                        onClick = { onSkipSetup() },
                                        modifier = Modifier.size(72.dp)
                                    ) {
                                        Text(
                                            text = "تخطي",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryDarkGreen.copy(alpha = 0.8f)
                                        )
                                    }
                                } else {
                                    Box(modifier = Modifier.size(72.dp))
                                }
                            }
                            "del" -> KeypadButton(
                                icon = Icons.Default.Backspace,
                                onClick = { onBackspaceClick() }
                            )
                            else -> KeypadButton(
                                text = key,
                                onClick = { onDigitClick(key) }
                            )
                        }
                    }
                }
            }
        }

        if (isSetupMode) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = { onSkipSetup() }
            ) {
                Text(
                    text = "تخطي إعداد كلمة المرور الآن",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryDarkGreen.copy(alpha = 0.75f)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun KeypadButton(
    text: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(PrimaryGreen.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        text?.let {
            Text(
                text = it,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryDarkGreen
            )
        }
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = "مسح",
                modifier = Modifier.size(28.dp),
                tint = PrimaryDarkGreen
            )
        }
    }
}
