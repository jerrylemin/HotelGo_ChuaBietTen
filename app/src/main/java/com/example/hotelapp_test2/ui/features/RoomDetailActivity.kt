package com.example.hotelapp_test2.ui.features

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.AddOnItem
import com.example.hotelapp_test2.data.model.AppNotification
import com.example.hotelapp_test2.data.model.Booking
import com.example.hotelapp_test2.data.model.Room
import com.example.hotelapp_test2.data.model.Voucher
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar

class RoomDetailActivity : BaseActivity() {
    private var addOnItems: List<AddOnItem> = emptyList()
    private val selectedAddOnIds = linkedSetOf<String>()
    private var selectedVoucher: Voucher? = null

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
        val hotelName = findViewById<TextView>(R.id.roomDetailHotelName)
        val info = findViewById<TextView>(R.id.roomDetailInfo)
        val price = findViewById<TextView>(R.id.roomDetailPrice)
        val reviewSummary = findViewById<TextView>(R.id.roomDetailReviewSummary)
        val reviewList = findViewById<TextView>(R.id.roomDetailReviewList)
        val openHotelButton = findViewById<MaterialButton>(R.id.roomDetailOpenHotelButton)
        val checkInInput = findViewById<TextView>(R.id.roomDetailCheckIn)
        val checkOutInput = findViewById<TextView>(R.id.roomDetailCheckOut)
        val guestsInput = findViewById<TextInputEditText>(R.id.roomDetailGuests)
        val totalText = findViewById<TextView>(R.id.roomDetailTotal)
        val addOnContainer = findViewById<LinearLayout>(R.id.roomDetailAddOnContainer)
        val voucherInput = findViewById<TextInputEditText>(R.id.roomDetailVoucherCode)
        val voucherButton = findViewById<MaterialButton>(R.id.roomDetailVoucherApplyButton)
        val bookButton = findViewById<MaterialButton>(R.id.roomDetailBookButton)

