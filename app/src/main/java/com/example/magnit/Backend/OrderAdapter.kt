package com.example.magnit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.magnit.databinding.ItemOrderBinding
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class OrderAdapter(
    private var ids: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<OrderAdapter.VH>() {

    private val cache = mutableMapOf<String, JSONObject>()
    private val db = FirebaseFirestore.getInstance()

    class VH(val b: ItemOrderBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
        ItemOrderBinding.inflate(LayoutInflater.from(p.context), p, false)
    )

    override fun onBindViewHolder(h: VH, p: Int) {
        val id = ids[p]
        h.itemView.setOnClickListener { onClick(id) }

        cache[id]?.let { display(h, it, id) } ?: run {
            db.collection("Orders").document(id).get()
                .addOnSuccessListener { doc ->
                    doc.getString("info")?.let { info ->
                        try { JSONObject(info).also { cache[id] = it; display(h, it, id) } }
                        catch (e: Exception) { error(h, id) }
                    } ?: error(h, id)
                }.addOnFailureListener { error(h, id) }
        }
    }

    private fun display(h: VH, j: JSONObject, id: String) = with(h.b) {
        orderIdText.text = "Заказ #${id.takeLast(6)}"
        orderDate.text = "от ${j.optString("creation_date", "Неизвестно")}"
        deliveryDateText.text = "Доставка: ${j.optString("delivery_date", "Неизвестно")}"
        addressText.text = j.optString("address", "Пункт выдачи").ifEmpty { "Пункт выдачи" }
        totalAmount.text = String.format("%.2f ₽", j.optDouble("total_amount", 0.0))

        val cnt = j.optJSONArray("products")?.length() ?: 0
        itemsCountText.text = "$cnt ${when {
            cnt % 10 == 1 && cnt % 100 != 11 -> "товар"
            cnt % 10 in 2..4 && (cnt % 100 !in 12..14) -> "товара"
            else -> "товаров"
        }}"

        val (status, color) = when (j.optString("status", "processing")) {
            "processing" -> "Обрабатывается" to android.R.color.holo_orange_dark
            "shipped" -> "Отправлен" to android.R.color.holo_blue_dark
            "delivered" -> "Доставлен" to android.R.color.holo_green_dark
            "cancelled" -> "Отменён" to android.R.color.holo_red_dark
            else -> j.optString("status", "processing") to android.R.color.darker_gray
        }
        orderStatus.text = status
        orderStatus.setTextColor(root.context.getColor(color))
    }

    private fun error(h: VH, id: String) = with(h.b) {
        orderIdText.text = "Заказ #${id.takeLast(6)}"
        orderDate.text = "Дата неизвестна"
        orderStatus.text = "Ошибка загрузки"
        orderStatus.setTextColor(root.context.getColor(android.R.color.holo_red_dark))
        totalAmount.text = "0 ₽"
        itemsCountText.text = "0 товаров"
    }

    override fun getItemCount() = ids.size
    fun updateOrders(new: List<String>) { ids = new; cache.clear(); notifyDataSetChanged() }
}