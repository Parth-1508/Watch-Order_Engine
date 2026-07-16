package com.example.watchorderengine.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.FavoriteShowsCodec
import com.example.watchorderengine.data.model.MediaCategory
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.data.model.UserProfile
import com.example.watchorderengine.data.model.UserStats
import com.example.watchorderengine.data.model.WatchStatsCodec
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.data.repository.UserProfileRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

private const val MAX_FAVORITE_SHOWS = 5

sealed interface SaveProfileState {
    data object Idle : SaveProfileState
    data object Saving : SaveProfileState
    data object Saved : SaveProfileState
    data class Failed(val message: String) : SaveProfileState
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val mediaRepository: MediaRepository,
    private val userPrefs: UserPreferencesRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    val currentUserId: String? get() = auth.currentUser?.uid

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _avatarUrl = MutableStateFlow<String?>(null)
    val avatarUrl: StateFlow<String?> = _avatarUrl.asStateFlow()

    private val _isStatsPublic = MutableStateFlow(false)
    val isStatsPublic: StateFlow<Boolean> = _isStatsPublic.asStateFlow()

    private val _isFavoritesPublic = MutableStateFlow(false)
    val isFavoritesPublic: StateFlow<Boolean> = _isFavoritesPublic.asStateFlow()

    private val _favoriteShows = MutableStateFlow<List<MediaSummary>>(emptyList())
    val favoriteShows: StateFlow<List<MediaSummary>> = _favoriteShows.asStateFlow()

    private val _candidateShows = MutableStateFlow<List<MediaSummary>>(emptyList())
    val candidateShows: StateFlow<List<MediaSummary>> = _candidateShows.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveState = MutableStateFlow<SaveProfileState>(SaveProfileState.Idle)
    val saveState: StateFlow<SaveProfileState> = _saveState.asStateFlow()

    private val _pendingCropUri = MutableStateFlow<Uri?>(null)
    val pendingCropUri: StateFlow<Uri?> = _pendingCropUri.asStateFlow()

    private val _isUploadingAvatar = MutableStateFlow(false)
    val isUploadingAvatar: StateFlow<Boolean> = _isUploadingAvatar.asStateFlow()

    private var existingWatchStats: UserStats? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            val uid = currentUserId
            _displayName.value = userPrefs.username.first()
            _avatarUrl.value = userPrefs.avatarUrl.first()

