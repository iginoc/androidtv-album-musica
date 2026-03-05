package com.igino.album

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

class ConfigActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        val etUrl = findViewById<EditText>(R.id.etUrl)
        val etUser = findViewById<EditText>(R.id.etUser)
        val etPass = findViewById<EditText>(R.id.etPass)
        val btnSave = findViewById<Button>(R.id.btnSave)

        // Pre-popola se esistono già
        SambaConfig.getConfig(this)?.let {
            etUrl.setText(it.url)
            etUser.setText(it.user)
            etPass.setText(it.pass)
        }

        btnSave.setOnClickListener {
            val url = etUrl.text.toString().trim()
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString().trim()

            if (url.isEmpty()) {
                Toast.makeText(this, "Inserisci un URL valido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            SambaConfig.saveConfig(this, url, user, pass)
            
            // Avvia MainActivity e chiudi questa
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
