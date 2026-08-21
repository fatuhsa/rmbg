package com.rmbg.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rmbg.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedFile: File? = null
    private var resultBitmap: Bitmap? = null
    private var resultBytes: ByteArray? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val prefs by lazy { getSharedPreferences("rmbg_prefs", MODE_PRIVATE) }
    private val defaultApiUrl = "http://10.0.2.2:8000/remove-bg"

    private fun getApiUrl(): String {
        return prefs.getString("server_url", defaultApiUrl) ?: defaultApiUrl
    }

    private fun setApiUrl(url: String) {
        prefs.edit().putString("server_url", url).apply()
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { imageUri ->
            lifecycleScope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        val tempFile = File(cacheDir, "selected_image_${System.currentTimeMillis()}.jpg")
                        contentResolver.openInputStream(imageUri)?.use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        tempFile
                    }
                    selectedFile = file
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    }
                    binding.contentMain.imageView.setImageBitmap(bitmap)
                    binding.contentMain.statusText.text = getString(R.string.status_image_selected)
                } catch (e: Exception) {
                    binding.contentMain.statusText.text = "Failed to load image: ${e.localizedMessage}"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.contentMain.btnSelectImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.contentMain.btnRemoveBg.setOnClickListener {
            val file = selectedFile
            if (file != null && file.exists()) {
                uploadImage(file)
            } else {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
            }
        }

        binding.contentMain.btnSave.setOnClickListener {
            val bytes = resultBytes
            if (bytes != null && resultBitmap != null) {
                saveResultToGallery(bytes)
            } else {
                Toast.makeText(this, "No processed image to save", Toast.LENGTH_SHORT).show()
            }
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_settings) {
                showServerSettingsDialog()
                true
            } else {
                false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private fun showServerSettingsDialog() {
        val input = EditText(this).apply {
            setText(getApiUrl())
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Server API URL")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    setApiUrl(newUrl)
                    Toast.makeText(this, "API URL updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setProcessingState(isProcessing: Boolean) {
        binding.contentMain.progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
        binding.contentMain.btnSelectImage.isEnabled = !isProcessing
        binding.contentMain.btnRemoveBg.isEnabled = !isProcessing
        binding.contentMain.btnSave.isEnabled = !isProcessing
    }

    private fun uploadImage(file: File) {
        lifecycleScope.launch {
            setProcessingState(true)
            binding.contentMain.statusText.text = "Uploading and removing background..."

            try {
                val apiUrl = getApiUrl()
                val (success, message, bytes) = withContext(Dispatchers.IO) {
                    val requestBody = file.asRequestBody("image/*".toMediaType())
                    val multipart = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", file.name, requestBody)
                        .build()

                    val request = Request.Builder()
                        .url(apiUrl)
                        .post(multipart)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyBytes = response.body?.bytes()
                            if (bodyBytes != null && bodyBytes.isNotEmpty()) {
                                Triple(true, "Done! Background removed successfully.", bodyBytes)
                            } else {
                                Triple(false, "Server returned empty response", null)
                            }
                        } else {
                            val errBody = response.body?.string() ?: response.message
                            Triple(false, "Server error (${response.code}): $errBody", null)
                        }
                    }
                }

                if (success && bytes != null) {
                    resultBytes = bytes
                    val bitmap = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    resultBitmap = bitmap
                    binding.contentMain.resultImageView.setImageBitmap(bitmap)
                    binding.contentMain.statusText.text = message
                } else {
                    binding.contentMain.statusText.text = message
                }
            } catch (e: Exception) {
                binding.contentMain.statusText.text = "Error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                setProcessingState(false)
            }
        }
    }

    private fun saveResultToGallery(bytes: ByteArray) {
        lifecycleScope.launch {
            binding.contentMain.statusText.text = "Saving image..."
            try {
                val success = withContext(Dispatchers.IO) {
                    val filename = "rmbg_${System.currentTimeMillis()}.png"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RMBG")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }

                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.use { output: OutputStream ->
                            output.write(bytes)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            contentResolver.update(uri, contentValues, null, null)
                        }
                        true
                    } else {
                        false
                    }
                }

                if (success) {
                    binding.contentMain.statusText.text = "Saved to Gallery / Pictures / RMBG"
                    Toast.makeText(this@MainActivity, "Image saved to Gallery!", Toast.LENGTH_SHORT).show()
                } else {
                    binding.contentMain.statusText.text = "Failed to save image"
                    Toast.makeText(this@MainActivity, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.contentMain.statusText.text = "Error saving image: ${e.localizedMessage}"
            }
        }
    }
}
