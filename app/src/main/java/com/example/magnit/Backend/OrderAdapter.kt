package com.example.magnit

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.magnit.databinding.ItemOrderBinding
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderAdapter(
    private var ids: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<OrderAdapter.VH>() {

    private val cache = mutableMapOf<String, JSONObject>()
    private val db = FirebaseFirestore.getInstance()

    // Флаг, чтобы не обновлять один заказ много раз
    private val updatingOrders = mutableSetOf<String>()

    class VH(val b: ItemOrderBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val id = ids[position]
        holder.itemView.setOnClickListener { onClick(id) }

        cache[id]?.let { display(holder, it, id) } ?: run {
            loadOrderAndCheckStatus(holder, id)
        }
    }

    private fun loadOrderAndCheckStatus(holder: VH, orderId: String) {
        db.collection("Orders").document(orderId).get()
            .addOnSuccessListener { doc ->
                doc.getString("info")?.let { info ->
                    try {
                        val order = JSONObject(info)
                        val currentStatus = order.optString("status", "processing")
                        val creationDateStr = order.optString("creation_date", "")

                        val minutesSinceCreation = getMinutesSinceCreation(creationDateStr)
                        val daysSinceCreation = minutesSinceCreation / (60 * 24)
                        Log.d("OrderAdapter", "Заказ $orderId, минут прошло: $minutesSinceCreation, дней: $daysSinceCreation, статус: $currentStatus")

                        var newStatus = currentStatus

                        // Логика обновления статусов
                        when {
                            currentStatus == "processing" && minutesSinceCreation >= 1 -> {
                                newStatus = "shipped"
                                Log.d("OrderAdapter", "Обновляем заказ $orderId: processing -> shipped (прошло $minutesSinceCreation минут)")
                            }
                            currentStatus == "shipped" && daysSinceCreation >= 3 -> {
                                newStatus = "delivered"
                                Log.d("OrderAdapter", "Обновляем заказ $orderId: shipped -> delivered (прошло $daysSinceCreation дней)")
                            }
                        }

                        if (newStatus != currentStatus && !updatingOrders.contains(orderId)) {
                            updatingOrders.add(orderId)
                            order.put("status", newStatus)
                            db.collection("Orders").document(orderId).update("info", order.toString())
                                .addOnSuccessListener {
                                    updatingOrders.remove(orderId)
                                    cache[orderId] = order
                                    display(holder, order, orderId)
                                }
                                .addOnFailureListener {
                                    updatingOrders.remove(orderId)
                                    cache[orderId] = order
                                    display(holder, order, orderId)
                                }
                            return@addOnSuccessListener
                        }

                        cache[orderId] = order
                        display(holder, order, orderId)

                    } catch (e: Exception) {
                        error(holder, orderId)
                    }
                } ?: error(holder, orderId)
            }
            .addOnFailureListener { error(holder, orderId) }
    }

    private fun getMinutesSinceCreation(creationDateStr: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
            val creationDate = format.parse(creationDateStr)
            val currentDate = Date()
            val diffMillis = currentDate.time - creationDate.time
            diffMillis / (1000 * 60)
        } catch (e: Exception) {
            0
        }
    }

    private fun updateOrderStatus(orderId: String, order: JSONObject, holder: VH) {
        // Если уже обновляем этот заказ, не делаем повторный запрос
        if (updatingOrders.contains(orderId)) return

        updatingOrders.add(orderId)

        order.put("status", "delivered")
        db.collection("Orders").document(orderId).update("info", order.toString())
            .addOnSuccessListener {
                updatingOrders.remove(orderId)
                cache[orderId] = order
                display(holder, order, orderId)
            }
            .addOnFailureListener {
                updatingOrders.remove(orderId)
                cache[orderId] = order
                display(holder, order, orderId)
            }
    }

    @SuppressLint("SetTextI18n")
    private fun display(holder: VH, order: JSONObject, id: String) = with(holder.b) {
        orderIdText.text = "Заказ #${id.takeLast(6)}"
        orderDate.text = "от ${order.optString("creation_date", "Неизвестно")}"
        deliveryDateText.text = "Доставка: ${order.optString("delivery_date", "Неизвестно")}"
        addressText.text = order.optString("address", "Пункт выдачи").ifEmpty { "Пункт выдачи" }
        totalAmount.text = String.format("%.2f ₽", order.optDouble("total_amount", 0.0))

        val cnt = order.optJSONArray("products")?.length() ?: 0
        itemsCountText.text = "$cnt ${getProductDeclension(cnt)}"

        val status = order.optString("status", "processing")
        val (statusText, colorRes) = getStatusInfo(status)
        orderStatus.text = statusText
        orderStatus.setTextColor(holder.itemView.context.getColor(colorRes))
    }

    private fun getProductDeclension(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "товар"
            count % 10 in 2..4 && (count % 100 !in 12..14) -> "товара"
            else -> "товаров"
        }
    }

    private fun getStatusInfo(status: String): Pair<String, Int> {
        return when (status) {
            "processing" -> "Обрабатывается" to android.R.color.holo_orange_dark
            "shipped" -> "В пути" to android.R.color.holo_blue_dark
            "delivered" -> "Доставлен" to android.R.color.holo_green_dark
            else -> status to android.R.color.darker_gray
        }
    }

    private fun error(holder: VH, id: String) = with(holder.b) {
        orderIdText.text = "Заказ #${id.takeLast(6)}"
        orderDate.text = "Дата неизвестна"
        orderStatus.text = "Ошибка загрузки"
        orderStatus.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
        totalAmount.text = "0 ₽"
        itemsCountText.text = "0 товаров"
    }

    override fun getItemCount() = ids.size

    fun updateOrders(new: List<String>) {
        ids = new
        cache.clear()
        notifyDataSetChanged()
    }
}