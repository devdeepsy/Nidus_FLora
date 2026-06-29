package com.devdeepsy.nidusflora.store

data class StoreProduct(
    val id: String = "",
    val description: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val price: String = "",
    val rating: Float = 0f,
    val affiliateUrl: String = "",
    val badge: String = "",
    val categoryId: String = "",
    val isSeasonal: Boolean = false
)