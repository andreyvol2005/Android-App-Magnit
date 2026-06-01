package com.example.magnit.Secondary

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.magnit.Backend.Product
import com.example.magnit.MainActivity
import com.example.magnit.R
import com.example.magnit.databinding.ActivityOrderBinding
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class Order : AppCompatActivity() {
    private var _b: ActivityOrderBinding? = null
    private val b get() = _b!!
    private val db = FirebaseFirestore.getInstance()
    private var total = 0.0
    private var items = emptyList<String>()
    private val uid get() = getSharedPreferences("filter_prefs", Context.MODE_PRIVATE).getString("account", "none").takeIf { it != "none" }
    private val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _b = ActivityOrderBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.systemBars()).let { v.setPadding(it.left, it.top, it.right, it.bottom) }
            insets
        }

        total = intent.getDoubleExtra("total_amount", 0.0)
        items = intent.getStringArrayListExtra("basket_items") ?: emptyList()

        // Получаем любимые категории пользователя и применяем скидку
        val userId = getCurrentUserId()
        if (userId != null) {
            loadFavoriteCategoriesAndApplyDiscount(userId)
        } else {
            b.totalAmount.text = String.format("Сумма заказа: %.2f м", total)
        }

        Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 3) }
            .let { b.deliveryDate.text = "Ожидаемая дата доставки: ${df.format(it.time)}" }

        b.deliveryGroup.setOnCheckedChangeListener { _, id ->
            b.addressInput.visibility = if (id == R.id.deliveryHome) EditText.VISIBLE else EditText.GONE
        }

        b.backButton.setOnClickListener { finish() }
        b.checkoutButton.setOnClickListener { if (validate()) processPayment() }
    }

    private fun getCurrentUserId(): String? {
        return getSharedPreferences("filter_prefs", Context.MODE_PRIVATE)
            .getString("account", "none")
            .takeIf { it != "none" }
    }

    private fun loadFavoriteCategoriesAndApplyDiscount(userId: String) {
        db.collection("Accounts").document(userId).get()
            .addOnSuccessListener { doc ->
                doc.getString("info")?.let { info ->
                    try {
                        val json = JSONObject(info)
                        val favCategoriesStr = json.optString("favoriteCategories", "[]")

                        if (favCategoriesStr.isNotEmpty() && favCategoriesStr != "[]") {
                            val pattern = "\"([^\"]+)\"".toRegex()
                            val favoriteCategories = pattern.findAll(favCategoriesStr).map { it.groupValues[1] }.toList()

                            // Загружаем товары и применяем скидку
                            applyDiscountForFavoriteCategories(favoriteCategories)
                        } else {
                            b.totalAmount.text = String.format("Сумма заказа: %.2f м", total)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        b.totalAmount.text = String.format("Сумма заказа: %.2f м", total)
                    }
                } ?: run {
                    b.totalAmount.text = String.format("Сумма заказа: %.2f м", total)
                }
            }
    }

    private fun applyDiscountForFavoriteCategories(favoriteCategories: List<String>) {
        if (items.isEmpty()) {
            b.totalAmount.text = String.format("Сумма заказа: %.2f м", total)
            return
        }

        db.collection("Products").get()
            .addOnSuccessListener { documents ->
                var discountedTotal = 0.0
                var hasDiscount = false

                for (doc in documents) {
                    if (items.contains(doc.id)) {
                        doc.getString("info")?.let { info ->
                            val product = Product.fromJson(info, doc.id)
                            // Проверяем, входит ли товар в любимые категории
                            if (product.category in favoriteCategories) {
                                // Скидка 10% на товары из любимых категорий
                                discountedTotal += product.price * 0.9
                                hasDiscount = true
                            } else {
                                discountedTotal += product.price
                            }
                        }
                    }
                }

                val finalTotal = if (hasDiscount) discountedTotal else total
                b.totalAmount.text = String.format("Сумма заказа: %.2f м", finalTotal)

                // Сохраняем новую сумму для оплаты
                total = finalTotal
            }
            .addOnFailureListener {
                b.totalAmount.text = String.format("Сумма заказа: %.2f м", total)
            }
    }

    private fun validate(): Boolean {
        if (b.deliveryGroup.checkedRadioButtonId == -1) return toast("Выберите способ доставки")
        if (b.deliveryHome.isChecked && b.addressInput.text.toString().trim().isEmpty()) {
            b.addressInput.error = "Введите адрес"; return false
        }
        if (b.paymentGroup.checkedRadioButtonId == -1) return toast("Выберите способ оплаты")
        if (items.isEmpty()) return toast("Корзина пуста")
        return true
    }

    private fun toast(msg: String): Boolean { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); return false }

    private fun processPayment() {
        uid?.let { id ->
            db.collection("Accounts").document(id).get().addOnSuccessListener { doc ->
                try {
                    val json = JSONObject(doc.getString("info") ?: "")
                    val date = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date())

                    when {
                        b.paymentOnline.isChecked -> {
                            val balance = json.optString("balance", "0").toDoubleOrNull() ?: 0.0
                            if (balance >= total) {
                                json.put("balance", (balance - total).toString())
                                val wallet = json.optString("wallet", "[]")
                                json.put("wallet", if (wallet == "[]") "[\"$date\":-$total]" else "[\"$date\":-$total,${wallet.substring(1, wallet.length - 1)}]")
                                val bonus = (total / 500).toInt()
                                if (bonus > 0) json.put("bonuses", ((json.optString("bonuses", "0").toIntOrNull() ?: 0) + bonus).toString())
                                db.collection("Accounts").document(id).update("info", json.toString()).addOnSuccessListener { createOrder() }.addOnFailureListener { toast("Ошибка") }
                            } else toast("Недостаточно средств")
                        }
                        b.paymentCash.isChecked -> {
                            val bonus = (total / 500).toInt()
                            if (bonus > 0) json.put("bonuses", ((json.optString("bonuses", "0").toIntOrNull() ?: 0) + bonus).toString())
                            db.collection("Accounts").document(id).update("info", json.toString()).addOnSuccessListener { createOrder() }.addOnFailureListener { toast("Ошибка") }
                        }
                        b.paymentBonuces.isChecked -> {
                            val bonuses = json.optString("bonuses", "0").toIntOrNull() ?: 0
                            if (bonuses >= total.toInt()) {
                                json.put("bonuses", (bonuses - total.toInt()).toString())
                                db.collection("Accounts").document(id).update("info", json.toString()).addOnSuccessListener { createOrder() }.addOnFailureListener { toast("Ошибка") }
                            } else toast("Недостаточно бонусов")
                        }
                    }
                } catch (e: Exception) { toast("Ошибка") }
            }
        } ?: createOrder()
    }

    private fun createOrder() = uid?.let { id ->
        Calendar.getInstance().let { cal ->
            val order = JSONObject().apply {
                put("creation_date", df.format(Date()))
                put("delivery_date", df.format(cal.apply { add(Calendar.DAY_OF_MONTH, 3) }.time))
                put("products", JSONArray().apply { items.forEach { put(it) } })
                put("address", if (b.deliveryHome.isChecked) b.addressInput.text.toString() else "")
                put("total_amount", total)
                put("status", "processing")
            }
            db.collection("Orders").add(mapOf("info" to order.toString()))
                .addOnSuccessListener { doc -> addOrderToUser(id, doc.id) }
                .addOnFailureListener { toast("Ошибка создания заказа") }
        }
    }

    private fun addOrderToUser(uid: String, oid: String) {
        db.collection("Accounts").document(uid).get().addOnSuccessListener { doc ->
            try {
                JSONObject(doc.getString("info") ?: "").let { json ->
                    val orders = if (json.optString("order", "[]") != "[]")
                        JSONArray(json.optString("order", "[]")) else JSONArray()
                    orders.put(oid)
                    json.put("order", orders.toString()).put("basket", "[]")

                    db.collection("Accounts").document(uid).update("info", json.toString())
                        .addOnSuccessListener {
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                putExtra("open_fragment", "account")
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            })
                            finish()
                        }.addOnFailureListener { toast("Ошибка сохранения") }
                }
            } catch (e: Exception) { toast("Ошибка обработки") }
        }
    }

    override fun onDestroy() { super.onDestroy(); _b = null }
}