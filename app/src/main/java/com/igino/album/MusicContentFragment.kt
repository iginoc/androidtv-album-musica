package com.igino.album

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrowseSupportFragment

class MusicContentFragment : Fragment(), BrowseSupportFragment.MainFragmentAdapterProvider {

    private val mMainFragmentAdapter = BrowseSupportFragment.MainFragmentAdapter(this)
    private var mPath: String? = null
    private var mUser: String? = null
    private var mPass: String? = null

    override fun getMainFragmentAdapter(): BrowseSupportFragment.MainFragmentAdapter<*> {
        return mMainFragmentAdapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mPath = arguments?.getString("PATH")
        mUser = arguments?.getString("USER")
        mPass = arguments?.getString("PASS")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_music_content, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        if (savedInstanceState == null && mPath != null) {
            val gridFragment = MainFragment.GridFragment.newInstance(mPath!!, mUser ?: "", mPass ?: "")
            childFragmentManager.beginTransaction()
                .replace(R.id.list_container, gridFragment)
                .commit()
        }
    }

    fun playSong(url: String) {
        view?.findViewById<View>(R.id.player_container)?.visibility = View.VISIBLE
        
        val playerFragment = AudioPlayerFragment.newInstance(url, mUser ?: "", mPass ?: "")
        childFragmentManager.beginTransaction()
            .replace(R.id.player_container, playerFragment)
            .commit()
    }

    fun refreshIfFavorites() {
        if (mPath == "favorites://") {
            val gridFrag = childFragmentManager.findFragmentById(R.id.list_container) as? MainFragment.GridFragment
            gridFrag?.loadData()
        }
    }

    companion object {
        fun newInstance(path: String, user: String, pass: String): MusicContentFragment {
            return MusicContentFragment().apply {
                arguments = Bundle().apply {
                    putString("PATH", path)
                    putString("USER", user)
                    putString("PASS", pass)
                }
            }
        }
    }
}
