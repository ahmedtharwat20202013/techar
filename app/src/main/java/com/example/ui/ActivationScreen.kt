package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.ActivationViewModel

// Premium Forest Green and Luxurious Gold Colors
val GoldStart = Color(0xFFFFDF73)
val GoldMid = Color(0xFFD4AF37)
val GoldEnd = Color(0xFFAA7C11)
val PremiumForestGreen = Color(0xFF0F3010)
val SoftBgGreen = Color(0xFFF4F7F4)
val DarkGoldText = Color(0xFF8A6200)

val PremiumGoldGradient = Brush.linearGradient(
    colors = listOf(GoldStart, GoldMid, GoldEnd)
)

val PremiumGreenGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF1D4A1E), PremiumForestGreen)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(
    viewModel: ActivationViewModel,
    onActivationSuccess: () -> Unit
) {
    val context = LocalContext.current
    val customerName by viewModel.customerName.collectAsState()
    val licenseKey by viewModel.licenseKey.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isSuccess by viewModel.isSuccess.collectAsState()

    // Screen-level loading/entry animations
    val infiniteTransition = rememberInfiniteTransition(label = "gold_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    var cardVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        cardVisible = true
    }

    // Force RTL for Arabic localization
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFEBF2EB), Color(0xFFF5F7F5)))),
            contentAlignment = Alignment.Center
        ) {
            // Elegant background ambient rings
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xFF1D4A1E).copy(alpha = 0.03f),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.5f, 0f)
                )
                drawCircle(
                    color = GoldMid.copy(alpha = 0.04f),
                    radius = size.width * 0.5f,
                    center = Offset(size.width * 0.5f, size.height)
                )
            }

            AnimatedVisibility(
                visible = cardVisible,
                enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600, easing = EaseOutBack)),
                modifier = Modifier
                    .padding(20.dp)
                    .widthIn(max = 440.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(28.dp), clip = false)
                        .border(1.5.dp, PremiumGoldGradient, RoundedCornerShape(28.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Premium Locked Emblem Header
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(PremiumGreenGradient)
                                .padding(2.dp)
                                .border(1.dp, PremiumGoldGradient, RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "تفعيل الاشتراك",
                                tint = GoldMid,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Screen Titles
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "تفعيل الاشتراك",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = PremiumForestGreen,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "أدخل رمز الاشتراك الخاص بك لتفعيل التطبيق",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // License Key Field (رمز الاشتراك)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "رمز الاشتراك",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PremiumForestGreen
                            )
                            OutlinedTextField(
                                value = licenseKey,
                                onValueChange = { viewModel.onLicenseKeyChange(it) },
                                placeholder = { Text("مثال: XXXX-XXXX-XXXX-XXXX", fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.VpnKey,
                                        contentDescription = null,
                                        tint = PremiumForestGreen.copy(alpha = 0.6f)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PremiumForestGreen,
                                    unfocusedBorderColor = Color.LightGray,
                                    cursorColor = PremiumForestGreen
                                )
                            )
                        }

                        // Feedback Status Message
                        AnimatedVisibility(
                            visible = statusMessage != null,
                            enter = slideInVertically() + fadeIn(),
                            exit = slideOutVertically() + fadeOut()
                        ) {
                            statusMessage?.let { msg ->
                                val isSuccessMsg = isSuccess
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSuccessMsg) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSuccessMsg) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (isSuccessMsg) Color(0xFF2E7D32) else Color(0xFFC62828),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = msg,
                                        color = if (isSuccessMsg) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Submit Button (تفعيل)
                        Button(
                            onClick = {
                                viewModel.activate(context, onActivationSuccess)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .shadow(
                                    elevation = if (isLoading) 2.dp else 6.dp,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isLoading
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(PremiumGreenGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            color = GoldMid,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "جاري الاتصال والتحقق...",
                                            color = GoldMid,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            text = "تفعيل",
                                            color = GoldStart,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Premium Brand Signature
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "🔒 حماية مشفرة مدمجة مع نظام البصمة الرقمية",
                                fontSize = 11.sp,
                                color = Color.Gray.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}
