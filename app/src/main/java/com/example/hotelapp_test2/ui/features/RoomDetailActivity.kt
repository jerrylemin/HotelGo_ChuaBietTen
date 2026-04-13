package com.example.hotelapp_test2.ui.features

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import coil.load
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.AppNotification
import com.example.hotelapp_test2.data.model.Booking
import com.example.hotelapp_test2.data.model.Room
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar

class RoomDetailActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_detail)
        setupToolbar(R.string.room_detail_title, R.string.toolbar_booking_subtitle)
        if (!requireRole("client")) return

        val roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty()
        val roomCode = intent.getStringExtra(EXTRA_ROOM_CODE).orEmpty()
        if (roomId.isBlank() && roomCode.isBlank()) {
            toast(getString(R.string.error_room_not_found_simple))
            finish()
            return
        }

        val image = findViewById<ImageView>(R.id.roomDetailImage)
        val title = findViewById<TextView>(R.id.roomDetailTitle)
        val info = findViewById<TextView>(R.id.roomDetailInfo)
        val price = findViewById<TextView>(R.id.roomDetailPrice)
        val checkInInput = findViewById<TextView>(R.id.roomDetailCheckIn)
        val checkOutInput = findViewById<TextView>(R.id.roomDetailCheckOut)
        val guestsInput = findViewById<TextInputEditText>(R.id.roomDetailGuests)
        val totalText = findViewById<TextView>(R.id.roomDetailTotal)
        val bookButton = findViewById<MaterialButton>(R.id.roomDetailBookButton)

        fun bindRoom(room: Room) {
            val code = room.code.ifBlank { room.id.ifBlank { getString(R.string.common_na) } }
            title.text = getString(R.string.room_title_format, code, room.type.ifBlank { getString(R.string.room_default_type) })
            val ratingText = if (room.rating > 0.0) {
                getString(R.string.room_rating_format, room.rating)
            } else {
                getString(R.string.room_no_rating)
            }
            info.text = getString(R.string.room_info_format, room.capacity, ratingText, statusLabel(room.status))
            price.text = if (room.price > 0.0) getString(R.string.room_price_per_night, room.price.toInt()) else getString(R.string.room_price_empty)
            val imageUrl = room.images.firstOrNull().orEmpty()
            image.load(imageUrl.ifBlank { null }) {
                placeholder(R.mipmap.ic_launcher)
                error(R.mipmap.ic_launcher)
                crossfade(true)
            }

            fun updateTotal() {
                val inText = checkInInput.text?.toString().orEmpty().trim()
                val outText = checkOutInput.text?.toString().orEmpty().trim()
                if (inText.isBlank() || outText.isBlank()) {
                    totalText.text = getString(R.string.booking_total_empty)
                    return
                }
                val nights = runCatching {
                    val inDate = LocalDate.parse(inText)
                    val outDate = LocalDate.parse(outText)
                    val diff = ChronoUnit.DAYS.between(inDate, outDate)
                    if (diff <= 0) 1 else diff
                }.getOrDefault(1)
                val total = room.price * nights
                totalText.text = getString(R.string.booking_total_format, total.toInt())
            }

            fun showDatePicker(target: TextView) {
                val calendar = Calendar.getInstance()
                val dialog = DatePickerDialog(
                    this,
                    { _, year, month, day ->
                        val monthValue = (month + 1).toString().padStart(2, '0')
                        val dayValue = day.toString().padStart(2, '0')
                        target.setText("$year-$monthValue-$dayValue")
                        updateTotal()
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
                dialog.show()
            }

            checkInInput.setOnClickListener { showDatePicker(checkInInput) }
            checkOutInput.setOnClickListener { showDatePicker(checkOutInput) }

            bookButton.setOnClickListener {
                val userId = SupabaseRepository.currentUser()?.uid.orEmpty()
                if (userId.isBlank()) {
                    toast(getString(R.string.error_login_required))
                    return@setOnClickListener
                }
                val checkIn = checkInInput.text?.toString().orEmpty().trim()
                val checkOut = checkOutInput.text?.toString().orEmpty().trim()
                val guests = guestsInput.text?.toString().orEmpty().toIntOrNull() ?: 1
                if (checkIn.isBlank() || checkOut.isBlank()) {
                    toast(getString(R.string.error_date_required))
                    return@setOnClickListener
                }
                val nights = runCatching {
                    val inDate = LocalDate.parse(checkIn)
                    val outDate = LocalDate.parse(checkOut)
                    val diff = ChronoUnit.DAYS.between(inDate, outDate)
                    if (diff <= 0) 1 else diff
                }.getOrDefault(1)
                val total = room.price * nights

                val booking = Booking(
                    userId = userId,
                    roomId = room.id.ifBlank { room.code },
                    checkIn = checkIn,
                    checkOut = checkOut,
                    status = "pending",
                    total = total,
                    addOns = emptyList()
                )
                SupabaseRepository.createBooking(
                    booking = booking,
                    onSuccess = {
                        toast(getString(R.string.success_booking_created))
                        SupabaseRepository.createNotification(
                            AppNotification(
                                title = getString(R.string.booking_notification_title),
                                body = getString(R.string.booking_notification_body, room.code.ifBlank { room.id }),
                                targetRole = "admin"
                            ),
                            onSuccess = {},
                            onError = {}
                        )
                        finish()
                    },
                    onError = { error ->
                        toast(getString(R.string.error_booking_create, error.message.orEmpty()))
                    }
                )
            }
        }

        if (roomCode.isNotBlank()) {
            loadByCode(roomCode, ::bindRoom)
        } else if (roomId.isNotBlank()) {
            loadByCode(roomId, ::bindRoom)
        } else {
            toast(getString(R.string.error_room_not_found_simple))
            finish()
        }
    }

    private fun loadByCode(code: String, onLoaded: (Room) -> Unit) {
        SupabaseRepository.getRoomByCode(
            code = code,
            onSuccess = { room ->
                if (room == null) {
                    toast(getString(R.string.error_room_not_found_simple))
                    finish()
                    return@getRoomByCode
                }
                onLoaded(room)
            },
            onError = { error ->
                toast(getString(R.string.error_room_load, error.message.orEmpty()))
            }
        )
    }

    private fun statusLabel(status: String): String {
        return when (status) {
            "available" -> getString(R.string.status_available)
            "maintenance" -> getString(R.string.status_maintenance)
            "occupied" -> getString(R.string.status_occupied)
            else -> status
        }
    }

    companion object {
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_ROOM_CODE = "room_code"
    }
}
