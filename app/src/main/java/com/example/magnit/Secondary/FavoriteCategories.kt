package com.example.magnit.Secondary

import android.content.Context
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.magnit.R
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class FavoriteCategories : AppCompatActivity() {

    private val allCategories = listOf(
        "Алкоголь", "Готовая еда", "Молочный прилавок", "Овощи и фрукты",
        "Хлеб и выпечка", "Бакалея", "Консервы", "Птица, мясо",
        "Рыба, морепродукты", "Заморозка", "Сладости", "Снеки",
        "Чай, кофе, какао", "Вода и напитки", "Для детей", "Для животных",
        "Гигиена и уход", "Для дома и не только", "Макияж", "Уход за лицом",
        "Уход за телом", "Парфюмерия", "Волосы", "Для дома", "Для питомцев", "Детям"
    )
    private val selected = mutableListOf<String>()
    private val db = FirebaseFirestore.getInstance()
    private val checkBoxes = mutableMapOf<String, CheckBox>()
    private var updating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_favorite_categories)

        findViewById<android.widget.ImageView>(R.id.backButton).setOnClickListener { finish() }
        findViewById<android.widget.Button>(R.id.saveButton).setOnClickListener { save() }

        val container = findViewById<LinearLayout>(R.id.categoriesContainer)
        for (cat in allCategories) {
            val cb = CheckBox(this).apply {
                text = cat
                textSize = 16f
                setPadding(0, 16, 0, 16)
                setOnCheckedChangeListener { _, isChecked ->
                    if (!updating) {
                        if (isChecked) {
                            if (selected.size >= 3) {
                                Toast.makeText(this@FavoriteCategories, "Можно выбрать только 3 категории", Toast.LENGTH_SHORT).show()
                                this.isChecked = false
                            } else {
                                selected.add(cat)
                            }
                        } else {
                            selected.remove(cat)
                        }
                    }
                }
            }
            container.addView(cb)
            checkBoxes[cat] = cb
        }

        val userId = getSharedPreferences("filter_prefs", Context.MODE_PRIVATE).getString("account", "none")
        if (userId != null && userId != "none") {
            db.collection("Accounts").document(userId).get()
                .addOnSuccessListener { doc ->
                    val info = doc.getString("info")
                    if (info != null) {
                        try {
                            val json = JSONObject(info)
                            var favStr = json.optString("favoriteCategories", "")
                            if (favStr.isEmpty()) favStr = json.optString("favorite_categories", "[]")

                            if (favStr.isNotEmpty() && favStr != "[]") {
                                updating = true
                                selected.clear()
                                checkBoxes.values.forEach { it.isChecked = false }

                                // Правильный парсинг JSON массива с учетом кавычек
                                val pattern = "\"([^\"]+)\"".toRegex()
                                val categories = pattern.findAll(favStr).map { it.groupValues[1] }.toList()

                                categories.forEach { category ->
                                    if (selected.size < 3) {
                                        selected.add(category)
                                        checkBoxes[category]?.isChecked = true
                                    }
                                }
                                updating = false
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
        }
    }

    private fun save() {
        val userId = getSharedPreferences("filter_prefs", Context.MODE_PRIVATE).getString("account", "none")
        if (userId != null && userId != "none") {
            db.collection("Accounts").document(userId).get()
                .addOnSuccessListener { doc ->
                    try {
                        val json = JSONObject(doc.getString("info") ?: "")
                        val categoriesJson = if (selected.isEmpty()) "[]" else "[${selected.joinToString(",") { "\"$it\"" }}]"
                        json.put("favoriteCategories", categoriesJson)
                        db.collection("Accounts").document(userId).update("info", json.toString())
                            .addOnSuccessListener {
                                Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                            }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}