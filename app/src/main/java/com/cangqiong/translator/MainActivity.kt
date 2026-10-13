package com.cangqiong.translator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : AppCompatActivity() {

    private lateinit var tvResult: TextView

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            readImageAndExtractText(uri)
        } else {
            Toast.makeText(this, "Tidak ada gambar yang dipilih", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pickImage.launch(arrayOf("image/*"))
        } else {
            Toast.makeText(this, "Izin akses gambar ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvResult = findViewById(R.id.tvResult)

        findViewById<Button>(R.id.btnPickImage).setOnClickListener {
            if (!hasImagePermission()) {
                requestImagePermission()
            } else {
                pickImage.launch(arrayOf("image/*"))
            }
        }

        findViewById<Button>(R.id.btnScanText).setOnClickListener {
            if (!hasImagePermission()) {
                requestImagePermission()
                return@setOnClickListener
            }
            Toast.makeText(this, "Pilih gambar komik untuk dipindai", Toast.LENGTH_SHORT).show()
            pickImage.launch(arrayOf("image/*"))
        }
    }

    private fun readImageAndExtractText(uri: Uri) {
        try {
            val inputImage = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text.ifBlank {
                        "Tidak ada teks yang terbaca di gambar ini."
                    }

                    tvResult.text = text
                    Toast.makeText(this, "Pindai selesai", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Gagal membaca teks: ${it.message}", Toast.LENGTH_LONG).show()
                    tvResult.text = "Gagal memindai teks dari gambar."
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka gambar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasImagePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestImagePermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(perm)
    }
}
