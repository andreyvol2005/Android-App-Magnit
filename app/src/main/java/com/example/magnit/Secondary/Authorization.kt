package com.example.magnit.Secondary

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.magnit.databinding.ActivityAuthorizationBinding
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class Authorization : AppCompatActivity() {
    private var _b: ActivityAuthorizationBinding? = null
    private val b get() = _b!!
    private val db = FirebaseFirestore.getInstance()
    private val prefs by lazy { getSharedPreferences("filter_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _b = ActivityAuthorizationBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        b.btnLogin.setOnClickListener {
            val (login, pass) = b.etLogin.text.toString() to b.etPassword.text.toString()
            when {
                login.isEmpty() -> b.etLogin.error = "Введите логин"
                pass.isEmpty() -> b.etPassword.error = "Введите пароль"
                else -> auth(login, pass)
            }
        }
        b.tvRegister.setOnClickListener { startActivity(Intent(this, Registration::class.java)) }
        b.btnBack.setOnClickListener { finish() }
    }

    private fun auth(login: String, pass: String) = db.collection("Accounts").get()
        .addOnSuccessListener { docs ->
            var ok = false
            docs.forEach { doc ->
                try {
                    JSONObject(doc.getString("info") ?: "").let { json ->
                        if (json.getString("login") == login && json.getString("pass") == pass) {
                            prefs.edit().putString("account", doc.id).apply()
                            Toast.makeText(this, "Успешный вход", Toast.LENGTH_SHORT).show()
                            ok = true
                            finish()
                            return@forEach
                        }
                    }
                } catch (_: Exception) { }
            }
            if (!ok) Toast.makeText(this, "Неверный логин или пароль", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { Toast.makeText(this, "Ошибка: ${it.message}", Toast.LENGTH_SHORT).show() }

    override fun onDestroy() { super.onDestroy(); _b = null }
}