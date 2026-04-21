package com.deepgamer.agriexpert.store

data class StoreCategory(
    val categoryId: String = "",
    val categoryName: String = "",
    val emoji: String = "",
    val products: List<StoreProduct> = emptyList()
)