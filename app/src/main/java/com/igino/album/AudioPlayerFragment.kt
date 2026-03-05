package com.igino.album

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.util.*
import kotlin.concurrent.thread

@OptIn(UnstableApi::class)
class AudioPlayerFragment : Fragment() {

    private var player: ExoPlayer? = null
    private lateinit var albumArt: ImageView
    private lateinit var songTitle: TextView
    private lateinit var songArtist: TextView
    private lateinit var songGenre: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnSearchMetadata: ImageButton
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnYoutube: ImageButton
    private lateinit var progressBar: ProgressBar
    
    private var currentSmbUrl: String? = null
    private var smbContext: CIFSContext? = null
    
    private var cachedSmbFile: SmbFile? = null
    private var cachedLength: Long = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_audio_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        albumArt = view.findViewById(R.id.albumArt)
        songTitle = view.findViewById(R.id.songTitle)
        songArtist = view.findViewById(R.id.songArtist)
        songGenre = view.findViewById(R.id.songGenre)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        btnSearchMetadata = view.findViewById(R.id.btnSearchMetadata)
        btnFavorite = view.findViewById(R.id.btnFavorite)
        btnYoutube = view.findViewById(R.id.btnYoutube)
        progressBar = view.findViewById(R.id.songProgressBar)

        // Focus iniziale
        btnPlayPause.post { btnPlayPause.requestFocus() }

        currentSmbUrl = arguments?.getString("SMB_URL")
        val user = arguments?.getString("SMB_USER")
        val pass = arguments?.getString("SMB_PASS")

        if (currentSmbUrl != null && user != null && pass != null) {
            updateFavoriteIcon()
            thread {
                try {
                    initSmb(user, pass)
                    val url = currentSmbUrl!!
                    cachedSmbFile = SmbFile(url, smbContext!!)
                    cachedLength = cachedSmbFile!!.length()
                    
                    activity?.runOnUiThread {
                        setupPlayer(url)
                    }
                } catch (e: Exception) {
                    Log.e("AudioPlayer", "Init error", e)
                }
            }
        }

        btnSearchMetadata.setOnClickListener { fetchMetadata() }
        btnPlayPause.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        btnFavorite.setOnClickListener { toggleFavorite() }
        btnYoutube.setOnClickListener { searchOnYoutube() }
    }

    private fun searchOnYoutube() {
        player?.pause()
        val title = songTitle.text.toString()
        val artist = songArtist.text.toString()
        val query = if (artist.isNotEmpty() && artist != "Artista sconosciuto") "$artist - $title" else title
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
        startActivity(intent)
    }

    private fun updateFavoriteIcon() {
        val context = context ?: return
        val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        val favorites = prefs.getStringSet("urls", null) ?: emptySet()
        val isFav = favorites.contains(currentSmbUrl)
        btnFavorite.setImageResource(if (isFav) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
        // Evidenzia di giallo se è nei preferiti, altrimenti bianco
        btnFavorite.setColorFilter(if (isFav) Color.YELLOW else Color.WHITE)
    }

    private fun toggleFavorite() {
        val url = currentSmbUrl ?: return
        val context = context ?: return
        val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        
        // Copia il set per evitare crash (non si può modificare direttamente il set restituito da getStringSet)
        val favorites = prefs.getStringSet("urls", null)?.toMutableSet() ?: mutableSetOf()
        
        if (favorites.contains(url)) {
            favorites.remove(url)
        } else {
            favorites.add(url)
        }
        
        prefs.edit().putStringSet("urls", favorites).apply()
        updateFavoriteIcon()
        
        // Notifica il MainFragment di aggiornare la lista se necessario
        (activity as? MainActivity)?.refreshFavorites()
    }

    private fun initSmb(user: String, pass: String) {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.enableSMB2", "true")
            setProperty("jcifs.smb.client.dfs.disabled", "true")
            setProperty("jcifs.smb.client.connTimeout", "5000")
            setProperty("jcifs.smb.client.responseTimeout", "5000")
        }
        smbContext = BaseContext(PropertyConfiguration(props))
            .withCredentials(NtlmPasswordAuthenticator(null, user, pass))
    }

    private fun setupPlayer(url: String) {
        if (!isAdded) return
        songTitle.text = url.substringAfterLast("/").replace(".mp3", "")

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 30000, 500, 1000)
            .build()

        val dataSourceFactory = DataSource.Factory { SmbDataSource(smbContext!!, url) }

        player = ExoPlayer.Builder(requireContext())
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(requireContext()).setDataSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            progressBar.max = duration.toInt()
                            btnSearchMetadata.post { btnSearchMetadata.requestFocus() }
                            updateProgress()
                        }
                    }
                })
                prepare()
                play()
            }
    }

    private fun updateProgress() {
        player?.let {
            progressBar.progress = it.currentPosition.toInt()
            if (it.isPlaying) {
                progressBar.postDelayed({ updateProgress() }, 1000)
            }
        }
    }

    private fun fetchMetadata() {
        val url = currentSmbUrl ?: return
        val context = smbContext ?: return
        Toast.makeText(requireContext(), "Lettura Tag ID3...", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val smbFile = cachedSmbFile ?: SmbFile(url, context)
                val length = if (cachedLength != -1L) cachedLength else smbFile.length()
                val smbRaf = SmbRandomAccessFile(smbFile, "r")
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(object : android.media.MediaDataSource() {
                    override fun getSize() = length
                    override fun readAt(pos: Long, b: ByteArray, off: Int, sz: Int): Int {
                        synchronized(this) { smbRaf.seek(pos); return smbRaf.read(b, off, sz) }
                    }
                    override fun close() { smbRaf.close() }
                })
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val art = retriever.embeddedPicture
                
                retriever.release()
                smbRaf.close()

                activity?.runOnUiThread {
                    if (title != null) songTitle.text = title
                    songArtist.text = artist ?: "Artista sconosciuto"
                    if (art != null) albumArt.setImageBitmap(BitmapFactory.decodeByteArray(art, 0, art.size))
                }
            } catch (e: Exception) { Log.e("Meta", "Error", e) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
    }

    private inner class SmbDataSource(val context: CIFSContext, val url: String) : BaseDataSource(true) {
        private var raf: SmbRandomAccessFile? = null
        private var opened = false
        private var bytesRem = 0L

        override fun open(ds: DataSpec): Long {
            if (cachedSmbFile == null) cachedSmbFile = SmbFile(url, context)
            if (cachedLength == -1L) cachedLength = cachedSmbFile!!.length()
            
            raf = SmbRandomAccessFile(cachedSmbFile!!, "r")
            raf!!.seek(ds.position)
            bytesRem = if (ds.length == C.LENGTH_UNSET.toLong()) cachedLength - ds.position else ds.length
            opened = true
            transferStarted(ds)
            return bytesRem
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (bytesRem == 0L) return C.RESULT_END_OF_INPUT
            val read = raf!!.read(b, off, Math.min(bytesRem, len.toLong()).toInt())
            if (read == -1) return C.RESULT_END_OF_INPUT
            bytesRem -= read
            bytesTransferred(read)
            return read
        }

        override fun getUri() = android.net.Uri.parse(url)
        override fun close() {
            if (opened) { opened = false; raf?.close(); raf = null; transferEnded() }
        }
    }

    companion object {
        fun newInstance(url: String, user: String, pass: String): AudioPlayerFragment {
            return AudioPlayerFragment().apply {
                arguments = Bundle().apply {
                    putString("SMB_URL", url)
                    putString("SMB_USER", user)
                    putString("SMB_PASS", pass)
                }
            }
        }
    }
}
