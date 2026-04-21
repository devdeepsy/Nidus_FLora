package com.deepgamer.agriexpert.community

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CommunityRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val postsRef = db.collection("community_posts")

    // ─── Auth ──────────────────────────────────────────────────────────────────

    fun currentUser(): FirebaseUser? = auth.currentUser

    fun signOut() = auth.signOut()

    // ─── Feed Queries ──────────────────────────────────────────────────────────

    fun getTrendingPosts(): Flow<List<CommunityPost>> = callbackFlow {
        val listener = postsRef
            .orderBy("likesCount", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(
                    snapshot?.documents?.mapNotNull {
                        it.toObject(CommunityPost::class.java)
                    } ?: emptyList()
                )
            }
        awaitClose { listener.remove() }
    }

    fun getRecentPosts(): Flow<List<CommunityPost>> = callbackFlow {
        val listener = postsRef
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(
                    snapshot?.documents?.mapNotNull {
                        it.toObject(CommunityPost::class.java)
                    } ?: emptyList()
                )
            }
        awaitClose { listener.remove() }
    }

    fun getMyPosts(uid: String): Flow<List<CommunityPost>> = callbackFlow {
        val listener = postsRef
            .whereEqualTo("authorUid", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(
                    snapshot?.documents?.mapNotNull {
                        it.toObject(CommunityPost::class.java)
                    } ?: emptyList()
                )
            }
        awaitClose { listener.remove() }
    }

    // ─── Write Operations ──────────────────────────────────────────────────────

    suspend fun createPost(
        title: String,
        body: String,
        tag: String
    ): Result<String> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Not signed in"))

        return try {
            val post = CommunityPost(
                authorUid = user.uid,
                authorName = user.displayName ?: "Anonymous",
                authorAvatarUrl = user.photoUrl?.toString() ?: "",
                title = title,
                body = body,
                tag = tag
                // createdAt is @ServerTimestamp — Firestore sets it
            )
            val docRef = postsRef.add(post).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleLike(postId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))

        return try {
            val docRef = postsRef.document(postId)
            db.runTransaction { tx ->
                val snapshot = tx.get(docRef)
                @Suppress("UNCHECKED_CAST")
                val likedBy = snapshot.get("likedBy") as? List<String> ?: emptyList()

                if (uid in likedBy) {
                    tx.update(docRef, "likedBy", FieldValue.arrayRemove(uid))
                    tx.update(docRef, "likesCount", FieldValue.increment(-1))
                } else {
                    tx.update(docRef, "likedBy", FieldValue.arrayUnion(uid))
                    tx.update(docRef, "likesCount", FieldValue.increment(1))
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))

        return try {
            val doc = postsRef.document(postId).get().await()
            if (doc.getString("authorUid") != uid) {
                return Result.failure(SecurityException("Not the post author"))
            }
            postsRef.document(postId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Storage stub ──────────────────────────────────────────────────────────

    suspend fun uploadImage(imageBytes: ByteArray, postId: String): Result<String> {
        // TODO: wire Firebase Storage once billing is enabled
        return Result.failure(UnsupportedOperationException("Storage not available yet"))
    }
}