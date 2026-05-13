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
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.HotelCatalogItem
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast

class HotelDetailActivity : BaseActivity() {
    private lateinit var roomAdapter: HotelRoomCatalogAdapter
    private lateinit var galleryAdapter: HotelImageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotel_detail)
        setupToolbar(R.string.hotel_detail_title, R.string.hotel_detail_subtitle)

        val hotelId = intent.getStringExtra(EXTRA_HOTEL_ID).orEmpty()
        if (hotelId.isBlank()) {
            toast(getString(R.string.error_hotel_not_found))
            finish()
            return
        }

        val galleryList = findViewById<RecyclerView>(R.id.hotelDetailGallery)
        val roomList = findViewById<RecyclerView>(R.id.hotelDetailRoomList)
        galleryAdapter = HotelImageAdapter()
        roomAdapter = HotelRoomCatalogAdapter { room ->
            startActivity(
                Intent(this, HotelRoomDetailActivity::class.java)
                    .putExtra(HotelRoomDetailActivity.EXTRA_ROOM_ID, room.id)
                    .putExtra(HotelRoomDetailActivity.EXTRA_HOTEL_ID, hotelId)
            )
        }
        galleryList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        roomList.layoutManager = LinearLayoutManager(this)
        galleryList.adapter = galleryAdapter
        roomList.adapter = roomAdapter

        loadHotel(hotelId)
    }

    private fun loadHotel(hotelId: String) {
        SupabaseRepository.getHotelById(
            hotelId = hotelId,
            onSuccess = { hotel ->
                if (hotel == null) {
                    toast(getString(R.string.error_hotel_not_found))
                    finish()
                    return@getHotelById
                }
                bindHotel(hotel)
                loadRooms(hotel.id)
            },
            onError = { error ->
                toast(getString(R.string.error_hotel_load, error.message.orEmpty()))
                finish()
            }
        )
    }

    private fun loadRooms(hotelId: String) {
        val summary = findViewById<TextView>(R.id.hotelDetailRoomSummary)
        val empty = findViewById<TextView>(R.id.hotelDetailRoomEmpty)
        SupabaseRepository.listHotelRooms(
            hotelId = hotelId,
            onSuccess = { rooms ->
                roomAdapter.submitList(rooms)
                summary.text = getString(R.string.hotel_detail_room_summary, rooms.size)
                empty.visibility = if (rooms.isEmpty()) View.VISIBLE else View.GONE
            },
            onError = { error ->
                toast(getString(R.string.error_hotel_rooms_load, error.message.orEmpty()))
            }
        )
    }

    private fun bindHotel(hotel: HotelCatalogItem) {
        val heroImage = findViewById<ImageView>(R.id.hotelDetailHeroImage)
        val title = findViewById<TextView>(R.id.hotelDetailTitle)
        val meta = findViewById<TextView>(R.id.hotelDetailMeta)
        val address = findViewById<TextView>(R.id.hotelDetailAddress)
        val schedule = findViewById<TextView>(R.id.hotelDetailSchedule)
        val contact = findViewById<TextView>(R.id.hotelDetailContact)
        val description = findViewById<TextView>(R.id.hotelDetailDescription)
        val amenities = findViewById<TextView>(R.id.hotelDetailAmenities)
        val policies = findViewById<TextView>(R.id.hotelDetailPolicies)
        val source = findViewById<TextView>(R.id.hotelDetailSource)

        title.text = hotel.displayName.ifBlank { hotel.name }
        val reviewMeta = if (hotel.reviewScore > 0.0) {
            getString(R.string.hotel_review_meta, hotel.reviewScore, hotel.reviewCount)
        } else {
            getString(R.string.hotel_no_review_meta)
        }
        meta.text = getString(R.string.hotel_detail_meta_format, hotel.starRating, reviewMeta, hotel.roomCount)
        address.text = hotel.addressFull.ifBlank {
            listOf(hotel.area, hotel.city, hotel.country).filter { it.isNotBlank() }.joinToString(", ")
        }
        schedule.text = getString(
            R.string.hotel_detail_schedule_format,
            hotel.checkInFrom.ifBlank { getString(R.string.common_na) },
            hotel.checkOutUntil.ifBlank { getString(R.string.common_na) }
        )
        val contactParts = listOf(hotel.contactPhone, hotel.contactEmail).filter { it.isNotBlank() }
        contact.text = if (contactParts.isEmpty()) {
            getString(R.string.hotel_detail_contact_empty)
        } else {
            getString(R.string.hotel_detail_contact_format, contactParts.joinToString(" | "))
        }
        description.text = hotel.description.ifBlank { hotel.shortDescription }

        val amenityLines = (hotel.featuredAmenities + hotel.generalAmenities).distinct().take(18)
        amenities.text = if (amenityLines.isEmpty()) {
            getString(R.string.hotel_detail_amenities_empty)
        } else {
            amenityLines.joinToString("\n") { "- $it" }
        }

        policies.text = if (hotel.policyNotes.isEmpty()) {
            getString(R.string.hotel_detail_policies_empty)
        } else {
            hotel.policyNotes.joinToString("\n") { "- $it" }
        }
        source.text = hotel.sourceUrl

        heroImage.load(hotel.heroImage.ifBlank { hotel.galleryImages.firstOrNull() }) {
            placeholder(R.mipmap.ic_launcher)
            error(R.mipmap.ic_launcher)
            crossfade(true)
        }
        galleryAdapter.submitList(hotel.galleryImages)
    }

    companion object {
        const val EXTRA_HOTEL_ID = "hotel_id"
    }
}
