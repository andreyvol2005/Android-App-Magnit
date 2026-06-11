package com.example.magnit.Secondary

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.magnit.databinding.ActivityAuthorizationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Authorization : AppCompatActivity() {
    private var _b: ActivityAuthorizationBinding? = null
    private val b get() = _b!!
    private val auth = FirebaseAuth.getInstance()
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
            val email = b.etLogin.text.toString()
            val password = b.etPassword.text.toString()
            when {
                email.isEmpty() -> b.etLogin.error = "Введите email"
                password.isEmpty() -> b.etPassword.error = "Введите пароль"
                else -> authWithFirebase(email, password)
            }
        }
        b.tvRegister.setOnClickListener { startActivity(Intent(this, Registration::class.java)) }
        b.btnBack.setOnClickListener { finish() }
    }

    private fun authWithFirebase(login: String, password: String) {
        val email = "$login@magnit.test"  // преобразуем логин в email
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val userId = authResult.user?.uid ?: return@addOnSuccessListener
                prefs.edit().putString("account", userId).apply()
                Toast.makeText(this, "Успешный вход", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Неверный логин или пароль", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() { super.onDestroy(); _b = null }
}