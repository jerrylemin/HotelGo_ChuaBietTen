package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.Booking
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup

/**
 * Admin Check-in & Check-out screen (fully redesigned).
 *
 * Flow:
 * 1. Screen auto-loads all relevant bookings from Supabase.
 * 2. Admin can filter by stay status using chip tabs.
 * 3. Tapping a card selects it and shows the action panel at the bottom.
 * 4. Check-in button is only enabled when stay_status == pending_checkin.
 * 5. Check-out button is only enabled when stay_status == checked_in (or overdue).
 * 6. Both buttons are disabled for cancelled / already checked-out stays.
 */
class CheckInOutActivity : BaseActivity() {

    private lateinit var adapter: AdminBookingAdapter
    private lateinit var listView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var actionPanel: MaterialCardView
    private lateinit var actionPanelTitle: TextView
    private lateinit var actionPanelDetails: TextView
    private lateinit var actionPanelOverdue: TextView
    private lateinit var checkInButton: MaterialButton
    private lateinit var checkOutButton: MaterialButton
    private lateinit var filterChipGroup: ChipGroup

    private var allBookings: List<Booking> = emptyList()
    private var selectedBooking: Booking? = null
    private var currentFilter: String = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_in_out)
        setupToolbar(R.string.feature_checkin_title, R.string.toolbar_checkin_subtitle)
        if (!requireRole("admin")) return

        // Bind views
        listView = findViewById(R.id.adminBookingList)
        emptyText = findViewById(R.id.checkInEmptyText)
        actionPanel = findViewById(R.id.actionPanel)
        actionPanelTitle = findViewById(R.id.actionPanelTitle)
        actionPanelDetails = findViewById(R.id.actionPanelDetails)
        actionPanelOverdue = findViewById(R.id.actionPanelOverdue)
        checkInButton = findViewById(R.id.checkInButton)
        checkOutButton = findViewById(R.id.checkOutButton)
        filterChipGroup = findViewById(R.id.filterChipGroup)

        // Setup adapter
        adapter = AdminBookingAdapter { booking ->
            onBookingSelected(booking)
        }
        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = adapter

        // Filter chip listener
        filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                R.id.chipPendingCheckin -> "pending_checkin"
                R.id.chipCheckedIn -> "checked_in"
                R.id.chipCheckedOut -> "checked_out"
                R.id.chipOverdue -> "overdue"
                R.id.chipCancelled -> "cancelled"
                else -> "all"
            }
            applyFilter()
        }

        // Action buttons
        checkInButton.setOnClickListener { performCheckIn() }
        checkOutButton.setOnClickListener { performCheckOut() }

        // Initial load
        loadBookings()
    }

    // ─── Load ────────────────────────────────────────────────────────────────

    private fun loadBookings() {
        SupabaseRepository.listAdminBookings(
            statusFilter = null,
            onSuccess = { bookings ->
                allBookings = bookings
                applyFilter()
                loadRoomsForBookings()
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
                val map = buildMap<String, com.example.hotelapp_test2.data.model.Room> {
                    rooms.forEach { room ->
                        if (room.id.isNotBlank()) put(room.id, room)
                        if (room.code.isNotBlank()) put(room.code, room)
                    }
                }
                adapter.roomLookup = map
                // Refresh selected booking panel if room data now available
                selectedBooking?.let { onBookingSelected(it) }
            },
            onError = { /* silent – room names are best-effort */ }
        )
    }

    // ─── Filter ──────────────────────────────────────────────────────────────

    private fun applyFilter() {
        val today = runCatching { java.time.LocalDate.now() }.getOrNull()
        val filtered = allBookings.filter { booking ->
            val status = SupabaseRepository.resolveStayStatus(booking)
            when (currentFilter) {
                "all" -> true
                "overdue" -> status == "overdue"
                "pending_checkin" -> status == "pending_checkin"
                "checked_in" -> status == "checked_in"
                "checked_out" -> status == "checked_out"
                "cancelled" -> status == "cancelled"
                else -> true
            }
        }
        adapter.submitList(filtered)
        emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        listView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    // ─── Selection ───────────────────────────────────────────────────────────

    private fun onBookingSelected(booking: Booking) {
        selectedBooking = booking
        val stayStatus = SupabaseRepository.resolveStayStatus(booking)

        // Action panel title
        actionPanelTitle.text = getString(
            R.string.checkin_selected_booking_code,
            SupabaseRepository.shortBookingCode(booking.id)
        )

        // Resolve room display name
        val room = adapter.roomLookup[booking.roomId]
            ?: adapter.roomLookup[booking.roomId.substringAfterLast(":")]
        val roomLabel = if (room != null) {
            val hotelPart = room.hotelName.toDisplayWords(3)
            val typePart = room.displayType.ifBlank { room.type }.toDisplayWords(3)
            listOf(hotelPart, typePart).filter { it.isNotBlank() }.joinToString(" - ")
                .ifBlank { SupabaseRepository.displayRoomName(booking.roomId) }
        } else {
            SupabaseRepository.displayRoomName(booking.roomId)
        }

        val guest = getString(
            R.string.client_contact_email_format,
            booking.guestName.ifBlank { getString(R.string.customer_unknown) },
            booking.guestPhone.ifBlank { getString(R.string.common_na) },
            booking.guestEmail.ifBlank { getString(R.string.common_na) }
        )
        val stayLabel = stayStatusDisplayLabel(stayStatus)
        actionPanelDetails.text = getString(
            R.string.checkin_action_panel_details,
            roomLabel,
            guest,
            booking.checkIn,
            booking.checkOut,
            stayLabel
        )

        // Overdue warning
        actionPanelOverdue.visibility = if (stayStatus == "overdue") View.VISIBLE else View.GONE

        // Button states
        checkInButton.isEnabled = stayStatus == "pending_checkin"
        checkOutButton.isEnabled = stayStatus == "checked_in" || stayStatus == "overdue"

        // Show panel
        actionPanel.visibility = View.VISIBLE
    }

    // ─── Check-in / Check-out ─────────────────────────────────────────────

    private fun performCheckIn() {
        val booking = selectedBooking ?: return
        val stayStatus = SupabaseRepository.resolveStayStatus(booking)
        if (stayStatus != "pending_checkin") {
            toast(getString(R.string.error_checkin_not_allowed))
            return
        }
        checkInButton.isEnabled = false
        SupabaseRepository.checkInBooking(
            bookingId = booking.id,
            onSuccess = {
                toast(getString(R.string.success_checkin))
                reloadAndRefresh()
            },
            onError = { error ->
                checkInButton.isEnabled = true
                toast(getString(R.string.error_checkin, error.message.orEmpty()))
            }
        )
    }

    private fun performCheckOut() {
        val booking = selectedBooking ?: return
        val stayStatus = SupabaseRepository.resolveStayStatus(booking)
        if (stayStatus != "checked_in" && stayStatus != "overdue") {
            toast(getString(R.string.error_checkout_not_allowed))
            return
        }
        checkOutButton.isEnabled = false

        // Also update the room status back to available
        val roomId = booking.roomId.ifBlank { null }
        SupabaseRepository.checkOutBooking(
            bookingId = booking.id,
            onSuccess = {
                if (!roomId.isNullOrBlank()) {
                    SupabaseRepository.updateRoomStatus(
                        roomId = roomId,
                        status = "available",
                        onSuccess = {},
                        onError = {}
                    )
                }
                toast(getString(R.string.success_checkout))
                reloadAndRefresh()
            },
            onError = { error ->
                checkOutButton.isEnabled = true
                toast(getString(R.string.error_checkout, error.message.orEmpty()))
            }
        )
    }

    private fun reloadAndRefresh() {
        // Preserve selected booking id so we can re-select it after reload
        val prevId = selectedBooking?.id.orEmpty()
        actionPanel.visibility = View.GONE
        selectedBooking = null

        SupabaseRepository.listAdminBookings(
            statusFilter = null,
            onSuccess = { bookings ->
                allBookings = bookings
                applyFilter()
                loadRoomsForBookings()
                // Re-select the same booking to refresh the action panel
                val updated = bookings.firstOrNull { it.id == prevId }
                if (updated != null) {
                    adapter.selectedBookingId = updated.id
                    adapter.notifyDataSetChanged()
                    onBookingSelected(updated)
                }
            },
            onError = { error ->
                toast(getString(R.string.error_booking_history, error.message.orEmpty()))
            }
        )
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun stayStatusDisplayLabel(stayStatus: String): String = when (stayStatus) {
        "pending_checkin" -> getString(R.string.stay_status_pending_checkin)
        "checked_in" -> getString(R.string.stay_status_checked_in)
        "checked_out" -> getString(R.string.stay_status_checked_out)
        "overdue" -> getString(R.string.stay_status_overdue)
        "cancelled" -> getString(R.string.stay_status_cancelled)
        else -> stayStatus
    }

    private fun String.toDisplayWords(maxWords: Int): String =
        substringAfterLast(":")
            .replace('_', ' ')
            .replace('-', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(maxWords)
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
}
