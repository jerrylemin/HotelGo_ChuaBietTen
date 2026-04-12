package com.example.hotelapp_test2.ui.features

import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.example.hotelapp_test2.BuildConfig
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.Room
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class RoomCrudActivity : BaseActivity() {
    private val selectedImages = mutableListOf<Uri>()
    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        selectedImages.clear()
        selectedImages.addAll(uris)
        imageStatus?.text = if (selectedImages.isEmpty()) {
            getString(R.string.room_image_none)
        } else {
            getString(R.string.room_image_selected, selectedImages.size)
        }
    }
    private var imageStatus: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_crud)
        setupToolbar(R.string.feature_room_crud_title, R.string.toolbar_room_crud_subtitle)
        if (!requireRole("admin")) return

        val codeInput = findViewById<TextInputEditText>(R.id.roomCodeInput)
        val typeInput = findViewById<TextInputEditText>(R.id.roomTypeInput)
        val priceInput = findViewById<TextInputEditText>(R.id.roomPriceInput)
        val imageInput = findViewById<TextInputEditText>(R.id.roomImageInput)
        imageStatus = findViewById(R.id.roomImageStatus)
        val imagePickButton = findViewById<MaterialButton>(R.id.roomImagePickButton)
        val statusGroup = findViewById<ChipGroup>(R.id.roomStatusGroup)
        val statusAvailable = findViewById<Chip>(R.id.roomStatusAvailable)
        val createButton = findViewById<MaterialButton>(R.id.roomCreateButton)
        val updateButton = findViewById<MaterialButton>(R.id.roomUpdateButton)
        val deleteButton = findViewById<MaterialButton>(R.id.roomDeleteButton)

        statusAvailable.isChecked = true

        fun currentStatus(): String {
            return if (statusGroup.checkedChipId == statusAvailable.id) "available" else "maintenance"
        }

        fun parseImages(raw: String): List<String> {
            return raw.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }

        fun refreshImageStatus() {
            imageStatus?.text = if (selectedImages.isEmpty()) {
                getString(R.string.room_image_none)
            } else {
                getString(R.string.room_image_selected, selectedImages.size)
            }
        }

        imagePickButton.setOnClickListener {
            pickImagesLauncher.launch("image/*")
        }

        refreshImageStatus()

        createButton.setOnClickListener {
            val code = codeInput.text?.toString().orEmpty().trim()
            val type = typeInput.text?.toString().orEmpty().trim()
            val price = priceInput.text?.toString().orEmpty().toDoubleOrNull() ?: 0.0
            val manualImages = parseImages(imageInput.text?.toString().orEmpty())
            if (code.isBlank() || type.isBlank()) {
                toast(getString(R.string.error_room_code_type_required))
                return@setOnClickListener
            }
            if (selectedImages.isNotEmpty()) {
                uploadImages(
                    code,
                    selectedImages,
                    onSuccess = { uploaded ->
                        val allImages = (uploaded + manualImages).distinct()
                        val room = Room(
                            id = code,
                            code = code,
                            type = type,
                            price = price,
                            images = allImages,
                            status = currentStatus()
                        )
                        SupabaseRepository.createRoom(
                            room = room,
                            onSuccess = { toast(getString(R.string.success_room_created, code)) },
                            onError = { error -> toast(getString(R.string.error_room_create, error.message.orEmpty())) }
                        )
                    },
                    onError = { error ->
                        toast(getString(R.string.error_image_upload, error.message.orEmpty()))
                    }
                )
            } else {
                val room = Room(
                    id = code,
                    code = code,
                    type = type,
                    price = price,
                    images = manualImages,
                    status = currentStatus()
                )
                SupabaseRepository.createRoom(
                    room = room,
                    onSuccess = { toast(getString(R.string.success_room_created, code)) },
                    onError = { error -> toast(getString(R.string.error_room_create, error.message.orEmpty())) }
                )
            }
        }

        updateButton.setOnClickListener {
            val code = codeInput.text?.toString().orEmpty().trim()
            val type = typeInput.text?.toString().orEmpty().trim()
            val price = priceInput.text?.toString().orEmpty().toDoubleOrNull() ?: 0.0
            val manualImages = parseImages(imageInput.text?.toString().orEmpty())
            if (code.isBlank()) {
                toast(getString(R.string.error_room_code_required))
                return@setOnClickListener
            }
            if (selectedImages.isNotEmpty()) {
                uploadImages(
                    code,
                    selectedImages,
                    onSuccess = { uploaded ->
                        val allImages = (uploaded + manualImages).distinct()
                        val room = Room(
                            id = code,
                            code = code,
                            type = type,
                            price = price,
                            images = allImages,
                            status = currentStatus()
                        )
                        SupabaseRepository.updateRoom(
                            room = room,
                            onSuccess = { toast(getString(R.string.success_room_updated, code)) },
                            onError = { error -> toast(getString(R.string.error_room_update_short, error.message.orEmpty())) }
                        )
                    },
                    onError = { error ->
                        toast(getString(R.string.error_image_upload, error.message.orEmpty()))
                    }
                )
            } else {
                val room = Room(
                    id = code,
                    code = code,
                    type = type,
                    price = price,
                    images = manualImages,
                    status = currentStatus()
                )
                SupabaseRepository.updateRoom(
                    room = room,
                    onSuccess = { toast(getString(R.string.success_room_updated, code)) },
                    onError = { error -> toast(getString(R.string.error_room_update_short, error.message.orEmpty())) }
                )
            }
        }

        deleteButton.setOnClickListener {
            val code = codeInput.text?.toString().orEmpty().trim()
            SupabaseRepository.deleteRoomByCode(
                code = code,
                onSuccess = { toast(getString(R.string.success_room_deleted, code)) },
                onError = { error -> toast(getString(R.string.error_room_delete, error.message.orEmpty())) }
            )
        }
    }

    private fun uploadImages(
        roomCode: String,
        uris: List<Uri>,
        onSuccess: (List<String>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        if (baseUrl.isBlank() || anonKey.isBlank()) {
            onError(IllegalStateException(getString(R.string.error_supabase_missing)))
            return
        }
        val bucket = "rooms"

        Thread {
            val results = mutableListOf<String>()
            try {
                uris.forEach { uri ->
                    val mime = contentResolver.getType(uri) ?: "image/jpeg"
                    val ext = when {
                        mime.endsWith("png") -> "png"
                        mime.endsWith("webp") -> "webp"
                        mime.endsWith("gif") -> "gif"
                        else -> "jpg"
                    }
                    val objectPath = "$roomCode/${UUID.randomUUID()}.$ext"
                    val uploadUrl = URL("$baseUrl/storage/v1/object/$bucket/$objectPath")
                    val connection = (uploadUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Content-Type", mime)
                        setRequestProperty("apikey", anonKey)
                        setRequestProperty("Authorization", "Bearer $anonKey")
                        setRequestProperty("x-upsert", "true")
                    }
                    contentResolver.openInputStream(uri)?.use { input ->
                        connection.outputStream.use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IllegalStateException(getString(R.string.error_image_read))

                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        throw IllegalStateException(getString(R.string.error_image_upload_code, responseCode))
                    }
                    val publicUrl = "$baseUrl/storage/v1/object/public/$bucket/$objectPath"
                    results.add(publicUrl)
                }
                runOnUiThread { onSuccess(results) }
            } catch (error: Exception) {
                runOnUiThread { onError(error) }
            }
        }.start()
    }
}
