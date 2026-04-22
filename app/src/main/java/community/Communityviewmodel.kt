package com.deepgamer.agriexpert.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class FeedState {
    object Loading : FeedState()
    data class Success(val posts: List<CommunityPost>) : FeedState()
    data class Error(val message: String) : FeedState()
}

data class AuthState(
    val isSignedIn: Boolean = false,
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String = ""
)

class CommunityViewModel(
    private val repository: CommunityRepository
) : ViewModel() {

    // ── ALL properties declared first ─────────────────────────────────

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _trendingState = MutableStateFlow<FeedState>(FeedState.Loading)
    val trendingState: StateFlow<FeedState> = _trendingState.asStateFlow()

    private val _recentState = MutableStateFlow<FeedState>(FeedState.Loading)
    val recentState: StateFlow<FeedState> = _recentState.asStateFlow()

    private val _myPostsState = MutableStateFlow<FeedState>(FeedState.Loading)
    val myPostsState: StateFlow<FeedState> = _myPostsState.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    // ── init AFTER all properties — never before ──────────────────────
    init {
        repository.currentUser()?.let { user -> applyUser(user) }
        loadTrending()
        loadRecent()
    }

    // ── Auth ──────────────────────────────────────────────────────────

    fun onSignInSuccess() {
        val user = repository.currentUser() ?: return
        applyUser(user)
        loadMyPosts()
    }

    fun signOut() {
        repository.signOut()
        _authState.value = AuthState()
        _myPostsState.value = FeedState.Success(emptyList())
    }

    private fun applyUser(user: FirebaseUser) {
        _authState.value = AuthState(
            isSignedIn = true,
            uid = user.uid,
            displayName = user.displayName ?: "Anonymous",
            photoUrl = user.photoUrl?.toString() ?: ""
        )
    }

    // ── Feed ──────────────────────────────────────────────────────────

    fun loadTrending() {
        viewModelScope.launch {
            _trendingState.value = FeedState.Loading
            try {
                repository.getTrendingPosts().collect { posts ->
                    _trendingState.value = FeedState.Success(posts)
                }
            } catch (e: Exception) {
                _trendingState.value = FeedState.Error(e.message ?: "Failed to load")
            }
        }
    }

    fun loadRecent() {
        viewModelScope.launch {
            _recentState.value = FeedState.Loading
            try {
                repository.getRecentPosts().collect { posts ->
                    _recentState.value = FeedState.Success(posts)
                }
            } catch (e: Exception) {
                _recentState.value = FeedState.Error(e.message ?: "Failed to load")
            }
        }
    }

    fun loadMyPosts() {
        val uid = _authState.value.uid
        if (uid.isEmpty()) {
            _myPostsState.value = FeedState.Success(emptyList())
            return
        }
        viewModelScope.launch {
            _myPostsState.value = FeedState.Loading
            try {
                repository.getMyPosts(uid).collect { posts ->
                    _myPostsState.value = FeedState.Success(posts)
                }
            } catch (e: Exception) {
                _myPostsState.value = FeedState.Error(e.message ?: "Failed to load")
            }
        }
    }

    // ── Post Actions ──────────────────────────────────────────────────

    sealed class SubmitState {
        object Idle : SubmitState()
        object Loading : SubmitState()
        data class Success(val postId: String) : SubmitState()
        data class Error(val message: String) : SubmitState()
    }

    fun submitPost(title: String, body: String, tag: String, imageBytes: ByteArray? = null) {
        viewModelScope.launch {
            _submitState.value = SubmitState.Loading
            val base64Image = imageBytes?.let {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
                }
            }
            val result = repository.createPost(title, body, tag, base64Image)
            _submitState.value = if (result.isSuccess) {
                SubmitState.Success(result.getOrThrow())
            } else {
                SubmitState.Error(result.exceptionOrNull()?.message ?: "Failed to post")
            }
        }
    }

    fun resetSubmitState() {
        _submitState.value = SubmitState.Idle
    }

    fun toggleLike(postId: String) {
        viewModelScope.launch { repository.toggleLike(postId) }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch { repository.deletePost(postId) }
    }

    // ── Factory ───────────────────────────────────────────────────────

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CommunityViewModel(CommunityRepository()) as T
    }
}