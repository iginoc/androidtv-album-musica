package com.igino.album

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity

class SearchActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.search_fragment, SearchFragment())
                .commit()
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
}
