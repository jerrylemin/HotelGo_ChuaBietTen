package com.example.hotelapp_test2.data.model

data class UserProfile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "client",
    val createdAt: Long = 0L
)

data class Room(
    val id: String = "",
    val code: String = "",
    val type: String = "",
    val displayType: String = "",
    val typeKey: String = "",
    val price: Double = 0.0,
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val status: String = "available",
    val capacity: Int = 2,
    val images: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val hotelId: String = "",
    val hotelName: String = "",
    val area: String = "",
    val city: String = ""
)

data class HotelCatalogItem(
    val id: String = "",
    val folderName: String = "",
    val slug: String = "",
    val name: String = "",
    val displayName: String = "",
    val city: String = "",
    val area: String = "",
    val country: String = "",
    val addressFull: String = "",
    val shortDescription: String = "",
    val description: String = "",
    val starRating: Double = 0.0,
    val reviewScore: Double = 0.0,
    val reviewCount: Int = 0,
    val roomCount: Int = 0,
    val checkInFrom: String = "",
    val checkOutUntil: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val contactPhone: String = "",
    val contactEmail: String = "",
    val heroImage: String = "",
    val galleryImages: List<String> = emptyList(),
    val featuredAmenities: List<String> = emptyList(),
    val generalAmenities: List<String> = emptyList(),
    val policyNotes: List<String> = emptyList(),
    val sourceUrl: String = ""
)

data class HotelCatalogRoom(
    val id: String = "",
    val hotelId: String = "",
    val hotelSlug: String = "",
    val roomCode: String = "",
    val name: String = "",
    val slug: String = "",
    val price: Double = 0.0,
    val originalPrice: Double? = null,
    val currency: String = "VND",
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val status: String = "available",
    val capacity: Int = 2,
    val images: List<String> = emptyList(),
    val heroImage: String = "",
    val roomSizeSqm: Double? = null,
    val roomSizeSqft: Double? = null,
    val view: String = "",
    val breakfastIncluded: Boolean = false,
    val cancellationPolicy: String = "",
    val bedSummary: String = "",
    val bedCount: Int = 0,
    val amenities: List<String> = emptyList(),
    val bathroomAmenities: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val shortDescription: String = ""
)

data class Booking(
    val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val checkIn: String = "",
    val checkOut: String = "",
    val status: String = "pending",
    val total: Double = 0.0,
    val addOns: List<String> = emptyList(),
    val actualCheckInAt: Long = 0L,
    val actualCheckOutAt: Long = 0L,
    val createdAt: Long = 0L
)

data class Review(
    val id: String = "",
    val roomId: String = "",
    val userId: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val createdAt: Long = 0L
)

data class IssueReport(
    val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val bookingId: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "new",
    val createdAt: Long = 0L
)

data class Voucher(
    val id: String = "",
    val code: String = "",
    val type: String = "percent",
    val value: Double = 0.0,
    val minSpend: Double = 0.0,
    val startAt: String = "",
    val endAt: String = "",
    val active: Boolean = true,
    val usageLimit: Int = 0
)

data class Poster(
    val id: String = "",
    val type: String = "recommend",
    val title: String = "",
    val content: String = "",
    val imageUrl: String = "",
    val roomId: String = "",
    val active: Boolean = true,
    val userId: String = "",
    val status: String = "new",
    val response: String = "",
    val role: String = "client",
    val createdAt: Long = 0L
)

data class AddOnItem(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val description: String = "",
    val imageUrl: String = "",
    val category: String = "snack",
    val active: Boolean = true
)

data class AppNotification(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val targetRole: String = "all",
    val read: Boolean = false,
    val createdAt: Long = 0L
)

data class Payment(
    val id: String = "",
    val bookingId: String = "",
    val userId: String = "",
    val amount: Double = 0.0,
    val method: String = "",
    val status: String = "paid",
    val cardLast4: String = "",
    val createdAt: Long = 0L
)

data class NotificationSettings(
    val checkIn: Boolean = true,
    val promo: Boolean = true,
    val roomStatus: Boolean = true
)
