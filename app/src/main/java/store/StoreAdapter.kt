package com.devdeepsy.nidusflora.store

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.devdeepsy.nidusflora.R
import com.google.android.material.chip.Chip

class StoreAdapter(
    private val categories: List<StoreCategory>,
    private val onBuyClick: (StoreProduct) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_CATEGORY = 0
    }

    override fun getItemCount() = categories.size

    override fun getItemViewType(position: Int) = VIEW_TYPE_CATEGORY

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_header, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as CategoryViewHolder).bind(categories[position])
    }

    inner class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvEmoji: TextView = view.findViewById(R.id.tvCategoryEmoji)
        private val tvName: TextView = view.findViewById(R.id.tvCategoryName)
        private val rvProducts: RecyclerView = view.findViewById(R.id.rvCategoryProducts)

        fun bind(category: StoreCategory) {
            tvEmoji.text = category.emoji
            tvName.text = category.categoryName
            rvProducts.layoutManager = LinearLayoutManager(
                itemView.context, LinearLayoutManager.HORIZONTAL, false
            )
            rvProducts.adapter = ProductAdapter(category.products, onBuyClick)
        }
    }
}

// Inner adapter for the horizontal product list inside each category
class ProductAdapter(
    private val products: List<StoreProduct>,
    private val onBuyClick: (StoreProduct) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    override fun getItemCount() = products.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_store_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(products[position])
    }

    inner class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val img: ImageView = view.findViewById(R.id.imgProduct)
        private val chip: Chip = view.findViewById(R.id.chipBadge)
        private val tvName: TextView = view.findViewById(R.id.tvProductName)
        private val tvDesc: TextView = view.findViewById(R.id.tvDescription)
        private val rating: RatingBar = view.findViewById(R.id.ratingBar)
        private val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        private val btnBuy: Button = view.findViewById(R.id.btnBuy)

        fun bind(product: StoreProduct) {
            tvName.text = product.name
            tvDesc.text = product.description
            tvPrice.text = product.price
            rating.rating = product.rating

            // Badge chip — only show if badge text exists
            if (product.badge.isNotEmpty()) {
                chip.visibility = View.VISIBLE
                chip.text = product.badge
            } else {
                chip.visibility = View.GONE
            }

            // Glide image load with placeholder
            Glide.with(itemView.context)
                .load(product.imageUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_launcher_background)
                .into(img)

            btnBuy.setOnClickListener { onBuyClick(product) }
        }
    }
}