package com.example.magnit.Secondary

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.magnit.Backend.User
import com.example.magnit.databinding.ActivitySettingsBinding
import com.google.firebase.firestore.FirebaseFirestore

class Settings : AppCompatActivity() {
    private var _b: ActivitySettingsBinding? = null
    private val b get() = _b!!
    private val db = FirebaseFirestore.getInstance()
    private val prefs by lazy { getSharedPreferences("filter_prefs", MODE_PRIVATE) }
    private val uid get() = prefs.getString("account", "none").takeIf { it != "none" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.systemBars()).let { v.setPadding(it.left, it.top, it.right, it.bottom) }
            insets
        }

        loadUser()
        b.btnLogout.setOnClickListener { logout() }
        b.btnDeleteAccount.setOnClickListener { showDeleteDialog() }
        b.btnBack.setOnClickListener { finish() }
    }

    private fun loadUser() = uid?.let { id ->
        db.collection("Accounts").document(id).get()
            .addOnSuccessListener { doc ->
                doc.getString("info")?.let { info ->
                    User.fromJson(info, id).let { user ->
                        b.tvUserId.text = "ID: ${user.id}"
                        b.tvUserLogin.text = "Логин: ${user.login}"
                        b.tvUserEmail.text = "Email: ${user.email}"
                    }
                } ?: run { b.tvUserLogin.text = "Нет данных" }
            }.addOnFailureListener { b.tvUserLogin.text = "Ошибка загрузки" }
    } ?: run { b.tvUserLogin.text = "Пользователь не найден" }

    private fun logout() {
        prefs.edit { putString("account", "none") }
        toast("Вы вышли из аккаунта")
        finish()
    }

    private fun showDeleteDialog() = AlertDialog.Builder(this)
        .setTitle("Удаление аккаунта")
        .setMessage("Это действие нельзя отменить")
        .setPositiveButton("Удалить") { _, _ -> deleteAccount() }
        .setNegativeButton("Отмена", null)
        .show()

    private fun deleteAccount() = uid?.let { id ->
        db.collection("Accounts").document(id).delete()
            .addOnSuccessListener {
                prefs.edit { putString("account", "none") }
                toast("Аккаунт удален")
                finish()
            }.addOnFailureListener { toast("Ошибка: ${it.message}") }
    } ?: toast("Ошибка: пользователь не найден")

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    override fun onDestroy() { super.onDestroy(); _b = null }
}