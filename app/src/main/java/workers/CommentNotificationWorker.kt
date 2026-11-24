package com.devdeepsy.nidusflora.workers

import android.content.Context
import androidx.work.*
import com.devdeepsy.nidusflora.notifications.NotificationHelper

class CommentNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val postTitle = inputData.getString(KEY_POST_TITLE)?.takeIf { it.isNotBlank() } ?: "your post"
            val commenterName = inputData.getString(KEY_COMMENTER_NAME)?.takeIf { it.isNotBlank() } ?: "Someone"
            val postId = inputData.getString(KEY_POST_ID).orEmpty()

            NotificationHelper.createAllChannels(applicationContext)
            NotificationHelper.send(
                context = applicationContext,
                notifId = 400_000 + (postId.hashCode() and 0x7FFF_FFFF),
                channelId = NotificationHelper.CHANNEL_GENERAL,
                title = "💬 New comment on your post",
                body = "$commenterName commented on \"$postTitle\""
            )
            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_POST_TITLE = "post_title"
        const val KEY_COMMENTER_NAME = "commenter_name"
        const val KEY_POST_ID = "post_id"

        fun fire(context: Context, postId: String, postTitle: String, commenterName: String) {
            val data = workDataOf(
                KEY_POST_ID to postId,
                KEY_POST_TITLE to postTitle,
                KEY_COMMENTER_NAME to commenterName
            )
            val request = OneTimeWorkRequestBuilder<CommentNotificationWorker>()
                .setInputData(data)
                .build()

            val uniqueName = if (postId.isNotBlank()) {
                "comment_notif_$postId"
            } else {
                "comment_notif_${System.nanoTime()}"
            }

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueName,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}