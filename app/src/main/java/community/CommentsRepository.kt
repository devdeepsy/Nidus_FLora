package com.deepgamer.agriexpert.community

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

    // ── add comment ───────────────────────────────────────────────────────────
    suspend fun addComment(postId: String, comment: Comment): Result<Unit> = runCatching {
        // Get post author details first
        val postSnap = postRef(postId).get().await()
        val postAuthorUid = postSnap.getString("authorUid") ?: ""
        val postTitle     = postSnap.getString("title") ?: "your post"

        val batch = db.batch()
        val commentRef = commentsRef(postId).document()
        batch.set(commentRef, comment.copy(postId = postId))
        batch.update(postRef(postId), "commentCount", FieldValue.increment(1))
        batch.commit().await()

        // Store notification data in Firestore for the post author
        // (only if commenter is not the post author themselves)
        if (postAuthorUid.isNotBlank() && postAuthorUid != comment.authorUid) {
            db.collection("notifications").add(
                mapOf(
                    "recipientUid"  to postAuthorUid,
                    "type"          to "comment",
                    "postId"        to postId,
                    "postTitle"     to postTitle,
                    "commenterName" to comment.authorName,
                    "createdAt"     to FieldValue.serverTimestamp(),
                    "read"          to false
                )
            ).await()
        }
    }

    // ── delete comment ────────────────────────────────────────────────────────
    suspend fun deleteComment(postId: String, commentId: String): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.delete(commentsRef(postId).document(commentId))
        batch.update(postRef(postId), "commentCount", FieldValue.increment(-1))
        batch.commit().await()
    }
}