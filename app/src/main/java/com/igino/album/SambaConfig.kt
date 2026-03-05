package com.igino.album

import android.content.Context

object SambaConfig {
    fun getConfig(context: Context): Config? {
        val prefs = context.getSharedPreferences("samba_settings", Context.MODE_PRIVATE)
        val url = prefs.getString("smb_url", null) ?: return null
        val user = prefs.getString("smb_user", "") ?: ""
        val pass = prefs.getString("smb_pass", "") ?: ""
        return Config(url, user, pass)
    }

    fun saveConfig(context: Context, url: String, user: String, pass: String) {
        context.getSharedPreferences("samba_settings", Context.MODE_PRIVATE).edit()
            .putString("smb_url", if (url.endsWith("/")) url else "$url/")
            .putString("smb_user", user)
            .putString("smb_pass", pass)
            .apply()
    }

    data class Config(val url: String, val user: String, val pass: String)
}
