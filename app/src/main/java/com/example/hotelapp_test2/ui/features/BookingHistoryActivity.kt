package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.model.AppNotification
import com.example.hotelapp_test2.data.model.Payment
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast

class BookingHistoryActivity : BaseActivity() {
    private lateinit var adapter: BookingHistoryAdapter
    private var isAdmin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_history)
        setupToolbar(R.string.booking_history_title, R.string.toolbar_booking_history_subtitle)

        val titleText = findViewById<TextView>(R.id.bookingHistoryTitle)
        val subtitleText = findViewById<TextView>(R.id.bookingHistorySubtitle)
        val emptyText = findViewById<TextView>(R.id.bookingHistoryEmpty)
        val listView = findViewById<RecyclerView>(R.id.bookingHistoryList)

        val role = SessionManager.getRole(this)
        isAdmin = role == "admin"
        titleText.text = getString(if (isAdmin) R.string.feature_booking_history_admin_title else R.string.feature_booking_history_client_title)
        subtitleText.text = if (isAdmin) {
            getString(R.string.feature_booking_history_admin_subtitle)
        } else {
            getString(R.string.booking_history_client_subtitle)
        }

        adapter = BookingHistoryAdapter(
            isAdmin = isAdmin,
            onClientCancel = { booking ->
                if (booking.id.isBlank()) {
                    toast(getString(R.string.error_booking_id_missing))
                    return@BookingHistoryAdapter
                }
                val canCancel = canCancelBooking(booking.checkIn)
                if (!canCancel) {
                    toast(getString(R.string.error_cancel_too_late))
                    return@BookingHistoryAdapter
                }
                val refundAmount = booking.total * 0.8
                SupabaseRepository.updateBookingStatus(
                    bookingId = booking.id,
                    status = "cancelled",
                    onSuccess = {
                        SupabaseRepository.createPayment(
                            payment = Payment(
                                bookingId = booking.id,
                                userId = booking.userId,
                                amount = refundAmount,
                                method = "Refund",
                                status = "refunded"
                            ),
                            onSuccess = {},
                            onError = {}
                        )
                        toast(getString(R.string.success_booking_cancel_refund, refundAmount.toInt()))
                        loadBookings(emptyText)
                    },
                    onError = { error ->
                        toast(getString(R.string.error_booking_cancel, error.message.orEmpty()))
                    }
                )
            },
            onAdminConfirm = { booking ->
                if (booking.id.isBlank()) {
                    toast(getString(R.string.error_booking_id_missing))
                    return@BookingHistoryAdapter
                }
                SupabaseRepository.updateBookingStatus(
                    bookingId = booking.id,
                    status = "confirmed",
                    onSuccess = {
                        toast(getString(R.string.success_booking_confirmed))
                        SupabaseRepository.createNotification(
                            AppNotification(
                                userId = booking.userId,
                                title = getString(R.string.booking_confirm_notification_title),
                                body = getString(R.string.booking_confirm_notification_body, SupabaseRepository.shortBookingCode(booking.id)),
                                type = "booking",
                                relatedId = booking.id,
                                targetRole = "client"
                            ),
                            onSuccess = {},
                            onError = {}
                        )
                        loadBookings(emptyText)
                    },
                    onError = { error ->
                        toast(getString(R.string.error_booking_confirm, error.message.orEmpty()))
                    }
                )
            },
            onAdminCancel = { booking ->
                if (booking.id.isBlank()) {
                    toast(getString(R.string.error_booking_id_missing))
                    return@BookingHistoryAdapter
                }
                SupabaseRepository.updateBookingStatus(
                    bookingId = booking.id,
                    status = "cancelled",
                    onSuccess = {
                        toast(getString(R.string.success_booking_cancelled))
                        SupabaseRepository.createNotification(
                            AppNotification(
                                userId = booking.userId,
                                title = getString(R.string.booking_cancel_notification_title),
                                body = getString(R.string.booking_cancel_notification_body, SupabaseRepository.shortBookingCode(booking.id)),
                                type = "booking",
                                relatedId = booking.id,
                                targetRole = "client"
                            ),
                            onSuccess = {},
                            onError = {}
                        )
                        loadBookings(emptyText)
                    },
                    onError = { error ->
                        toast(getString(R.string.error_booking_cancel, error.message.orEmpty()))
                    }
                )
            }
        )

        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = adapter

        loadBookings(emptyText)
    }

    private fun loadBookings(emptyText: TextView) {
        val userId = if (isAdmin) null else SupabaseRepository.currentUser()?.uid.orEmpty()
        if (!isAdmin && userId.isNullOrBlank()) {
            toast(getString(R.string.error_login_required))
            return
        }
        SupabaseRepository.listBookings(
            userId = userId,
            onSuccess = { bookings ->
                adapter.submitList(bookings)
                emptyText.visibility = if (bookings.isEmpty()) View.VISIBLE else View.GONE
                if (bookings.isNotEmpty()) {
                    loadRoomsForBookings()
                }
            },
            onError = { error ->
                toast(getString(R.string.error_booking_history, error.message.orEmpty()))
            }
        )
    }

    private fun loadRoomsForBookings() {
        SupabaseRepository.searchRooms(
            queryText = "",
            onSuccess = { rooms ->
                val map = mutableMapOf<String, com.example.hotelapp_test2.data.model.Room>()
                rooms.forEach { room ->
                    if (room.id.isNotBlank()) {
                        map[room.id] = room
                    }
                    if (room.code.isNotBlank()) {
                        map[room.code] = room
                    }
                }
                adapter.updateRooms(map)
            },
            onError = {
                // Ignore room lookup failure, booking list still shows.
            }
        )
    }

    private fun canCancelBooking(checkIn: String): Boolean {
        return runCatching {
            val today = java.time.LocalDate.now()
            val checkInDate = java.time.LocalDate.parse(checkIn)
            java.time.temporal.ChronoUnit.DAYS.between(today, checkInDate) >= 2
        }.getOrDefault(false)
    }
}
