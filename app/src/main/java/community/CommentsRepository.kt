package com.devdeepsy.nidusflora.community

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CommentsRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun commentsRef(postId: String) =
        db.collection("community_posts")
            .document(postId)
            .collection("comments")

    private fun postRef(postId: String) =
        db.collection("community_posts").document(postId)

    // ── real-time stream ──────────────────────────────────────────────────────
    fun observeComments(postId: String): Flow<List<Comment>> = callbackFlow {
        val listener = commentsRef(postId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(
                    snapshot?.documents?.mapNotNull {
                        it.toObject(Comment::class.java)
                    } ?: emptyList()
                )
            }
        awaitClose { listener.remove() }
    }

    data class CommentAddResult(
        val postAuthorUid: String,
        val postTitle: String
    )
    // ── add comment ───────────────────────────────────────────────────────────
    suspend fun addComment(postId: String, comment: Comment): Result<CommentAddResult> = runCatching {
        val postSnap = postRef(postId).get().await()
        val postAuthorUid = postSnap.getString("authorUid") ?: ""
        val postTitle = postSnap.getString("title") ?: "your post"

        val batch = db.batch()
        val commentRef = commentsRef(postId).document()
        batch.set(commentRef, comment.copy(postId = postId))
        batch.update(postRef(postId), "commentCount", FieldValue.increment(1))
        batch.commit().await()

        if (postAuthorUid.isNotBlank() && postAuthorUid != comment.authorUid) {
            db.collection("notifications").add(
                mapOf(
                    "recipientUid" to postAuthorUid,
                    "type" to "comment",
                    "postId" to postId,
                    "postTitle" to postTitle,
                    "commenterName" to comment.authorName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "read" to false
                )
            ).await()
        }

        CommentAddResult(postAuthorUid = postAuthorUid, postTitle = postTitle)
    }

    // ── delete comment ────────────────────────────────────────────────────────
    suspend fun deleteComment(postId: String, commentId: String): Result<Unit> = runCatching {
        val targetCommentRef = commentsRef(postId).document(commentId)
        val targetPostRef = postRef(postId)
        db.runTransaction { tx ->
            val postSnap = tx.get(targetPostRef)
            val currentCount = (postSnap.getLong("commentCount") ?: 0L).coerceAtLeast(0L)
            tx.delete(targetCommentRef)
            tx.update(targetPostRef, "commentCount", (currentCount - 1L).coerceAtLeast(0L))
        }.await()
    }
}