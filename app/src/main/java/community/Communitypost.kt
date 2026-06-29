package com.devdeepsy.nidusflora.community

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
data class CommunityPost(
    @DocumentId  val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String = "",
    val title: String = "",
    val body: String = "",
    val imageUrl: String? = null,
    val tag: String = "",
    val likesCount: Int = 0,
    val likedBy: List<String> = emptyList(),
    val commentCount: Int=0,
    @ServerTimestamp val createdAt: Timestamp?= null
)

enum class PostTag(val label: String, val colorRes: Int) {
    QUESTION("Question", 0xFF1565C0.toInt()),   // deep blue
    TIP("Tip", 0xFF2E7D32.toInt()),             // deep green
    PHOTO("Photo", 0xFFE65100.toInt())          // deep orange
}