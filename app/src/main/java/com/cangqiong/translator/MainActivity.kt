package com.cangqiong.translator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnStart: Button
    private lateinit var mpm: MediaProjectionManager

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val svc = Intent(this, BubbleService::class.java).apply {
                action = "START_CAPTURE"
                putExtra("code", result.resultCode)
                putExtra("data", result.data)
            }
            startService(svc)
            Toast.makeText(this, "Terjemahan aktif", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Izin rekam layar ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnStart = findViewById(R.id.btnStart)
        mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        btnStart.setOnClickListener { onToggle() }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateButtonText()
    }

    private fun updateButtonText() {
        btnStart.text = if (BubbleService.isRunning) "Matikan Bubble" else "Aktifkan Bubble"
    }

    private fun onToggle() {
        if (BubbleService.isRunning) {
            stopService(Intent(this, BubbleService::class.java))
            btnStart.postDelayed({ updateButtonText() }, 300)
        } else {
            startFlow()
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("request_capture", false) == true) {
            intent.removeExtra("request_capture")
            captureLauncher.launch(mpm.createScreenCaptureIntent())
        }
    }

    private fun startFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan 'Display over other apps'", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }

        try {
            startForegroundService(Intent(this, BubbleService::class.java))
            btnStart.postDelayed({ updateButtonText() }, 300)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) startFlow()
    }
}
