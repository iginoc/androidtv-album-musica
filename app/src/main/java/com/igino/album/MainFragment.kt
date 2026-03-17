package com.igino.album

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.app.ProgressBarManager
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.*
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.util.*
import kotlin.concurrent.thread

/**
 * Browser SMB con navigazione ad Albero a sinistra e Elenco a destra.
 * Gestisce il Mini-Player tramite MusicContentFragment.
 */
class MainFragment : BrowseSupportFragment() {

    private val mHandler = Handler(Looper.myLooper()!!)
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val mProgressBarManager = ProgressBarManager()
    
    private val mFolderMap = mutableMapOf<Long, String>()
    private val mLevelMap = mutableMapOf<Long, Int>()
    private val mExpandedFolders = mutableSetOf<Long>()
    private val mChildrenMap = mutableMapOf<Long, MutableList<Long>>()
    
    private var nextHeaderId = 1L

    private var sambaUrl: String = ""
    private var sambaUser: String = ""
    private var sambaPass: String = ""
    private val FAVORITES_PATH = "favorites://"

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        super.onActivityCreated(savedInstanceState)
        
        val config = SambaConfig.getConfig(requireContext())
        if (config == null) {
            return
        }
        
        sambaUrl = config.url
        sambaUser = config.user
        sambaPass = config.pass

        setupUIElements()
        
        mainFragmentRegistry.registerFragment(PageRow::class.java, object : FragmentFactory<Fragment>() {
            override fun createFragment(rowObj: Any?): Fragment {
                val row = rowObj as PageRow
                val url = mFolderMap[row.headerItem.id] ?: sambaUrl
                return MusicContentFragment.newInstance(url, sambaUser, sambaPass)
            }
        })

        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter
        
        loadInitialRoot()
        loadFavoritesFromSamba()
        
        headersSupportFragment?.setOnHeaderClickedListener { _, row ->
            if (row is PageRow) toggleFolder(row)
        }

        setOnSearchClickedListener {
            val intent = Intent(requireContext(), SearchActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupUIElements() {
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.fastlane_background)
        badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.app_icon_your_company)
        mProgressBarManager.setRootView(view as ViewGroup)
    }