        fun bindRoom(room: Room) {
            val code = room.code.ifBlank { room.id.ifBlank { getString(R.string.common_na) } }
            val displayType = room.displayType.ifBlank { room.type.ifBlank { getString(R.string.room_default_type) } }
            title.text = getString(R.string.room_title_format, code, displayType)
            val ratingText = if (room.rating > 0.0) {
                getString(R.string.room_rating_format, room.rating)
            } else {
                getString(R.string.room_no_rating)
            }
            info.text = getString(R.string.room_info_format, room.capacity, ratingText, statusLabel(room.status))
            price.text = if (room.price > 0.0) getString(R.string.room_price_per_night, room.price.toInt()) else getString(R.string.room_price_empty)
            loadReviews(room.id.ifBlank { room.code }, reviewSummary, reviewList)
            val imageUrl = room.images.firstOrNull().orEmpty()
            image.load(imageUrl.ifBlank { null }) {
                placeholder(R.mipmap.ic_launcher)
                error(R.mipmap.ic_launcher)
                crossfade(true)
            }
            if (room.hotelId.isNotBlank() && room.hotelName.isNotBlank()) {
                val openHotel = {
                    startActivity(
                        Intent(this, HotelDetailActivity::class.java)
                            .putExtra(HotelDetailActivity.EXTRA_HOTEL_ID, room.hotelId)
                    )
                }
                hotelName.visibility = View.VISIBLE
                hotelName.text = getString(R.string.room_detail_hotel_label, room.hotelName)
                hotelName.setOnClickListener { openHotel() }
                openHotelButton.visibility = View.VISIBLE
                openHotelButton.setOnClickListener { openHotel() }
            } else {
                hotelName.visibility = View.GONE
                hotelName.text = ""
                hotelName.setOnClickListener(null)
                openHotelButton.visibility = View.GONE
                openHotelButton.setOnClickListener(null)
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
                val addOnTotal = selectedAddOns().sumOf { it.price }
                val subtotal = total + addOnTotal
                val discount = discountFor(subtotal)
                totalText.text = getString(R.string.booking_total_with_discount, total.toInt(), addOnTotal.toInt(), discount.toInt(), (subtotal - discount).coerceAtLeast(0.0).toInt())
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
            loadAddOns(addOnContainer) { updateTotal() }
            voucherButton.setOnClickListener {
                val codeText = voucherInput.text?.toString().orEmpty().trim()
                if (codeText.isBlank()) {
                    selectedVoucher = null
                    updateTotal()
                    return@setOnClickListener
                }
                SupabaseRepository.getVoucherByCode(
                    code = codeText,
                    onSuccess = { voucher ->
                        selectedVoucher = voucher
                        toast(if (voucher == null) getString(R.string.voucher_not_found) else getString(R.string.voucher_ready, voucher.code))
                        updateTotal()
                    },
                    onError = { error -> toast(getString(R.string.voucher_check_error, error.message.orEmpty())) }
                )
            }

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
                val roomTotal = room.price * nights
                val addOnTotal = selectedAddOns().sumOf { it.price }
                val subtotal = roomTotal + addOnTotal
                val discount = discountFor(subtotal)
                if (selectedVoucher != null && discount <= 0.0) {
                    toast(getString(R.string.voucher_invalid))
                    return@setOnClickListener
                }
                val total = (subtotal - discount).coerceAtLeast(0.0)

                val booking = Booking(
                    userId = userId,
                    roomId = room.id.ifBlank { room.code },
                    checkIn = checkIn,
                    checkOut = checkOut,
                    status = "pending",
                    total = total,
                    addOns = selectedAddOnIds.toList() + listOfNotNull(selectedVoucher?.code?.takeIf { it.isNotBlank() }?.let { "voucher:$it" })
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

    private fun loadAddOns(container: LinearLayout, onChanged: () -> Unit) {
        SupabaseRepository.listAddOns(
            onSuccess = { items ->
                addOnItems = items.filter { it.active }
                container.removeAllViews()
                if (addOnItems.isEmpty()) {
                    val empty = TextView(this).apply {
                        text = getString(R.string.addon_empty)
                        setTextColor(getColor(R.color.text_secondary))
                        textSize = 13f
                    }
                    container.addView(empty)
                } else {
                    addOnItems.forEach { item ->
                        val checkBox = MaterialCheckBox(this).apply {
                            text = getString(R.string.addon_client_item, item.name, item.price.toInt())
                            isChecked = selectedAddOnIds.contains(item.id)
                            setOnCheckedChangeListener { _, checked ->
                                if (checked) selectedAddOnIds.add(item.id) else selectedAddOnIds.remove(item.id)
                                onChanged()
                            }
                        }
                        container.addView(checkBox)
                    }
                }
                onChanged()
            },
            onError = { error -> toast(getString(R.string.error_addon_load, error.message.orEmpty())) }
        )
    }

    private fun selectedAddOns(): List<AddOnItem> = addOnItems.filter { selectedAddOnIds.contains(it.id) }

    private fun discountFor(subtotal: Double): Double {
        val voucher = selectedVoucher ?: return 0.0
        if (!voucher.active || subtotal < voucher.minSpend || voucher.usageLimit == 0) return 0.0
        val today = LocalDate.now()
        val startOk = runCatching { !today.isBefore(LocalDate.parse(voucher.startAt)) }.getOrDefault(true)
        val endOk = runCatching { !today.isAfter(LocalDate.parse(voucher.endAt)) }.getOrDefault(true)
        if (!startOk || !endOk) return 0.0
        return if (voucher.type.equals("percent", true)) {
            subtotal * voucher.value.coerceIn(0.0, 100.0) / 100.0
        } else {
            voucher.value.coerceAtMost(subtotal)
        }
    }

    private fun loadReviews(roomId: String, summaryView: TextView, listView: TextView) {
        if (roomId.isBlank()) {
            summaryView.text = getString(R.string.review_average_empty)
            listView.text = getString(R.string.review_empty)
            return
        }
        SupabaseRepository.listReviewsForRoom(
            roomId = roomId,
            onSuccess = { reviews ->
                if (reviews.isEmpty()) {
                    summaryView.text = getString(R.string.review_average_empty)
                    listView.text = getString(R.string.review_empty)
                } else {
                    val average = reviews.map { it.rating.coerceIn(1, 5) }.average()
                    summaryView.text = getString(R.string.review_average_format, average, reviews.size)
                    listView.text = reviews.joinToString("\n\n") { review ->
                        getString(R.string.review_room_detail_item, review.rating.coerceIn(1, 5), review.comment)
                    }
                }
            },
            onError = { error ->
                summaryView.text = getString(R.string.review_average_empty)
                listView.text = getString(R.string.error_review_load, error.message.orEmpty())
            }
        )
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
