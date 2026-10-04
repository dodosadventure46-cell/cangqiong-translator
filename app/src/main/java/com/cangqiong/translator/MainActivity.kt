package com.cangqiong.translator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var player: MediaPlayer? = null
    private lateinit var etUrl: EditText
    private lateinit var tvStatus: TextView

    private val pickAudio = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            playUri(uri, "Memori: ${uri.lastPathSegment}")
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pickAudio.launch(arrayOf("audio/*"))
        else Toast.makeText(this, "Izin ditolak", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUrl = findViewById(R.id.etUrl)
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnUrl).setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Isi link dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            playUri(Uri.parse(url), "URL: $url")
        }

        findViewById<Button>(R.id.btnLocal).setOnClickListener {
            if (!hasAudioPermission()) requestAudioPermission()
            else pickAudio.launch(arrayOf("audio/*"))
        }

        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            player?.let {
                if (!it.isPlaying) it.start()
                tvStatus.text = "Putar"
            } ?: Toast.makeText(this, "Belum ada lagu", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnPause).setOnClickListener {
            player?.let {
                if (it.isPlaying) it.pause()
                tvStatus.text = "Jeda"
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            player?.let { it.stop(); it.release() }
            player = null
            tvStatus.text = "Stop"
        }
    }

    private fun playUri(uri: Uri, label: String) {
        try {
            player?.let { it.stop(); it.release() }
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                setOnPreparedListener {
                    it.start()
                    tvStatus.text = "Sedang diputar\n$label"
                }
                setOnErrorListener { _, what, extra ->
                    Toast.makeText(this@MainActivity,
                        "Error: $what / $extra", Toast.LENGTH_LONG).show()
                    tvStatus.text = "Gagal memutar"
                    true
                }
                prepareAsync()
            }
            tvStatus.text = "Memuat..."
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAudioPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        permLauncher.launch(perm)
    }

    override fun onDestroy() {
        player?.let { it.release() }
        player = null
        super.onDestroy()
    }
}
