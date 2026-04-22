package com.deepgamer.agriexpert.workers

import android.content.Context
import androidx.work.*
import com.deepgamer.agriexpert.notifications.NotificationHelper
import java.util.concurrent.TimeUnit

class CommentNotificationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val postTitle     = inputData.getString(KEY_POST_TITLE)     ?: "your post"
        val commenterName = inputData.getString(KEY_COMMENTER_NAME) ?: "Someone"
        val postId        = inputData.getString(KEY_POST_ID)        ?: ""

        NotificationHelper.send(
            context   = applicationContext,
            notifId   = NotificationHelper.NOTIF_GENERAL_TIP + postId.hashCode(),
            channelId = NotificationHelper.CHANNEL_GENERAL,
            title     = "💬 New comment on your post",
            body      = "$commenterName commented on \"$postTitle\""
        )
        return Result.success()
    }

    companion object {
        const val KEY_POST_TITLE     = "post_title"
        const val KEY_COMMENTER_NAME = "commenter_name"
        const val KEY_POST_ID        = "post_id"

        fun fire(context: Context, postId: String, postTitle: String, commenterName: String) {
            val data = workDataOf(
                KEY_POST_ID        to postId,
                KEY_POST_TITLE     to postTitle,
                KEY_COMMENTER_NAME to commenterName
            )
            val request = OneTimeWorkRequestBuilder<CommentNotificationWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}