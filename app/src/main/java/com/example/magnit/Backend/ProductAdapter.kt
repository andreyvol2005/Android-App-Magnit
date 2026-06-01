package com.example.magnit

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.magnit.Backend.Product
import com.example.magnit.Secondary.ProductActivity
import com.example.magnit.databinding.ItemProductBinding
import com.squareup.picasso.Picasso

class ProductAdapter(private var products: List<Product>) :
    RecyclerView.Adapter<ProductAdapter.VH>() {

    private var fav = emptyList<String>()
    private var cart = emptyList<String>()
    private var onFav: ((Product) -> Unit)? = null
    private var onCart: ((Product) -> Unit)? = null

    class VH(val b: ItemProductBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
        ItemProductBinding.inflate(LayoutInflater.from(p.context), p, false)
    )

    override fun onBindViewHolder(h: VH, p: Int) = with(h.b) {
        val pr = products[p]
        productPrice.text = String.format("%.2f м", pr.price)
        productTitle.text = pr.name
        ratingValue.text = pr.rating.toString()
        reviewsCount.text = "(${pr.reviewsCount})"

        favoriteButton.setImageResource(if (fav.contains(pr.id)) R.drawable.favorite_ else R.drawable.favorite)
        basket.setImageResource(if (cart.contains(pr.id)) R.drawable.basket_ else R.drawable.basket)

        Picasso.get().load(pr.imageUrl).placeholder(R.drawable.image).error(R.drawable.image).into(productImage)

        root.setOnClickListener {
            it.context.startActivity(Intent(it.context, ProductActivity::class.java).putExtra("id", pr.id))
        }
        favoriteButton.setOnClickListener { onFav?.invoke(pr) }
        basket.setOnClickListener { onCart?.invoke(pr) }
    }

    override fun getItemCount() = products.size
    fun updateProducts(p: List<Product>) { products = p; notifyDataSetChanged() }
    fun setFavoriteIds(i: List<String>) { fav = i; notifyDataSetChanged() }
    fun setCartIds(i: List<String>) { cart = i; notifyDataSetChanged() }
    fun setOnFavoriteClickListener(l: (Product) -> Unit) { onFav = l }
    fun setOnCartClickListener(l: (Product) -> Unit) { onCart = l }
}