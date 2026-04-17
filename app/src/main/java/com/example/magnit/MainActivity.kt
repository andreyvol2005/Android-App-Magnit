package com.example.magnit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.example.magnit.Fragments.*
import com.google.firebase.FirebaseApp

class MainActivity : AppCompatActivity() {
    private val tag = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("filter_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("account")) {
            prefs.edit { putString("account", "none") }
        }

        handleIntent(intent)
        setupNavigation()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val openFragment = intent.getStringExtra("open_fragment")
        val fragment = when (openFragment) {
            "basket" -> Basket()
            "account" -> Account()
            "cosmetic" -> Cosmetic()
            else -> Home()
        }
        replaceFragment(fragment)
    }

    private fun setupNavigation() {
        val navMap = mapOf(
            R.id.nav_home to Home(),
            R.id.nav_category to Categories(),
            R.id.nav_cart to Basket(),
            R.id.nav_profile to Account(),
            R.id.nav_favorites to Favourites()
        )

        navMap.forEach { (id, fragment) ->
            findViewById<ImageView>(id).setOnClickListener {
                replaceFragment(fragment)
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}