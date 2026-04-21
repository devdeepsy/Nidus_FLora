package com.deepgamer.agriexpert.store

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

object FirestoreSeeder {

    fun seedIfEmpty() {
        val db = Firebase.firestore

        // Check if already seeded
        db.collection("store_products").limit(1).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    seedCategories(db)
                    seedProducts(db)
                }
                // Already seeded — do nothing
            }
    }

    private fun seedCategories(db: com.google.firebase.firestore.FirebaseFirestore) {
        val categories = listOf(
            mapOf("categoryName" to "Seeds",          "emoji" to "🌱", "displayOrder" to 1),
            mapOf("categoryName" to "Pots & Planters","emoji" to "🪴", "displayOrder" to 2),
            mapOf("categoryName" to "Soil & Compost", "emoji" to "🪱", "displayOrder" to 3),
            mapOf("categoryName" to "Tools", "emoji" to "🛠️", "displayOrder" to 4),
            mapOf("categoryName" to "Fertilizers",    "emoji" to "💧", "displayOrder" to 5)
        )
        val ids = listOf("seeds", "pots", "soil", "tools", "fertilizers")
        val col = db.collection("store_categories")
        categories.forEachIndexed { i, data -> col.document(ids[i]).set(data) }
    }

    private fun seedProducts(db: com.google.firebase.firestore.FirebaseFirestore) {
        val col = db.collection("store_products")
        val allProducts = buildList {
            // Seasonal picks
            addAll(StoreRepository.getSeasonalPicks())
            // Category products
            StoreRepository.getCategories().forEach { addAll(it.products) }
        }
        allProducts.forEach { product ->
            val map = mapOf(
                "id"          to product.id,
                "name"        to product.name,
                "description" to product.description,
                "imageUrl"    to product.imageUrl,
                "price"       to product.price,
                "rating"      to product.rating,
                "affiliateUrl" to product.affiliateUrl,
                "badge"       to product.badge,
                "categoryId"  to product.categoryId,
                "isSeasonal"  to product.isSeasonal
            )
            col.document(product.id).set(map)
        }
    }
}