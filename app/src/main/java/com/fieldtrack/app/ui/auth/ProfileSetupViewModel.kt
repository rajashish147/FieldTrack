package com.fieldtrack.app.ui.auth

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.BuildConfig
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.remote.api.StorageApi
import com.fieldtrack.app.data.remote.api.UserApi
import com.fieldtrack.app.data.remote.dto.UpdateProfileImageDto
import com.fieldtrack.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

class ProfileSetupViewModel(
    private val storageApi: StorageApi,
    private val userApi: UserApi,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _uploadSuccess = MutableLiveData(false)
    val uploadSuccess: LiveData<Boolean> = _uploadSuccess

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun uploadProfileImage(context: Context, uri: Uri) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val userId = sessionManager.userId
                if (userId.isNullOrBlank()) {
                    _errorMessage.value = "User not logged in"
                    _isLoading.value = false
                    return@launch
                }

                // Upload image to Supabase Storage
                val imageUrl = uploadImageToStorage(context, userId, uri)
                
                if (imageUrl != null) {
                    // Update user profile with image URL
                    val updateSuccess = updateUserProfileImage(userId, imageUrl)
                    if (updateSuccess) {
                        _uploadSuccess.value = true
                    } else {
                        _errorMessage.value = "Failed to update profile"
                    }
                } else {
                    _errorMessage.value = "Failed to upload image"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Upload failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun uploadImageToStorage(context: Context, userId: String, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val compressedFile = ImageUtils.compressImage(context, uri) ?: return@withContext null
                
                val fileName = "${userId}_${System.currentTimeMillis()}.jpg"
                val requestBody = compressedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", compressedFile.name, requestBody)

                val response = storageApi.uploadFile("profile-images", "$userId/$fileName", part)
                
                if (response.isSuccessful) {
                    "${BuildConfig.SUPABASE_URL}/storage/v1/object/profile-images/$userId/$fileName"
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun updateUserProfileImage(userId: String, imageUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = userApi.updateProfileImage(
                    id = "eq.$userId",
                    body = UpdateProfileImageDto(imageUrl)
                )
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
