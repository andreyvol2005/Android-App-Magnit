package com.example.magnit.Secondary

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.magnit.databinding.ActivityRegistrationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class Registration : AppCompatActivity() {
    private var _b: ActivityRegistrationBinding? = null
    private val b get() = _b!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val prefs by lazy { getSharedPreferences("filter_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _b = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.systemBars()).let { v.setPadding(it.left, it.top, it.right, it.bottom) }
            insets
        }

        // Ссылка на политику обработки персональных данных
        b.tvPrivacyLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://e.magnit.ru/personal-data/"))
            startActivity(intent)
        }

        b.btnRegister.setOnClickListener {
            val login = b.etLogin.text.toString()
            val password = b.etPassword.text.toString()
            val email = b.etEmail.text.toString()
            Log.d("Registration", "${login} ${password} ${email} ${b.cbPrivacy.isChecked}")

            when {
                login.isEmpty() -> b.etLogin.error = "Введите логин"
                password.isEmpty() -> b.etPassword.error = "Введите пароль"
                email.isEmpty() -> b.etEmail.error = "Введите email"
                !b.cbPrivacy.isChecked -> Toast.makeText(this, "Подтвердите согласие на обработку персональных данных", Toast.LENGTH_SHORT).show()
                else -> registerWithFirebase(login, password, email)
            }
        }
        b.btnBack.setOnClickListener { finish() }
    }

    private fun registerWithFirebase(login: String, password: String, email: String) {
        val authEmail = "$login@magnit.test"  // преобразуем логин в email для Firebase Auth
        auth.createUserWithEmailAndPassword(authEmail, password)
            .addOnSuccessListener { authResult ->
                val userId = authResult.user?.uid ?: return@addOnSuccessListener

                // Создаём документ пользователя в Firestore с ID = UID
                val userData = mapOf(
                    "info" to JSONObject().apply {
                        put("login", login)
                        put("pass", "")
                        put("email", email)
                        put("favorite", "[]")
                        put("basket", "[]")
                        put("balance", "0")
                        put("order", "[]")
                        put("wallet", "[]")
                        put("bonuses", "0")
                        put("favoriteCategories", "[]")
                    }.toString()
                )

                db.collection("Accounts").document(userId).set(userData)
                    .addOnSuccessListener {
                        prefs.edit { putString("account", userId) }
                        toast("Регистрация успешна!")
                        finish()
                    }
                    .addOnFailureListener { e ->
                        toast("Ошибка сохранения данных: ${e.message}")
                        // Если не удалось сохранить в Firestore, удаляем пользователя из Auth
                        auth.currentUser?.delete()
                    }
            }
            .addOnFailureListener { e ->
                toast("Ошибка регистрации: ${e.message}")
            }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    override fun onDestroy() { super.onDestroy(); _b = null }
}