package com.example.magnit.Secondary

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.magnit.R
import com.example.magnit.databinding.ActivityHistoryBinding
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class History : AppCompatActivity() {

    private var _binding: ActivityHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var historyAdapter: HistoryAdapter
    private val historyItems = mutableListOf<HistoryAdapter.HistoryItem>()
    private val db = FirebaseFirestore.getInstance()
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currentUserId = getCurrentUserId()
        setupRecycler()
        binding.backButton.setOnClickListener { finish() }

        if (currentUserId != null) {
            loadHistory()
        } else {
            binding.emptyHistory.visibility = View.VISIBLE
            Toast.makeText(this, "Войдите в аккаунт", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentUserId(): String? {
        return getSharedPreferences("filter_prefs", Context.MODE_PRIVATE)
            .getString("account", "none")
            .takeIf { it != "none" }
    }

    private fun setupRecycler() {
        historyAdapter = HistoryAdapter(historyItems) { item ->
            Toast.makeText(this, "${item.title}: ${item.amount} м", Toast.LENGTH_SHORT).show()
        }
        binding.historyRecycler.layoutManager = LinearLayoutManager(this)
        binding.historyRecycler.adapter = historyAdapter
    }

    private fun loadHistory() {
        val userId = currentUserId ?: return

        db.collection("Accounts").document(userId).get()
            .addOnSuccessListener { document ->
                val infoString = document.getString("info")
                Log.d("History", "Raw info: $infoString")

                if (infoString != null && infoString.isNotEmpty()) {
                    try {
                        val json = JSONObject(infoString)
                        val walletStr = json.optString("wallet", "[]")
                        Log.d("History", "Wallet string: $walletStr")

                        if (walletStr.isNotEmpty() && walletStr != "[]" && walletStr != "null") {
                            historyItems.clear()

                            val pattern = "\"([^\"]+)\"\\s*:\\s*(-?\\d+\\.?\\d*)".toRegex()
                            val matches = pattern.findAll(walletStr)

                            for (match in matches) {
                                val date = match.groupValues[1]
                                val amount = match.groupValues[2].toDoubleOrNull() ?: 0.0

                                Log.d("History", "Found: date='$date', amount=$amount")

                                val type = if (amount < 0) "expense" else "income"
                                val title = if (amount < 0) "Списание" else "Пополнение"

                                historyItems.add(
                                    HistoryAdapter.HistoryItem(
                                        id = date,
                                        title = title,
                                        amount = kotlin.math.abs(amount),
                                        date = date,
                                        type = type
                                    )
                                )
                            }

                            historyItems.sortByDescending { it.date }

                            if (historyItems.isNotEmpty()) {
                                historyAdapter.updateItems(historyItems)
                                binding.emptyHistory.visibility = View.GONE
                            } else {
                                binding.emptyHistory.visibility = View.VISIBLE
                            }
                        } else {
                            binding.emptyHistory.visibility = View.VISIBLE
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        binding.emptyHistory.visibility = View.VISIBLE
                        Toast.makeText(this, "Ошибка данных: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    binding.emptyHistory.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                binding.emptyHistory.visibility = View.VISIBLE
                Toast.makeText(this, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}