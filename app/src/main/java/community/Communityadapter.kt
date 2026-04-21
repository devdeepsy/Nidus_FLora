package com.deepgamer.agriexpert.community

import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.deepgamer.agriexpert.R
import com.google.android.material.chip.Chip

class CommunityAdapter(
    private val currentUid: () -> String?,
    private val onLikeClicked: (CommunityPost) -> Unit = {},
    private val onDeleteClicked: (CommunityPost) -> Unit = {},
    private val onPostClicked: (CommunityPost) -> Unit = {},
    private val onCommentClicked: (CommunityPost) -> Unit={}
) : ListAdapter<CommunityPost, CommunityAdapter.PostViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<CommunityPost>() {
        override fun areItemsTheSame(old: CommunityPost, new: CommunityPost) =
            old.id == new.id
        override fun areContentsTheSame(old: CommunityPost, new: CommunityPost) =
            old == new
    }

    inner class PostViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.img_author_avatar)
        val authorName: TextView = view.findViewById(R.id.tv_author_name)
        val timeAgo: TextView = view.findViewById(R.id.tv_time_ago)
        val tagChip: Chip = view.findViewById(R.id.chip_tag)
        val title: TextView = view.findViewById(R.id.tv_post_title)
        val body: TextView = view.findViewById(R.id.tv_post_body)
        val postImage: ImageView = view.findViewById(R.id.img_post_photo)
        val likeCount: TextView = view.findViewById(R.id.tv_like_count)
        val btnLike: ImageButton = view.findViewById(R.id.btn_like)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
        val btnComment: ImageButton = view.findViewById(R.id.btnComment)
        val tvCommentCount: TextView = view.findViewById(R.id.tvCommentCount)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_community_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = getItem(position)
        val uid = currentUid()

        // ── Author avatar ──────────────────────────────────────────────────────
        Glide.with(holder.view)
            .load(post.authorAvatarUrl)
            .apply(RequestOptions().transform(CircleCrop()))
            .placeholder(R.drawable.ic_person_placholder)
            .into(holder.avatar)

        // ── Author + time ──────────────────────────────────────────────────────
        holder.authorName.text = post.authorName.ifBlank { "Anonymous" }
        holder.timeAgo.text = post.createdAt?.toDate()?.let { date ->
            DateUtils.getRelativeTimeSpanString(
                date.time,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
        } ?: ""

        // ── Tag chip ───────────────────────────────────────────────────────────
        val postTag = PostTag.entries.firstOrNull { it.label == post.tag } ?: PostTag.QUESTION
        holder.tagChip.text = postTag.label
        holder.tagChip.chipBackgroundColor =
            android.content.res.ColorStateList.valueOf(
                Color.argb(40,
                    Color.red(postTag.colorRes),
                    Color.green(postTag.colorRes),
                    Color.blue(postTag.colorRes)
                )
            )
        holder.tagChip.setTextColor(postTag.colorRes)

        // ── Content ────────────────────────────────────────────────────────────
        holder.title.text = post.title
        holder.body.text = post.body

        // ── Post image ─────────────────────────────────────────────────────────
        if (!post.imageUrl.isNullOrBlank()) {
            holder.postImage.visibility = View.VISIBLE
            Glide.with(holder.view)
                .load(post.imageUrl)
                .apply(RequestOptions().transform(RoundedCorners(16)))
                .placeholder(R.drawable.ic_plant_placeholder)
                .into(holder.postImage)
        } else {
            holder.postImage.visibility = View.GONE
        }

        // ── Likes ──────────────────────────────────────────────────────────────
        // ── Likes ──────────────────────────────────────────────────────────────────
        holder.likeCount.text = formatCount(post.likesCount)

        val isLiked = uid != null && uid in post.likedBy

// FIX: use ImageViewCompat to set tint alongside the drawable
        val likeIcon = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_hearts_favourite
        holder.btnLike.setImageResource(likeIcon)
        androidx.core.widget.ImageViewCompat.setImageTintList(
            holder.btnLike,
            android.content.res.ColorStateList.valueOf(
                if (isLiked) {
                    androidx.core.content.ContextCompat.getColor(holder.view.context, R.color.red_like)
                } else {
                    androidx.core.content.ContextCompat.getColor(holder.view.context, android.R.color.darker_gray)
                }
            )
        )
        holder.btnLike.setOnClickListener { onLikeClicked(post) }

        // ── Delete ─────────────────────────────────────────────────────────────────
        val isOwner = uid != null && uid == post.authorUid
        holder.btnDelete.visibility = if (isOwner) View.VISIBLE else View.GONE
        holder.btnDelete.setOnClickListener { onDeleteClicked(post) }

        // ── Comment ─────────────────────────────────────────────────────────────────
        holder.tvCommentCount.text = formatCount(post.commentCount)
        holder.btnComment.setOnClickListener {onCommentClicked(post)}

    }

    private fun formatCount(count: Int): String = when {
        count >= 1000 -> String.format("%.1fk", count / 1000f)
        else -> count.toString()
    }
}