package com.example.magnit.Secondary

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.magnit.R
import com.example.magnit.databinding.ActivityFilterBinding
import com.google.android.material.chip.Chip
import androidx.core.content.edit

class Filter : AppCompatActivity() {
    private lateinit var b: ActivityFilterBinding
    private val prefs by lazy { getSharedPreferences("filter_prefs", MODE_PRIVATE) }

    private var currentMode: String = "magnit"

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        b = ActivityFilterBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        currentMode = intent.getStringExtra("mode") ?: "magnit"

        when (currentMode) {
            "magnit" -> {
                b.filterMagnit.visibility = android.view.View.VISIBLE
                b.filterCosmetic.visibility = android.view.View.GONE
            }
            "cosmetic" -> {
                b.filterMagnit.visibility = android.view.View.GONE
                b.filterCosmetic.visibility = android.view.View.VISIBLE
            }
        }

        b.priceRangeMin.text = "${intent.getIntExtra("minPrice", 0)} м"
        b.priceSeekBar.max = intent.getIntExtra("maxPrice", 10000)

        loadFilters()

        b.priceSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                b.priceRangeMax.text = "$progress м"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        b.applyButton.setOnClickListener { save() }
        b.resetButton.setOnClickListener { reset() }
    }

    private fun loadFilters() {
        val cat = prefs.getString("selected_category", "Все") ?: "Все"
        val magnitChips = mapOf(
            "Все" to R.id.categoryAll, "Алкоголь" to R.id.categoryAlcohol, "Готовая еда" to R.id.categoryReadyMeal,
            "Молочный прилавок" to R.id.categoryDairy, "Овощи и фрукты" to R.id.categoryFruitsVeg,
            "Хлеб и выпечка" to R.id.categoryBakery, "Бакалея" to R.id.categoryGrocery,
            "Консервы" to R.id.categoryCanned, "Птица, мясо" to R.id.categoryMeatPoultry,
            "Рыба, морепродукты" to R.id.categoryFish, "Заморозка" to R.id.categoryFrozen,
            "Сладости" to R.id.categorySweets, "Снеки" to R.id.categorySnacks,
            "Чай, кофе, какао" to R.id.categoryTeaCoffee, "Вода и напитки" to R.id.categoryDrinks,
            "Для детей" to R.id.categoryKids, "Для животных" to R.id.categoryPets,
            "Гигиена и уход" to R.id.categoryHygiene, "Для дома и не только" to R.id.categoryHome
        )
        magnitChips.values.forEach { findViewById<Chip>(it)?.isChecked = false }
        findViewById<Chip>(magnitChips[cat] ?: R.id.categoryAll)?.isChecked = true

        val cosmeticCat = prefs.getString("selected_cosmetic_category", "Все") ?: "Все"
        val cosmeticChips = mapOf(
            "Все" to R.id.categoryCosmeticAll,
            "Уход за кожей" to R.id.categorySkinCare,
            "Макияж" to R.id.categoryMakeup,
            "Уход за волосами" to R.id.categoryHairCare,
            "Парфюмерия" to R.id.categoryPerfumery,
            "Уход за телом" to R.id.categoryBodyCare,
            "Мужской уход" to R.id.categoryMenCare,
            "Детская косметика" to R.id.categoryChildrenCosmetic,
            "Декоративная косметика" to R.id.categoryDecorative,
            "Натуральная косметика" to R.id.categoryOrganic
        )
        cosmeticChips.values.forEach { findViewById<Chip>(it)?.isChecked = false }
        findViewById<Chip>(cosmeticChips[cosmeticCat] ?: R.id.categoryCosmeticAll)?.isChecked = true

        // Цена
        b.priceSeekBar.progress = prefs.getInt("max_price", 10000)
        b.priceRangeMax.text = "${b.priceSeekBar.progress} м"

        // Рейтинг
        val ratingMap = mapOf(
            R.id.ratingAny to 0f, R.id.rating4Plus to 4f, R.id.rating4_5Plus to 4.5f,
            R.id.rating4_8Plus to 4.8f, R.id.rating5 to 5f
        )
        val savedRating = prefs.getFloat("min_rating", 0f)
        ratingMap.entries.find { it.value == savedRating }?.key?.let { b.ratingRadioGroup.check(it) }
            ?: b.ratingRadioGroup.check(R.id.ratingAny)

        // Отзывы
        val reviewsMap = mapOf(
            R.id.reviewsAny to 0, R.id.reviews10Plus to 10, R.id.reviews50Plus to 50,
            R.id.reviews100Plus to 100, R.id.reviews500Plus to 500
        )
        val savedReviews = prefs.getInt("min_reviews", 0)
        reviewsMap.entries.find { it.value == savedReviews }?.key?.let { b.reviewsRadioGroup.check(it) }
            ?: b.reviewsRadioGroup.check(R.id.reviewsAny)
    }

    private fun save() {
        val magnitCategories = listOf(
            R.id.categoryAll to "Все", R.id.categoryAlcohol to "Алкоголь", R.id.categoryReadyMeal to "Готовая еда",
            R.id.categoryDairy to "Молочный прилавок", R.id.categoryFruitsVeg to "Овощи и фрукты",
            R.id.categoryBakery to "Хлеб и выпечка", R.id.categoryGrocery to "Бакалея",
            R.id.categoryCanned to "Консервы", R.id.categoryMeatPoultry to "Птица, мясо",
            R.id.categoryFish to "Рыба, морепродукты", R.id.categoryFrozen to "Заморозка",
            R.id.categorySweets to "Сладости", R.id.categorySnacks to "Снеки",
            R.id.categoryTeaCoffee to "Чай, кофе, какао", R.id.categoryDrinks to "Вода и напитки",
            R.id.categoryKids to "Для детей", R.id.categoryPets to "Для животных",
            R.id.categoryHygiene to "Гигиена и уход", R.id.categoryHome to "Для дома и не только"
        )
        val selectedMagnitCategory = magnitCategories.find { findViewById<Chip>(it.first)?.isChecked == true }?.second ?: "Все"

        val cosmeticCategories = listOf(
            R.id.categoryCosmeticAll to "Все",
            R.id.categorySkinCare to "Уход за кожей",
            R.id.categoryMakeup to "Макияж",
            R.id.categoryHairCare to "Уход за волосами",
            R.id.categoryPerfumery to "Парфюмерия",
            R.id.categoryBodyCare to "Уход за телом",
            R.id.categoryMenCare to "Мужской уход",
            R.id.categoryChildrenCosmetic to "Детская косметика",
            R.id.categoryDecorative to "Декоративная косметика",
            R.id.categoryOrganic to "Натуральная косметика"
        )
        val selectedCosmeticCategory = cosmeticCategories.find { findViewById<Chip>(it.first)?.isChecked == true }?.second ?: "Все"

        prefs.edit {
            if (currentMode == "magnit") {
                putString("selected_category", selectedMagnitCategory)
                putString("selected_cosmetic_category", "Все") // Сбрасываем косметическую
            } else {
                putString("selected_cosmetic_category", selectedCosmeticCategory)
                putString("selected_category", "Все") // Сбрасываем обычную
            }
            putString("filter_mode", currentMode)
            putInt("max_price", b.priceSeekBar.progress)
            putFloat(
                "min_rating", when (b.ratingRadioGroup.checkedRadioButtonId) {
                    R.id.rating4Plus -> 4f
                    R.id.rating4_5Plus -> 4.5f
                    R.id.rating4_8Plus -> 4.8f
                    R.id.rating5 -> 5f
                    else -> 0f
                }
            )
            putInt(
                "min_reviews", when (b.reviewsRadioGroup.checkedRadioButtonId) {
                    R.id.reviews10Plus -> 10
                    R.id.reviews50Plus -> 50
                    R.id.reviews100Plus -> 100
                    R.id.reviews500Plus -> 500
                    else -> 0
                }
            )
            putBoolean("is_filter_active", true)
        }
        finish()
    }

    private fun reset() {
        prefs.edit().clear().apply()
        findViewById<Chip>(R.id.categoryAll)?.isChecked = true
        findViewById<Chip>(R.id.categoryCosmeticAll)?.isChecked = true
        b.priceSeekBar.progress = b.priceSeekBar.max
        b.priceRangeMax.text = "${b.priceSeekBar.max} м"
        b.ratingRadioGroup.check(R.id.ratingAny)
        b.reviewsRadioGroup.check(R.id.reviewsAny)
    }
}