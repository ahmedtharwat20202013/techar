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
                // Collect hidden device details automatically
                val hiddenData: DeviceHiddenData = DeviceUtils.collectHiddenData(context)
                val currentFingerprint = hiddenData.fingerprint

                Log.d(TAG, "Attempting activation for: $name with key: $key")
                Log.d(TAG, "Device Fingerprint: $currentFingerprint")

                // 1. Connect to Neon PostgreSQL & Query licenses table
                val license = PostgresDatabaseService.getLicense(name, key)

                if (license == null) {
                    // License not found
                    _statusMessage.value = "بيانات الاشتراك غير صحيحة"
                    _isLoading.value = false
                    return@launch
                }

                // 2. Validate license parameters (status, expire_date, fingerprint)
                val validationResult = ActivationValidator.validateLicense(license, currentFingerprint)

                when (validationResult) {
                    is ValidationResult.Blocked -> {
                        _statusMessage.value = "تم إيقاف الاشتراك"
                    }
                    is ValidationResult.Expired -> {
                        _statusMessage.value = "انتهى الاشتراك"
                    }
                    is ValidationResult.InvalidFingerprint -> {
                        _statusMessage.value = "هذا الاشتراك مفعل على جهاز آخر"
                    }
                    is ValidationResult.Success -> {
                        // If device fingerprint is not set yet (NULL/empty in DB), bind it now
                        if (license.deviceFingerprint.isNullOrBlank()) {
                            Log.d(TAG, "Fingerprint is NULL, binding device to: $currentFingerprint")
                            val isUpdated = PostgresDatabaseService.updateDeviceFingerprint(name, key, currentFingerprint)
                            if (!isUpdated) {
                                _statusMessage.value = "فشل في ربط الاشتراك بجهازك. يرجى المحاولة لاحقاً."
                                _isLoading.value = false
                                return@launch
                            }
                        }

                        // Save local activation details securely
                        val storage = ActivationStorage(context)
                        storage.setActivated(true, name, key)
                        
                        _isSuccess.value = true
                        _statusMessage.value = "تم تفعيل الاشتراك بنجاح! جاري الدخول..."
                        
                        // Execute success callback
                        onSuccess()
                    }
                    else -> {
                        _statusMessage.value = "بيانات الاشتراك غير صحيحة"
                    }
                }
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "Connection failure", e)
                _statusMessage.value = "فشل الاتصال بخادم التفعيل. يرجى التحقق من اتصال الإنترنت والتحكم."
            } catch (e: Exception) {
                Log.e(TAG, "Activation error", e)
                _statusMessage.value = "حدث خطأ غير متوقع أثناء الاتصال بقاعدة البيانات. يرجى التأكد من صحة إعدادات الشبكة."
            } finally {
                _isLoading.value = false
            }
        }
    }
}
