package com.example.magnit.Fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.magnit.Backend.Product
import com.example.magnit.ProductAdapter
import com.example.magnit.R
import com.example.magnit.Secondary.Authorization
import com.example.magnit.Secondary.ProductActivity
import com.example.magnit.databinding.FragmentCategoriesBinding
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject

class Categories : Fragment() {
    private var _binding: FragmentCategoriesBinding? = null
    private val b get() = _binding!!
    private lateinit var adapter: ProductAdapter
    private val all = mutableListOf<Product>()
    private val filtered = mutableListOf<Product>()
    private val db = FirebaseFirestore.getInstance()
    private val fav = mutableListOf<String>()
    private val cart = mutableListOf<String>()
    private var uid: String? = null

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, b: Bundle?): View =
        FragmentCategoriesBinding.inflate(inflater, c, false).also { _binding = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        adapter = ProductAdapter(filtered).apply {
            setOnFavoriteClickListener { p ->
                if (uid == null) {
                    authToast()
                    startActivity(Intent(requireContext(), Authorization::class.java))
                } else toggleFav(p)
            }
            setOnCartClickListener { p ->
                if (uid == null) {
                    authToast()
                    startActivity(Intent(requireContext(), Authorization::class.java))
                } else toggleCart(p)
            }
        }

        v.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.popularRecycler)?.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@Categories.adapter
        }

        setupCategories()
        loadProducts()
        checkAuth()
    }

    override fun onResume() { super.onResume(); checkAuth() }

    private fun authToast() = Toast.makeText(requireContext(), "Войдите в аккаунт", Toast.LENGTH_SHORT).show()

    private fun checkAuth() {
        uid = requireContext().getSharedPreferences("filter_prefs", Context.MODE_PRIVATE).getString("account", "none").takeIf { it != "none" }
        if (uid != null) loadUserData(uid!!) else { fav.clear(); cart.clear(); updateAdapter() }
    }

    private fun loadUserData(id: String) = db.collection("Accounts").document(id).get()
        .addOnSuccessListener { doc ->
            try {
                JSONObject(doc.getString("info") ?: "").let { json ->
                    fun parseIds(key: String) = (0 until JSONArray(json.optString(key, "[]")).length()).map { JSONArray(json.optString(key, "[]")).getString(it) }
                    fav.apply { clear(); addAll(parseIds("favorite")) }
                    cart.apply { clear(); addAll(parseIds("basket")) }
                    updateAdapter()
                }
            } catch (e: Exception) { }
        }

    private fun updateAdapter() = adapter.apply { setFavoriteIds(fav); setCartIds(cart) }

    private fun loadProducts() = db.collection("Products").get().addOnSuccessListener { docs ->
        all.clear()
        docs.forEach { doc -> doc.getString("info")?.let { all.add(Product.fromJson(it, doc.id)) } }
        filtered.apply { clear(); addAll(all) }
        adapter.updateProducts(filtered)
        updateAdapter()
    }

    private fun toggleFav(p: Product) = uid?.let { id ->
        db.collection("Accounts").document(id).get().addOnSuccessListener { doc ->
            try {
                JSONObject(doc.getString("info") ?: "").let { json ->
                    val arr = JSONArray(json.optString("favorite", "[]"))
                    val exists = (0 until arr.length()).any { arr.getString(it) == p.id }
                    JSONArray().apply {
                        (0 until arr.length()).forEach { i -> val id = arr.getString(i); if (id != p.id) put(id) }
                        if (!exists) put(p.id)
                    }.let { new -> json.put("favorite", new.toString()) }
                    db.collection("Accounts").document(id).update("info", json.toString())
                    if (exists) fav.remove(p.id) else fav.add(p.id)
                    adapter.setFavoriteIds(fav)
                    Toast.makeText(requireContext(), if (exists) "Удалено" else "Добавлено", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { }
        }
    } ?: authToast()

    private fun toggleCart(p: Product) = uid?.let { id ->
        db.collection("Accounts").document(id).get().addOnSuccessListener { doc ->
            try {
                JSONObject(doc.getString("info") ?: "").let { json ->
                    val arr = JSONArray(json.optString("basket", "[]"))
                    val exists = (0 until arr.length()).any { arr.getString(it) == p.id }
                    JSONArray().apply {
                        (0 until arr.length()).forEach { i -> val id = arr.getString(i); if (id != p.id) put(id) }
                        if (!exists) put(p.id)
                    }.let { new -> json.put("basket", new.toString()) }
                    db.collection("Accounts").document(id).update("info", json.toString())
                    if (exists) cart.remove(p.id) else cart.add(p.id)
                    adapter.setCartIds(cart)
                    Toast.makeText(requireContext(), if (exists) "Удалено из корзины" else "Добавлено в корзину", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { }
        }
    } ?: authToast()

    private fun setupCategories() {
        val cats = mapOf(
            R.id.categoryAlcohol to "Алкоголь", R.id.categoryReadyMeal to "Готовая еда", R.id.categoryDairy to "Молочный прилавок",
            R.id.categoryFruitsVeg to "Овощи и фрукты", R.id.categoryBakery to "Хлеб и выпечка", R.id.categoryGrocery to "Бакалея",
            R.id.categoryCanned to "Консервы", R.id.categoryMeatPoultry to "Птица, мясо", R.id.categoryFish to "Рыба, морепродукты",
            R.id.categoryFrozen to "Заморозка", R.id.categorySweets to "Сладости", R.id.categorySnacks to "Снеки",
            R.id.categoryTeaCoffee to "Чай, кофе, какао", R.id.categoryDrinks to "Вода и напитки", R.id.categoryKids to "Для детей",
            R.id.categoryPets to "Для животных", R.id.categoryHygiene to "Гигиена и уход", R.id.categoryHome to "Для дома и не только"
        )
        cats.forEach { (id, name) ->
            view?.findViewById<CardView>(id)?.setOnClickListener {
                filtered.apply { clear(); addAll(all.filter { it.category == name }) }
                adapter.updateProducts(filtered)
                b.popularTitle.text = name
                Toast.makeText(context, name, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}