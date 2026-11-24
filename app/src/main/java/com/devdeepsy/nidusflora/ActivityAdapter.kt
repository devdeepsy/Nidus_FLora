package com.devdeepsy.nidusflora

import android.app.Activity
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.devdeepsy.nidusflora.models.FarmActivity

class ActivityAdapter : ListAdapter<FarmActivity, ActivityAdapter.ActivityViewHolder>(DiffCallback) {

    var onEdit: ((FarmActivity) -> Unit)? = null
    var onDelete: ((FarmActivity) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(android.R.id.text1)
        private val subtitle: TextView = itemView.findViewById(android.R.id.text2)
        private var currentItem: FarmActivity? = null

        init {
            itemView.setOnLongClickListener {
                val item = currentItem ?: return@setOnLongClickListener true
                val activity = itemView.context as? Activity
                if (activity == null || activity.isFinishing || activity.isDestroyed) {
                    return@setOnLongClickListener true
                }

                AlertDialog.Builder(activity)
                    .setTitle("Activity options")
                    .setItems(arrayOf("Edit", "Delete")) { _, which ->
                        when (which) {
                            0 -> onEdit?.invoke(item)
                            1 -> {
                                if (activity.isFinishing || activity.isDestroyed) return@setItems
                                AlertDialog.Builder(activity)
                                    .setTitle("Delete activity?")
                                    .setMessage("This action cannot be undone.")
                                    .setPositiveButton("Delete") { _, _ ->
                                        onDelete?.invoke(item)
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                        }
                    }
                    .show()
                true
            }
        }

        fun bind(item: FarmActivity) {
            currentItem = item
            title.text= "${item.crop} - ${item.activityType}"
            subtitle.text = item.notes ?: ""
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<FarmActivity>() {
        override fun areItemsTheSame(oldItem: FarmActivity, newItem: FarmActivity): Boolean =
            oldItem.id == newItem.id // Replace `id` with your unique identifier property

        override fun areContentsTheSame(oldItem: FarmActivity, newItem: FarmActivity): Boolean =
            oldItem == newItem
    }
}