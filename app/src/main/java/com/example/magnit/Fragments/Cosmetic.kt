package com.example.magnit.Fragments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
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
import com.example.magnit.databinding.FragmentCosmeticBinding
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject

class Cosmetic : Fragment() {

    private var _binding: FragmentCosmeticBinding? = null
    private val b get() = _binding!!

    private lateinit var adapter: ProductAdapter
    private val all = mutableListOf<Product>()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var prefs: SharedPreferences
    private var selected: TextView? = null
    private val fav = mutableListOf<String>()
    private val cart = mutableListOf<String>()
    private var uid: String? = null

    // Категории для косметики
    private val cats = listOf("Все", "Макияж", "Уход за лицом", "Уход за телом", "Парфюмерия",
        "Волосы", "Для дома", "Для питомцев", "Детям")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCosmeticBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("filter_prefs", Context.MODE_PRIVATE)

        // Адаптер
        adapter = ProductAdapter(all).apply {
            setOnFavoriteClickListener { product ->
                if (uid == null) authThen() else toggleFav(product)
            }
            setOnCartClickListener { product ->
                if (uid == null) authThen() else toggleCart(product)
            }
        }
        b.popularRecycler.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@Cosmetic.adapter
        }

        // Кнопка назад в Магнит
        b.magnitButton.setOnClickListener {
            startActivity(Intent(requireContext(), MainActivity::class.java).apply {
                putExtra("open_fragment", "home")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }

        // Кнопка фильтра
        b.searchCard.findViewById<ImageView>(R.id.filterIcon)?.setOnClickListener {
            startActivity(Intent(requireContext(), Filter::class.java))
        }

        // Инициализация
        setupCategories()
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
        uid = requireContext().getSharedPreferences("filter_prefs", Context.MODE_PRIVATE)
            .getString("account", "none")
            .takeIf { it != "none" }

        if (uid != null) {
            loadUserData(uid!!)
        } else {
            fav.clear()
            cart.clear()
            adapter.setFavoriteIds(fav)
            adapter.setCartIds(cart)
        }
    }

    private fun loadUserData(id: String) {
        db.collection("Accounts").document(id).get()
            .addOnSuccessListener { doc ->
                try {
                    val json = JSONObject(doc.getString("info") ?: "")

                    fun parse(key: String): List<String> {
                        val arr = JSONArray(json.optString(key, "[]"))
                        return (0 until arr.length()).map { arr.getString(it) }
                    }

                    fav.clear()
                    fav.addAll(parse("favorite"))

                    cart.clear()
                    cart.addAll(parse("basket"))

                    adapter.setFavoriteIds(fav)
                    adapter.setCartIds(cart)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
    }

    private fun toggleFav(product: Product) {
        val id = uid ?: return authThen()

        db.collection("Accounts").document(id).get()
            .addOnSuccessListener { doc ->
                try {
                    val json = JSONObject(doc.getString("info") ?: "")
                    val arr = JSONArray(json.optString("favorite", "[]"))

                    val exists = (0 until arr.length()).any { arr.getString(it) == product.id }

                    val new = JSONArray().apply {
                        for (i in 0 until arr.length()) {
                            val pid = arr.getString(i)
                            if (pid != product.id) put(pid)
                        }
                        if (!exists) put(product.id)
                    }

                    json.put("favorite", new.toString())
                    db.collection("Accounts").document(id).update("info", json.toString())

                    if (exists) fav.remove(product.id) else fav.add(product.id)
                    adapter.setFavoriteIds(fav)

                    Toast.makeText(requireContext(), if (exists) "Удалено из избранного" else "Добавлено в избранное", Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
    }

    private fun toggleCart(product: Product) {
        val id = uid ?: return authThen()

        db.collection("Accounts").document(id).get()
            .addOnSuccessListener { doc ->
                try {
                    val json = JSONObject(doc.getString("info") ?: "")
                    val arr = JSONArray(json.optString("basket", "[]"))

                    val exists = (0 until arr.length()).any { arr.getString(it) == product.id }

                    val new = JSONArray().apply {
                        for (i in 0 until arr.length()) {
                            val pid = arr.getString(i)
                            if (pid != product.id) put(pid)
                        }
                        if (!exists) put(product.id)
                    }

                    json.put("basket", new.toString())
                    db.collection("Accounts").document(id).update("info", json.toString())

                    if (exists) cart.remove(product.id) else cart.add(product.id)
                    adapter.setCartIds(cart)

                    Toast.makeText(requireContext(), if (exists) "Удалено из корзины" else "Добавлено в корзину", Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
    }

    private fun loadProducts() {
        db.collection("Products").get()
            .addOnSuccessListener { docs ->
                all.clear()
                docs.forEach { doc ->
                    doc.getString("info")?.let {
                        val product = Product.fromJson(it, doc.id)
                        // Фильтруем только товары из косметических категорий
                        if (product.category in cats) {
                            all.add(product)
                        }
                    }
                }
                applyFilters()
            }
    }

    private fun applyFilters() {
        if (!prefs.getBoolean("is_filter_active", false)) {
            adapter.updateProducts(all)
            updateSelected("Все")
            return
        }

        var filtered = all

        prefs.getString("selected_category", "Все")?.takeIf { it != "Все" }?.let { cat ->
            filtered = filtered.filter { it.category == cat } as MutableList<Product>
            updateSelected(cat)
        }

        filtered = filtered.filter { it.price <= prefs.getInt("max_price", 10000) } as MutableList<Product>

        prefs.getFloat("min_rating", 0f).takeIf { it > 0 }?.let { rating ->
            filtered = filtered.filter { it.rating >= rating } as MutableList<Product>
        }

        prefs.getInt("min_reviews", 0).takeIf { it > 0 }?.let { reviews ->
            filtered = filtered.filter { it.reviewsCount >= reviews } as MutableList<Product>
        }

        adapter.updateProducts(filtered)
    }

    private fun updateSelected(cat: String) {
        for (i in 0 until b.categoriesContainer.childCount) {
            (b.categoriesContainer.getChildAt(i) as? TextView)?.takeIf { it.text.toString() == cat }?.let { select(it) }
        }
    }

    private fun setupCategories() {
        cats.forEachIndexed { index, name ->
            TextView(requireContext()).apply {
                text = name
                textSize = 16f
                setPadding(24, 12, 24, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 12 }

                setOnClickListener {
                    select(this)
                    adapter.updateProducts(
                        if (name == "Все") all else all.filter { it.category == name }
                    )
                    prefs.edit().putString("selected_category", name).apply()
                }

                if (index == 0) select(this) else deselect(this)
                b.categoriesContainer.addView(this)
            }
        }
    }

    private fun select(view: TextView) {
        selected?.let { deselect(it) }
        view.background = ContextCompat.getDrawable(requireContext(), R.drawable.category_background_selected)
        view.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        selected = view
    }

    private fun deselect(view: TextView) {
        view.background = ContextCompat.getDrawable(requireContext(), R.drawable.category_background)
        view.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}