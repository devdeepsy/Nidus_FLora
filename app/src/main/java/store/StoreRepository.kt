package com.devdeepsy.nidusflora.store

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object StoreRepository {

    private val db: FirebaseFirestore = Firebase.firestore

    // ─── Firestore fetch ────────────────────────────────────────────────────

    suspend fun fetchSeasonalPicks(): List<StoreProduct> {
        return try {
            db.collection("store_products")
                .whereEqualTo("isSeasonal", true)
                .get()
                .await()
                .toObjects(StoreProduct::class.java)
                .ifEmpty { getSeasonalPicksFallback() }
        } catch (e: Exception) {
            getSeasonalPicksFallback()
        }
    }

    suspend fun fetchCategories(): List<StoreCategory> {
        return try {
            // 1. Fetch all categories ordered by displayOrder
            val categoryDocs = db.collection("store_categories")
                .orderBy("displayOrder")
                .get()
                .await()
                .documents

            if (categoryDocs.isEmpty()) return getCategoriesFallback()

            // 2. Fetch all products in one query
            val allProducts = db.collection("store_products")
                .whereEqualTo("isSeasonal", false)
                .get()
                .await()
                .toObjects(StoreProduct::class.java)

            // 3. Group products by categoryId
            val productsByCategory = allProducts.groupBy { it.categoryId }

            // 4. Build StoreCategory list
            categoryDocs.mapNotNull { doc ->
                val catId = doc.id
                StoreCategory(
                    categoryId = catId,
                    categoryName = doc.getString("categoryName") ?: return@mapNotNull null,
                    emoji = doc.getString("emoji") ?: "",
                    products = productsByCategory[catId] ?: emptyList()
                )
            }.ifEmpty { getCategoriesFallback() }

        } catch (e: Exception) {
            getCategoriesFallback()
        }
    }

    // ─── Hardcoded fallback (your existing data, kept intact) ───────────────

    fun getSeasonalPicksFallback(): List<StoreProduct> = getSeasonalPicks()
    fun getCategoriesFallback(): List<StoreCategory> = getCategories()

    fun getSeasonalPicks(): List<StoreProduct> {
        return listOf(
            StoreProduct(
                id = "sp1", categoryId = "seasonal", isSeasonal = true,
                name = "Tomato Hybrid Seeds",
                description = "High yield, disease resistant. Perfect for containers.",
                imageUrl = "https://images.unsplash.com/photo-1592841200221-a6898f307baa?w=400",
                price = "₹129", rating = 4.5f, affiliateUrl = "#", badge = "This Month's Pick"
            ),
            StoreProduct(
                id = "sp2", categoryId = "seasonal", isSeasonal = true,
                name = "Self-Watering Planter",
                description = "12-inch pot with water reservoir. Great for balconies.",
                imageUrl = "https://images.unsplash.com/photo-1416879595882-3373a0480b5b?w=400",
                price = "₹849", rating = 4.7f, affiliateUrl = "#", badge = "This Month's Pick"
            ),
            StoreProduct(
                id = "sp3", categoryId = "seasonal", isSeasonal = true,
                name = "Neem Organic Fertilizer",
                description = "Cold-pressed neem cake. Natural pest repellent.",
                imageUrl = "https://images.unsplash.com/photo-1585314062340-f1a5a7c9328d?w=400",
                price = "₹199", rating = 4.3f, affiliateUrl = "#", badge = "This Month's Pick"
            )
        )
    }

    fun getCategories(): List<StoreCategory> {
        return listOf(
            StoreCategory(
                categoryId = "seeds", categoryName = "Seeds", emoji = "🌱",
                products = listOf(
                    StoreProduct(id = "s1", categoryId = "seeds", name = "Spinach Seeds",
                        description = "Fast growing, ready in 30 days.",
                        imageUrl = "https://images.unsplash.com/photo-1576045057995-568f588f82fb?w=400",
                        price = "₹49", rating = 4.2f, affiliateUrl = "#", badge = "Best Seller"),
                    StoreProduct(id = "s2", categoryId = "seeds", name = "Chilli Seeds Mix",
                        description = "5 varieties — mild to extra hot.",
                        imageUrl = "https://images.unsplash.com/photo-1526346698789-22fd84314424?w=400",
                        price = "₹89", rating = 4.4f, affiliateUrl = "#", badge = "Trending"),
                    StoreProduct(id = "s3", categoryId = "seeds", name = "Coriander Seeds",
                        description = "Kitchen essential, grows in any pot.",
                        imageUrl = "https://images.unsplash.com/photo-1615485290382-441e4d049cb5?w=400",
                        price = "₹39", rating = 4.1f, affiliateUrl = "#", badge = "")
                )
            ),
            StoreCategory(
                categoryId = "pots", categoryName = "Pots & Planters", emoji = "🪴",
                products = listOf(
                    StoreProduct(id = "p1", categoryId = "pots", name = "Terracotta Pot Set",
                        description = "Set of 3 classic pots with drainage holes.",
                        imageUrl = "https://images.unsplash.com/photo-1485955900006-10f4d324d411?w=400",
                        price = "₹449", rating = 4.6f, affiliateUrl = "#", badge = "Best Seller"),
                    StoreProduct(id = "p2", categoryId = "pots", name = "Hanging Basket",
                        description = "Coco-lined, ideal for ferns and herbs.",
                        imageUrl = "https://images.unsplash.com/photo-1463936575829-25148e1db1b8?w=400",
                        price = "₹299", rating = 4.0f, affiliateUrl = "#", badge = "")
                )
            ),
            StoreCategory(
                categoryId = "soil", categoryName = "Soil & Compost", emoji = "🪱",
                products = listOf(
                    StoreProduct(id = "so1", categoryId = "soil", name = "Potting Mix Premium",
                        description = "Cocopeat + perlite + compost blend. 5kg bag.",
                        imageUrl = "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=400",
                        price = "₹349", rating = 4.5f, affiliateUrl = "#", badge = "Best Seller"),
                    StoreProduct(id = "so2", categoryId = "soil", name = "Vermicompost",
                        description = "Pure worm castings. Odour free, 2kg.",
                        imageUrl = "https://images.unsplash.com/photo-1592419044706-39796d40f98c?w=400",
                        price = "₹179", rating = 4.3f, affiliateUrl = "#", badge = "New")
                )
            ),
            StoreCategory(
                categoryId = "tools", categoryName = "Tools", emoji = "🛠️",
                products = listOf(
                    StoreProduct(id = "t1", categoryId = "tools", name = "Hand Trowel Set",
                        description = "Stainless steel, ergonomic grip. Set of 3.",
                        imageUrl = "https://images.unsplash.com/photo-1416879595882-3373a0480b5b?w=400",
                        price = "₹599", rating = 4.7f, affiliateUrl = "#", badge = "Best Seller"),
                    StoreProduct(id = "t2", categoryId = "tools", name = "Watering Can 5L",
                        description = "Long spout, gentle rose head included.",
                        imageUrl = "https://images.unsplash.com/photo-1523348837708-15d4a09cfac2?w=400",
                        price = "₹399", rating = 4.2f, affiliateUrl = "#", badge = "")
                )
            ),
            StoreCategory(
                categoryId = "fertilizers", categoryName = "Fertilizers", emoji = "💧",
                products = listOf(
                    StoreProduct(id = "f1", categoryId = "fertilizers", name = "NPK 19-19-19",
                        description = "Balanced water soluble fertilizer. 500g.",
                        imageUrl = "https://images.unsplash.com/photo-1585314062340-f1a5a7c9328d?w=400",
                        price = "₹249", rating = 4.4f, affiliateUrl = "#", badge = "Trending"),
                    StoreProduct(id = "f2", categoryId = "fertilizers", name = "Seaweed Extract",
                        description = "Liquid bio-stimulant. Boosts root growth.",
                        imageUrl = "https://images.unsplash.com/photo-1592841200221-a6898f307baa?w=400",
                        price = "₹319", rating = 4.6f, affiliateUrl = "#", badge = "New")
                )
            )
        )
    }
}