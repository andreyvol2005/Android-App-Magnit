package com.example.magnit.Fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
        promokod.setOnClickListener {
            val input = EditText(requireContext())
            input.hint = "Введите промокод"
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT

            AlertDialog.Builder(requireContext())
                .setTitle("Промокод")
                .setMessage("Введите промокод для получения бонуса 500 ₽")
                .setView(input)
                .setPositiveButton("Применить") { _, _ ->
                    val code = input.text.toString().trim()
                    if (code.isEmpty()) {
                        Toast.makeText(requireContext(), "Введите промокод", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val userId = requireContext().getSharedPreferences("filter_prefs", Context.MODE_PRIVATE)
                        .getString("account", "none").takeIf { it != "none" }

                    if (userId == null) {
                        Toast.makeText(requireContext(), "Войдите в аккаунт", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val validPromoCodes = listOf("MAGNIT500", "BONUS2026", "SKIDKA100", "WELCOME")

                    if (!validPromoCodes.contains(code.uppercase())) {
                        Toast.makeText(requireContext(), "Неверный промокод", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val db = FirebaseFirestore.getInstance()
                    db.collection("Accounts").document(userId).get()
                        .addOnSuccessListener { doc ->
                            try {
                                val json = JSONObject(doc.getString("info") ?: "")


                                val currentBalance = json.optString("balance", "0").toDoubleOrNull() ?: 0.0
                                json.put("balance", (currentBalance + 500.0).toString())


                                val walletStr = json.optString("wallet", "[]")
                                val date = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date())
                                val newWallet = if (walletStr == "[]") "[ \"$date\":500.0 ]"
                                else "[ \"$date\":500.0,${walletStr.substring(1, walletStr.length - 1)} ]"
                                json.put("wallet", newWallet)

                                db.collection("Accounts").document(userId).update("info", json.toString())
                                    .addOnSuccessListener {
                                        Toast.makeText(requireContext(), "Промокод активирован! Начислено 500 ₽", Toast.LENGTH_LONG).show()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(requireContext(), "Ошибка", Toast.LENGTH_SHORT).show()
                                    }
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), "Ошибка", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
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

                            // Получаем баланс из JSON напрямую (без User)
                            val balance = json.optDouble("balance", 0.0)
                            val bonuses = json.optInt("bonuses", 0)

                            val intBalance = balance.toInt()

                            val magnetWord = when (intBalance % 10) {
                                1 -> "Магнит"
                                in 2..4 -> "Магнита"
                                else -> "Магнитов"
                            }

                            binding.walletBalance.text = "${intBalance} $magnetWord"
                            binding.bonusBalance.text = bonuses.toString()

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
                } catch (e: Exception) {
                    e.printStackTrace()
                    showUserView("Пользователь")
                }
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
        bonusCard.visibility = View.GONE
        favoriteCategoriesCard.visibility = View.GONE
    }

    private fun showUserView(login: String) = with(binding) {
        listOf(userCard, linksCard, walletCard, ordersCard).forEach { it.visibility = View.VISIBLE }
        guestCard.visibility = View.GONE
        bonusCard.visibility = View.VISIBLE
        favoriteCategoriesCard.visibility = View.VISIBLE
        if (login.isNotEmpty()) user.text = login
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}