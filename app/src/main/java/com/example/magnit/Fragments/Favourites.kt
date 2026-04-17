package com.example.magnit.Fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.magnit.Backend.Product
import com.example.magnit.ProductAdapter
import com.example.magnit.Secondary.Authorization
import com.example.magnit.databinding.FragmentFavouritesBinding
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject

class Favourites : Fragment() {
    private var _binding: FragmentFavouritesBinding? = null
    private val b get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: ProductAdapter
    private val products = mutableListOf<Product>()
    private val tag = "FavouritesDebug"
    private var loading = false
    private var uid: String? = null
    private val fav = mutableListOf<String>()
    private val cart = mutableListOf<String>()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        FragmentFavouritesBinding.inflate(inflater, c, false).also { _binding = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        adapter = ProductAdapter(products).apply {
            setOnFavoriteClickListener { p -> if (uid == null) authThen { toggleFav(p) } else toggleFav(p) }
            setOnCartClickListener { p -> if (uid == null) authThen { toggleCart(p) } else toggleCart(p) }
        }
        b.favouritesRecycler.apply { layoutManager = GridLayoutManager(requireContext(), 2); adapter = this@Favourites.adapter }
        checkAuth()
    }

    override fun onResume() { super.onResume(); if (uid != null) loadUserData(uid!!) }

    private fun authThen(action: () -> Unit) {
        Toast.makeText(requireContext(), "Войдите в аккаунт", Toast.LENGTH_SHORT).show()
        startActivity(Intent(requireContext(), Authorization::class.java))
    }

    private fun checkAuth() {
        if (loading) return
        uid = requireContext().getSharedPreferences("filter_prefs", Context.MODE_PRIVATE).getString("account", "none").takeIf { it != "none" }
        if (uid != null) { loadUserData(uid!!); loadFavourites(uid!!) } else showGuest()
    }

    private fun loadUserData(id: String) = db.collection("Accounts").document(id).get()
        .addOnSuccessListener { doc ->
            try {
                JSONObject(doc.getString("info") ?: "").let { json ->
                    fun parse(key: String) = (0 until JSONArray(json.optString(key, "[]")).length()).map { JSONArray(json.optString(key, "[]")).getString(it) }
                    fav.apply { clear(); addAll(parse("favorite")) }
                    cart.apply { clear(); addAll(parse("basket")) }
                    adapter.apply { setFavoriteIds(fav); setCartIds(cart) }
                }
            } catch (e: Exception) { }
        }

    private fun loadFavourites(id: String) {
        if (loading) return
        loading = true
        db.collection("Accounts").document(id).get()
            .addOnSuccessListener { doc ->
                try {
                    val arr = JSONArray(JSONObject(doc.getString("info") ?: "").optString("favorite", "[]"))
                    if (arr.length() > 0) loadProducts(arr) else showEmpty().also { loading = false }
                } catch (e: Exception) { showEmpty(); loading = false }
            }.addOnFailureListener { showEmpty(); loading = false }
    }

    private fun loadProducts(arr: JSONArray) {
        products.clear()
        val ids = (0 until arr.length()).map { arr.getString(it) }
        if (ids.isEmpty()) return showEmpty().also { loading = false }

        db.collection("Products").get()
            .addOnSuccessListener { docs ->
                docs.forEach { doc ->
                    if (ids.contains(doc.id)) doc.getString("info")?.let {
                        products.add(Product.fromJson(it, doc.id))
                    }
                }
                if (products.isNotEmpty()) showContent() else showEmpty()
                loading = false
            }.addOnFailureListener { showEmpty(); loading = false }
    }

    private fun toggleFav(p: Product) = uid?.let { id ->
        db.collection("Accounts").document(id).get().addOnSuccessListener { doc ->
            try {
                JSONObject(doc.getString("info") ?: "").let { json ->
                    val arr = JSONArray(json.optString("favorite", "[]"))
                    val exists = (0 until arr.length()).any { arr.getString(it) == p.id }
                    val new = JSONArray().apply {
                        (0 until arr.length()).forEach { i -> val id = arr.getString(i); if (id != p.id) put(id) }
                        if (!exists) put(p.id)
                    }
                    json.put("favorite", new.toString())
                    db.collection("Accounts").document(id).update("info", json.toString())
                    if (exists) fav.remove(p.id) else fav.add(p.id)
                    adapter.setFavoriteIds(fav)
                    if (!exists) products.add(p) else products.removeAll { it.id == p.id }
                    adapter.updateProducts(products)
                    if (products.isEmpty()) showEmpty()
                    Toast.makeText(requireContext(), if (exists) "Удалено" else "Добавлено", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { }
        }
    } ?: authThen { }

    private fun toggleCart(p: Product) = uid?.let { id ->
        db.collection("Accounts").document(id).get().addOnSuccessListener { doc ->
            try {
                JSONObject(doc.getString("info") ?: "").let { json ->
                    val arr = JSONArray(json.optString("basket", "[]"))
                    val exists = (0 until arr.length()).any { arr.getString(it) == p.id }
                    val new = JSONArray().apply {
                        (0 until arr.length()).forEach { i -> val id = arr.getString(i); if (id != p.id) put(id) }
                        if (!exists) put(p.id)
                    }
                    json.put("basket", new.toString())
                    db.collection("Accounts").document(id).update("info", json.toString())
                    if (exists) cart.remove(p.id) else cart.add(p.id)
                    adapter.setCartIds(cart)
                    Toast.makeText(requireContext(), if (exists) "Удалено из корзины" else "Добавлено в корзину", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { }
        }
    } ?: authThen { }

    private fun showGuest() = with(b) {
        favouritesRecycler.visibility = View.GONE
        emptyFavouritesLayout.visibility = View.VISIBLE
        emptyTitle.text = "Войдите в аккаунт"
        emptyMessage.text = "Чтобы увидеть избранные товары"
    }

    private fun showEmpty() = with(b) {
        favouritesRecycler.visibility = View.GONE
        emptyFavouritesLayout.visibility = View.VISIBLE
        emptyTitle.text = "В избранном пусто"
        emptyMessage.text = "Добавляйте товары, чтобы не потерять"
    }

    private fun showContent() = with(b) {
        favouritesRecycler.visibility = View.VISIBLE
        emptyFavouritesLayout.visibility = View.GONE
        adapter.updateProducts(products)
        adapter.setFavoriteIds(fav)
        adapter.setCartIds(cart)
    }

    fun refreshFavourites() { if (!loading) { products.clear(); checkAuth() } }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}