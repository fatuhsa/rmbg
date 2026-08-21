package com.rmbg.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedFile: File? = null
    private val client = OkHttpClient()
    private val API_URL = "http://YOUR_SERVER_IP:8000/remove-bg"

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            contentResolver.openInputStream(it)?.use { input ->
                val file = File(cacheDir, "selected_image.jpg")
                file.outputStream().use { output -> input.copyTo(output) }
                selectedFile = file
                binding.imageView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                binding.statusText.text = "Image selected. Tap 'Remove BG' to process."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSelectImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.btnRemoveBg.setOnClickListener {
            selectedFile?.let { file ->
                binding.statusText.text = "Processing..."
                uploadImage(file)
            } ?: run {
                Toast.makeText(this, "Select an image first", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSave.setOnClickListener {
            // Save functionality
        }
    }

    private fun uploadImage(file: File) {
        lifecycleScope.launch {
            try {
                val requestBody = file.asRequestBody("image/*".toMediaType())
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name, requestBody)
                    .build()

                val request = Request.Builder()
                    .url(API_URL)
                    .post(multipart)
                    .build()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val bytes = withContext(Dispatchers.IO) {
                        response.body?.bytes() ?: throw Exception("Empty response")
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    binding.resultImageView.setImageBitmap(bitmap)
                    binding.statusText.text = "Done! Background removed."

                    val resultFile = File(cacheDir, "result.png")
                    resultFile.outputStream().use { it.write(bytes) }
                } else {
                    binding.statusText.text = "Error: ${response.message}"
                }
            } catch (e: Exception) {
                binding.statusText.text = "Error: ${e.message}"
            }
        }
    }
}
