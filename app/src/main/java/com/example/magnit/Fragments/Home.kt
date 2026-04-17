package com.example.magnit.Fragments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.magnit.*
import com.example.magnit.Backend.Product
import com.example.magnit.Secondary.Authorization
import com.example.magnit.Secondary.Filter
import com.example.magnit.databinding.FragmentHomeBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.MainScope
import org.json.JSONArray
import org.json.JSONObject

class Home : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val b get() = _binding!!
    private lateinit var adapter: ProductAdapter
    private val all = mutableListOf<Product>()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var prefs: SharedPreferences
    private var selected: TextView? = null
    private val fav = mutableListOf<String>()
    private val cart = mutableListOf<String>()
    private var uid: String? = null

    private val cats = listOf("Все", "Алкоголь", "Готовая еда", "Молочный прилавок", "Овощи и фрукты",
        "Хлеб и выпечка", "Бакалея", "Консервы", "Птица, мясо", "Рыба, морепродукты", "Заморозка",
        "Сладости", "Снеки", "Чай, кофе, какао", "Вода и напитки", "Для детей", "Для животных",
        "Гигиена и уход", "Для дома и не только")

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        FragmentHomeBinding.inflate(inflater, c, false).also { _binding = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        prefs = requireContext().getSharedPreferences("filter_prefs", Context.MODE_PRIVATE)

        adapter = ProductAdapter(all).apply {
            setOnFavoriteClickListener { p -> if (uid == null) authThen() else toggleFav(p) }
            setOnCartClickListener { p -> if (uid == null) authThen() else toggleCart(p) }
        }
        b.popularRecycler.apply { layoutManager = GridLayoutManager(requireContext(), 2); adapter = this@Home.adapter }

        setupCategories()
        b.searchCard.findViewById<ImageView>(R.id.filterIcon)?.setOnClickListener { startActivity(Intent(requireContext(), Filter::class.java)) }

        b.cosmeticButton.findViewById<CardView>(R.id.cosmeticButton)?.setOnClickListener {
            startActivity(Intent(requireContext(), MainActivity::class.java).apply {
                putExtra("open_fragment", "cosmetic")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })}

        checkAuth()
        loadProducts()
    }

    override fun onResume() {
        super.onResume()
        checkAuth()
        if (::adapter.isInitialized && all.isNotEmpty()) applyFilters()
    }

    private fun authThen() {
        Toast.makeText(requireContext(), "Войдите в аккаунт", Toast.LENGTH_SHORT).show()
        startActivity(Intent(requireContext(), Authorization::class.java))
    }

    private fun checkAuth() {
        uid = requireContext().getSharedPreferences("filter_prefs", Context.MODE_PRIVATE).getString("account", "none").takeIf { it != "none" }
        if (uid != null) loadUserData(uid!!) else { fav.clear(); cart.clear(); adapter.setFavoriteIds(fav); adapter.setCartIds(cart) }
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
                    Toast.makeText(requireContext(), if (exists) "Удалено" else "Добавлено", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { }
        }
    } ?: authThen()

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
                    Toast.makeText(requireContext(), if (exists) "Удалено из корзины" else "Добавлено", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { }
        }
    } ?: authThen()

    private fun loadProducts() = db.collection("Products").get().addOnSuccessListener { docs ->
        all.clear()
        docs.forEach { doc -> doc.getString("info")?.let { all.add(Product.fromJson(it, doc.id)) } }
        applyFilters()
    }

    private fun applyFilters() {
        if (!prefs.getBoolean("is_filter_active", false)) {
            adapter.updateProducts(all)
            updateSelected("Все")
            return
        }

        var filtered = all
        prefs.getString("selected_category", "Все")?.takeIf { it != "Все" }?.let { cat ->
            filtered = filtered.filter { it.category == cat }.toMutableList()
            updateSelected(cat)
        }

        filtered = filtered.filter { it.price <= prefs.getInt("max_price", 10000) }.toMutableList()
        prefs.getFloat("min_rating", 0f).takeIf { it > 0 }?.let { rating ->
            filtered = filtered.filter { it.rating >= rating }.toMutableList()
        }
        prefs.getInt("min_reviews", 0).takeIf { it > 0 }?.let { reviews ->
            filtered = filtered.filter { it.reviewsCount >= reviews }.toMutableList()
        }

        adapter.updateProducts(filtered)
    }

    private fun updateSelected(cat: String) {
        for (i in 0 until b.categoriesContainer.childCount) {
            (b.categoriesContainer.getChildAt(i) as? TextView)?.takeIf { it.text.toString() == cat }?.let { select(it) }
        }
    }

    private fun setupCategories() {
        cats.forEachIndexed { i, name ->
            TextView(requireContext()).apply {
                text = name; textSize = 16f; setPadding(24, 12, 24, 12)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = 12 }
                setOnClickListener {
                    select(this)
                    adapter.updateProducts(if (name == "Все") all else all.filter { it.category == name })
                    prefs.edit().putString("selected_category", name).apply()
                }
                if (i == 0) select(this) else deselect(this)
                b.categoriesContainer.addView(this)
            }
        }
    }

    private fun select(v: TextView) {
        selected?.let { deselect(it) }
        v.background = ContextCompat.getDrawable(requireContext(), R.drawable.category_background_selected)
        v.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        selected = v
    }

    private fun deselect(v: TextView) {
        v.background = ContextCompat.getDrawable(requireContext(), R.drawable.category_background)
        v.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}