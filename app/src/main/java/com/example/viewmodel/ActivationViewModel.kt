package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DeviceHiddenData
import com.example.data.service.PostgresDatabaseService
import com.example.data.storage.ActivationStorage
import com.example.data.validation.ActivationValidator
import com.example.data.validation.ValidationResult
import com.example.utils.DeviceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ActivationViewModel : ViewModel() {
    private val TAG = "ActivationViewModel"

    private val _customerName = MutableStateFlow("")
    val customerName: StateFlow<String> = _customerName.asStateFlow()

    private val _licenseKey = MutableStateFlow("")
    val licenseKey: StateFlow<String> = _licenseKey.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isSuccess = MutableStateFlow(false)
    val isSuccess: StateFlow<Boolean> = _isSuccess.asStateFlow()

    fun onCustomerNameChange(name: String) {
        _customerName.value = name
    }

    fun onLicenseKeyChange(key: String) {
        _licenseKey.value = key
    }

    fun activate(context: Context, onSuccess: () -> Unit) {
        val name = _customerName.value.trim()
        val key = _licenseKey.value.trim()

        if (name.isEmpty() || key.isEmpty()) {
            _statusMessage.value = "يرجى إدخال اسم المستخدم ورمز الاشتراك"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = null
            
            try {
                // Automatically collect hidden device details for fingerprinting
                val hiddenData: DeviceHiddenData = DeviceUtils.collectHiddenData(context)
                val currentFingerprint = hiddenData.fingerprint

                Log.d(TAG, "Attempting subscription activation for: $name with key: $key")
                Log.d(TAG, "Device Fingerprint: $currentFingerprint")

                // 1. Fetch license from Supabase (Never direct connection to PostgreSQL)
                val license = PostgresDatabaseService.getLicense(name, key)

                if (license == null) {
                    // Record attempt
                    PostgresDatabaseService.saveActivationAttempt(
                        licenseId = null,
                        hiddenData = hiddenData,
                        result = "بيانات الاشتراك غير صحيحة"
                    )
                    _statusMessage.value = "بيانات الاشتراك غير صحيحة"
                    _isLoading.value = false
                    return@launch
                }

                // 2. Validate license parameters (status, expire_date, fingerprint)
                val validationResult = ActivationValidator.validateLicense(license, currentFingerprint)

                when (validationResult) {
                    is ValidationResult.Blocked -> {
                        PostgresDatabaseService.saveActivationAttempt(
                            licenseId = license.id,
                            hiddenData = hiddenData,
                            result = "تم إيقاف الاشتراك"
                        )
                        _statusMessage.value = "تم إيقاف الاشتراك"
                    }
                    is ValidationResult.Expired -> {
                        PostgresDatabaseService.saveActivationAttempt(
                            licenseId = license.id,
                            hiddenData = hiddenData,
                            result = "انتهى الاشتراك"
                        )
                        _statusMessage.value = "انتهى الاشتراك"
                    }
                    is ValidationResult.InvalidFingerprint -> {
                        PostgresDatabaseService.saveActivationAttempt(
                            licenseId = license.id,
                            hiddenData = hiddenData,
                            result = "هذا الاشتراك مفعل على جهاز آخر"
                        )
                        _statusMessage.value = "هذا الاشتراك مفعل على جهاز آخر"
                    }
                    is ValidationResult.Success -> {
                        // Always update device details in the database upon successful activation/entry
                        Log.d(TAG, "Binding and updating device attributes: $currentFingerprint")
                        val isUpdated = PostgresDatabaseService.updateDeviceFingerprint(name, key, hiddenData)
                        if (!isUpdated) {
                            PostgresDatabaseService.saveActivationAttempt(
                                licenseId = license.id,
                                hiddenData = hiddenData,
                                result = "فشل في ربط الاشتراك بالجهاز"
                            )
                            _statusMessage.value = "فشل في ربط الاشتراك بجهازك. يرجى المحاولة لاحقاً."
                            _isLoading.value = false
                            return@launch
                        }

                        // 4. Record successful activation log
                        PostgresDatabaseService.saveActivationAttempt(
                            licenseId = license.id,
                            hiddenData = hiddenData,
                            result = "تفعيل ناجح"
                        )

                        // 5. Save activation locally to skip activation screen on subsequent app launches
                        val storage = ActivationStorage(context)
                        storage.setActivated(true, name, key, license.expireDate)
                        
                        _isSuccess.value = true
                        _statusMessage.value = "تم تفعيل الاشتراك بنجاح! جاري الدخول..."
                        
                        // Execute success callback to navigate to main app screen
                        onSuccess()
                    }
                    else -> {
                        PostgresDatabaseService.saveActivationAttempt(
                            licenseId = license.id,
                            hiddenData = hiddenData,
                            result = "بيانات الاشتراك غير صحيحة"
                        )
                        _statusMessage.value = "بيانات الاشتراك غير صحيحة"
                    }
                }
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e(TAG, "Supabase API returned HTTP ${e.code()}: $errorBody", e)
                val userFriendlyMessage = when (e.code()) {
                    401, 403 -> "فشل الترخيص (HTTP ${e.code()}): يرجى التحقق من صحة مفتاح Supabase Anon Key وصلاحيات الجداول (RLS)."
                    404 -> "غير موجود (HTTP ${e.code()}): لم يتم العثور على مسار الجدول المطلوب في Supabase (تأكد من إنشاء جدول licenses)."
                    else -> "خطأ في الاتصال بالخادم (${e.code()}): $errorBody"
                }
                _statusMessage.value = userFriendlyMessage
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "Connection failure connecting to Supabase gateway", e)
                _statusMessage.value = "فشل الاتصال بخادم التفعيل. يرجى التحقق من اتصال الإنترنت."
            } catch (e: Exception) {
                Log.e(TAG, "Activation workflow crash", e)
                _statusMessage.value = "حدث خطأ غير متوقع أثناء الاتصال بقاعدة البيانات: ${e.localizedMessage ?: "تأكد من صحة الرابط"}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