            if (uid != null) {
                val profileDef = async { userProfileRepository.getProfile(uid) }
                val completedDef = async { mediaRepository.getListByState(TrackingState.COMPLETED) }
                val watchingDef = async { mediaRepository.getListByState(TrackingState.WATCHING) }

                val profile = profileDef.await().getOrNull()
                if (profile != null) {
                    _displayName.value = profile.displayName.ifBlank { _displayName.value }
                    _avatarUrl.value = profile.avatarUrl ?: _avatarUrl.value
                    _isStatsPublic.value = profile.isStatsPublic
                    _isFavoritesPublic.value = profile.isFavoritesPublic
                    _favoriteShows.value = profile.favoriteShows
                    existingWatchStats = profile.watchStats
                }

                _candidateShows.value = (completedDef.await() + watchingDef.await())
                    .distinctBy { it.id }
            }
            _isLoading.value = false
        }
    }

    fun updateDisplayName(name: String) {
        _displayName.value = name
    }

    fun setStatsPublic(public: Boolean) {
        _isStatsPublic.value = public
    }

    fun setFavoritesPublic(public: Boolean) {
        _isFavoritesPublic.value = public
    }

    fun toggleFavoriteShow(media: MediaSummary) {
        val current = _favoriteShows.value
        _favoriteShows.value = if (current.any { it.id == media.id }) {
            current.filterNot { it.id == media.id }
        } else {
            if (current.size >= MAX_FAVORITE_SHOWS) return
            current + media
        }
    }

    fun onImagePicked(uri: Uri) {
        _pendingCropUri.value = uri
    }

    fun dismissCrop() {
        _pendingCropUri.value = null
    }

    fun onAvatarCropped(bitmap: Bitmap, cacheDir: File) {
        _pendingCropUri.value = null
        viewModelScope.launch {
            _isUploadingAvatar.value = true
            try {
                val result = userProfileRepository.processAvatarToBase64(bitmap)
                result.onSuccess { dataUri ->
                    _avatarUrl.value = dataUri
                    // Store the data URI locally so it persists immediately
                    userPrefs.updateAvatarUrl(dataUri)
                }
            } finally {
                _isUploadingAvatar.value = false
            }
        }
    }

    fun saveProfile() {
        val uid = currentUserId ?: run {
            _saveState.value = SaveProfileState.Failed("You must be signed in to save your profile.")
            return
        }
        viewModelScope.launch {
            _saveState.value = SaveProfileState.Saving
            try {
                userPrefs.updateUsername(_displayName.value)

                val profile = UserProfile(
                    userId = uid,
                    displayName = _displayName.value,
                    avatarUrl = _avatarUrl.value,
                    isStatsPublic = _isStatsPublic.value,
                    isFavoritesPublic = _isFavoritesPublic.value,
                    favoriteShowsJson = FavoriteShowsCodec.encode(_favoriteShows.value),
                    watchStatsJson = computeWatchStatsJson(),
                )

                userProfileRepository.saveProfile(profile)
                    .onSuccess { _saveState.value = SaveProfileState.Saved }
                    .onFailure { e -> _saveState.value = SaveProfileState.Failed(e.message ?: "Failed to save profile.") }
            } catch (e: Exception) {
                _saveState.value = SaveProfileState.Failed(e.message ?: "Failed to save profile.")
            }
        }
    }

    private suspend fun computeWatchStatsJson(): String {
        return try {
            val completed = mediaRepository.getListByState(TrackingState.COMPLETED)
            val watching = mediaRepository.getListByState(TrackingState.WATCHING)
            val planned = mediaRepository.getListByState(TrackingState.PLANNED)
            val dropped = mediaRepository.getListByState(TrackingState.DROPPED)
            val paused = mediaRepository.getListByState(TrackingState.PAUSED)
            val totalWatched = mediaRepository.countWatchedEpisodes()
            val totalMinutes = mediaRepository.getTotalWatchedMinutes()
            val streak = mediaRepository.computeWatchStreak()
            val reviews = mediaRepository.countUserReviews()

            val genreCounts = (completed + watching + planned + paused + dropped)
                .flatMap { it.genres }
                .groupingBy { it }
                .eachCount()
            val topGenres = genreCounts.entries.sortedByDescending { it.value }.take(3).map { it.key }

            val totalMovies = completed.count { it.mediaCategory == MediaCategory.MOVIE }
            val score = computeScore(totalWatched, totalMovies, reviews, streak)

            val stats = UserStats(
                totalMinutesWatched = totalMinutes.toLong(),
                totalEpisodesWatched = totalWatched,
                totalMoviesWatched = totalMovies,
                showsCompleted = completed.size,
                showsDropped = dropped.size,
                showsWatching = watching.size,
                showsPlanned = planned.size,
                showsPaused = paused.size,
                topGenres = topGenres,
                averageRating = existingWatchStats?.averageRating,
                favoriteGenre = topGenres.firstOrNull(),
                streakDays = streak,
                profileScore = score,
                profileRank = getRankForScore(score)
            )
            WatchStatsCodec.encode(stats)
        } catch (e: Exception) {
            existingWatchStats?.let { WatchStatsCodec.encode(it) } ?: ""
        }
    }

    private fun computeScore(episodes: Int, movies: Int, reviews: Int, streak: Int): Int =
        (episodes * 1) + (movies * 10) + (reviews * 25) + (streak * 5)

    private fun getRankForScore(score: Int): String = when {
        score >= 10000 -> "Legend"
        score >= 5000  -> "Grandmaster"
        score >= 2000  -> "Master"
        score >= 1000  -> "Expert"
        score >= 500   -> "Cinephile"
        score >= 100   -> "Explorer"
        else           -> "Novice"
    }

    fun acknowledgeSaveResult() {
        _saveState.value = SaveProfileState.Idle
    }
}
