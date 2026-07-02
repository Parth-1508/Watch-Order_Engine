package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TasteProfileViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    fun saveSelectedGenres(genres: Set<String>) {
        viewModelScope.launch {
            prefsRepository.setSelectedGenres(genres)
            prefsRepository.setTasteProfileCompleted(true)
            
            // Sync to cloud immediately so recommendations work on other devices too
            mediaRepository.syncProfileToCloud(
                isTasteDone = true,
                lastActive = System.currentTimeMillis(),
                streak = 1, // Assume first day of taste profile is day 1
                genres = genres
            )
        }
    }
}
