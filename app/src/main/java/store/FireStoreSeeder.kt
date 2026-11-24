package com.devdeepsy.nidusflora.store

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

object FirestoreSeeder {

    fun seedIfEmpty(onComplete: ((Boolean) -> Unit)? = null) {
        val db = Firebase.firestore

        db.collection("store_products").limit(1).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    onComplete?.invoke(true)
                    return@addOnSuccessListener
                }
                seedBatch(db, onComplete)
            }
            .addOnFailureListener {
                onComplete?.invoke(false)
            }
    }

    private fun seedBatch(db: FirebaseFirestore, onComplete: ((Boolean) -> Unit)?) {
        val batch = db.batch()

        val categories = listOf(
            mapOf("categoryName" to "Seeds",           "emoji" to "🌱", "displayOrder" to 1),
            mapOf("categoryName" to "Pots & Planters", "emoji" to "🪴", "displayOrder" to 2),
            mapOf("categoryName" to "Soil & Compost",  "emoji" to "🪱", "displayOrder" to 3),
            mapOf("categoryName" to "Tools",           "emoji" to "🛠️", "displayOrder" to 4),
            mapOf("categoryName" to "Fertilizers",     "emoji" to "💧", "displayOrder" to 5)
        )
        val ids = listOf("seeds", "pots", "soil", "tools", "fertilizers")
        val catCol = db.collection("store_categories")
        categories.forEachIndexed { i, data ->
            batch.set(catCol.document(ids[i]), data)
        }

        val prodCol = db.collection("store_products")
        val allProducts = buildList {
            addAll(StoreRepository.getSeasonalPicks())
            StoreRepository.getCategories().forEach { addAll(it.products) }
        }

        allProducts.forEach { product ->
            val map = mapOf(
                "id"           to product.id,
                "name"         to product.name,
                "description"  to product.description,
                "imageUrl"     to product.imageUrl,
                "price"        to product.price,
                "rating"       to product.rating,
                "affiliateUrl" to product.affiliateUrl,
                "badge"        to product.badge,
                "categoryId"   to product.categoryId,
                "isSeasonal"   to product.isSeasonal
            )
            batch.set(prodCol.document(product.id), map)
        }

        batch.commit()
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
    }
}