package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DeviceHiddenData
import com.example.data.service.LicenseService
import com.example.data.storage.ActivationStorage
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

        if (key.isEmpty()) {
            _statusMessage.value = "يرجى إدخال رمز الاشتراك"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = null
            
            try {
                // Automatically collect hidden device details for fingerprinting
                val hiddenData: DeviceHiddenData = DeviceUtils.collectHiddenData(context)

                Log.d(TAG, "Attempting subscription activation with key: $key")
                Log.d(TAG, "Device Fingerprint: ${hiddenData.fingerprint}")

                // Send request to Cloudflare Worker LicenseService
                val response = LicenseService.verifyLicense(
                    licenseKey = key,
                    deviceFingerprint = hiddenData.fingerprint,
                    androidId = hiddenData.androidId,
                    deviceModel = hiddenData.model,
                    manufacturer = hiddenData.manufacturer,
                    brand = hiddenData.brand,
                    androidVersion = hiddenData.androidVersion,
                    appVersion = hiddenData.appVersion
                )

                if (response.success) {
                    val expireDate = response.expireDate ?: "2099-12-31"
                    val customerName = if (!response.customerName.isNullOrBlank()) response.customerName else if (name.isNotBlank()) name else "مشترك"
                    
                    // Save encrypted license via LicenseManager & ActivationStorage
                    com.example.security.LicenseManager.saveLicense(
                        context = context,
                        customerName = customerName,
                        licenseKey = key,
                        expireDate = expireDate,
                        licenseId = response.licenseId ?: key,
                        startDate = response.startDate ?: "",
                        planType = response.planType ?: "PREMIUM"
                    )

                    val storage = ActivationStorage(context)
                    storage.setActivated(true, customerName, key, expireDate)
                    
                    _isSuccess.value = true
                    _statusMessage.value = response.message ?: "تم تفعيل الاشتراك بنجاح! جاري الدخول..."
                    
                    // Execute success callback to navigate to main app screen
                    onSuccess()
                } else {
                    val errMsg = response.message ?: "رمز الاشتراك غير صحيح"
                    _statusMessage.value = errMsg
                }
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "UnknownHostException", e)
                _statusMessage.value = "حدث خطأ في الاتصال بالإنترنت. يرجى التأكد من اتصالك بالإنترنت والمحاولة مجدداً."
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "ConnectException", e)
                _statusMessage.value = "حدث خطأ في الاتصال بالإنترنت. يرجى التأكد من اتصالك بالإنترنت والمحاولة مجدداً."
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "SocketTimeoutException", e)
                _statusMessage.value = "انتهت مهلة الاتصال بالخادم. يرجى التأكد من اتصالك بالإنترنت والمحاولة لاحقاً."
            } catch (e: java.io.IOException) {
                Log.e(TAG, "IOException", e)
                _statusMessage.value = "حدث خطأ في الاتصال بالإنترنت. يرجى التأكد من اتصالك بالإنترنت والمحاولة مجدداً."
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e(TAG, "HTTP Exception ${e.code()}: $errorBody", e)
                val serverMsg = if (!errorBody.isNullOrBlank()) {
                    try {
                        val json = org.json.JSONObject(errorBody)
                        json.optString("message", null) ?: json.optString("error", null)
                    } catch (ex: Exception) {
                        null
                    }
                } else null
                _statusMessage.value = serverMsg ?: "حدث خطأ في الاتصال بالخادم (${e.code()}). يرجى المحاولة لاحقاً."
            } catch (e: Exception) {
                Log.e(TAG, "Activation workflow error", e)
                _statusMessage.value = "حدث خطأ غير متوقع أثناء التفعيل: ${e.localizedMessage ?: "يرجى المحاولة لاحقاً."}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
