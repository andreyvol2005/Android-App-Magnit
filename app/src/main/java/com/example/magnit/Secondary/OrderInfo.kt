package com.example.magnit.Secondary

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.magnit.databinding.ActivityOrderInfoBinding
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
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
            "shipped" -> Triple("В пути", android.R.color.holo_blue_dark, false)
            "delivered" -> Triple("Доставлен", android.R.color.holo_green_dark, false)
            else -> Triple(status, android.R.color.darker_gray, false)
        }
        b.orderStatus.apply {
            this.text = text
            setTextColor(getColor(color))
        }
    }

    private fun showCancelDialog() = AlertDialog.Builder(this)
        .setTitle("Отмена заказа")
        .setMessage("Заказ будет отменён без возвратно!")
        .setPositiveButton("Отменить") { _, _ -> cancelOrder() }
        .setNegativeButton("Оставить", null)
        .show()

    private fun cancelOrder() {
        val userId = uid!!
        db.collection("Orders").document(oid).get()
            .addOnSuccessListener { doc ->
                val order = JSONObject(doc.getString("info")!!)
                val paymentCash = order.optBoolean("paymentCash", true)
                val amount = order.optDouble("total_amount", 0.0)

                if (!paymentCash) {
                    Log.d("CancelOrder", "=== НАЧАЛО ВОЗВРАТА ДЕНЕГ ===")
                    Log.d("CancelOrder", "Сумма к возврату: $amount")

                    db.collection("Accounts").document(userId).get()
                        .addOnSuccessListener { accDoc ->
                            try {
                                val account = JSONObject(accDoc.getString("info")!!)

                                val currentBalance = account.optString("balance", "0").toDoubleOrNull() ?: 0.0
                                val newBalance = currentBalance + amount
                                account.put("balance", newBalance.toString())
                                Log.d("CancelOrder", "Баланс: $currentBalance -> $newBalance")

                                val wallet = account.optString("wallet", "[]")
                                val date = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date())
                                val newWallet = if (wallet == "[]" || wallet.isEmpty()) {
                                    "[\"$date\":$amount]"
                                } else {
                                    val withoutEndBracket = wallet.substring(0, wallet.length - 1)
                                    "$withoutEndBracket, \"$date\":$amount]"
                                }
                                account.put("wallet", newWallet)
                                Log.d("CancelOrder", "Wallet обновлён")

                                val ordersStr = account.optString("order", "[]")
                                val newOrders = if (ordersStr != "[]") {
                                    val list = Regex("\"([^\"]+)\"").findAll(ordersStr).map { it.groupValues[1] }.toMutableList()
                                    list.remove(oid)
                                    if (list.isEmpty()) "[]" else "[${list.joinToString(",") { "\"$it\"" }}]"
                                } else "[]"
                                account.put("order", newOrders)
                                Log.d("CancelOrder", "Заказ удалён из списка")

                                db.collection("Accounts").document(userId).update("info", account.toString())
                                    .addOnSuccessListener {
                                        Log.d("CancelOrder", "=== ДАННЫЕ УСПЕШНО ОБНОВЛЕНЫ ===")

                                        db.collection("Orders").document(oid).delete()
                                            .addOnSuccessListener {
                                                Log.d("CancelOrder", "Заказ полностью удалён")
                                                toast("Заказ отменён")
                                                finish()
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("CancelOrder", "Ошибка удаления заказа: ${e.message}")
                                                toast("Заказ отменён, но не удалён")
                                                finish()
                                            }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("CancelOrder", "Ошибка обновления: ${e.message}")
                                    }
                            } catch (e: Exception) {
                                Log.e("CancelOrder", "ИСКЛЮЧЕНИЕ: ${e.message}")
                            }
                        }
                } else {
                    db.collection("Accounts").document(userId).get()
                        .addOnSuccessListener { accDoc ->
                            val account = JSONObject(accDoc.getString("info")!!)
                            val ordersStr = account.optString("order", "[]")
                            val newOrders = if (ordersStr != "[]") {
                                val list = Regex("\"([^\"]+)\"").findAll(ordersStr).map { it.groupValues[1] }.toMutableList()
                                list.remove(oid)
                                if (list.isEmpty()) "[]" else "[${list.joinToString(",") { "\"$it\"" }}]"
                            } else "[]"
                            account.put("order", newOrders)

                            db.collection("Accounts").document(userId).update("info", account.toString())
                                .addOnSuccessListener {
                                    db.collection("Orders").document(oid).delete()
                                        .addOnSuccessListener {
                                            toast("Заказ отменён")
                                            finish()
                                        }
                                }
                        }
                }
            }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    override fun onDestroy() { super.onDestroy(); _b = null }
}