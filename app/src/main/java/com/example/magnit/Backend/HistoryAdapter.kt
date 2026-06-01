package com.example.magnit.Secondary

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.magnit.databinding.ItemHistoryBinding

class HistoryAdapter(
    private var items: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    data class HistoryItem(
        val id: String,
        val title: String,
        val amount: Double,
        val date: String,
        val type: String // "income" или "expense"
    )

    class HistoryViewHolder(val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            title.text = item.title
            date.text = item.date

            // Форматируем сумму с знаком +/-
            val sign = if (item.type == "income") "+" else "-"
            amount.text = "$sign${String.format("%.2f", item.amount)} м"

            // Цвет суммы
            amount.setTextColor(
                if (item.type == "income")
                    holder.itemView.context.getColor(android.R.color.holo_green_dark)
                else
                    holder.itemView.context.getColor(android.R.color.holo_red_dark)
            )

            root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}