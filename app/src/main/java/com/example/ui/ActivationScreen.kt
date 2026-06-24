package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.LicenseManager
import com.example.utils.ActivationDetails
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(
    onActivationSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var licenseKey by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F11)) // Rich Dark Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Online Activation Warning Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE65100))
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Warning",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "يتطلب التفعيل الاتصال بالإنترنت في المرة الأولى للتحقق من الرخصة والعتاد.",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Beautiful Identity Logo
            Icon(
                imageVector = Icons.Default.VpnKey,
                contentDescription = "Activation logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .padding(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "مساعد المعلم الذكي",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "يرجى إدخال مفتاح التفعيل لبدء استخدام التطبيق",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp, start = 24.dp, end = 24.dp)
            )

            // Modern Input Form Container
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF18181C)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "رمز الترخيص (License Key)",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        textAlign = TextAlign.Right
                    )

                    // Text Field with unique testTag
                    OutlinedTextField(
                        value = licenseKey,
                        onValueChange = { licenseKey = it },
                        label = { Text("مثال: XXXX-XXXX-XXXX-XXXX", color = Color.DarkGray) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "License Icon",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF2C2C35),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF121214),
                            unfocusedContainerColor = Color(0xFF121214)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                            .testTag("license_input")
                    )

                    // Feedback messages
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        errorMessage?.let { msg ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                Text(
                                    text = msg,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = successMessage != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        successMessage?.let { msg ->
                            Surface(
                                color = Color(0xFF1B5E20),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                Text(
                                    text = msg,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Activation Button with unique testTag
                    Button(
                        onClick = {
                            val keyToValidate = licenseKey.trim()
                            if (keyToValidate.isEmpty()) {
                                errorMessage = "يرجى إدخال مفتاح الترخيص أولاً للمتابعة"
                                return@Button
                            }
                            errorMessage = null
                            isLoading = true
                            
                            scope.launch {
                                val result = LicenseManager.activateLicenseOnline(context, keyToValidate)
                                isLoading = false
                                if (result.isSuccess) {
                                    val details = result.getOrNull()
                                    successMessage = if (details?.userName != null) {
                                        "مرحباً بك ${details.userName}! تم التفعيل بنجاح."
                                    } else {
                                        "تم تفعيل البرنامج بنجاح! جاري تحويلك للرئيسية..."
                                    }
                                    delay(1800)
                                    onActivationSuccess()
                                } else {
                                    errorMessage = result.exceptionOrNull()?.message ?: "رمز التفعيل غير صالح."
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("activate_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "تفعيل التطبيق",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Aesthetic security assurance footer
            Text(
                text = "حماية أمنية مشفرة بـ AES-256 وربط عتاد الأجهزة",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}
