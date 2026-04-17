package com.example.magnit.Backend

import com.google.gson.Gson

data class User(
    val id: String = "",
    val login: String = "",
    val pass: String = "",
    val email: String = "",
    val favorite: String = "[]",
    val basket: String = "[]",
    val balance: String = "0",
    val order: String = "[]",
    val wallet: String = "[]",
    val bonuses: String = "",
    val favoriteCategories: String = ""
) {
    companion object {
        fun fromJson(jsonString: String, documentId: String): User {
            return try {
                val gson = Gson()
                gson.fromJson(jsonString, User::class.java).copy(id = documentId)
            } catch (e: Exception) {
                User(id = documentId)
            }
        }
    }
}