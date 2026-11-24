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
    private var categories: List<StoreCategory>,
    private val sharedRecycledViewPool: RecyclerView.RecycledViewPool = RecyclerView.RecycledViewPool(),
    private val onBuyClick: (StoreProduct) -> Unit
) : RecyclerView.Adapter<StoreAdapter.CategoryViewHolder>() {

    fun updateCategories(newCategories: List<StoreCategory>) {
        categories = newCategories
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = categories.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_header, parent, false)
        return CategoryViewHolder(view, sharedRecycledViewPool, onBuyClick)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    class CategoryViewHolder(
        view: View,
        recycledPool: RecyclerView.RecycledViewPool,
        onBuyClick: (StoreProduct) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val tvEmoji: TextView = view.findViewById(R.id.tvCategoryEmoji)
        private val tvName: TextView = view.findViewById(R.id.tvCategoryName)
        private val rvProducts: RecyclerView = view.findViewById(R.id.rvCategoryProducts)
        private val productAdapter = ProductAdapter(emptyList(), onBuyClick)

        init {
            rvProducts.layoutManager = LinearLayoutManager(
                view.context,
                LinearLayoutManager.HORIZONTAL,
                false
            ).apply {
                initialPrefetchItemCount = 4
            }
            rvProducts.setRecycledViewPool(recycledPool)
            rvProducts.setHasFixedSize(true)
            rvProducts.adapter = productAdapter
        }

        fun bind(category: StoreCategory) {
            tvEmoji.text = category.emoji
            tvName.text = category.categoryName
            productAdapter.updateItems(category.products)
        }
    }
}

class ProductAdapter(
    private var products: List<StoreProduct>,
    private val onBuyClick: (StoreProduct) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    fun updateItems(newProducts: List<StoreProduct>) {
        products = newProducts
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = products.size

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

            if (product.badge.isNotEmpty()) {
                chip.visibility = View.VISIBLE
                chip.text = product.badge
            } else {
                chip.visibility = View.GONE
            }

            Glide.with(itemView)
                .load(product.imageUrl.ifBlank { null })
                .centerCrop()
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .into(img)

            btnBuy.setOnClickListener { onBuyClick(product) }
        }
    }
}