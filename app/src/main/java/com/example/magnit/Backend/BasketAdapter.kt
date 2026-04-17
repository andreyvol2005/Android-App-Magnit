package com.example.magnit.Backend

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.magnit.R
import com.example.magnit.databinding.ItemBasketBinding
import com.squareup.picasso.Picasso

class BasketAdapter(
    public var items: List<BasketItem>,
    private val onItemClick: (BasketItem) -> Unit,
    private val onFavoriteClick: (BasketItem) -> Unit,
    private val onDeleteClick: (BasketItem) -> Unit,
    private val onQuantityChange: (BasketItem, Int) -> Unit,
    private val onCheckChange: (BasketItem, Boolean) -> Unit
) : RecyclerView.Adapter<BasketAdapter.VH>() {

    data class BasketItem(
        val product: Product,
        var quantity: Int = 1,
        var isChecked: Boolean = false,
        var isFavorite: Boolean = false
    )

    class VH(val b: ItemBasketBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
        ItemBasketBinding.inflate(LayoutInflater.from(p.context), p, false)
    )

    override fun onBindViewHolder(h: VH, p: Int) = with(h.b) {
        val i = items[p]
        val pr = i.product

        Picasso.get().load(pr.imageUrl).placeholder(R.drawable.image).error(R.drawable.image).into(productImage)
        productName.text = pr.name
        productPrice.text = String.format("%.2f ₽", pr.price)
        quantityText.text = i.quantity.toString()
        itemCheckbox.isChecked = i.isChecked
        favoriteButton.setImageResource(if (i.isFavorite) R.drawable.favorite_ else R.drawable.favorite)

        itemCheckbox.setOnCheckedChangeListener { _, c -> i.isChecked = c; onCheckChange(i, c) }
        favoriteButton.setOnClickListener {
            i.isFavorite = !i.isFavorite
            favoriteButton.setImageResource(if (i.isFavorite) R.drawable.favorite_ else R.drawable.favorite)
            onFavoriteClick(i)
        }
        deleteButton.setOnClickListener { onDeleteClick(i) }
        increaseButton.setOnClickListener {
            i.quantity++
            quantityText.text = i.quantity.toString()
            onQuantityChange(i, i.quantity)
        }
        decreaseButton.setOnClickListener {
            if (i.quantity > 1) {
                i.quantity--
                quantityText.text = i.quantity.toString()
                onQuantityChange(i, i.quantity)
            }
        }
        root.setOnClickListener { onItemClick(i) }
    }

    override fun getItemCount() = items.size
    fun updateItems(new: List<BasketItem>) { items = new; notifyDataSetChanged() }
}