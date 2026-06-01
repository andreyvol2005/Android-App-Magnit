package com.example.magnit.Fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.magnit.Secondary.Authorization
import com.example.magnit.R
import com.example.magnit.Secondary.Registration
import com.example.magnit.Secondary.Settings
import com.example.magnit.Backend.User
import com.example.magnit.OrderAdapter
import com.example.magnit.Secondary.FavoriteCategories
import com.example.magnit.Secondary.History
import com.example.magnit.Secondary.OrderInfo
import com.example.magnit.databinding.FragmentAccountBinding
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject

class Account : Fragment() {
    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private lateinit var ordersAdapter: OrderAdapter
    private val orderIds = mutableListOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        checkAuth()
    }

    override fun onResume() {
        super.onResume()
        checkAuth()
    }

    private fun setupClickListeners() = with(binding) {
        loginButton.setOnClickListener { startActivity(Intent(requireContext(), Authorization::class.java)) }
        registerButton.setOnClickListener { startActivity(Intent(requireContext(), Registration::class.java)) }
        settingsIcon.setOnClickListener {
            if (requireContext().getSharedPreferences("filter_prefs", Context.MODE_PRIVATE).getString("account", "none") != "none") {
                startActivity(Intent(requireContext(), Settings::class.java))
            } else Toast.makeText(requireContext(), "Сначала войдите в аккаунт", Toast.LENGTH_SHORT).show()
        }
        favouritesLink.setOnClickListener { parentFragmentManager.beginTransaction().replace(R.id.fragment_container, Favourites()).commit() }
        purchasesLink.setOnClickListener { parentFragmentManager.beginTransaction().replace(R.id.fragment_container, Basket()).commit() }
        historyButton.setOnClickListener { startActivity(Intent(requireContext(), History::class.java)) }
        selectCategoriesButton.setOnClickListener { startActivity(Intent(requireContext(), FavoriteCategories::class.java)) }

        ordersAdapter = OrderAdapter(orderIds) { orderId ->
            startActivity(Intent(requireContext(), OrderInfo::class.java).putExtra("order_id", orderId))
        }
        ordersRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ordersAdapter
        }
    }

    private fun checkAuth() {
        val accountId = requireContext().getSharedPreferences("filter_prefs", Context.MODE_PRIVATE).getString("account", "none")
        if (accountId == "none") showGuestView() else loadUserData(accountId!!)
    }

    private fun loadUserData(userId: String) = db.collection("Accounts").document(userId).get()
        .addOnSuccessListener { doc ->
            doc.getString("info")?.let { info ->
                try {
                    JSONObject(info).let { json ->
                        User.fromJson(info, userId).also { user ->
                            binding.user.text = user.login

                            // Отображение заказов
                            val ordersStr = json.optString("order", "[]")
                            if (ordersStr.isNotEmpty() && ordersStr != "[]") {
                                val ordersArray = JSONArray(ordersStr)
                                orderIds.clear()
                                for (i in 0 until ordersArray.length()) {
                                    orderIds.add(ordersArray.getString(i))
                                }
                                ordersAdapter.updateOrders(orderIds)
                                binding.ordersRecycler.visibility = View.VISIBLE
                                binding.emptyOrdersLayout.visibility = View.GONE
                            } else {
                                binding.ordersRecycler.visibility = View.GONE
                                binding.emptyOrdersLayout.visibility = View.VISIBLE
                            }

                            loadFavouritesCount(userId)
                            loadCartCount(userId)
                            var value: String
                            when(user.balance.toInt() % 10) {
                                1 -> value = "Магнит"
                                2 -> value = "Магнита"
                                3 -> value = "Магнита"
                                4 -> value = "Магнита"
                                5 -> value = "Магнитоа"
                                else -> value = "Магнитов"
                            }
                            binding.walletBalance.text = String.format("%.0f ${value}", (user.balance.toDoubleOrNull() ?: 0.0))
                            binding.bonusBalance.text = (user.bonuses.toIntOrNull() ?: 0).toString()

                            val favCategoriesStr = json.optString("favoriteCategories", "[]")
                            if (favCategoriesStr.isNotEmpty() && favCategoriesStr != "[]") {
                                val pattern = "\"([^\"]+)\"".toRegex()
                                val categories = pattern.findAll(favCategoriesStr).map { it.groupValues[1] }.joinToString(", ")
                                binding.favoriteCategoriesText.text = categories
                                binding.editCategoriesButton.visibility = View.VISIBLE
                            } else {
                                binding.favoriteCategoriesText.text = "Не выбраны"
                                binding.editCategoriesButton.visibility = View.GONE
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            } ?: showUserView("Пользователь")
        }.addOnFailureListener { showUserView("Пользователь") }

    private fun loadFavouritesCount(userId: String) = db.collection("Accounts").document(userId).get()
        .addOnSuccessListener { doc ->
            doc.getString("info")?.let { info ->
                try {
                    binding.favouritesCount.text = JSONArray(JSONObject(info).optString("favorite", "[]")).length().toString()
                    showUserView("")
                } catch (e: Exception) { binding.favouritesCount.text = "0" }
            }
        }

    private fun loadCartCount(userId: String) = db.collection("Accounts").document(userId).get()
        .addOnSuccessListener { doc ->
            doc.getString("info")?.let { info ->
                try {
                    binding.purchasesCount.text = JSONArray(JSONObject(info).optString("basket", "[]")).length().toString()
                } catch (e: Exception) { binding.purchasesCount.text = "0" }
            }
        }

    private fun showGuestView() = with(binding) {
        listOf(userCard, linksCard, walletCard, ordersCard).forEach { it.visibility = View.GONE }
        guestCard.visibility = View.VISIBLE
    }

    private fun showUserView(login: String) = with(binding) {
        listOf(userCard, linksCard, walletCard, ordersCard).forEach { it.visibility = View.VISIBLE }
        guestCard.visibility = View.GONE
        if (login.isNotEmpty()) user.text = login
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}