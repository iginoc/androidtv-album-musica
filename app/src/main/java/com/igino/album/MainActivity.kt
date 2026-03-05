package com.igino.album

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity

/**
 * Carica [MainFragment] e gestisce l'uscita pulita dalla memoria.
 * Gestisce il player globale per non interrompere la musica durante la navigazione.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Controlla se la configurazione esiste, altrimenti vai alla ConfigActivity
        val config = SambaConfig.getConfig(this)
        if (config == null) {
            startActivity(Intent(this, ConfigActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, MainFragment())
                .commitNow()
        }
    }

    fun playAudio(url: String, user: String, pass: String) {
        val container = findViewById<View>(R.id.global_player_container)
        container.visibility = View.VISIBLE
        
        val playerFragment = AudioPlayerFragment.newInstance(url, user, pass)
        supportFragmentManager.beginTransaction()
            .replace(R.id.global_player_container, playerFragment)
            .commit()
    }

    /**
     * Sposta il focus sui controlli del player globale.
     */
    fun focusPlayer() {
        val container = findViewById<View>(R.id.global_player_container)
        if (container != null && container.visibility == View.VISIBLE) {
            val playPauseBtn = findViewById<View>(R.id.btnPlayPause)
            val searchBtn = findViewById<View>(R.id.btnSearchMetadata)
            val favBtn = findViewById<View>(R.id.btnFavorite)
            
            val target = when {
                playPauseBtn != null && playPauseBtn.isFocusable -> playPauseBtn
                favBtn != null && favBtn.isFocusable -> favBtn
                searchBtn != null && searchBtn.isFocusable -> searchBtn
                else -> container
            }
            
            target.requestFocus()
            target.postDelayed({ target.requestFocus() }, 50)
        }
    }

    fun refreshFavorites() {
        val mainFragment = supportFragmentManager.findFragmentById(R.id.main_browse_fragment) as? MainFragment
        mainFragment?.refreshFavorites()
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount == 0) {
            finishAndRemoveTask()
        } else {
            super.onBackPressed()
        }
    }
}
