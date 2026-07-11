package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    fun saveUsername(name: String) {
        viewModelScope.launch {
            userPrefs.updateUsername(name)
            val uid = auth.currentUser?.uid ?: return@launch
            try {
                firestore.collection("users").document(uid)
                    .collection("profile").document("metadata")
                    .set(mapOf("username" to name), com.google.firebase.firestore.SetOptions.merge())
            } catch (e: Exception) {
                // ignore or log
            }
        }
    }
    
    fun syncUsernameFromCloud() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            try {
                val doc = firestore.collection("users").document(uid)
                    .collection("profile").document("metadata").get().await()
                doc.getString("username")?.let { 
                    userPrefs.updateUsername(it)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