    fun updateTitle(name: String, count: Int) {
        val mainTitle = if (name.isBlank() || name == "Musica") "Musica" else name
        val subtitle = "$count canzoni trovate"
        val spannable = SpannableString("$mainTitle\n$subtitle")
        spannable.setSpan(RelativeSizeSpan(0.35f), mainTitle.length + 1, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(Color.GRAY), mainTitle.length + 1, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        title = spannable
    }

    private fun loadInitialRoot() {
        val favId = nextHeaderId++
        val favHeader = HeaderItem(favId, "⭐ Preferiti")
        mFolderMap[favId] = FAVORITES_PATH
        rowsAdapter.add(PageRow(favHeader))

        val rootId = nextHeaderId++
        val rootHeader = HeaderItem(rootId, "▶ 📁 Musica")
        mFolderMap[rootId] = sambaUrl
        mLevelMap[rootId] = 0
        rowsAdapter.add(PageRow(rootHeader))
    }

    private fun toggleFolder(row: PageRow) {
        val id = row.headerItem.id
        val url = mFolderMap[id] ?: return
        if (url == FAVORITES_PATH) return

        if (mExpandedFolders.contains(id)) {
            collapseFolder(id)
        } else {
            val level = mLevelMap[id] ?: 0
            expandFolder(id, url, level)
        }
    }

    private fun expandFolder(parentId: Long, url: String, level: Int) {
        mProgressBarManager.show()
        thread {
            try {
                val context = getSmbContext()
                val dir = SmbFile(url, context)
                val files = dir.listFiles() ?: emptyArray()
                
                val subFolders = files.filter { it.isDirectory }
                    .sortedBy { it.name.lowercase() }
                    .map { 
                        val name = it.name.removeSuffix("/").substringAfterLast("/")
                        val path = it.canonicalPath
                        name to path
                    }

                mHandler.post {
                    mProgressBarManager.hide()
                    val parentRow = findRowById(parentId) ?: return@post
                    val parentIndex = rowsAdapter.indexOf(parentRow)
                    if (parentIndex == -1) return@post

                    mExpandedFolders.add(parentId)
                    val childrenIds = mutableListOf<Long>()
                    var insertIndex = parentIndex + 1
                    
                    subFolders.forEach { (name, path) ->
                        val childId = addTreeRow(name, path, level + 1, insertIndex)
                        childrenIds.add(childId)
                        insertIndex++
                    }
                    mChildrenMap[parentId] = childrenIds
                    updateHeaderText(parentId, true)
                }
            } catch (e: Exception) { showError(e) }
        }
    }

    private fun addTreeRow(name: String, path: String, level: Int, insertIndex: Int): Long {
        val headerId = nextHeaderId++
        val indent = "    ".repeat(level)
        val header = HeaderItem(headerId, "$indent▶ 📁 $name")
        mFolderMap[headerId] = path
        mLevelMap[headerId] = level
        rowsAdapter.add(insertIndex, PageRow(header))
        return headerId
    }

    private fun collapseFolder(id: Long) {
        val children = mChildrenMap[id] ?: return
        children.forEach { childId ->
            if (mExpandedFolders.contains(childId)) collapseFolder(childId)
            findRowById(childId)?.let {
                rowsAdapter.remove(it)
                mFolderMap.remove(childId)
                mLevelMap.remove(childId)
            }
        }
        mExpandedFolders.remove(id)
        mChildrenMap.remove(id)
        updateHeaderText(id, false)
    }

    private fun findRowById(id: Long): PageRow? {
        for (i in 0 until rowsAdapter.size()) {
            val r = rowsAdapter.get(i) as? PageRow
            if (r?.headerItem?.id == id) return r
        }
        return null
    }

    private fun updateHeaderText(id: Long, expanded: Boolean) {
        for (i in 0 until rowsAdapter.size()) {
            val row = rowsAdapter.get(i) as? PageRow
            if (row?.headerItem?.id == id) {
                val header = row.headerItem
                val currentText = header.name
                val newText = if (expanded) currentText.replace("▶", "▼") else currentText.replace("▼", "▶")
                rowsAdapter.replace(i, PageRow(HeaderItem(header.id, newText)))
                break
            }
        }
    }

    private fun getSmbContext(): CIFSContext {
        val baseProps = Properties().apply {
            setProperty("jcifs.smb.client.enableSMB2", "true")
            setProperty("jcifs.smb.client.dfs.disabled", "false")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
        }
        return BaseContext(PropertyConfiguration(baseProps)).withCredentials(NtlmPasswordAuthenticator(null, sambaUser, sambaPass))
    }

    private fun showError(e: Exception) {
        Log.e(TAG, "SMB Error", e)
        mHandler.post {
            if (isAdded) {
                mProgressBarManager.hide()
                Toast.makeText(requireContext(), "Errore connessione", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun playSong(url: String) {
        (activity as? MainActivity)?.playAudio(url, sambaUser, sambaPass)
    }

    fun refreshFavorites() {
        childFragmentManager.fragments.forEach { musicFrag ->
            if (musicFrag is MusicContentFragment) {
                musicFrag.refreshIfFavorites()
            }
        }
        saveFavoritesToSamba()
    }

    private fun loadFavoritesFromSamba() {
        if (sambaUrl.isEmpty()) return
        val appContext = activity?.applicationContext ?: return
        thread {
            try {
                val context = getSmbContext()
                val m3uFile = SmbFile(sambaUrl + "favorites.m3u", context)
                if (m3uFile.exists()) {
                    val urls = mutableSetOf<String>()
                    m3uFile.getInputStream().use { inputStream ->
                        inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                val t = line.trim()
                                if (t.isNotEmpty() && !t.startsWith("#")) {
                                    urls.add(t)
                                }
                            }
                        }
                    }
                    val prefs = appContext.getSharedPreferences("favorites", Context.MODE_PRIVATE)
                    prefs.edit().putStringSet("urls", urls).apply()
                    Log.d(TAG, "Playlist M3U caricata da Samba: ${urls.size} brani")
                    
                    mHandler.post {
                        childFragmentManager.fragments.forEach { musicFrag ->
                            if (musicFrag is MusicContentFragment) {
                                musicFrag.refreshIfFavorites()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore caricamento playlist M3U", e)
            }
        }
    }

    private fun saveFavoritesToSamba() {
        if (sambaUrl.isEmpty()) return
        val appContext = activity?.applicationContext ?: return
        thread {
            try {
                val context = getSmbContext()
                val prefs = appContext.getSharedPreferences("favorites", Context.MODE_PRIVATE)
                val favorites = prefs.getStringSet("urls", emptySet()) ?: emptySet()
                
                val m3uFile = SmbFile(sambaUrl + "favorites.m3u", context)
                val m3uContent = StringBuilder("#EXTM3U\n")
                
                favorites.filterNotNull().sorted().forEach { url ->
                    val fileName = url.substringAfterLast("/").replace(".mp3", "")
                    m3uContent.append("#EXTINF:-1,$fileName\n")
                    m3uContent.append(url).append("\n")
                }

                m3uFile.getOutputStream().use { os ->
                    os.write(m3uContent.toString().toByteArray())
                }
                Log.d(TAG, "Playlist M3U salvata su Samba: ${m3uFile.canonicalPath}")
            } catch (e: Exception) {
                Log.e(TAG, "Errore salvataggio playlist M3U", e)
            }
        }
    }

    companion object {
        private const val TAG = "MainFragment"
    }

    class GridFragment : VerticalGridSupportFragment() {
        private lateinit var mAdapter: ArrayObjectAdapter
        private val mHandler = Handler(Looper.myLooper()!!)

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false).apply { 
                numberOfColumns = 1 
                shadowEnabled = false
            }
            setGridPresenter(gridPresenter)
            mAdapter = ArrayObjectAdapter(ListRowItemPresenter())
            adapter = mAdapter
            setupEventListeners()
            loadData()
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
        }

        private fun setupEventListeners() {
            onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
                if (item is SmbFileWrapper) {
                    findMainFragment()?.playSong(item.path)
                }
            }
        }

        private fun findMainFragment(): MainFragment? {
            var parent = parentFragment
            while (parent != null) {
                if (parent is MainFragment) return parent
                parent = parent.parentFragment
            }
            return null
        }

        fun loadData() {
            val path = arguments?.getString("PATH") ?: return
            if (path == "favorites://") {
                loadFavorites()
                return
            }

            val user = arguments?.getString("USER") ?: ""
            val pass = arguments?.getString("PASS") ?: ""
            thread {
                try {
                    val baseProps = Properties().apply {
                        setProperty("jcifs.smb.client.enableSMB2", "true")
                        setProperty("jcifs.smb.client.dfs.disabled", "false")
                    }
                    val context = BaseContext(PropertyConfiguration(baseProps)).withCredentials(NtlmPasswordAuthenticator(null, user, pass))
                    val smbFile = SmbFile(path, context)
                    val files = smbFile.listFiles() ?: emptyArray()
                    
                    val items = files.filter { !it.isDirectory && it.name.lowercase().endsWith(".mp3") }
                        .map { 
                            val name = it.name.replace("/", "")
                            val canonicalPath = it.canonicalPath
                            SmbFileWrapper(name, canonicalPath) 
                        }
                        .sortedBy { it.name.lowercase() }
                    
                    val folderName = smbFile.name.removeSuffix("/").substringAfterLast("/")

                    mHandler.post {
                        mAdapter.clear()
                        mAdapter.addAll(0, items)
                        findMainFragment()?.updateTitle(folderName, items.size)
                    }
                } catch (e: Exception) { Log.e("GridFragment", "Error", e) }
            }
        }

        private fun loadFavorites() {
            val context = context ?: return
            val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
            val favorites = prefs.getStringSet("urls", emptySet()) ?: emptySet()
            val items = favorites.filterNotNull().map { url ->
                val name = url.substringAfterLast("/").replace(".mp3", "")
                SmbFileWrapper(name, url)
            }.sortedBy { it.name.lowercase() }

            mHandler.post {
                mAdapter.clear()
                mAdapter.addAll(0, items)
                findMainFragment()?.updateTitle("⭐ Preferiti", items.size)
            }
        }

        data class SmbFileWrapper(val name: String, val path: String)

        inner class ListRowItemPresenter : Presenter() {
            override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
                val textView = TextView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    setPadding(48, 0, 48, 0)
                    setSingleLine(true)
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    
                    val outValue = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)

                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            (context as? MainActivity)?.focusPlayer()
                            return@setOnKeyListener true
                        }
                        false
                    }
                }
                return ViewHolder(textView)
            }
            override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
                val wrapper = item as? SmbFileWrapper ?: return
                (viewHolder.view as TextView).text = "🎵  ${wrapper.name}"
            }
            override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
        }

        companion object {
            fun newInstance(path: String, user: String, pass: String): GridFragment {
                return GridFragment().apply {
                    arguments = Bundle().apply {
                        putString("PATH", path)
                        putString("USER", user)
                        putString("PASS", pass)
                    }
                }
            }
        }
    }
}
