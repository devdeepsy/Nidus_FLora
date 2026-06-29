package com.devdeepsy.nidusflora

import android.R
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.devdeepsy.nidusflora.models.FarmActivity
import java.text.SimpleDateFormat
import java.util.Locale

class ActivityAdapter : RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder>() {

    private var list = listOf<FarmActivity>()
    private val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    var onEdit: ((FarmActivity) -> Unit)? = null
    var onDelete: ((FarmActivity) -> Unit)? = null

    fun submitList(newList: List<FarmActivity>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.simple_list_item_2, parent, false)
        return ActivityViewHolder(view)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        val activity = list[position]
        holder.title.text = "${activity.crop} - ${activity.activityType}"
        holder.subtitle.text = activity.notes ?: ""
        holder.itemView.setOnLongClickListener {

            AlertDialog.Builder(holder.itemView.context)
                .setTitle("Activity options")
                .setItems(arrayOf("Edit", "Delete")) { _, which ->
                    when (which) {
                        0 -> onEdit?.invoke(activity)
                        1 -> {
                            AlertDialog.Builder(holder.itemView.context)
                                .setTitle("Delete activity?")
                                .setMessage("This action cannot be undone.")
                                .setPositiveButton("Delete") { _, _ ->
                                    onDelete?.invoke(activity)
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

    class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.text1)
        val subtitle: TextView = itemView.findViewById(R.id.text2)
    }
}