package com.igino.album

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
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
class AudioPlayerActivity : FragmentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var albumArt: ImageView
    private lateinit var songTitle: TextView
    private lateinit var songArtist: TextView
    private lateinit var songGenre: TextView
    private lateinit var btnSearchMetadata: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var progressBar: ProgressBar
    
    private var currentSmbUrl: String? = null
    private var smbContext: CIFSContext? = null
    
    // Cache per evitare chiamate di rete ripetitive sulla struttura del file
    private var cachedSmbFile: SmbFile? = null
    private var cachedLength: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)

        albumArt = findViewById(R.id.albumArt)
        songTitle = findViewById(R.id.songTitle)
        songArtist = findViewById(R.id.songArtist)
        songGenre = findViewById(R.id.songGenre)
        btnSearchMetadata = findViewById(R.id.btnSearchMetadata)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        progressBar = findViewById(R.id.songProgressBar)

        // Focus iniziale
        btnPlayPause.post { btnPlayPause.requestFocus() }

        val url = intent.getStringExtra("SMB_URL")
        val user = intent.getStringExtra("SMB_USER")
        val pass = intent.getStringExtra("SMB_PASS")
        currentSmbUrl = url

        if (url != null && user != null && pass != null) {
            thread {
                try {
                    initSmb(user, pass)
                    // Prefetch metadata background
                    cachedSmbFile = SmbFile(url, smbContext!!)
                    cachedLength = cachedSmbFile!!.length()
                    
                    runOnUiThread {
                        setupPlayer(url)
                    }
                } catch (e: Exception) {
                    Log.e("AudioPlayer", "Init error", e)
                }
            }
        } else {
            finish()
        }

        btnSearchMetadata.setOnClickListener { fetchMetadata() }
        btnPlayPause.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }
    }

    private fun initSmb(user: String, pass: String) {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.enableSMB2", "true")
            setProperty("jcifs.smb.client.dfs.disabled", "true") // Cruciale: evita ritardi di risoluzione DFS
            setProperty("jcifs.smb.client.connTimeout", "5000")
            setProperty("jcifs.smb.client.responseTimeout", "5000")
        }
        smbContext = BaseContext(PropertyConfiguration(props))
            .withCredentials(NtlmPasswordAuthenticator(null, user, pass))
    }

    private fun setupPlayer(url: String) {
        songTitle.text = url.substringAfterLast("/").replace(".mp3", "")

        // Configurazione LoadControl per avvio istantaneo (stile VLC)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // Buffer totale
                30000, // Max buffer
                500,   // Buffer necessario per far partire l'audio (0.5s invece di 2.5s)
                1000   // Buffer per ripartire dopo interruzione
            ).build()

        val dataSourceFactory = DataSource.Factory { SmbDataSource(smbContext!!, url) }

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
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
                            // All'avvio sposta il focus sulla ricerca tag
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
            if (it.isPlaying) progressBar.postDelayed({ updateProgress() }, 1000)
        }
    }

    private fun fetchMetadata() {
        val url = currentSmbUrl ?: return
        val context = smbContext ?: return
        Toast.makeText(this, "Lettura Tag ID3...", Toast.LENGTH_SHORT).show()
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
                
                // Rilascia risorse in background
                retriever.release()
                smbRaf.close()

                runOnUiThread {
                    if (title != null) songTitle.text = title
                    songArtist.text = artist ?: "Artista sconosciuto"
                    if (art != null) albumArt.setImageBitmap(BitmapFactory.decodeByteArray(art, 0, art.size))
                }
            } catch (e: Exception) { Log.e("Meta", "Error", e) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }

    private inner class SmbDataSource(val context: CIFSContext, val url: String) : BaseDataSource(true) {
        private var raf: SmbRandomAccessFile? = null
        private var opened = false
        private var bytesRem = 0L

        override fun open(ds: DataSpec): Long {
            // Ottimizzazione: recuperiamo file e lunghezza una sola volta per sessione
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
}
