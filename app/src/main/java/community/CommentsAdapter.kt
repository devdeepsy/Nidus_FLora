package com.devdeepsy.nidusflora.community

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
import com.bumptech.glide.request.RequestOptions
import com.devdeepsy.nidusflora.R
import com.google.firebase.Timestamp

class CommentsAdapter(
    private val currentUid: String?,
    private val onDeleteClicked: (Comment) -> Unit
) : ListAdapter<Comment, CommentsAdapter.CommentViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(old: Comment, new: Comment) = old.id == new.id
        override fun areContentsTheSame(old: Comment, new: Comment) = old == new
    }

    inner class CommentViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.IvCommentAvatar)
        val authorName: TextView = view.findViewById(R.id.TvCommentAuthor)
        val timeAgo: TextView = view.findViewById(R.id.TvCommentTime)
        val text: TextView = view.findViewById(R.id.TvCommentText)
        val btnDelete: ImageButton = view.findViewById(R.id.BtnDeleteComment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = getItem(position)

        // ── Avatar ────────────────────────────────────────────────────────────
        Glide.with(holder.view)
            .load(comment.authorAvatarUrl.ifBlank { null })
            .apply(RequestOptions().transform(CircleCrop()))
            .placeholder(R.drawable.ic_person_placholder)
            .into(holder.avatar)

        // ── Author + time ─────────────────────────────────────────────────────
        holder.authorName.text = comment.authorName.ifBlank { "Anonymous" }
        val timestamp: Timestamp? = comment.createdAt
        holder.timeAgo.text = if (timestamp != null) {
            DateUtils.getRelativeTimeSpanString(
                timestamp.toDate().time,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
        } else ""

        // ── Text ──────────────────────────────────────────────────────────────
        holder.text.text = comment.text

        // ── Delete (owner only) ───────────────────────────────────────────────
        if (comment.authorUid == currentUid) {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener { onDeleteClicked(comment) }
        } else {
            holder.btnDelete.visibility = View.GONE
        }
    }
}