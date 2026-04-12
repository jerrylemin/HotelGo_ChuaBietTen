package com.example.hotelapp_test2.ui.features

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.HotelCatalogRoom
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton

class HotelRoomDetailActivity : BaseActivity() {
    private lateinit var galleryAdapter: HotelImageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotel_room_detail)
        setupToolbar(R.string.hotel_room_detail_title, R.string.hotel_room_detail_subtitle)

        val roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty()
        if (roomId.isBlank()) {
            toast(getString(R.string.error_hotel_room_not_found))
            finish()
            return
        }

        val galleryList = findViewById<RecyclerView>(R.id.hotelRoomDetailGallery)
        galleryAdapter = HotelImageAdapter()
        galleryList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        galleryList.adapter = galleryAdapter

        SupabaseRepository.getHotelRoomById(
            roomId = roomId,
            onSuccess = { room ->
                if (room == null) {
                    toast(getString(R.string.error_hotel_room_not_found))
                    finish()
                    return@getHotelRoomById
                }
                bindRoom(room)
            },
            onError = { error ->
                toast(getString(R.string.error_hotel_room_load, error.message.orEmpty()))
                finish()
            }
        )
    }

    private fun bindRoom(room: HotelCatalogRoom) {
        val heroImage = findViewById<ImageView>(R.id.hotelRoomDetailHeroImage)
        val title = findViewById<TextView>(R.id.hotelRoomDetailTitle)
        val price = findViewById<TextView>(R.id.hotelRoomDetailPrice)
        val info = findViewById<TextView>(R.id.hotelRoomDetailInfo)
        val summary = findViewById<TextView>(R.id.hotelRoomDetailSummary)
        val specs = findViewById<TextView>(R.id.hotelRoomDetailSpecs)
        val cancellation = findViewById<TextView>(R.id.hotelRoomDetailCancellation)
        val amenities = findViewById<TextView>(R.id.hotelRoomDetailAmenities)
        val bathroom = findViewById<TextView>(R.id.hotelRoomDetailBathroomAmenities)
        val tags = findViewById<TextView>(R.id.hotelRoomDetailTags)
        val bookingButton = findViewById<MaterialButton>(R.id.hotelRoomDetailBookButton)

        title.text = room.name
        price.text = if (room.price > 0.0) {
            getString(R.string.room_price_per_night, room.price.toInt())
        } else {
            getString(R.string.room_price_empty)
        }
        val ratingText = if (room.rating > 0.0) {
            getString(R.string.room_rating_format, room.rating)
        } else {
            getString(R.string.room_no_rating)
        }
        info.text = getString(R.string.room_info_format, room.capacity, ratingText, statusLabel(room.status))

        val specLines = mutableListOf<String>()
        if (room.roomSizeSqm != null) specLines.add(getString(R.string.hotel_room_detail_size_format, room.roomSizeSqm))
        if (room.view.isNotBlank()) specLines.add(getString(R.string.hotel_room_detail_view_format, room.view))
        if (room.bedSummary.isNotBlank()) specLines.add(getString(R.string.hotel_room_detail_bed_format, room.bedSummary))
        specLines.add(
            if (room.breakfastIncluded) getString(R.string.hotel_room_detail_breakfast_yes)
            else getString(R.string.hotel_room_detail_breakfast_no)
        )
        specs.text = specLines.joinToString("\n")
        summary.text = room.shortDescription.ifBlank { room.name }
        cancellation.text = room.cancellationPolicy.ifBlank { getString(R.string.hotel_room_detail_cancellation_empty) }
        amenities.text = if (room.amenities.isEmpty()) getString(R.string.hotel_room_detail_amenities_empty) else room.amenities.joinToString("\n") { "- $it" }
        bathroom.text = if (room.bathroomAmenities.isEmpty()) getString(R.string.hotel_room_detail_amenities_empty) else room.bathroomAmenities.joinToString("\n") { "- $it" }
        tags.text = if (room.tags.isEmpty()) getString(R.string.hotel_room_detail_tags_empty) else room.tags.joinToString(" | ")

        heroImage.load(room.heroImage.ifBlank { room.images.firstOrNull() }) {
            placeholder(R.mipmap.ic_launcher)
            error(R.mipmap.ic_launcher)
            crossfade(true)
        }
        galleryAdapter.submitList(room.images)

        if (SessionManager.getRole(this) == "client") {
            bookingButton.visibility = View.VISIBLE
            bookingButton.setOnClickListener {
                startActivity(
                    Intent(this, RoomDetailActivity::class.java)
                        .putExtra(RoomDetailActivity.EXTRA_ROOM_ID, "catalog:${room.hotelSlug}:${room.slug}")
                        .putExtra(RoomDetailActivity.EXTRA_ROOM_CODE, room.roomCode)
                )
            }
        } else {
            bookingButton.visibility = View.GONE
        }
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
        const val EXTRA_HOTEL_ID = "hotel_id"
    }
}
