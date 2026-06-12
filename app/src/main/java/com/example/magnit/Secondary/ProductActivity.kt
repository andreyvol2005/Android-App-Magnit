package com.example.magnit.Secondary

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.magnit.Backend.Product
import com.example.magnit.MainActivity
import com.example.magnit.ProductAdapter
import com.example.magnit.R
import com.example.magnit.databinding.ActivityProductBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import org.json.JSONArray
import org.json.JSONObject

class ProductActivity : AppCompatActivity() {
    private var _b: ActivityProductBinding? = null
    private val b get() = _b!!
    private val db = FirebaseFirestore.getInstance()
    private var pid = ""
    private var p: Product? = null
    private var fav = false
    private var cart = false
    private val uid get() = getSharedPreferences("filter_prefs", MODE_PRIVATE).getString("account", "none").takeIf { it != "none" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _b = ActivityProductBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.systemBars()).let { v.setPadding(it.left, it.top, it.right, it.bottom) }
            insets
        }

        // Устанавливаем пустой адаптер для RecyclerView сразу
        b.recommendedRecycler.apply {
            layoutManager = GridLayoutManager(this@ProductActivity, 2)
            adapter = ProductAdapter(emptyList())
        }

        pid = intent.getStringExtra("id") ?: ""
        setupButtons()
        db.collection("Products").document(pid).get()
            .addOnSuccessListener { doc ->
                doc.getString("info")?.let {
                    p = Product.fromJson(it, doc.id)
                    with(b) {
                        productPrice.text = String.format("%.2f ₽", p!!.price)
                        productName.text = p!!.name
                        ratingValue.text = p!!.rating.toString()
                        reviewsCount.text = "${p!!.reviewsCount}"
                        Picasso.get().load(p!!.imageUrl).placeholder(R.drawable.image).error(R.drawable.image).into(productImage)
                    }
                    uid?.let { checkFav(); checkCart() }

                    db.collection("Products").get().addOnSuccessListener { docs ->
                        val recommended = docs.documents
                            .mapNotNull { it.getString("info")?.let { info -> Product.fromJson(info, it.id) } }
                            .filter { it.id != pid && it.category == p!!.category }
                            .take(4)
                        b.recommendedRecycler.adapter = ProductAdapter(recommended)
                    }
                }
            }
    }

    private fun setupButtons() = with(b) {
        backButton.setOnClickListener { finish() }
        shareButton.setOnClickListener { share() }
        favoriteButton.setOnClickListener { toggleFav() }
        addToCartButton.setOnClickListener { handleCart() }
    }

    private fun share() = AlertDialog.Builder(this)
        .setTitle("Поделиться")
        .setMessage("Товар: ${b.productName.text}\nЦена: ${b.productPrice.text}\nID: $pid")
        .setPositiveButton("Копировать") { _, _ ->
            (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(android.content.ClipData.newPlainText("Product", "Товар: ${b.productName.text}\nЦена: ${b.productPrice.text}"))
            toast("Скопировано")
        }.setNegativeButton("Отмена", null).show()

    private fun checkFav() = uid?.let { id ->
        db.collection("Accounts").document(id).get().addOnSuccessListener { doc ->
            try {
                JSONObject(doc.getString("info") ?: "").optString("favorite", "[]").let { str ->
                    if (str.isNotEmpty() && str != "[]") {
                        fav = (0 until JSONArray(str).length()).any { JSONArray(str).getString(it) == pid }
                    }
                }
                b.favoriteButton.setImageResource(if (fav) R.drawable.favorite_ else R.drawable.favorite)
            } catch (_: Exception) { }
        }
    }

    private fun checkCart() = uid?.let { id ->
        db.collection("Accounts").document(id).get().addOnSuccessListener { doc ->
            try {
                JSONObject(doc.getString("info") ?: "").optString("basket", "[]").let { str ->
                    if (str.isNotEmpty() && str != "[]") {
                        cart = (0 until JSONArray(str).length()).any { JSONArray(str).getString(it) == pid }
                    }
                }
                updateCartBtn()
            } catch (_: Exception) { }
        }
    }

    private fun updateCartBtn() = with(b.addToCartButton) {
        text = if (cart) "Перейти в корзину" else "Добавить в корзину"
        setBackgroundColor(getColor(if (cart) R.color.light_red else R.color.red))
    }

    private fun toggleFav() = uid?.let { id ->
        db.collection("Accounts").document(id).get().addOnSuccessListener { doc ->
            try {
                JSONObject(doc.getString("info") ?: "").let { json ->
                    val arr = JSONArray(json.optString("favorite", "[]"))
                    val exists = (0 until arr.length()).any { arr.getString(it) == pid }
                    val new = JSONArray().apply {
                        (0 until arr.length()).forEach { i -> val x = arr.getString(i); if (x != pid) put(x) }
                        if (!exists) put(pid)
                    }
                    json.put("favorite", new.toString())
                    db.collection("Accounts").document(id).update("info", json.toString())
                    fav = !exists
                    b.favoriteButton.setImageResource(if (fav) R.drawable.favorite_ else R.drawable.favorite)
                    toast(if (fav) "Добавлено" else "Удалено")
                }
            } catch (_: Exception) { }
        }
    } ?: run { toast("Войдите в аккаунт"); startActivity(Intent(this, Authorization::class.java)) }

    private fun handleCart() = uid?.let { id ->
        if (cart) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("open_fragment", "basket")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        } else {
            db.collection("Accounts").document(id).get().addOnSuccessListener { doc ->
                try {
                    JSONObject(doc.getString("info") ?: "").let { json ->
                        val arr = JSONArray(json.optString("basket", "[]"))
                        val new = JSONArray().apply {
                            (0 until arr.length()).forEach { i -> put(arr.getString(i)) }
                            put(pid)
                        }
                        json.put("basket", new.toString())
                        db.collection("Accounts").document(id).update("info", json.toString())
                        cart = true
                        updateCartBtn()
                        toast("Добавлено в корзину")
                    }
                } catch (_: Exception) { }
            }
        }
    } ?: run { toast("Войдите в аккаунт"); startActivity(Intent(this, Authorization::class.java)) }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    override fun onDestroy() { super.onDestroy(); _b = null }
}