package com.example.watchorderengine.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    private val _phoneVerificationId = MutableStateFlow<String?>(null)
    val phoneVerificationId: StateFlow<String?> = _phoneVerificationId.asStateFlow()

    fun signInWithCredential(credential: AuthCredential, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val result = auth.signInWithCredential(credential).await()
                val user = result.user
                if (user != null) {
                    // Update user info from social provider if empty
                    val currentName = userPrefs.username.first()
                    if (currentName == "Guest" || currentName == "Player One") {
                        user.displayName?.let { saveUsername(it) }
                        user.photoUrl?.let { userPrefs.updateAvatarUrl(it.toString()) }
                    } else {
                        syncUsernameFromCloud()
                    }
                    onResult(true, null)
                } else {
                    onResult(false, "User is null")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Authentication failed")
            }
        }
    }

    fun sendVerificationCode(
        phoneNumber: String,
        activity: android.app.Activity,
        onCodeSent: () -> Unit,
        onError: (String) -> Unit
    ) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithCredential(credential) { success, error ->
                        if (!success) onError(error ?: "Auto-verification failed")
                    }
                }

                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    onError(e.message ?: "Verification failed")
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    _phoneVerificationId.value = verificationId
                    onCodeSent()
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyPhoneCode(code: String, onResult: (Boolean, String?) -> Unit) {
        val verificationId = _phoneVerificationId.value
        if (verificationId != null) {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            signInWithCredential(credential, onResult)
        } else {
            onResult(false, "No verification ID found")
        }
    }

    fun saveUsername(name: String) {
        viewModelScope.launch {
            userPrefs.updateUsername(name)
            val uid = auth.currentUser?.uid ?: return@launch
            try {
                // Save to private metadata
                firestore.collection("users").document(uid)
                    .collection("profile").document("metadata")
                    .set(mapOf("username" to name), com.google.firebase.firestore.SetOptions.merge())
                
                // Save to public profile (Used by Community/Social)
                firestore.collection("user_profiles").document(uid)
                    .set(mapOf("username" to name, "userId" to uid), com.google.firebase.firestore.SetOptions.merge())
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Failed to save username: ${e.message}")
            }
        }
    }
    
    fun syncUsernameFromCloud() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            try {
                // Try public profile first
                val publicDoc = firestore.collection("user_profiles").document(uid).get().await()
                val cloudName = publicDoc.getString("username")
                
                if (!cloudName.isNullOrBlank()) {
                    userPrefs.updateUsername(cloudName)
                    publicDoc.getString("avatarUrl")?.let { userPrefs.updateAvatarUrl(it) }
                } else {
                    // Fallback to private metadata
                    val doc = firestore.collection("users").document(uid)
                        .collection("profile").document("metadata").get().await()
                    doc.getString("username")?.let { 
                        userPrefs.updateUsername(it)
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Failed to sync username: ${e.message}")
            }
        }
    }
}
