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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.magnit.Backend.BasketAdapter
import com.example.magnit.Backend.Product
import com.example.magnit.Secondary.Authorization
import com.example.magnit.Secondary.Order
import com.example.magnit.Secondary.ProductActivity
import com.example.magnit.databinding.FragmentBasketBinding
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject

class Basket : Fragment() {
    private var _binding: FragmentBasketBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: BasketAdapter
    private val items = mutableListOf<BasketAdapter.BasketItem>()
    private var loading = false
    private var allSelected = true

    private val userId get() = requireContext().getSharedPreferences("filter_prefs", Context.MODE_PRIVATE).getString("account", "none").takeIf { it != "none" }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, b: Bundle?): View = FragmentBasketBinding.inflate(inflater, c, false).also { _binding = it }.root

    override fun onViewCreated(v: View, b: Bundle?) {
        super.onViewCreated(v, b)
        adapter = BasketAdapter(items,
            { it -> startActivity(Intent(requireContext(), ProductActivity::class.java).putExtra("id", it.product.id)) },
            { toggleFavorite(it) },
            { removeFromCart(it) },
            { i, q -> i.quantity = q; updateTotal() },
            { _, _ -> updateTotal(); updateSelectState() }
        )
        binding.cartRecycler.apply { layoutManager = LinearLayoutManager(requireContext()); adapter = this@Basket.adapter }
        binding.selectAllButton.setOnClickListener {
            if (items.isEmpty()) return@setOnClickListener
            allSelected = !allSelected
            items.forEach { it.isChecked = allSelected }
            binding.selectAllButton.text = if (allSelected) "Снять выделение" else "Выделить всё"
            adapter.updateItems(items)
            updateTotal()
        }
        binding.checkoutButton.setOnClickListener {
            userId?.let {
                items.filter { it.isChecked }.takeIf { it.isNotEmpty() }?.let { selected ->
                    startActivity(Intent(requireContext(), Order::class.java).apply {
                        putExtra("total_amount", selected.sumOf { it.product.price * it.quantity })
                        putStringArrayListExtra("basket_items", ArrayList(selected.map { it.product.id }))
                    })
                } ?: Toast.makeText(requireContext(), "Выберите товары", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(requireContext(), "Войдите в аккаунт", Toast.LENGTH_SHORT).show()
                startActivity(Intent(requireContext(), Authorization::class.java))
            }
        }
        checkAuth()
    }

    override fun onResume() { super.onResume(); if (!loading) checkAuth() }

    private fun updateSelectState() {
        if (items.isEmpty()) return
        allSelected = items.all { it.isChecked }
        binding.selectAllButton.text = if (allSelected) "Снять выделение" else "Выделить всё"
    }

    private fun checkAuth() { if (!loading) userId?.let { loadCart(it) } ?: showGuest() }

    private fun loadCart(id: String) {
        loading = true
        db.collection("Accounts").document(id).get().addOnSuccessListener { doc ->
            try {
                JSONObject(doc.getString("info") ?: "").optString("basket", "[]").let { str ->
                    if (str != "[]" && str.isNotEmpty()) loadProducts(JSONArray(str)) else showEmpty()
                }
            } catch (e: Exception) { showEmpty() }
        }.addOnFailureListener { showEmpty() }
    }

    private fun loadProducts(arr: JSONArray) {
        items.clear()
        val ids = (0 until arr.length()).map { arr.getString(it) }
        if (ids.isEmpty()) return showEmpty()

        userId?.let { uid ->
            db.collection("Accounts").document(uid).get().addOnSuccessListener { userDoc ->
                val favs = try {
                    (0 until JSONArray(JSONObject(userDoc.getString("info") ?: "").optString("favorite", "[]")).length())
                        .map { JSONArray(JSONObject(userDoc.getString("info") ?: "").optString("favorite", "[]")).getString(it) }
                } catch (e: Exception) { emptyList() }

                db.collection("Products").get().addOnSuccessListener { docs ->
                    docs.forEach { doc ->
                        if (ids.contains(doc.id)) doc.getString("info")?.let {
                            items.add(BasketAdapter.BasketItem(Product.fromJson(it, doc.id), 1, true, favs.contains(doc.id)))
                        }
                    }
                    if (items.isNotEmpty()) showCart() else showEmpty()
                    loading = false
                }.addOnFailureListener { showEmpty(); loading = false }
            }.addOnFailureListener { showEmpty(); loading = false }
        }
    }

    private fun toggleFavorite(i: BasketAdapter.BasketItem) {
        userId?.let { uid ->
            db.collection("Accounts").document(uid).get().addOnSuccessListener { doc ->
                try {
                    JSONObject(doc.getString("info") ?: "").let { json ->
                        val fav = JSONArray(json.optString("favorite", "[]"))
                        val exists = (0 until fav.length()).any { fav.getString(it) == i.product.id }
                        val new = JSONArray().apply {
                            (0 until fav.length()).forEach { idx -> val id = fav.getString(idx); if (id != i.product.id) put(id) }
                            if (!exists) put(i.product.id)
                        }
                        json.put("favorite", new.toString())
                        db.collection("Accounts").document(uid).update("info", json.toString())
                        i.isFavorite = !exists
                        Toast.makeText(requireContext(), if (!exists) "Добавлено" else "Удалено", Toast.LENGTH_SHORT).show()
                        items.indexOf(i).takeIf { it != -1 }?.let { adapter.notifyItemChanged(it) }
                    }
                } catch (e: Exception) { Toast.makeText(requireContext(), "Ошибка", Toast.LENGTH_SHORT).show() }
            }
        } ?: startActivity(Intent(requireContext(), Authorization::class.java))
    }

    private fun removeFromCart(i: BasketAdapter.BasketItem) {
        userId?.let { uid ->
            db.collection("Accounts").document(uid).get().addOnSuccessListener { doc ->
                try {
                    JSONObject(doc.getString("info") ?: "").let { json ->
                        val basket = JSONArray(json.optString("basket", "[]"))
                        val new = JSONArray().apply {
                            (0 until basket.length()).forEach { idx -> val id = basket.getString(idx); if (id != i.product.id) put(id) }
                        }
                        json.put("basket", new.toString())
                        db.collection("Accounts").document(uid).update("info", json.toString())
                        items.remove(i)
                        adapter.updateItems(items)
                        if (items.isEmpty()) showEmpty() else { updateSelectState(); updateTotal() }
                        Toast.makeText(requireContext(), "Удалено", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { }
            }
        }
    }

    private fun updateTotal() {
        val userId = requireContext().getSharedPreferences("filter_prefs", Context.MODE_PRIVATE)
            .getString("account", "none").takeIf { it != "none" }

        val favCats = mutableListOf<String>()
        if (userId != null) {
            db.collection("Accounts").document(userId).get().addOnSuccessListener { doc ->
                doc.getString("info")?.let {
                    try {
                        JSONObject(it).optString("favoriteCategories", "[]").let { str ->
                            if (str.isNotEmpty() && str != "[]") {
                                favCats.addAll(Regex("\"([^\"]+)\"").findAll(str).map { it.groupValues[1] }.toList())
                            }
                        }
                    } catch (_: Exception) { }
                }
                calculateAndUpdate(favCats)
            }.addOnFailureListener { calculateAndUpdate(favCats) }
        } else {
            calculateAndUpdate(emptyList())
        }
    }

    private fun calculateAndUpdate(favCats: List<String>) {
        var orig = 0.0
        var disc = 0.0
        var cnt = 0
        var sel = 0
        var discAmt = 0.0
        var has = false

        for (item in items) {
            if (item.isChecked) {
                cnt += item.quantity
                sel++
                val total = item.product.price * item.quantity
                orig += total
                if (item.product.category in favCats) {
                    disc += total * 0.9
                    discAmt += total * 0.1
                    has = true
                } else {
                    disc += total
                }
            }
        }

        val final = if (has) disc else orig

        if (has) {
            binding.originalPriceLayout.visibility = View.VISIBLE
            binding.originalTotalPrice.text = String.format("%.2f ₽", orig)
            binding.discountLayout.visibility = View.VISIBLE
            binding.discountAmount.text = String.format("-%.2f ₽", discAmt)
        } else {
            binding.originalPriceLayout.visibility = View.GONE
            binding.discountLayout.visibility = View.GONE
        }

        binding.totalPrice.text = String.format("%.2f ₽", final)
        binding.itemsCount.text = when {
            cnt == 0 -> "0 товаров"
            cnt % 10 == 1 && cnt % 100 != 11 -> "$cnt товар"
            cnt % 10 in 2..4 && (cnt % 100 !in 12..14) -> "$cnt товара"
            else -> "$cnt товаров"
        } + (if (sel > 0) " (выбрано $sel)" else "")

        binding.checkoutButton.apply { alpha = if (cnt == 0) 0.5f else 1f; isEnabled = cnt > 0 }
    }

    private fun showGuest() = with(binding) {
        listOf(cartRecycler, bottomCheckoutCard, selectAllButton).forEach { it.visibility = View.GONE }
        emptyCartLayout.visibility = View.VISIBLE
        emptyCartTitle.text = "Войдите в аккаунт"; emptyCartMessage.text = "Чтобы увидеть товары"
    }

    private fun showEmpty() = with(binding) {
        listOf(cartRecycler, bottomCheckoutCard, selectAllButton).forEach { it.visibility = View.GONE }
        emptyCartLayout.visibility = View.VISIBLE
        emptyCartTitle.text = "Корзина пуста"; emptyCartMessage.text = "Добавьте товары из каталога"
        totalPrice.text = "0 ₽"; itemsCount.text = "0 товаров"
        checkoutButton.apply { alpha = 0.5f; isEnabled = false }
        loading = false
    }

    private fun showCart() = with(binding) {
        listOf(cartRecycler, bottomCheckoutCard, selectAllButton).forEach { it.visibility = View.VISIBLE }
        emptyCartLayout.visibility = View.GONE
        selectAllButton.text = "Снять выделение"
        allSelected = true
        adapter.updateItems(items)
        updateTotal()
    }

    fun refreshCart() { if (!loading) { items.clear(); checkAuth() } }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}