package com.example.magnit.Secondary

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.magnit.databinding.ActivityOrderInfoBinding
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderInfo : AppCompatActivity() {
    private var _b: ActivityOrderInfoBinding? = null
    private val b get() = _b!!
    private val db = FirebaseFirestore.getInstance()
    private var oid = ""
    private val uid get() = getSharedPreferences("filter_prefs", MODE_PRIVATE).getString("account", "none").takeIf { it != "none" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _b = ActivityOrderInfoBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.systemBars()).let { v.setPadding(it.left, it.top, it.right, it.bottom) }
            insets
        }

        oid = intent.getStringExtra("order_id") ?: ""
        b.backButton.setOnClickListener { finish() }
        b.cancelOrderButton.setOnClickListener { showCancelDialog() }
        loadOrder()
    }

    private fun loadOrder() = db.collection("Orders").document(oid).get()
        .addOnSuccessListener { doc ->
            doc.getString("info")?.let {
                try { display(JSONObject(it)) }
                catch (_: Exception) { toast("Ошибка"); finish() }
            } ?: run { toast("Ошибка"); finish() }
        }.addOnFailureListener { toast("Ошибка"); finish() }

    private fun display(j: JSONObject) {
        b.orderIdText.text = "Заказ #${oid.takeLast(6)}"
        b.orderDate.text = "Создан: ${j.optString("creation_date", "Неизвестно")}"
        b.deliveryDate.text = "Доставка: ${j.optString("delivery_date", "Неизвестно")}"
        b.addressText.text = j.optString("address", "Пункт выдачи").ifEmpty { "Пункт выдачи" }
        b.totalAmount.text = String.format("%.2f м", j.optDouble("total_amount", 0.0))

        val status = j.optString("status", "processing")
        val (text, color, enabled) = when (status) {
            "processing" -> Triple("Обрабатывается", android.R.color.holo_orange_dark, true)
            "shipped" -> Triple("Отправлен", android.R.color.holo_blue_dark, false)
            "delivered" -> Triple("Доставлен", android.R.color.holo_green_dark, false)
            "cancelled" -> Triple("Отменён", android.R.color.holo_red_dark, false)
            else -> Triple(status, android.R.color.darker_gray, false)
        }

        b.cancelOrderButton.apply { isEnabled = enabled; visibility = if (status != "cancelled") Button.VISIBLE else Button.GONE }
    }

    private fun showCancelDialog() = AlertDialog.Builder(this)
        .setTitle("Отмена заказа")
        .setMessage("Средства будут возвращены на кошелёк")
        .setPositiveButton("Отменить") { _, _ -> cancelOrder() }
        .setNegativeButton("Оставить", null)
        .show()

    private fun cancelOrder() = uid?.let { id ->
        db.collection("Orders").document(oid).get()
            .addOnSuccessListener { doc ->
                doc.getString("info")?.let { info ->
                    val order = JSONObject(info)
                    val amount = order.optDouble("total_amount", 0.0)

                    order.put("status", "cancelled")
                    db.collection("Orders").document(oid).update("info", order.toString())
                        .addOnSuccessListener { refund(id, amount) }
                        .addOnFailureListener { toast("Ошибка отмены") }
                }
            }
    } ?: toast("Ошибка")

    private fun refund(id: String, amt: Double) = db.collection("Accounts").document(id).get()
        .addOnSuccessListener { doc ->
            try {
                val acc = JSONObject(doc.getString("info") ?: "")
                acc.put("balance", (acc.optString("balance", "0").toDoubleOrNull() ?: 0.0) + amt)

                val wallet = acc.optString("wallet", "[]")
                val date = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date())
                val newWallet = if (wallet == "[]") "[ \"$date\":$amt ]" else "[ \"$date\":$amt,${wallet.substring(1, wallet.length - 1)} ]"
                acc.put("wallet", newWallet)

                db.collection("Accounts").document(id).update("info", acc.toString())
                    .addOnSuccessListener { removeOrder(id) }
                    .addOnFailureListener { toast("Ошибка возврата") }
            } catch (_: Exception) { toast("Ошибка") }
        }

    private fun removeOrder(userId: String) = db.collection("Accounts").document(userId).get()
        .addOnSuccessListener { doc ->
            try {
                val acc = JSONObject(doc.getString("info") ?: "")
                val orders = acc.optString("order", "[]").let { str ->
                    if (str.isNotEmpty() && str != "[]")
                        Regex("\"([^\"]+)\"").findAll(str).map { it.groupValues[1] }.toMutableList()
                    else mutableListOf()
                }
                orders.remove(oid)
                acc.put("order", if (orders.isEmpty()) "[]" else "[${orders.joinToString(",") { "\"$it\"" }}]")

                db.collection("Orders").document(oid).delete()
                    .addOnSuccessListener {
                        db.collection("Accounts").document(userId).update("info", acc.toString())
                            .addOnSuccessListener { toast("Заказ отменён"); finish() }
                            .addOnFailureListener { toast("Ошибка") }
                    }
                    .addOnFailureListener { toast("Ошибка") }
            } catch (_: Exception) { toast("Ошибка") }
        }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    override fun onDestroy() { super.onDestroy(); _b = null }
}