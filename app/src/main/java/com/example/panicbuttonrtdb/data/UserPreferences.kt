package com.example.panicbuttonrtdb.data

import android.content.Context

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)


    // Menyimpan status login pengguna
    fun saveUserLoggedIn(isLoggedIn: Boolean) {
        prefs.edit().putBoolean("is_logged_in", isLoggedIn).apply()
    }

    // Save selected perumahan id (or name)
    fun savePerumahan(perumahanId: String, perumahanName: String) {
        prefs.edit()
            .putString("perumahan_id", perumahanId)
            .putString("perumahan_name", perumahanName)
            .apply()
    }

    // Mengambil status login pengguna
    fun isUserLoggedIn(): Boolean {
        return prefs.getBoolean("is_logged_in", false)
    }

    // Fungsi untuk menyimpan nama pengguna dan nomor rumah
    fun saveUserInfo(name: String, houseNumber: String) {
        prefs.edit().putString("user_name", name).putString("house_number", houseNumber).apply()
    }

    fun getUserName(): String? {
        return prefs.getString("user_name", "")
    }

    fun getHouseNumber(): String? {
        return prefs.getString("house_number", "")
    }

    fun getPerumahanId(): String? = prefs.getString("perumahan_id", null)
    fun getPerumahanName(): String? = prefs.getString("perumahan_name", null)

    // Fungsi untuk menghapus data pengguna saat logout
    fun clearUserInfo() {
        prefs.edit()
            .remove("is_logged_in")
            .remove("user_name")
            .remove("house_number")
            .remove("isAdminLoggedIn")
            .remove("perumahan_id")
            .remove("perumahan_name")
            .apply()
    }

    // Simpan status admin login
    fun saveAdminLoggedIn(isLoggedIn: Boolean) {
        prefs.edit().putBoolean("isAdminLoggedIn", isLoggedIn).apply()
    }

    // Cek apakah admin sudah login
    fun isAdminLoggedIn(): Boolean {
        return prefs.getBoolean("isAdminLoggedIn", false)
    }
}