package com.igino.album

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.util.*
import kotlin.concurrent.thread

class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val mHandler = Handler(Looper.myLooper()!!)
    private lateinit var mRowsAdapter: ArrayObjectAdapter
    private var mSambaUrl: String = ""
    private var mSambaUser: String = ""
    private var mSambaPass: String = ""

    private var mSearchRunnable: Runnable? = null
    private val SEARCH_DELAY_MS = 1000L

    companion object {
        private const val REQUEST_SPEECH = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val config = SambaConfig.getConfig(requireContext())
        if (config != null) {
            mSambaUrl = config.url
            mSambaUser = config.user
            mSambaPass = config.pass
        }

        mRowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        setSearchResultProvider(this)
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is MainFragment.GridFragment.SmbFileWrapper) {
                (activity as? SearchActivity)?.playAudio(item.path, mSambaUser, mSambaPass)
            }
        }

        // Richiedi permesso se necessario
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        }

        setSpeechRecognitionCallback {
            try {
                // Utilizziamo l'intent predefinito arricchito con lingua Italiana forzata
                val intent = recognizerIntent
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "it-IT")
                
                startActivityForResult(intent, REQUEST_SPEECH)
            } catch (e: ActivityNotFoundException) {
                Log.e("SearchFragment", "Speech recognition not available")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SPEECH && resultCode == Activity.RESULT_OK) {
            // Estraiamo il testo manualmente per sicurezza se setSearchQuery non basta
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                setSearchQuery(results[0], true)
            } else {
                setSearchQuery(data, true)
            }
        }
    }

    override fun getResultsAdapter(): ObjectAdapter = mRowsAdapter

    override fun onQueryTextChange(newQuery: String?): Boolean {
        mRowsAdapter.clear()
        mSearchRunnable?.let { mHandler.removeCallbacks(it) }
        
        if (!newQuery.isNullOrEmpty() && newQuery.length >= 3) {
            mSearchRunnable = Runnable { startSearch(newQuery) }
            mHandler.postDelayed(mSearchRunnable!!, SEARCH_DELAY_MS)
        }
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        mRowsAdapter.clear()
        mSearchRunnable?.let { mHandler.removeCallbacks(it) }
        
        if (!query.isNullOrEmpty()) {
            startSearch(query)
        }
        return true
    }

    private fun startSearch(query: String) {
        thread {
            try {
                val props = Properties().apply {
                    setProperty("jcifs.smb.client.enableSMB2", "true")
                    setProperty("jcifs.smb.client.dfs.disabled", "false")
                }
                val context = BaseContext(PropertyConfiguration(props))
                    .withCredentials(NtlmPasswordAuthenticator(null, mSambaUser, mSambaPass))
                
                val results = mutableListOf<MainFragment.GridFragment.SmbFileWrapper>()
                recursiveSearch(SmbFile(mSambaUrl, context), query.lowercase(), results)
                
                mHandler.post {
                    val listRowAdapter = ArrayObjectAdapter(MainFragment.GridFragment().ListRowItemPresenter())
                    listRowAdapter.addAll(0, results)
                    val header = HeaderItem("Risultati per \"$query\"")
                    mRowsAdapter.add(ListRow(header, listRowAdapter))
                }
            } catch (e: Exception) {
                Log.e("Search", "Search error", e)
            }
        }
    }

    private fun recursiveSearch(dir: SmbFile, query: String, results: MutableList<MainFragment.GridFragment.SmbFileWrapper>) {
        try {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    recursiveSearch(file, query, results)
                } else if (file.name.lowercase().contains(query) && file.name.lowercase().endsWith(".mp3")) {
                    results.add(MainFragment.GridFragment.SmbFileWrapper(file.name.replace("/", ""), file.canonicalPath))
                }
                if (results.size >= 50) return // Limit results for performance
            }
        } catch (e: Exception) {
            // Ignore folder access errors
        }
    }
}
