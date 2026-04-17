package com.example.magnit.Backend

import com.google.gson.Gson

data class Product(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val category: String = "",
    val rating: Double = 0.0,
    val reviewsCount: Int = 0,
    val imageUrl: String = ""
) {
    companion object {
        fun fromJson(jsonString: String?, documentId: String): Product {
            return try {
                val gson = Gson()
                gson.fromJson(jsonString, Product::class.java).copy(id = documentId)
            } catch (e: Exception) {
                Product(id = documentId)
            }
        }
    }
}