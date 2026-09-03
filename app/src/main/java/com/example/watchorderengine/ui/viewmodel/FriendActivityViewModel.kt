package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.repository.FriendActivityItem
import com.example.watchorderengine.data.repository.FriendActivityRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendActivityViewModel @Inject constructor(
    private val repository: FriendActivityRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _feed = MutableStateFlow<List<FriendActivityItem>>(emptyList())
    val feed: StateFlow<List<FriendActivityItem>> = _feed.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isEmpty = MutableStateFlow(false)
    val isEmpty: StateFlow<Boolean> = _isEmpty.asStateFlow()

    init { refresh() }

    fun refresh() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val following = repository.observeFollowingOnce(uid)
            if (following.isEmpty()) {
                _feed.value = emptyList()
                _isEmpty.value = true
                _isLoading.value = false
                return@launch
            }
            _feed.value = repository.getFriendActivity(following.map { it.followedUserId })
            _isEmpty.value = _feed.value.isEmpty()
            _isLoading.value = false
        }
    }
}
