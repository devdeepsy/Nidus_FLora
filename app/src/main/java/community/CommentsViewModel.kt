package com.devdeepsy.nidusflora.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ── States ────────────────────────────────────────────────────────────────────

sealed class CommentsState {
    object Loading : CommentsState()
    data class Success(val comments: List<Comment>) : CommentsState()
    data class Error(val message: String) : CommentsState()
}

sealed class SubmitCommentState {
    object Idle : SubmitCommentState()
    object Loading : SubmitCommentState()
    object Success : SubmitCommentState()
    data class Error(val message: String) : SubmitCommentState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class CommentsViewModel(
    private val postId: String,
    private val repository: CommentsRepository = CommentsRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _commentsState = MutableStateFlow<CommentsState>(CommentsState.Loading)
    val commentsState: StateFlow<CommentsState> = _commentsState.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitCommentState>(SubmitCommentState.Idle)
    val submitState: StateFlow<SubmitCommentState> = _submitState.asStateFlow()

    val currentUid: String? get() = auth.currentUser?.uid
    val isSignedIn: Boolean get() = auth.currentUser != null

    init { observeComments() }

    // ── observe ───────────────────────────────────────────────────────────────
    private fun observeComments() {
        viewModelScope.launch {
            repository.observeComments(postId)
                .catch { e -> _commentsState.value = CommentsState.Error(e.message ?: "Error") }
                .collect { comments -> _commentsState.value = CommentsState.Success(comments) }
        }
    }

    // ── submit ────────────────────────────────────────────────────────────────
    fun submitComment(text: String, context: android.content.Context) {  // ← add context param
        val user = auth.currentUser ?: run {
            _submitState.value = SubmitCommentState.Error("sign_in_required")
            return
        }
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        _submitState.value = SubmitCommentState.Loading
        val comment = Comment(
            postId          = postId,
            authorUid       = user.uid,
            authorName      = user.displayName ?: "Anonymous",
            authorAvatarUrl = user.photoUrl?.toString() ?: "",
            text            = trimmed
        )
        viewModelScope.launch {
            val result = repository.addComment(postId, comment)
            if (result.isSuccess) {
                _submitState.value = SubmitCommentState.Success

                // Fire notification for post author
                val postSnap = com.google.firebase.firestore.FirebaseFirestore
                    .getInstance()
                    .collection("community_posts")
                    .document(postId)
                    .get()
                    .await()
                val postTitle = postSnap.getString("title") ?: "a post"
                val postAuthorUid = postSnap.getString("authorUid") ?: ""

                // Only notify if commenter is not the post author
                if (postAuthorUid != user.uid) {
                    com.devdeepsy.nidusflora.workers.CommentNotificationWorker.fire(
                        context       = context,
                        postId        = postId,
                        postTitle     = postTitle,
                        commenterName = user.displayName ?: "Someone"
                    )
                }
            } else {
                _submitState.value = SubmitCommentState.Error(
                    result.exceptionOrNull()?.message ?: "Failed"
                )
            }
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────
    fun deleteComment(commentId: String) {
        viewModelScope.launch { repository.deleteComment(postId, commentId) }
    }

    fun resetSubmitState() { _submitState.value = SubmitCommentState.Idle }

    // ── Factory ───────────────────────────────────────────────────────────────
    class Factory(private val postId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CommentsViewModel(postId) as T
    }
}