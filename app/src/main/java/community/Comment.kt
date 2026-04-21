package com.deepgamer.agriexpert.community

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.Timestamp

data class Comment (
    @DocumentId val id:String =" ",
    val postId: String ="",
    val authorUid: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
