package com.example.hotelapp_test2.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.example.hotelapp_test2.BuildConfig
import com.example.hotelapp_test2.data.model.AddOnItem
import com.example.hotelapp_test2.data.model.AppNotification
import com.example.hotelapp_test2.data.model.Booking
import com.example.hotelapp_test2.data.model.BookingAddOn
import com.example.hotelapp_test2.data.model.BookingAddOnSelection
import com.example.hotelapp_test2.data.model.HotelCatalogItem
import com.example.hotelapp_test2.data.model.HotelCatalogRoom
import com.example.hotelapp_test2.data.model.IssueReport
import com.example.hotelapp_test2.data.model.NotificationSettings
import com.example.hotelapp_test2.data.model.Payment
import com.example.hotelapp_test2.data.model.Poster
import com.example.hotelapp_test2.data.model.Review
import com.example.hotelapp_test2.data.model.ReviewableBooking
import com.example.hotelapp_test2.data.model.Room
import com.example.hotelapp_test2.data.model.RoomRequest
import com.example.hotelapp_test2.data.model.UserProfile
import com.example.hotelapp_test2.data.model.Voucher
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

object SupabaseRepository {
    data class SupabaseUser(
        val uid: String,
        val email: String = "",
        val displayName: String = "",
        val phone: String = ""
    )

    data class SignUpResult(
        val user: SupabaseUser?,
        val requiresEmailConfirmation: Boolean
    )

    private data class ParsedAuthPayload(
        val user: SupabaseUser,
        val accessToken: String,
        val refreshToken: String
    )

    private data class HttpResult(val code: Int, val body: String)
    private data class HotelLookup(
        val displayName: String = "",
        val area: String = "",
        val city: String = ""
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private const val PAGE_SIZE = 1000
    private const val AUTH_PREFS = "supabase_auth"
    private const val KEY_UID = "uid"
    private const val KEY_EMAIL = "email"
    private const val KEY_NAME = "name"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var authUser: SupabaseUser? = null

    @Volatile
    private var accessToken: String = ""

    @Volatile
    private var refreshToken: String = ""

    private val authLock = Any()

    fun init(context: Context) {
        appContext = context.applicationContext
        loadAuthState()
    }

    fun currentUser(): SupabaseUser? = authUser

    fun signOut() {
        val token = accessToken
        clearAuthState()
        if (token.isBlank()) return
        Thread {
            try {
                request("POST", "/auth/v1/logout", jsonBody = "{}", bearer = token)
            } catch (_: Exception) {
            }
        }.start()
    }

    fun signInWithEmail(
        email: String,
        password: String,
        onSuccess: (SupabaseUser) -> Unit,
        onError: (Exception) -> Unit
    ) {
        runAsync(onSuccess, onError) {
            val payload = JSONObject().put("email", email).put("password", password)
            val response = request(
                method = "POST",
                path = "/auth/v1/token",
                query = mapOf("grant_type" to "password"),
                jsonBody = payload.toString(),
                bearer = BuildConfig.SUPABASE_ANON_KEY
            )
            if (response.code !in 200..299) {
                throw IllegalStateException(authError(response.body, "Dang nhap that bai"))
            }
            parseAndPersistAuth(response.body).user
        }
    }

    fun signUpWithEmail(
        email: String,
        password: String,
        name: String,
        phone: String,
        onSuccess: (SignUpResult) -> Unit,
        onError: (Exception) -> Unit
    ) {
        runAsync(onSuccess, onError) {
            val payload = JSONObject()
                .put("email", email)
                .put("password", password)
                .put(
                    "data",
                    JSONObject()
                        .put("full_name", name)
                        .put("phone", phone)
                )
            BuildConfig.SUPABASE_AUTH_REDIRECT_URL.trim()
                .takeIf { it.isNotBlank() }
                ?.let { payload.put("redirect_to", it) }
            val response = request(
                method = "POST",
                path = "/auth/v1/signup",
                jsonBody = payload.toString(),
                bearer = BuildConfig.SUPABASE_ANON_KEY
            )
            if (response.code !in 200..299) {
                throw IllegalStateException(authError(response.body, "Dang ky that bai"))
            }
            parseSignUpResponse(response.body)
        }
    }

    fun completeAuthFromRedirect(
        redirectUrl: String,
        onSuccess: (SupabaseUser) -> Unit,
        onError: (Exception) -> Unit
    ) {
        runAsync(onSuccess, onError) {
            val session = SupabaseAuthRedirectParser.parse(redirectUrl)
                ?: throw IllegalStateException("Lien ket xac nhan khong hop le hoac da het han")
            val response = request(
                method = "GET",
                path = "/auth/v1/user",
                bearer = session.accessToken,
                retryOnUnauthorized = false
            )
            if (response.code !in 200..299) {
                throw IllegalStateException(authError(response.body, "Khong the khoi phuc phien dang nhap"))
            }
            val user = parseSupabaseUser(JSONObject(response.body))
            persistAuthState(user, session.accessToken, session.refreshToken)
            user
        }
    }

    fun signInWithGoogleIdToken(
        idToken: String,
        nonce: String? = null,
        onSuccess: (SupabaseUser) -> Unit,
        onError: (Exception) -> Unit
    ) {
        runAsync(onSuccess, onError) {
            val payload = JSONObject().put("provider", "google").put("id_token", idToken)
            if (!nonce.isNullOrBlank()) payload.put("nonce", nonce)
            val response = request(
                method = "POST",
                path = "/auth/v1/token",
                query = mapOf("grant_type" to "id_token"),
                jsonBody = payload.toString(),
                bearer = BuildConfig.SUPABASE_ANON_KEY
            )
            if (response.code !in 200..299) {
                throw IllegalStateException(authError(response.body, "Dang nhap Google that bai"))
            }
            parseAndPersistAuth(response.body).user
        }
    }

    fun ensureUserProfile(
        context: Context,
        name: String,
        email: String,
        phone: String,
        requestedRole: String,
        onSuccess: (UserProfile) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val user = authUser ?: run {
            onError(IllegalStateException("User not logged in"))
            return
        }
        runAsync(onSuccess, onError) {
            val existing = getUserProfileById(user.uid)
            val role = existing?.role?.ifBlank { requestedRole } ?: requestedRole
            val profile = UserProfile(
                id = user.uid,
                name = if (name.isBlank()) existing?.name.orEmpty() else name,
                email = if (email.isBlank()) existing?.email.orEmpty() else email,
                phone = if (phone.isBlank()) existing?.phone.orEmpty() else phone,
                role = normalizeRole(role),
                createdAt = existing?.createdAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
            val payload = JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("email", profile.email)
                .put("phone", profile.phone)
                .put("role", profile.role)
                .put("created_at", millisToIso(profile.createdAt))
            upsert("users", payload)
            SessionManager.setUser(context, user.uid, profile.role)
            profile
        }
    }

    fun fetchUserProfile(userId: String, onSuccess: (UserProfile?) -> Unit, onError: (Exception) -> Unit) {
        if (userId.isBlank()) {
            onSuccess(null)
            return
        }
        runAsync(onSuccess, onError) { getUserProfileById(userId) }
    }

    fun updateUserProfile(profile: UserProfile, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        runAsyncUnit(onSuccess, onError) {
            upsert(
                "users",
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("email", profile.email)
                    .put("phone", profile.phone)
                    .put("role", normalizeRole(profile.role))
                    .put("created_at", millisToIso(if (profile.createdAt == 0L) System.currentTimeMillis() else profile.createdAt))
            )
        }
    }

    fun createRoom(room: Room, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { upsert("rooms", roomToJson(room.copy(id = room.id.ifBlank { room.code.ifBlank { UUID.randomUUID().toString() } }))) }

    fun updateRoom(room: Room, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (room.id.isBlank()) {
            onError(IllegalArgumentException("Missing room id"))
            return
        }
        runAsyncUnit(onSuccess, onError) { upsert("rooms", roomToJson(room)) }
    }

    fun updateRoomStatus(roomId: String, status: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { patch("rooms", mapOf("id" to "eq.$roomId"), JSONObject().put("status", status)) }

    fun getRoomByCode(code: String, onSuccess: (Room?) -> Unit, onError: (Exception) -> Unit) {
        if (code.isBlank()) {
            onSuccess(null)
            return
        }
        runAsync(onSuccess, onError) {
            val hotelLookup = loadHotelLookup()
            select("rooms", mapOf("select" to "*", "code" to "eq.$code", "limit" to "1")).firstObjectOrNull()?.toRoom(hotelLookup)
                ?: select("rooms", mapOf("select" to "*", "id" to "eq.$code", "limit" to "1")).firstObjectOrNull()?.toRoom(hotelLookup)
                ?: select("hotel_rooms", mapOf("select" to "*", "room_code" to "eq.$code", "limit" to "1")).firstObjectOrNull()?.toSearchRoom(hotelLookup)
                ?: select("hotel_rooms", mapOf("select" to "*", "id" to "eq.$code", "limit" to "1")).firstObjectOrNull()?.toSearchRoom(hotelLookup)
                ?: fallbackSearchRooms().firstOrNull { it.code == code || it.id == code }
        }
    }

    fun deleteRoomByCode(code: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { delete("rooms", mapOf("id" to "eq.$code")) }

    fun searchRooms(queryText: String, onSuccess: (List<Room>) -> Unit, onError: (Exception) -> Unit) {
        runAsync(onSuccess, onError) {
            val hotelLookup = loadHotelLookup()
            val legacyRooms = selectAll("rooms", mapOf("select" to "*", "order" to "created_at.desc")).toRoomList(hotelLookup)
            val rooms = legacyRooms.ifEmpty {
                selectAll("hotel_rooms", mapOf("select" to "*", "order" to "price.asc")).toSearchRoomList(hotelLookup)
            }.ifEmpty {
                fallbackSearchRooms()
            }
            if (queryText.isBlank()) rooms else rooms.filter {
                it.area.contains(queryText, true) ||
                    it.city.contains(queryText, true) ||
                    it.hotelName.contains(queryText, true) ||
                    it.displayType.contains(queryText, true) ||
                    it.type.contains(queryText, true) ||
                    it.code.contains(queryText, true)
            }
        }
    }

    fun filterRooms(type: String?, sortAscending: Boolean?, onSuccess: (List<Room>) -> Unit, onError: (Exception) -> Unit) {
        runAsync(onSuccess, onError) {
            val hotelLookup = loadHotelLookup()
            val q = linkedMapOf("select" to "*")
            if (!type.isNullOrBlank()) q["type"] = "eq.$type"
            if (sortAscending != null) q["order"] = if (sortAscending) "price.asc" else "price.desc"
            selectAll("rooms", q).toRoomList(hotelLookup).ifEmpty {
                fallbackSearchRooms()
                    .filter { room -> type.isNullOrBlank() || room.type == type }
                    .let { rooms ->
                        when (sortAscending) {
                            true -> rooms.sortedBy { it.price }
                            false -> rooms.sortedByDescending { it.price }
                            null -> rooms
                        }
                    }
            }
        }
    }

    fun searchHotels(queryText: String, onSuccess: (List<HotelCatalogItem>) -> Unit, onError: (Exception) -> Unit) {
        runAsync(onSuccess, onError) {
            val hotels = selectAll("hotels", mapOf("select" to "*", "order" to "display_name.asc")).toHotelCatalogList()
                .ifEmpty { fallbackHotels() }
            if (queryText.isBlank()) {
                hotels
            } else {
                hotels.filter {
                    it.displayName.contains(queryText, true) ||
                        it.city.contains(queryText, true) ||
                        it.area.contains(queryText, true) ||
                        it.country.contains(queryText, true) ||
                        it.shortDescription.contains(queryText, true)
                }
            }
        }
    }

    fun getHotelById(hotelId: String, onSuccess: (HotelCatalogItem?) -> Unit, onError: (Exception) -> Unit) {
        if (hotelId.isBlank()) {
            onSuccess(null)
            return
        }
        runAsync(onSuccess, onError) {
            select("hotels", mapOf("select" to "*", "id" to "eq.$hotelId", "limit" to "1")).firstObjectOrNull()?.toHotelCatalogItem()
                ?: fallbackHotels().firstOrNull { it.id == hotelId || it.slug == hotelId }
        }
    }

    fun listHotelRooms(hotelId: String, onSuccess: (List<HotelCatalogRoom>) -> Unit, onError: (Exception) -> Unit) {
        if (hotelId.isBlank()) {
            onSuccess(emptyList())
            return
        }
        runAsync(onSuccess, onError) {
            selectAll(
                "hotel_rooms",
                mapOf("select" to "*", "hotel_id" to "eq.$hotelId", "order" to "price.asc")
            ).toHotelCatalogRoomList().ifEmpty {
                fallbackHotelRooms(hotelId)
            }
        }
    }

    fun getHotelRoomById(roomId: String, onSuccess: (HotelCatalogRoom?) -> Unit, onError: (Exception) -> Unit) {
        if (roomId.isBlank()) {
            onSuccess(null)
            return
        }
        runAsync(onSuccess, onError) {
            select("hotel_rooms", mapOf("select" to "*", "id" to "eq.$roomId", "limit" to "1")).firstObjectOrNull()?.toHotelCatalogRoom()
                ?: fallbackHotelRooms().firstOrNull { it.id == roomId || it.roomCode == roomId }
        }
    }

    fun createBooking(booking: Booking, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { upsertBookingWithSchemaFallback(booking.copy(id = booking.id.ifBlank { UUID.randomUUID().toString() })) }

    fun createBookingWithAddOns(
        booking: Booking,
        addOnSelections: List<BookingAddOnSelection>,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        runAsync(onSuccess, onError) {
            val bookingId = booking.id.ifBlank { UUID.randomUUID().toString() }
            val normalizedBooking = booking.copy(
                id = bookingId,
                addOns = addOnSelections
                    .filter { it.quantity > 0 }
                    .map { "${it.item.id}:${it.quantity}" }
            )
            upsertBookingWithSchemaFallback(normalizedBooking)
            addOnSelections
                .filter { it.quantity > 0 && it.item.id.isNotBlank() }
                .forEach { selection ->
                    runCatching {
                        upsert(
                            "booking_addons",
                            bookingAddOnToJson(
                                BookingAddOn(
                                    id = UUID.randomUUID().toString(),
                                    bookingId = bookingId,
                                    addOnItemId = selection.item.id,
                                    name = selection.item.name,
                                    description = selection.item.description,
                                    quantity = selection.quantity,
                                    unitPrice = selection.item.price,
                                    totalPrice = selection.totalPrice,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        )
                    }
                }
            bookingId
        }
    }

    fun listBookings(userId: String?, onSuccess: (List<Booking>) -> Unit, onError: (Exception) -> Unit) {
        runAsync(onSuccess, onError) {
            val q = linkedMapOf("select" to "*", "limit" to "50", "order" to "created_at.desc")
            if (!userId.isNullOrBlank()) q["user_id"] = "eq.$userId"
            attachBookingAddOns(select("bookings", q).toBookingList())
        }
    }

    private fun attachBookingAddOns(bookings: List<Booking>): List<Booking> {
        if (bookings.isEmpty()) return bookings
        val bookingIds = bookings.map { it.id }.filter { it.isNotBlank() }.toSet()
        if (bookingIds.isEmpty()) return bookings
        val addOnLookup = runCatching {
            selectAll("add_ons", mapOf("select" to "*", "order" to "name.asc"))
                .toAddOnList()
                .associateBy { it.id }
        }.getOrDefault(emptyMap())
        val rows = runCatching {
            selectAll(
                "booking_addons",
                mapOf("select" to "*", "order" to "created_at.asc")
            ).toBookingAddOnList(addOnLookup)
                .filter { it.bookingId in bookingIds }
        }.getOrDefault(emptyList())
        val rowsByBooking = rows.groupBy { it.bookingId }
        return bookings.map { booking ->
            booking.copy(addOnDetails = rowsByBooking[booking.id].orEmpty())
        }
    }

    fun updateBookingStatus(bookingId: String, status: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { patch("bookings", mapOf("id" to "eq.$bookingId"), JSONObject().put("status", status)) }

    fun updateBookingPaymentSummary(
        bookingId: String,
        voucher: Voucher?,
        originalTotal: Double,
        addonsTotal: Double,
        discountAmount: Double,
        finalTotal: Double,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (bookingId.isBlank()) {
            onError(IllegalArgumentException("Missing booking id"))
            return
        }
        runAsyncUnit(onSuccess, onError) {
            val payload = JSONObject()
                .put("total", finalTotal)
                .put("original_total", originalTotal)
                .put("addons_total", addonsTotal)
                .put("discount_amount", discountAmount)
                .put("final_total", finalTotal)
            if (voucher != null) {
                payload
                    .put("voucher_id", voucher.id)
                    .put("voucher_code", voucher.code)
            }
            patch("bookings", mapOf("id" to "eq.$bookingId"), payload)
        }
    }

    fun updateBookingStayStatus(bookingId: String, status: String, atMillis: Long, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (bookingId.isBlank()) {
            onError(IllegalArgumentException("Missing booking id"))
            return
        }
        runAsyncUnit(onSuccess, onError) {
            val payload = JSONObject().put("status", status).put("stay_status", status).put("updated_at", millisToIso(atMillis))
            if (status == "checked_in") {
                payload.put("actual_check_in_at", millisToIso(atMillis))
                payload.put("checked_in_at", millisToIso(atMillis))
            }
            if (status == "checked_out") {
                payload.put("actual_check_out_at", millisToIso(atMillis))
                payload.put("checked_out_at", millisToIso(atMillis))
            }
            patch("bookings", mapOf("id" to "eq.$bookingId"), payload)
        }
    }

    /** Check a guest in: sets stay_status=checked_in and records timestamp. */
    fun checkInBooking(bookingId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        updateBookingStayStatus(bookingId, "checked_in", System.currentTimeMillis(), onSuccess, onError)

    /** Check a guest out: sets stay_status=checked_out and records timestamp. */
    fun checkOutBooking(bookingId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        updateBookingStayStatus(bookingId, "checked_out", System.currentTimeMillis(), onSuccess, onError)

    /**
     * Load all bookings for the admin check-in/out screen.
     * Includes active (confirmed, paid, checked_in, checked_out) stays and optionally
     * enriches each booking with a guestName resolved from the users table.
     */
    fun listAdminBookings(statusFilter: String? = null, onSuccess: (List<Booking>) -> Unit, onError: (Exception) -> Unit) {
        runAsync(onSuccess, onError) {
            val q = linkedMapOf<String, String>("select" to "*", "order" to "created_at.desc", "limit" to "100")
            if (!statusFilter.isNullOrBlank() && statusFilter != "all") {
                when (statusFilter) {
                    "overdue" -> {
                        // Overdue = checked_in but checkout date is in the past
                        q["stay_status"] = "eq.checked_in"
                    }
                    else -> q["stay_status"] = "eq.$statusFilter"
                }
            } else {
                // Admin default: show relevant statuses (exclude purely pending/unconfirmed)
                q["status"] = "in.(confirmed,paid,checked_in,checked_out,cancelled)"
            }
            val bookings = select("bookings", q).toBookingList()
            // Try to resolve guest names from users table
            val enriched = runCatching {
                val userIds = bookings.map { it.userId }.filter { it.isNotBlank() }.toSet()
                if (userIds.isEmpty()) return@runCatching bookings
                val userRows = selectAll("users", mapOf("select" to "id,name", "id" to "in.(${userIds.joinToString(",")})"))
                val nameMap = buildMap<String, String> {
                    for (i in 0 until userRows.length()) {
                        val obj = userRows.optJSONObject(i) ?: continue
                        val uid = obj.optString("id")
                        val name = obj.optString("name")
                        if (uid.isNotBlank()) put(uid, name)
                    }
                }
                bookings.map { b -> b.copy(guestName = nameMap[b.userId].orEmpty()) }
            }.getOrDefault(bookings)
            attachBookingAddOns(enriched)
        }
    }

    // ─── Format helpers ──────────────────────────────────────────────────────

    /** Shorten a UUID or long ID to display as BK-XXXXXX */
    fun shortBookingCode(id: String): String {
        val clean = id.replace("-", "").uppercase()
        return "BK-${clean.take(6)}"
    }

    /** Convert a snake_case room id/name to a readable title, max 5 words. */
    fun displayRoomName(rawId: String, roomName: String = ""): String {
        val base = roomName.ifBlank { rawId }
        // Strip catalog prefixes like "catalog:hotel:room"
        val stripped = base
            .substringAfterLast(":")
            .replace('_', ' ')
            .replace('-', ' ')
        val words = stripped.split(Regex("\\s+")).filter { it.isNotBlank() }.take(5)
        return words.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }.ifBlank { rawId.takeLast(8) }
    }

    /**
     * Resolves the UI-visible stay status for a booking, taking into account overdue logic.
     * Returns one of: pending_checkin | checked_in | checked_out | overdue | cancelled
     */
    fun resolveStayStatus(booking: Booking): String {
        val explicitStay = booking.stayStatus.ifBlank { booking.status }
        if (explicitStay == "cancelled") return "cancelled"
        if (explicitStay == "checked_out") return "checked_out"
        if (explicitStay == "checked_in") {
            // Check if overdue
            val isOverdue = runCatching {
                val checkoutDate = java.time.LocalDate.parse(booking.checkOut)
                java.time.LocalDate.now().isAfter(checkoutDate)
            }.getOrDefault(false)
            return if (isOverdue) "overdue" else "checked_in"
        }
        // Not yet checked in
        if (explicitStay == "pending_checkin" || explicitStay in setOf("confirmed", "paid")) {
            return "pending_checkin"
        }
        return explicitStay.ifBlank { "pending_checkin" }
    }

    fun createReview(review: Review, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { upsert("reviews", reviewToJson(review.copy(id = review.id.ifBlank { UUID.randomUUID().toString() }))) }

    fun listRecentReviews(limit: Long, onSuccess: (List<Review>) -> Unit, onError: (Exception) -> Unit) =
        runAsync(onSuccess, onError) {
            runCatching {
                select("reviews", mapOf("select" to "*", "order" to "created_at.desc", "limit" to limit.toString())).toReviewList()
            }.getOrElse {
                select("reviews", mapOf("select" to "id,room_id,user_id,rating,comment", "limit" to limit.toString())).toReviewList()
            }
        }

    fun listReviewsForRoom(roomId: String, onSuccess: (List<Review>) -> Unit, onError: (Exception) -> Unit) {
        if (roomId.isBlank()) {
            onSuccess(emptyList())
            return
        }
        runAsync(onSuccess, onError) {
            val roomAliases = resolveReviewRoomAliases(roomId)
            val filter = "in.(${roomAliases.joinToString(",") { it.toPostgrestInValue() }})"
            runCatching {
                select(
                    "reviews",
                    mapOf("select" to "*", "room_id" to filter, "order" to "created_at.desc", "limit" to "100")
                ).toReviewList()
            }.getOrElse {
                selectAll("reviews", mapOf("select" to "*", "order" to "created_at.desc"))
                    .toReviewList()
                    .filter { it.roomId in roomAliases }
            }.distinctBy { it.id.ifBlank { "${it.roomId}:${it.userId}:${it.createdAt}" } }
        }
    }

    fun listReviewableBookings(userId: String, onSuccess: (List<ReviewableBooking>) -> Unit, onError: (Exception) -> Unit) {
        if (userId.isBlank()) {
            onSuccess(emptyList())
            return
        }
        runAsync(onSuccess, onError) {
            val eligibleStatuses = setOf("completed", "checked_out", "paid", "confirmed")
            val bookings = selectAll(
                "bookings",
                linkedMapOf(
                    "select" to "*",
                    "user_id" to "eq.$userId",
                    "status" to "in.(${eligibleStatuses.joinToString(",")})",
                    "order" to "created_at.desc"
                )
            ).toBookingList()
            if (bookings.isEmpty()) return@runAsync emptyList()

            val hotelLookup = loadHotelLookup()
            val rooms = selectAll("rooms", linkedMapOf("select" to "*", "order" to "code.asc")).toRoomList(hotelLookup)
            val hotelRooms = selectAll("hotel_rooms", linkedMapOf("select" to "*", "order" to "room_code.asc")).toSearchRoomList(hotelLookup)
            val roomLookup = buildMap {
                (rooms + hotelRooms).forEach { room ->
                    if (room.id.isNotBlank()) put(room.id, room)
                    if (room.code.isNotBlank()) put(room.code, room)
                }
            }
            val reviews = selectUserReviewsForReviewableBookings(userId)
            val reviewsByBooking = reviews.filter { it.bookingId.isNotBlank() }.associateBy { it.bookingId }
            val legacyReviewedRoomIds = reviews
                .filter { it.bookingId.isBlank() && it.roomId.isNotBlank() }
                .associateBy { it.roomId }
            bookings.map { booking ->
                ReviewableBooking(
                    booking = booking,
                    room = roomLookup[booking.roomId],
                    existingReview = reviewsByBooking[booking.id] ?: legacyReviewedRoomIds[booking.roomId]
                )
            }
        }
    }

    fun canUserReviewRoom(userId: String, roomId: String, onSuccess: (Boolean) -> Unit, onError: (Exception) -> Unit) {
        if (userId.isBlank() || roomId.isBlank()) {
            onSuccess(false)
            return
        }
        runAsync(onSuccess, onError) {
            val bookings = select(
                "bookings",
                mapOf(
                    "select" to "id,status",
                    "user_id" to "eq.$userId",
                    "room_id" to "eq.$roomId",
                    "status" to "in.(confirmed,paid,checked_in,checked_out)",
                    "limit" to "1"
                )
            )
            bookings.length() > 0
        }
    }

    fun createReviewAndRefreshRoom(review: Review, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (review.roomId.isBlank() || review.userId.isBlank() || review.bookingId.isBlank()) {
            onError(IllegalArgumentException("Missing review booking context"))
            return
        }
        runAsyncUnit(onSuccess, onError) {
            val booking = select(
                "bookings",
                mapOf(
                    "select" to "id,user_id,room_id,status",
                    "id" to "eq.${review.bookingId}",
                    "user_id" to "eq.${review.userId}",
                    "room_id" to "eq.${review.roomId}",
                    "status" to "in.(completed,checked_out,paid,confirmed)",
                    "limit" to "1"
                )
            ).firstObjectOrNull() ?: throw IllegalStateException("Booking is not eligible for review")
            val existing = selectExistingReviewForBooking(review.userId, booking.optString("id"), review.roomId)
            if (existing.length() > 0) throw IllegalStateException("Booking was already reviewed")
            upsertReviewWithSchemaFallback(review.copy(id = review.id.ifBlank { UUID.randomUUID().toString() }))
            val reviews = select(
                "reviews",
                mapOf("select" to "rating", "room_id" to "eq.${review.roomId}", "limit" to "1000")
            ).toReviewList()
            if (reviews.isNotEmpty()) {
                val average = reviews.map { it.rating.coerceIn(1, 5) }.average()
                patchRoomRating(review.roomId, average, reviews.size)
            }
        }
    }

    private fun selectUserReviewsForReviewableBookings(userId: String): List<Review> =
        runCatching {
            selectAll(
                "reviews",
                linkedMapOf(
                    "select" to "*",
                    "user_id" to "eq.$userId",
                    "order" to "created_at.desc"
                )
            ).toReviewList()
        }.getOrElse {
            selectAll(
                "reviews",
                linkedMapOf(
                    "select" to "id,room_id,user_id,rating,comment",
                    "user_id" to "eq.$userId"
                )
            ).toReviewList()
        }

    private fun selectExistingReviewForBooking(userId: String, bookingId: String, roomId: String): JSONArray =
        runCatching {
            select(
                "reviews",
                mapOf("select" to "id", "user_id" to "eq.$userId", "booking_id" to "eq.$bookingId", "limit" to "1")
            )
        }.getOrElse {
            select(
                "reviews",
                mapOf("select" to "id", "user_id" to "eq.$userId", "room_id" to "eq.$roomId", "limit" to "1")
            )
        }

    private fun upsertReviewWithSchemaFallback(review: Review) {
        runCatching {
            upsert("reviews", reviewToJson(review))
        }.getOrElse {
            upsert(
                "reviews",
                JSONObject()
                    .put("id", review.id)
                    .put("room_id", review.roomId)
                    .put("user_id", review.userId)
                    .put("rating", review.rating)
                    .put("comment", review.comment)
                    .put("created_at", millisToIso(review.createdAt))
            )
        }
    }

    private fun patchRoomRating(roomId: String, average: Double, reviewCount: Int) {
        val payload = JSONObject()
            .put("rating", average)
            .put("review_count", reviewCount)
        runCatching {
            patch("rooms", mapOf("id" to "eq.$roomId"), payload)
        }.recoverCatching {
            patch("hotel_rooms", mapOf("id" to "eq.$roomId"), payload)
        }
    }

    private fun resolveReviewRoomAliases(roomId: String): Set<String> {
        val aliases = linkedSetOf<String>()
        addRoomAliasVariants(roomId, aliases)
        val lookupKeys = aliases.toList()
        lookupKeys.forEach { key ->
            runCatching {
                select("hotel_rooms", mapOf("select" to "*", "id" to "eq.$key", "limit" to "1")).firstObjectOrNull()
                    ?: select("hotel_rooms", mapOf("select" to "*", "room_code" to "eq.$key", "limit" to "1")).firstObjectOrNull()
            }.getOrNull()?.let { row ->
                addRoomAliasVariants(row.optString("id"), aliases)
                addRoomAliasVariants(row.optString("room_code"), aliases)
                val slug = row.optString("slug")
                val hotelSlug = row.optString("hotel_slug").ifBlank { row.optString("hotel_id") }
                if (hotelSlug.isNotBlank() && slug.isNotBlank()) {
                    addRoomAliasVariants("$hotelSlug:$slug", aliases)
                    addRoomAliasVariants("catalog:$hotelSlug:$slug", aliases)
                }
            }
            runCatching {
                select("rooms", mapOf("select" to "id,code", "id" to "eq.$key", "limit" to "1")).firstObjectOrNull()
                    ?: select("rooms", mapOf("select" to "id,code", "code" to "eq.$key", "limit" to "1")).firstObjectOrNull()
            }.getOrNull()?.let { row ->
                addRoomAliasVariants(row.optString("id"), aliases)
                addRoomAliasVariants(row.optString("code"), aliases)
            }
        }
        return aliases.filterTo(linkedSetOf()) { it.isNotBlank() }
    }

    private fun addRoomAliasVariants(value: String, aliases: MutableSet<String>) {
        val clean = value.trim()
        if (clean.isBlank()) return
        aliases.add(clean)
        if (clean.startsWith("catalog:")) {
            aliases.add(clean.removePrefix("catalog:"))
        } else if (clean.count { it == ':' } == 1) {
            aliases.add("catalog:$clean")
        }
    }

    private fun upsertBookingWithSchemaFallback(booking: Booking) {
        runCatching {
            upsert("bookings", bookingToJson(booking))
        }.getOrElse {
            upsert(
                "bookings",
                JSONObject()
                    .put("id", booking.id)
                    .put("user_id", booking.userId)
                    .put("room_id", booking.roomId)
                    .put("check_in", normalizeDate(booking.checkIn))
                    .put("check_out", normalizeDate(booking.checkOut))
                    .put("status", booking.status)
                    .put("total", booking.total)
                    .put("add_ons", JSONArray(booking.addOns))
                    .put("created_at", millisToIso(booking.createdAt))
            )
        }
    }

    private fun upsertIssueWithSchemaFallback(issue: IssueReport) {
        runCatching {
            upsert("issues", issueToJson(issue))
        }.getOrElse {
            upsert(
                "issues",
                JSONObject()
                    .put("id", issue.id)
                    .put("user_id", issue.userId)
                    .put("room_id", issue.roomId)
                    .put("title", issue.title)
                    .put("description", issue.description)
                    .put("status", normalizeIssueStatus(issue.status))
                    .put("created_at", millisToIso(issue.createdAt))
            )
        }
    }

    fun createIssue(issue: IssueReport, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { upsertIssueWithSchemaFallback(issue.copy(id = issue.id.ifBlank { UUID.randomUUID().toString() })) }

    fun listIssues(userId: String?, onSuccess: (List<IssueReport>) -> Unit, onError: (Exception) -> Unit) {
        runAsync(onSuccess, onError) {
            val q = linkedMapOf("select" to "*", "limit" to "100", "order" to "created_at.desc")
            if (!userId.isNullOrBlank()) q["user_id"] = "eq.$userId"
            select("issues", q).toIssueList()
        }
    }

    fun updateIssueStatus(issueId: String, status: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (issueId.isBlank()) {
            onError(IllegalArgumentException("Missing issue id"))
            return
        }
        runAsyncUnit(onSuccess, onError) {
            patch("issues", mapOf("id" to "eq.$issueId"), JSONObject().put("status", normalizeIssueStatus(status)))
        }
    }

    fun createVoucher(voucher: Voucher, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) {
            val normalized = voucher.copy(id = voucher.id.ifBlank { voucher.code.ifBlank { UUID.randomUUID().toString() } })
            try {
                upsert("vouchers", voucherToJson(normalized))
            } catch (e: Exception) {
                if (!isSchemaMismatch(e)) throw e
                try {
                    upsert("vouchers", legacyVoucherToJson(normalized, includeUsageLimit = true))
                } catch (legacyError: Exception) {
                    if (!isSchemaMismatch(legacyError)) throw legacyError
                    upsert("vouchers", legacyVoucherToJson(normalized, includeUsageLimit = false))
                }
            }
        }

    fun listVouchers(onSuccess: (List<Voucher>) -> Unit, onError: (Exception) -> Unit) =
        runAsync(onSuccess, onError) { select("vouchers", mapOf("select" to "*", "order" to "code.asc")).toVoucherList() }

    fun getVoucherByCode(code: String, onSuccess: (Voucher?) -> Unit, onError: (Exception) -> Unit) =
        runAsync(onSuccess, onError) { select("vouchers", mapOf("select" to "*", "code" to "eq.$code", "limit" to "1")).firstObjectOrNull()?.toVoucher() }

    fun incrementVoucherUsage(voucherId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (voucherId.isBlank()) {
            onSuccess()
            return
        }
        runAsyncUnit(onSuccess, onError) {
            val voucher = select("vouchers", mapOf("select" to "*", "id" to "eq.$voucherId", "limit" to "1")).firstObjectOrNull()?.toVoucher()
                ?: return@runAsyncUnit
            patch("vouchers", mapOf("id" to "eq.$voucherId"), JSONObject().put("used_count", voucher.usedCount + 1))
        }
    }

    fun recordVoucherUsage(payment: Payment, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (payment.voucherCode.isBlank() || payment.userId.isBlank() || payment.bookingId.isBlank()) {
            onSuccess()
            return
        }
        runAsyncUnit(onSuccess, onError) {
            val existing = select(
                "voucher_usage",
                mapOf(
                    "select" to "id",
                    "user_id" to "eq.${payment.userId}",
                    "booking_id" to "eq.${payment.bookingId}",
                    "limit" to "1"
                )
            )
            if (existing.length() > 0) return@runAsyncUnit
            upsert(
                "voucher_usage",
                JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("voucher_id", payment.voucherId)
                    .put("voucher_code", payment.voucherCode)
                    .put("user_id", payment.userId)
                    .put("booking_id", payment.bookingId)
                    .put("payment_id", payment.id)
                    .put("discount_amount", payment.discountAmount)
                    .put("used_at", millisToIso(System.currentTimeMillis()))
            )
        }
    }

    fun hasUserUsedVoucher(userId: String, voucher: Voucher, onSuccess: (Boolean) -> Unit, onError: (Exception) -> Unit) {
        if (userId.isBlank() || (voucher.id.isBlank() && voucher.code.isBlank())) {
            onSuccess(false)
            return
        }
        runAsync(onSuccess, onError) {
            val byId = if (voucher.id.isNotBlank()) {
                select(
                    "voucher_usage",
                    mapOf("select" to "id", "user_id" to "eq.$userId", "voucher_id" to "eq.${voucher.id}", "limit" to "1")
                ).length() > 0
            } else {
                false
            }
            byId || select(
                "voucher_usage",
                mapOf("select" to "id", "user_id" to "eq.$userId", "voucher_code" to "eq.${voucher.code}", "limit" to "1")
            ).length() > 0
        }
    }

    fun deleteVoucher(voucherId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (voucherId.isBlank()) {
            onError(IllegalArgumentException("Missing voucher id"))
            return
        }
        runAsyncUnit(onSuccess, onError) { delete("vouchers", mapOf("id" to "eq.$voucherId")) }
    }

    fun createPoster(poster: Poster, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) {
            val normalized = poster.copy(id = poster.id.ifBlank { UUID.randomUUID().toString() })
            try {
                upsert("posters", posterToJson(normalized, canonical = true))
            } catch (e: Exception) {
                if (isSchemaMismatch(e)) upsert("posters", posterToJson(normalized, canonical = false)) else throw e
            }
        }

    fun listPosters(type: String, limit: Long, onSuccess: (List<Poster>) -> Unit, onError: (Exception) -> Unit) =
        runAsync(onSuccess, onError) { select("posters", mapOf("select" to "*", "type" to "eq.$type", "order" to "created_at.desc", "limit" to limit.toString())).toPosterList() }

    fun deletePoster(posterId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (posterId.isBlank()) {
            onError(IllegalArgumentException("Missing poster id"))
            return
        }
        runAsyncUnit(onSuccess, onError) { delete("posters", mapOf("id" to "eq.$posterId")) }
    }

    fun listAvailableRooms(onSuccess: (List<Room>) -> Unit, onError: (Exception) -> Unit) =
        searchRooms("", onSuccess, onError)

    fun createRoomRequest(request: RoomRequest, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) {
            upsert("room_requests", roomRequestToJson(request.copy(id = request.id.ifBlank { UUID.randomUUID().toString() })))
        }

    fun listRoomRequests(userId: String?, onSuccess: (List<RoomRequest>) -> Unit, onError: (Exception) -> Unit) {
        runAsync(onSuccess, onError) {
            val q = linkedMapOf("select" to "*", "limit" to "100", "order" to "created_at.desc")
            if (!userId.isNullOrBlank()) q["user_id"] = "eq.$userId"
            select("room_requests", q).toRoomRequestList()
        }
    }

    fun updateRoomRequest(requestId: String, status: String, adminReply: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (requestId.isBlank()) {
            onError(IllegalArgumentException("Missing room request id"))
            return
        }
        runAsyncUnit(onSuccess, onError) {
            patch(
                "room_requests",
                mapOf("id" to "eq.$requestId"),
                JSONObject()
                    .put("status", normalizeRoomRequestStatus(status))
                    .put("admin_reply", adminReply)
                    .put("updated_at", millisToIso(System.currentTimeMillis()))
            )
        }
    }

    fun createAddOn(item: AddOnItem, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { upsert("add_ons", addOnToJson(item.copy(id = item.id.ifBlank { UUID.randomUUID().toString() }))) }

    fun listAddOns(onSuccess: (List<AddOnItem>) -> Unit, onError: (Exception) -> Unit) =
        runAsync(onSuccess, onError) { select("add_ons", mapOf("select" to "*", "order" to "name.asc")).toAddOnList() }

    fun deleteAddOn(addOnId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (addOnId.isBlank()) {
            onError(IllegalArgumentException("Missing add-on id"))
            return
        }
        runAsyncUnit(onSuccess, onError) { delete("add_ons", mapOf("id" to "eq.$addOnId")) }
    }

    fun createNotification(notification: AppNotification, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        sendNotificationToUser(notification, onSuccess, onError)

    fun sendNotificationToUser(notification: AppNotification, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) {
            val normalized = notification.copy(id = notification.id.ifBlank { UUID.randomUUID().toString() })
            createInAppNotification(normalized)
            triggerEmailNotificationIfConfigured(normalized)
        }

    private fun createInAppNotification(notification: AppNotification) {
        try {
            upsert("notifications", notificationToJson(notification, canonical = true))
        } catch (e: Exception) {
            if (isSchemaMismatch(e)) upsert("notifications", notificationToJson(notification, canonical = false)) else throw e
        }
    }

    private fun triggerEmailNotificationIfConfigured(notification: AppNotification) {
        // Email delivery belongs in a backend/Edge Function. The Android app only persists in-app notifications.
    }

    fun markNotificationRead(notificationId: String, read: Boolean, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (notificationId.isBlank()) {
            onError(IllegalArgumentException("Missing notification id"))
            return
        }
        runAsyncUnit(onSuccess, onError) {
            try {
                patch(
                    "notifications",
                    mapOf("id" to "eq.$notificationId"),
                    JSONObject()
                        .put("is_read", read)
                        .put("read_at", if (read) millisToIso(System.currentTimeMillis()) else JSONObject.NULL)
                )
            } catch (e: Exception) {
                if (!isSchemaMismatch(e)) throw e
                patch("notifications", mapOf("id" to "eq.$notificationId"), JSONObject().put("is_read", read))
            }
        }
    }

    fun markAllNotificationsRead(userId: String, role: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        runAsyncUnit(onSuccess, onError) {
            val normalized = if (normalizeRole(role) == "admin") "admin" else "client"
            val filters = if (userId.isNotBlank()) {
                mapOf("or" to "(user_id.eq.$userId,user_id.is.null)", "target_role" to "in.(all,$normalized)")
            } else {
                mapOf("target_role" to "in.(all,$normalized)")
            }
            try {
                patch(
                    "notifications",
                    filters,
                    JSONObject()
                        .put("is_read", true)
                        .put("read_at", millisToIso(System.currentTimeMillis()))
                )
            } catch (e: Exception) {
                if (!isSchemaMismatch(e)) throw e
                patch(
                    "notifications",
                    mapOf("target_role" to "in.(all,$normalized)"),
                    JSONObject().put("is_read", true)
                )
            }
        }
    }

    fun createPayment(payment: Payment, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) {
            val paymentWithId = payment.copy(id = payment.id.ifBlank { UUID.randomUUID().toString() })
            try {
                upsert("payments", paymentToJson(paymentWithId, minimal = false))
            } catch (e: Exception) {
                if (e.message?.contains("PGRST204") == true || e.message?.contains("column") == true) {
                    upsert("payments", paymentToJson(paymentWithId, minimal = true))
                } else {
                    throw e
                }
            }
        }

    fun listPayments(userId: String?, onSuccess: (List<Payment>) -> Unit, onError: (Exception) -> Unit) {
        runAsync(onSuccess, onError) {
            val q = linkedMapOf("select" to "*", "limit" to "100", "order" to "created_at.desc")
            if (!userId.isNullOrBlank()) q["user_id"] = "eq.$userId"
            select("payments", q).toPaymentList()
        }
    }

    fun fetchNotificationSettings(userId: String, onSuccess: (NotificationSettings) -> Unit, onError: (Exception) -> Unit) {
        runAsync(onSuccess, onError) {
            val row = runCatching {
                select("notification_settings", mapOf("select" to "*", "user_id" to "eq.$userId", "limit" to "1")).firstObjectOrNull()
            }.getOrNull()
            val raw = row ?: runCatching {
                select("users", mapOf("select" to "raw", "id" to "eq.$userId", "limit" to "1")).firstObjectOrNull()?.optJSONObject("raw")
            }.getOrNull()
            NotificationSettings(
                checkIn = raw?.optBooleanCompat("check_in", raw.optBooleanCompat("notifCheckIn", true)) ?: true,
                promo = raw?.optBooleanCompat("promo", raw.optBooleanCompat("notifPromo", true)) ?: true,
                roomStatus = raw?.optBooleanCompat("room_status", raw.optBooleanCompat("notifRoomStatus", true)) ?: true,
                booking = raw?.optBooleanCompat("booking", raw.optBooleanCompat("notifBooking", true)) ?: true,
                review = raw?.optBooleanCompat("review", raw.optBooleanCompat("notifReview", true)) ?: true,
                issue = raw?.optBooleanCompat("issue", raw.optBooleanCompat("notifIssue", true)) ?: true,
                payment = raw?.optBooleanCompat("payment", raw.optBooleanCompat("notifPayment", true)) ?: true
            )
        }
    }

    fun updateNotificationSettings(userId: String, settings: NotificationSettings, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        runAsyncUnit(onSuccess, onError) {
            val payload = notificationSettingsToJson(userId, settings)
            try {
                upsert("notification_settings", payload)
            } catch (e: Exception) {
                if (!isSchemaMismatch(e)) throw e
                val currentRaw = select("users", mapOf("select" to "raw", "id" to "eq.$userId", "limit" to "1"))
                    .firstObjectOrNull()?.optJSONObject("raw") ?: JSONObject()
                currentRaw.put("notifCheckIn", settings.checkIn)
                currentRaw.put("notifPromo", settings.promo)
                currentRaw.put("notifRoomStatus", settings.roomStatus)
                currentRaw.put("notifBooking", settings.booking)
                currentRaw.put("notifReview", settings.review)
                currentRaw.put("notifIssue", settings.issue)
                currentRaw.put("notifPayment", settings.payment)
                patch("users", mapOf("id" to "eq.$userId"), JSONObject().put("raw", currentRaw))
            }
        }
    }

    fun listenNotifications(role: String, userId: String = "", onSuccess: (List<AppNotification>) -> Unit, onError: (Exception) -> Unit) {
        val normalized = if (normalizeRole(role) == "admin") "admin" else "client"
        runAsync(onSuccess, onError) {
            val q = linkedMapOf("select" to "*", "target_role" to "in.(all,$normalized)", "order" to "created_at.desc", "limit" to "50")
            if (userId.isNotBlank()) q["or"] = "(user_id.eq.$userId,user_id.is.null)"
            try {
                select("notifications", q).toNotificationList()
            } catch (e: Exception) {
                if (!isSchemaMismatch(e)) throw e
                select(
                    "notifications",
                    mapOf("select" to "*", "target_role" to "in.(all,$normalized)", "order" to "created_at.desc", "limit" to "50")
                ).toNotificationList()
            }
        }
    }

    private fun getUserProfileById(userId: String): UserProfile? =
        select("users", mapOf("select" to "*", "id" to "eq.$userId", "limit" to "1")).firstObjectOrNull()?.toUserProfile()

    private fun upsert(table: String, payload: JSONObject) {
        val response = request(
            method = "POST",
            path = "/rest/v1/$table",
            query = mapOf("on_conflict" to "id"),
            jsonBody = payload.toString(),
            headers = mapOf("Prefer" to "resolution=merge-duplicates,return=representation")
        )
        if (response.code !in 200..299) throw IllegalStateException("Upsert $table failed: HTTP ${response.code}${response.body.toErrorSuffix()}")
    }

    private fun patch(table: String, filters: Map<String, String>, payload: JSONObject) {
        val response = request("PATCH", "/rest/v1/$table", query = filters, jsonBody = payload.toString())
        if (response.code !in 200..299) throw IllegalStateException("Patch $table failed: HTTP ${response.code}${response.body.toErrorSuffix()}")
    }

    private fun delete(table: String, filters: Map<String, String>) {
        val response = request("DELETE", "/rest/v1/$table", query = filters)
        if (response.code !in 200..299) throw IllegalStateException("Delete $table failed: HTTP ${response.code}${response.body.toErrorSuffix()}")
    }

    private fun select(table: String, query: Map<String, String>): JSONArray {
        val response = request("GET", "/rest/v1/$table", query = query)
        if (response.code !in 200..299) throw IllegalStateException("Select $table failed: HTTP ${response.code}${response.body.toErrorSuffix()}")
        return if (response.body.isBlank()) JSONArray() else JSONArray(response.body)
    }

    private fun selectAll(table: String, query: Map<String, String>, pageSize: Int = 1000): JSONArray {
        val baseQuery = query.filterKeys { it != "limit" && it != "offset" }
        val rows = JSONArray()
        var offset = 0
        do {
            val page = select(
                table,
                baseQuery + mapOf("limit" to pageSize.toString(), "offset" to offset.toString())
            )
            for (index in 0 until page.length()) {
                rows.put(page.opt(index))
            }
            offset += page.length()
        } while (page.length() == pageSize)
        return rows
    }

    private fun parseAndPersistAuth(body: String): ParsedAuthPayload {
        val root = JSONObject(body)
        val userObj = root.optJSONObject("user") ?: throw IllegalStateException("Khong nhan duoc user")
        val parsed = ParsedAuthPayload(
            user = parseSupabaseUser(userObj),
            accessToken = root.optString("access_token"),
            refreshToken = root.optString("refresh_token")
        )
        if (parsed.accessToken.isBlank()) {
            clearAuthState()
        } else {
            persistAuthState(parsed.user, parsed.accessToken, parsed.refreshToken)
        }
        return parsed
    }

    private fun parseSignUpResponse(body: String): SignUpResult {
        if (body.isBlank()) {
            clearAuthState()
            return SignUpResult(user = null, requiresEmailConfirmation = true)
        }

        val root = try {
            JSONObject(body)
        } catch (_: Exception) {
            clearAuthState()
            return SignUpResult(user = null, requiresEmailConfirmation = true)
        }

        val accessToken = root.optString("access_token")
        val refreshToken = root.optString("refresh_token")
        val user = root.optJSONObject("user")?.let { userObj ->
            runCatching { parseSupabaseUser(userObj) }.getOrNull()
        }

        if (accessToken.isBlank()) {
            clearAuthState()
            return SignUpResult(user = user, requiresEmailConfirmation = true)
        }

        val resolvedUser = user ?: throw IllegalStateException("Khong nhan duoc user")
        persistAuthState(resolvedUser, accessToken, refreshToken)
        return SignUpResult(user = resolvedUser, requiresEmailConfirmation = false)
    }

    private fun parseSupabaseUser(userObj: JSONObject): SupabaseUser {
        val userMetadata = userObj.optJSONObject("user_metadata")
            ?: userObj.optJSONObject("raw_user_meta_data")
            ?: JSONObject()
        val user = SupabaseUser(
            uid = userObj.optString("id"),
            email = userObj.optString("email"),
            displayName = userMetadata.optString("full_name"),
            phone = userMetadata.optString("phone")
        )
        if (user.uid.isBlank()) throw IllegalStateException("User id tu Supabase bi trong")
        return user
    }

    private fun request(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        jsonBody: String? = null,
        headers: Map<String, String> = emptyMap(),
        bearer: String = accessToken.ifBlank { BuildConfig.SUPABASE_ANON_KEY },
        retryOnUnauthorized: Boolean = true
    ): HttpResult {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        if (base.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            throw IllegalStateException("Chua cau hinh SUPABASE_URL va SUPABASE_ANON_KEY")
        }
        val resolvedBearer = bearer.ifBlank { BuildConfig.SUPABASE_ANON_KEY }
        val uriBuilder = Uri.parse("$base$path").buildUpon()
        query.forEach { (k, v) -> uriBuilder.appendQueryParameter(k, v) }
        val connection = (URL(uriBuilder.build().toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 30000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $resolvedBearer")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        if (jsonBody != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
        if (
            code == 401 &&
            retryOnUnauthorized &&
            resolvedBearer.isNotBlank() &&
            resolvedBearer == accessToken &&
            refreshToken.isNotBlank() &&
            refreshSession()
        ) {
            return request(
                method = method,
                path = path,
                query = query,
                jsonBody = jsonBody,
                headers = headers,
                bearer = accessToken,
                retryOnUnauthorized = false
            )
        }
        return HttpResult(code, text)
    }

    private fun refreshSession(): Boolean {
        synchronized(authLock) {
            val tokenToRefresh = refreshToken
            if (tokenToRefresh.isBlank()) return false

            val response = request(
                method = "POST",
                path = "/auth/v1/token",
                query = mapOf("grant_type" to "refresh_token"),
                jsonBody = JSONObject().put("refresh_token", tokenToRefresh).toString(),
                bearer = BuildConfig.SUPABASE_ANON_KEY,
                retryOnUnauthorized = false
            )
            if (response.code !in 200..299) {
                clearAuthState()
                return false
            }
            parseAndPersistAuth(response.body)
            return true
        }
    }

    private fun persistAuthState(user: SupabaseUser, token: String, refresh: String) {
        authUser = user
        accessToken = token
        refreshToken = refresh
        appContext?.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY_UID, user.uid)
            ?.putString(KEY_EMAIL, user.email)
            ?.putString(KEY_NAME, user.displayName)
            ?.putString(KEY_ACCESS_TOKEN, token)
            ?.putString(KEY_REFRESH_TOKEN, refresh)
            ?.apply()
    }

    private fun loadAuthState() {
        val prefs = appContext?.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE) ?: return
        val uid = prefs.getString(KEY_UID, "").orEmpty()
        if (uid.isBlank()) {
            authUser = null
            accessToken = ""
            refreshToken = ""
            return
        }
        authUser = SupabaseUser(
            uid = uid,
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            displayName = prefs.getString(KEY_NAME, "").orEmpty()
        )
        accessToken = prefs.getString(KEY_ACCESS_TOKEN, "").orEmpty()
        refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "").orEmpty()
    }

    private fun clearAuthState() {
        authUser = null
        accessToken = ""
        refreshToken = ""
        appContext?.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)?.edit()
            ?.remove(KEY_UID)?.remove(KEY_EMAIL)?.remove(KEY_NAME)
            ?.remove(KEY_ACCESS_TOKEN)?.remove(KEY_REFRESH_TOKEN)?.apply()
    }

    private fun authError(raw: String, fallback: String): String {
        return try {
            val o = JSONObject(raw)
            o.optString("msg").ifBlank {
                o.optString("message").ifBlank {
                    o.optString("error_description").ifBlank {
                        o.optString("error").ifBlank { fallback }
                    }
                }
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun normalizeRole(role: String): String = if (role.trim().lowercase() == "admin") "admin" else "client"
    private fun normalizeTargetRole(role: String): String = when (role.trim().lowercase()) {
        "admin" -> "admin"
        "client" -> "client"
        else -> "all"
    }
    private fun normalizeIssueStatus(status: String): String = when (status.trim().lowercase()) {
        "processing", "in_progress", "dang_xu_ly", "đang xử lý" -> "processing"
        "resolved", "done", "closed", "da_xu_ly", "đã xử lý" -> "resolved"
        else -> "new"
    }

    private fun <T> runAsync(onSuccess: (T) -> Unit, onError: (Exception) -> Unit, work: () -> T) {
        Thread {
            try {
                val result = work()
                mainHandler.post { onSuccess(result) }
            } catch (e: Exception) {
                mainHandler.post { onError(e) }
            }
        }.start()
    }

    private fun runAsyncUnit(onSuccess: () -> Unit, onError: (Exception) -> Unit, work: () -> Unit) {
        runAsync(onSuccess = { _: Unit -> onSuccess() }, onError = onError, work = work)
    }

    private fun millisToIso(millis: Long): String = Instant.ofEpochMilli(if (millis <= 0L) System.currentTimeMillis() else millis).atOffset(ZoneOffset.UTC).toString()
    private fun normalizeDate(text: String): String = try { LocalDate.parse(text.trim()).toString() } catch (_: Exception) { LocalDate.now().toString() }
    private fun String.toErrorSuffix(): String = trim().takeIf { it.isNotBlank() }?.let { ": ${it.take(240)}" }.orEmpty()
    private fun String.toPostgrestInValue(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun parseTimestampMillis(value: Any?): Long {
        return when (value) {
            is Number -> value.toLong()
            is String -> try { OffsetDateTime.parse(value).toInstant().toEpochMilli() } catch (_: Exception) { try { Instant.parse(value).toEpochMilli() } catch (_: Exception) { 0L } }
            else -> 0L
        }
    }

    private fun loadHotelLookup(): Map<String, HotelLookup> {
        val hotels = selectAll("hotels", mapOf("select" to "id,display_name,area,city", "order" to "display_name.asc"))
        if (hotels.length() == 0) {
            return fallbackHotels().associate { hotel ->
                hotel.id to HotelLookup(
                    displayName = hotel.displayName.ifBlank { hotel.name },
                    area = hotel.area,
                    city = hotel.city
                )
            }
        }
        return (0 until hotels.length())
            .mapNotNull { index ->
                val item = hotels.optJSONObject(index)
                item?.optString("id")?.takeIf { it.isNotBlank() }?.let { id ->
                    id to HotelLookup(
                        displayName = item.optString("display_name"),
                        area = item.optString("area"),
                        city = item.optString("city")
                    )
                }
            }
            .toMap()
    }

    private fun JSONArray.firstObjectOrNull(): JSONObject? = if (length() == 0) null else optJSONObject(0)

    private fun JSONObject.optDoubleCompat(key: String): Double = when (val v = opt(key)) { is Number -> v.toDouble(); is String -> v.toDoubleOrNull() ?: 0.0; else -> 0.0 }
    private fun JSONObject.optIntCompat(key: String): Int = when (val v = opt(key)) { is Number -> v.toInt(); is String -> v.toIntOrNull() ?: 0; else -> 0 }
    private fun JSONObject.optBooleanCompat(key: String, default: Boolean): Boolean = when (val v = opt(key)) {
        null, JSONObject.NULL -> default
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v.equals("true", true) || v == "1"
        else -> default
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val value = opt(key)
        return when (value) {
            is JSONArray -> buildList {
                for (i in 0 until value.length()) {
                    val x = value.optString(i).trim()
                    if (x.isNotBlank()) add(x)
                }
            }
            is String -> value.split(",").map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun JSONObject.toUserProfile(): UserProfile = UserProfile(
        id = optString("id"),
        name = optString("name"),
        email = optString("email"),
        phone = optString("phone"),
        role = normalizeRole(optString("role", "client")),
        createdAt = parseTimestampMillis(opt("created_at"))
    )

    private fun JSONObject.toRoom(hotelLookup: Map<String, HotelLookup> = emptyMap()): Room {
        val raw = optJSONObject("raw")
        val hotelId = raw?.optString("hotel_id").orEmpty()
        val hotelMeta = hotelLookup[hotelId]
        val displayType = shortenRoomType(
            rawRoomName = raw?.optString("room_name").orEmpty(),
            fallbackType = optString("type")
        )
        return Room(
            id = optString("id"),
            code = optString("code"),
            type = optString("type"),
            displayType = displayType,
            typeKey = toRoomTypeKey(displayType),
            price = optDoubleCompat("price"),
            rating = optDoubleCompat("rating"),
            reviewCount = optIntCompat("review_count"),
            status = optString("status", "available"),
            capacity = optIntCompat("capacity").takeIf { it > 0 } ?: 2,
            images = optStringList("images"),
            createdAt = parseTimestampMillis(opt("created_at")),
            hotelId = hotelId,
            hotelName = raw?.optString("hotel_name").orEmpty().ifBlank { hotelMeta?.displayName.orEmpty() },
            area = hotelMeta?.area.orEmpty(),
            city = hotelMeta?.city.orEmpty()
        )
    }

    private fun JSONObject.toHotelCatalogItem(): HotelCatalogItem = HotelCatalogItem(
        id = optString("id"),
        folderName = optString("folder_name"),
        slug = optString("slug"),
        name = optString("name"),
        displayName = optString("display_name").ifBlank { optString("name") },
        city = optString("city"),
        area = optString("area"),
        country = optString("country"),
        addressFull = optString("address_full"),
        shortDescription = optString("short_description"),
        description = optString("description"),
        starRating = optDoubleCompat("star_rating"),
        reviewScore = optDoubleCompat("review_score"),
        reviewCount = optIntCompat("review_count"),
        roomCount = optIntCompat("room_count"),
        checkInFrom = optString("check_in_from"),
        checkOutUntil = optString("check_out_until"),
        latitude = when (val value = opt("latitude")) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        },
        longitude = when (val value = opt("longitude")) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        },
        contactPhone = optString("contact_phone"),
        contactEmail = optString("contact_email"),
        heroImage = optString("hero_image"),
        galleryImages = optStringList("gallery_images"),
        featuredAmenities = optStringList("featured_amenities"),
        generalAmenities = optStringList("general_amenities"),
        policyNotes = optStringList("policy_notes"),
        sourceUrl = optString("source_url")
    )

    private fun JSONObject.toHotelCatalogRoom(): HotelCatalogRoom = HotelCatalogRoom(
        id = optString("id"),
        hotelId = optString("hotel_id"),
        hotelSlug = optString("hotel_slug"),
        roomCode = optString("room_code"),
        name = optString("name"),
        slug = optString("slug"),
        price = optDoubleCompat("price"),
        originalPrice = when (val value = opt("original_price")) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        },
        currency = optString("currency", "VND"),
        rating = optDoubleCompat("rating"),
        reviewCount = optIntCompat("review_count"),
        status = optString("status", "available"),
        capacity = optIntCompat("capacity").takeIf { it > 0 } ?: 2,
        images = optStringList("images"),
        heroImage = optString("hero_image"),
        roomSizeSqm = when (val value = opt("room_size_sqm")) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        },
        roomSizeSqft = when (val value = opt("room_size_sqft")) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        },
        view = optString("view"),
        breakfastIncluded = optBooleanCompat("breakfast_included", false),
        cancellationPolicy = optString("cancellation_policy"),
        bedSummary = optString("bed_summary"),
        bedCount = optIntCompat("bed_count"),
        amenities = optStringList("amenities"),
        bathroomAmenities = optStringList("bathroom_amenities"),
        tags = optStringList("tags"),
        shortDescription = optString("short_description")
    )

    private fun JSONObject.toSearchRoom(hotelLookup: Map<String, HotelLookup> = emptyMap()): Room {
        val room = toHotelCatalogRoom()
        val hotelMeta = hotelLookup[room.hotelId]
        val displayType = room.name.ifBlank { room.slug }
        return Room(
            id = room.id,
            code = room.roomCode,
            type = displayType,
            displayType = displayType,
            typeKey = toRoomTypeKey(displayType),
            price = room.price,
            rating = room.rating,
            reviewCount = room.reviewCount,
            status = room.status,
            capacity = room.capacity,
            images = listOfNotNull(room.heroImage.takeIf { it.isNotBlank() }) + room.images,
            hotelId = room.hotelId,
            hotelName = hotelMeta?.displayName.orEmpty().ifBlank { room.hotelSlug },
            area = hotelMeta?.area.orEmpty(),
            city = hotelMeta?.city.orEmpty()
        )
    }

    private fun fallbackHotels(): List<HotelCatalogItem> {
        val context = appContext ?: return emptyList()
        val assets = context.assets
        return runCatching {
            assets.list("").orEmpty()
                .mapNotNull { folder ->
                    readAssetObject("$folder/data/hotel.json")?.toFallbackHotel(folder)
                }
                .sortedBy { it.displayName.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun fallbackHotelRooms(hotelId: String? = null): List<HotelCatalogRoom> {
        val context = appContext ?: return emptyList()
        val assets = context.assets
        return runCatching {
            assets.list("").orEmpty().flatMap { folder ->
                val hotel = readAssetObject("$folder/data/hotel.json")?.toFallbackHotel(folder)
                val roomsRoot = readAssetObject("$folder/data/rooms.json")
                if (hotel == null || roomsRoot == null || (!hotelId.isNullOrBlank() && hotel.id != hotelId && hotel.slug != hotelId)) {
                    emptyList()
                } else {
                    val rooms = roomsRoot.optJSONArray("rooms") ?: JSONArray()
                    (0 until rooms.length()).mapNotNull { index ->
                        rooms.optJSONObject(index)?.toFallbackHotelRoom(hotel, folder)
                    }
                }
            }.sortedBy { it.price }
        }.getOrDefault(emptyList())
    }

    private fun fallbackSearchRooms(): List<Room> {
        val lookup = fallbackHotels().associate { hotel ->
            hotel.id to HotelLookup(
                displayName = hotel.displayName.ifBlank { hotel.name },
                area = hotel.area,
                city = hotel.city
            )
        }
        return fallbackHotelRooms().map { room ->
            val hotelMeta = lookup[room.hotelId]
            val displayType = room.name.ifBlank { room.slug }
            Room(
                id = room.id,
                code = room.roomCode,
                type = displayType,
                displayType = displayType,
                typeKey = toRoomTypeKey(displayType),
                price = room.price,
                rating = room.rating,
                reviewCount = room.reviewCount,
                status = room.status,
                capacity = room.capacity,
                images = listOfNotNull(room.heroImage.takeIf { it.isNotBlank() }) + room.images,
                hotelId = room.hotelId,
                hotelName = hotelMeta?.displayName.orEmpty().ifBlank { room.hotelSlug },
                area = hotelMeta?.area.orEmpty(),
                city = hotelMeta?.city.orEmpty()
            )
        }
    }

    private fun readAssetObject(path: String): JSONObject? {
        val context = appContext ?: return null
        return runCatching {
            context.assets.open(path).use { stream ->
                JSONObject(BufferedReader(InputStreamReader(stream)).readText())
            }
        }.getOrNull()
    }

    private fun JSONObject.toFallbackHotel(folder: String): HotelCatalogItem {
        val address = optJSONObject("address")
        val review = optJSONObject("review")
        val description = optJSONObject("description")
        val checkIn = optJSONObject("check_in")
        val checkOut = optJSONObject("check_out")
        val contact = optJSONObject("contact")
        val coordinates = optJSONObject("coordinates")
        val gallery = optImageUrlList("images", folder)
        return HotelCatalogItem(
            id = optString("slug").ifBlank { folder },
            folderName = optString("folder_name").ifBlank { folder },
            slug = optString("slug").ifBlank { folder },
            name = optString("name"),
            displayName = optString("display_name").ifBlank { optString("name") },
            city = address?.optString("city").orEmpty(),
            area = address?.optString("area").orEmpty(),
            country = address?.optString("country").orEmpty(),
            addressFull = address?.optString("full").orEmpty(),
            shortDescription = optString("short_description"),
            description = description?.optString("overview_text").orEmpty(),
            starRating = optDoubleCompat("star_rating"),
            reviewScore = review?.optDoubleCompat("score") ?: 0.0,
            reviewCount = review?.optIntCompat("review_count") ?: 0,
            roomCount = optIntCompat("room_count"),
            checkInFrom = checkIn?.optString("from").orEmpty(),
            checkOutUntil = checkOut?.optString("until").orEmpty(),
            latitude = coordinates?.opt("latitude")?.let { value -> (value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull() },
            longitude = coordinates?.opt("longitude")?.let { value -> (value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull() },
            contactPhone = contact?.optString("phone").orEmpty(),
            contactEmail = contact?.optString("email").orEmpty(),
            heroImage = optString("hero_image_url").ifBlank { gallery.firstOrNull().orEmpty() },
            galleryImages = gallery,
            featuredAmenities = optStringList("featured_amenities"),
            generalAmenities = optStringList("general_amenities"),
            policyNotes = optStringList("policy_notes"),
            sourceUrl = optString("source_url")
        )
    }

    private fun JSONObject.toFallbackHotelRoom(hotel: HotelCatalogItem, folder: String): HotelCatalogRoom {
        val slug = optString("slug")
        val price = optJSONObject("price")
        val roomSize = optJSONObject("room_size")
        val bedConfig = optJSONObject("bed_configuration")
        val images = optImageUrlList("images", folder)
        val roomId = "${hotel.slug}:$slug"
        return HotelCatalogRoom(
            id = roomId,
            hotelId = hotel.id,
            hotelSlug = hotel.slug,
            roomCode = "${hotel.folderName}-$slug",
            name = optString("name"),
            slug = slug,
            price = price?.optDoubleCompat("current_amount") ?: 0.0,
            originalPrice = price?.opt("original_amount")?.let { value -> (value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull() },
            currency = price?.optString("currency", "VND") ?: "VND",
            rating = hotel.reviewScore,
            reviewCount = hotel.reviewCount,
            status = optString("status", "available"),
            capacity = optIntCompat("max_capacity").takeIf { it > 0 } ?: 2,
            images = images,
            heroImage = optString("hero_image_url").ifBlank { images.firstOrNull().orEmpty() },
            roomSizeSqm = roomSize?.opt("square_meters")?.let { value -> (value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull() },
            roomSizeSqft = roomSize?.opt("square_feet")?.let { value -> (value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull() },
            view = optString("view"),
            breakfastIncluded = optBooleanCompat("breakfast_included", false),
            cancellationPolicy = optString("cancellation_policy"),
            bedSummary = bedConfig?.optString("summary").orEmpty(),
            bedCount = optIntCompat("number_of_beds"),
            amenities = optStringList("amenities"),
            bathroomAmenities = optStringList("bathroom_amenities"),
            tags = optStringList("tags"),
            shortDescription = optString("short_description")
        )
    }

    private fun JSONObject.optImageUrlList(key: String, folder: String): List<String> {
        val value = opt(key)
        return when (value) {
            is JSONArray -> buildList {
                for (index in 0 until value.length()) {
                    val item = value.opt(index)
                    val url = when (item) {
                        is JSONObject -> item.optString("public_url")
                            .ifBlank { item.optString("url") }
                            .ifBlank { item.optString("local_path").takeIf { it.isNotBlank() }?.let { "file:///android_asset/$folder/$it" }.orEmpty() }
                        is String -> item
                        else -> ""
                    }
                    if (url.isNotBlank()) add(url)
                }
            }
            is String -> value.split(",").map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun JSONObject.toBooking(): Booking = Booking(
        id = optString("id"),
        userId = optString("user_id"),
        roomId = optString("room_id"),
        checkIn = optString("check_in"),
        checkOut = optString("check_out"),
        status = optString("status", "pending"),
        stayStatus = optString("stay_status"),
        total = optDoubleCompat("total"),
        addOns = optStringList("add_ons"),
        voucherId = optString("voucher_id"),
        voucherCode = optString("voucher_code"),
        discountAmount = optDoubleCompat("discount_amount"),
        originalTotal = optDoubleCompat("original_total"),
        addonsTotal = optDoubleCompat("addons_total"),
        finalTotal = optDoubleCompat("final_total"),
        actualCheckInAt = parseTimestampMillis(opt("actual_check_in_at")),
        actualCheckOutAt = parseTimestampMillis(opt("actual_check_out_at")),
        checkedInAt = parseTimestampMillis(opt("checked_in_at")),
        checkedOutAt = parseTimestampMillis(opt("checked_out_at")),
        createdAt = parseTimestampMillis(opt("created_at"))
    )

    private fun JSONObject.toBookingAddOn(addOnLookup: Map<String, AddOnItem> = emptyMap()): BookingAddOn {
        val itemId = optString("addon_item_id")
        val item = addOnLookup[itemId]
        return BookingAddOn(
            id = optString("id"),
            bookingId = optString("booking_id"),
            addOnItemId = itemId,
            name = item?.name.orEmpty().ifBlank { optString("name") },
            description = item?.description.orEmpty().ifBlank { optString("description") },
            quantity = optIntCompat("quantity"),
            unitPrice = optDoubleCompat("unit_price"),
            totalPrice = optDoubleCompat("total_price"),
            createdAt = parseTimestampMillis(opt("created_at"))
        )
    }

    private fun JSONObject.toReview(): Review = Review(
        id = optString("id"),
        roomId = optString("room_id"),
        hotelId = optString("hotel_id"),
        bookingId = optString("booking_id"),
        userId = optString("user_id"),
        rating = optIntCompat("rating"),
        comment = optString("comment"),
        createdAt = parseTimestampMillis(opt("created_at"))
    )

    private fun JSONObject.toIssue(): IssueReport = IssueReport(
        id = optString("id"),
        userId = optString("user_id"),
        roomId = optString("room_id"),
        bookingId = optString("booking_id"),
        title = optString("title"),
        description = optString("description"),
        status = normalizeIssueStatus(optString("status", "new")),
        createdAt = parseTimestampMillis(opt("created_at"))
    )

    private fun JSONObject.toVoucher(): Voucher = Voucher(
        id = optString("id"),
        code = optString("code"),
        title = optString("title").ifBlank { optString("name") },
        description = optString("description"),
        type = optString("discount_type").ifBlank { optString("type", "percent") },
        value = if (has("discount_value")) optDoubleCompat("discount_value") else optDoubleCompat("value"),
        minSpend = if (has("min_order_amount")) optDoubleCompat("min_order_amount") else optDoubleCompat("min_spend"),
        maxDiscountAmount = optDoubleCompat("max_discount_amount"),
        startAt = optString("start_date").ifBlank { optString("start_at") },
        endAt = optString("end_date").ifBlank { optString("end_at") },
        active = if (has("is_active")) optBooleanCompat("is_active", true) else optBooleanCompat("active", true),
        usageLimit = optIntCompat("usage_limit"),
        usedCount = optIntCompat("used_count")
    )

    private fun JSONObject.toPoster(): Poster = Poster(
        id = optString("id"),
        type = optString("type", "recommend"),
        title = optString("title"),
        content = optString("description").ifBlank { optString("content") },
        imageUrl = optString("image_url"),
        roomId = optString("room_id"),
        active = if (has("is_active")) optBooleanCompat("is_active", true) else optBooleanCompat("active", true),
        userId = optString("created_by").ifBlank { optString("user_id") },
        status = optString("status", "new"),
        response = optString("admin_reply").ifBlank { optString("response") },
        role = normalizeRole(optString("role", "client")),
        createdAt = parseTimestampMillis(opt("created_at"))
    )

    private fun JSONObject.toRoomRequest(): RoomRequest = RoomRequest(
        id = optString("id"),
        userId = optString("user_id"),
        userEmail = optString("user_email"),
        requestText = optString("request_text"),
        budget = optDoubleCompat("budget"),
        adminReply = optString("admin_reply"),
        status = normalizeRoomRequestStatus(optString("status", "new")),
        createdAt = parseTimestampMillis(opt("created_at")),
        updatedAt = parseTimestampMillis(opt("updated_at"))
    )

    private fun JSONObject.toAddOn(): AddOnItem = AddOnItem(
        id = optString("id"),
        name = optString("name"),
        price = optDoubleCompat("price"),
        description = optString("description"),
        imageUrl = optString("image_url"),
        category = optString("category", "snack"),
        active = optBooleanCompat("active", true)
    )

    private fun JSONObject.toNotification(): AppNotification = AppNotification(
        id = optString("id"),
        userId = optString("user_id"),
        userEmail = optString("user_email"),
        title = optString("title"),
        body = optString("message").ifBlank { optString("body") },
        type = optString("type"),
        targetRole = optString("target_role", "all"),
        read = optBooleanCompat("is_read", false),
        createdAt = parseTimestampMillis(opt("created_at")),
        readAt = parseTimestampMillis(opt("read_at")),
        relatedId = optString("related_id"),
        metadata = opt("metadata")?.toString().orEmpty()
    )

    private fun JSONObject.toPayment(): Payment = Payment(
        id = optString("id"),
        bookingId = optString("booking_id"),
        userId = optString("user_id"),
        amount = optDoubleCompat("amount"),
        method = optString("method"),
        status = optString("status", "paid"),
        cardLast4 = optString("card_last4"),
        voucherId = optString("voucher_id"),
        voucherCode = optString("voucher_code"),
        discountAmount = optDoubleCompat("discount_amount"),
        originalTotal = optDoubleCompat("original_total"),
        addonsTotal = optDoubleCompat("addons_total"),
        finalTotal = optDoubleCompat("final_total"),
        createdAt = parseTimestampMillis(opt("created_at"))
    )

    private fun JSONArray.toRoomList(hotelLookup: Map<String, HotelLookup> = emptyMap()): List<Room> =
        (0 until length()).mapNotNull { optJSONObject(it)?.toRoom(hotelLookup) }
    private fun JSONArray.toHotelCatalogList(): List<HotelCatalogItem> = (0 until length()).mapNotNull { optJSONObject(it)?.toHotelCatalogItem() }
    private fun JSONArray.toHotelCatalogRoomList(): List<HotelCatalogRoom> = (0 until length()).mapNotNull { optJSONObject(it)?.toHotelCatalogRoom() }
    private fun JSONArray.toSearchRoomList(hotelLookup: Map<String, HotelLookup> = emptyMap()): List<Room> =
        (0 until length()).mapNotNull { optJSONObject(it)?.toSearchRoom(hotelLookup) }
    private fun JSONArray.toBookingList(): List<Booking> = (0 until length()).mapNotNull { optJSONObject(it)?.toBooking() }
    private fun JSONArray.toBookingAddOnList(addOnLookup: Map<String, AddOnItem> = emptyMap()): List<BookingAddOn> =
        (0 until length()).mapNotNull { optJSONObject(it)?.toBookingAddOn(addOnLookup) }
    private fun JSONArray.toReviewList(): List<Review> = (0 until length()).mapNotNull { optJSONObject(it)?.toReview() }
    private fun JSONArray.toIssueList(): List<IssueReport> = (0 until length()).mapNotNull { optJSONObject(it)?.toIssue() }
    private fun JSONArray.toVoucherList(): List<Voucher> = (0 until length()).mapNotNull { optJSONObject(it)?.toVoucher() }
    private fun JSONArray.toPosterList(): List<Poster> = (0 until length()).mapNotNull { optJSONObject(it)?.toPoster() }
    private fun JSONArray.toRoomRequestList(): List<RoomRequest> = (0 until length()).mapNotNull { optJSONObject(it)?.toRoomRequest() }
    private fun JSONArray.toAddOnList(): List<AddOnItem> = (0 until length()).mapNotNull { optJSONObject(it)?.toAddOn() }
    private fun JSONArray.toNotificationList(): List<AppNotification> = (0 until length()).mapNotNull { optJSONObject(it)?.toNotification() }
    private fun JSONArray.toPaymentList(): List<Payment> = (0 until length()).mapNotNull { optJSONObject(it)?.toPayment() }

    private fun roomToJson(room: Room): JSONObject = JSONObject()
        .put("id", room.id)
        .put("code", room.code)
        .put("type", room.type)
        .put("price", room.price)
        .put("rating", room.rating)
        .put("review_count", room.reviewCount)
        .put("status", room.status)
        .put("capacity", room.capacity)
        .put("images", JSONArray(room.images))
        .put("created_at", millisToIso(room.createdAt))

    private fun bookingToJson(booking: Booking): JSONObject {
        val json = JSONObject()
            .put("id", booking.id)
            .put("user_id", booking.userId)
            .put("room_id", booking.roomId)
            .put("check_in", normalizeDate(booking.checkIn))
            .put("check_out", normalizeDate(booking.checkOut))
            .put("status", booking.status)
            .put("total", booking.total)
            .put("add_ons", JSONArray(booking.addOns))
            .put("voucher_id", booking.voucherId)
            .put("voucher_code", booking.voucherCode)
            .put("discount_amount", booking.discountAmount)
            .put("original_total", booking.originalTotal)
            .put("addons_total", booking.addonsTotal)
            .put("final_total", booking.finalTotal)
            .put("created_at", millisToIso(booking.createdAt))
        if (booking.actualCheckInAt > 0L) json.put("actual_check_in_at", millisToIso(booking.actualCheckInAt))
        if (booking.actualCheckOutAt > 0L) json.put("actual_check_out_at", millisToIso(booking.actualCheckOutAt))
        return json
    }

    private fun bookingAddOnToJson(addOn: BookingAddOn): JSONObject = JSONObject()
        .put("id", addOn.id)
        .put("booking_id", addOn.bookingId)
        .put("addon_item_id", addOn.addOnItemId)
        .put("quantity", addOn.quantity)
        .put("unit_price", addOn.unitPrice)
        .put("total_price", addOn.totalPrice)
        .put("created_at", millisToIso(addOn.createdAt))

    private fun reviewToJson(review: Review): JSONObject = JSONObject()
        .put("id", review.id)
        .put("room_id", review.roomId)
        .put("hotel_id", review.hotelId)
        .put("booking_id", review.bookingId)
        .put("user_id", review.userId)
        .put("rating", review.rating)
        .put("comment", review.comment)
        .put("created_at", millisToIso(review.createdAt))

    private fun legacyReviewToJson(review: Review): JSONObject = JSONObject()
        .put("id", review.id)
        .put("room_id", review.roomId)
        .put("user_id", review.userId)
        .put("rating", review.rating)
        .put("comment", review.comment)
        .put("created_at", millisToIso(review.createdAt))

    private fun issueToJson(issue: IssueReport): JSONObject = JSONObject()
        .put("id", issue.id)
        .put("user_id", issue.userId)
        .put("room_id", issue.roomId)
        .put("booking_id", issue.bookingId)
        .put("title", issue.title)
        .put("description", issue.description)
        .put("status", normalizeIssueStatus(issue.status))
        .put("created_at", millisToIso(issue.createdAt))

    private fun voucherToJson(voucher: Voucher): JSONObject = JSONObject()
        .put("id", voucher.id)
        .put("code", voucher.code)
        .put("title", voucher.title.ifBlank { voucher.code })
        .put("description", voucher.description)
        .put("discount_type", normalizeDiscountType(voucher.type))
        .put("discount_value", voucher.value)
        .put("min_order_amount", voucher.minSpend)
        .put("max_discount_amount", voucher.maxDiscountAmount)
        .put("start_date", normalizeDate(voucher.startAt))
        .put("end_date", normalizeDate(voucher.endAt))
        .put("is_active", voucher.active)
        .put("usage_limit", voucher.usageLimit)
        .put("used_count", voucher.usedCount)

    private fun legacyVoucherToJson(voucher: Voucher, includeUsageLimit: Boolean): JSONObject {
        val json = JSONObject()
            .put("id", voucher.id)
            .put("code", voucher.code)
            .put("type", voucher.type)
            .put("value", voucher.value)
            .put("min_spend", voucher.minSpend)
            .put("start_at", normalizeDate(voucher.startAt))
            .put("end_at", normalizeDate(voucher.endAt))
            .put("active", voucher.active)
        if (includeUsageLimit) json.put("usage_limit", voucher.usageLimit)
        return json
    }

    private fun posterToJson(poster: Poster, canonical: Boolean = true): JSONObject {
        val json = JSONObject()
            .put("id", poster.id)
            .put("type", poster.type)
            .put("title", poster.title)
            .put("image_url", poster.imageUrl)
            .put("room_id", poster.roomId)
            .put("status", normalizeRoomRequestStatus(poster.status))
            .put("created_at", millisToIso(poster.createdAt))
        if (canonical) {
            json.put("description", poster.content)
                .put("is_active", poster.active)
                .put("created_by", poster.userId)
                .put("admin_reply", poster.response)
                .put("updated_at", millisToIso(System.currentTimeMillis()))
        } else {
            json.put("content", poster.content)
                .put("active", poster.active)
                .put("user_id", poster.userId)
                .put("response", poster.response)
                .put("role", normalizeRole(poster.role))
        }
        return json
    }

    private fun roomRequestToJson(request: RoomRequest): JSONObject = JSONObject()
        .put("id", request.id)
        .put("user_id", request.userId)
        .put("user_email", request.userEmail)
        .put("request_text", request.requestText)
        .put("budget", request.budget)
        .put("admin_reply", request.adminReply)
        .put("status", normalizeRoomRequestStatus(request.status))
        .put("created_at", millisToIso(request.createdAt))
        .put("updated_at", millisToIso(request.updatedAt.takeIf { it > 0L } ?: request.createdAt))

    private fun addOnToJson(item: AddOnItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("name", item.name)
        .put("price", item.price)
        .put("description", item.description)
        .put("image_url", item.imageUrl)
        .put("category", item.category)
        .put("active", item.active)

    private fun notificationToJson(notification: AppNotification, canonical: Boolean = true): JSONObject {
        val json = JSONObject()
            .put("id", notification.id)
            .put("title", notification.title)
            .put("target_role", normalizeTargetRole(notification.targetRole))
            .put("is_read", notification.read)
            .put("created_at", millisToIso(notification.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis()))
        if (canonical) {
            json.put("user_id", notification.userId.ifBlank { JSONObject.NULL })
                .put("user_email", notification.userEmail.ifBlank { JSONObject.NULL })
                .put("message", notification.body)
                .put("type", notification.type.ifBlank { "general" })
                .put("read_at", if (notification.readAt > 0L) millisToIso(notification.readAt) else JSONObject.NULL)
                .put("related_id", notification.relatedId.ifBlank { JSONObject.NULL })
                .put("metadata", notification.metadata.ifBlank { "{}" })
        } else {
            json.put("body", notification.body)
        }
        return json
    }

    private fun notificationSettingsToJson(userId: String, settings: NotificationSettings): JSONObject = JSONObject()
        .put("id", userId)
        .put("user_id", userId)
        .put("check_in", settings.checkIn)
        .put("promo", settings.promo)
        .put("room_status", settings.roomStatus)
        .put("booking", settings.booking)
        .put("review", settings.review)
        .put("issue", settings.issue)
        .put("payment", settings.payment)
        .put("updated_at", millisToIso(System.currentTimeMillis()))

    private fun paymentToJson(payment: Payment, minimal: Boolean = false): JSONObject {
        val json = JSONObject()
            .put("id", payment.id)
            .put("booking_id", payment.bookingId)
            .put("user_id", payment.userId)
            .put("amount", payment.amount)
            .put("method", payment.method)
            .put("status", payment.status)
            .put("card_last4", payment.cardLast4)
            .put("created_at", millisToIso(payment.createdAt))
            
        if (!minimal) {
            json.put("voucher_id", payment.voucherId)
                .put("voucher_code", payment.voucherCode)
                .put("discount_amount", payment.discountAmount)
                .put("original_total", payment.originalTotal)
                .put("addons_total", payment.addonsTotal)
                .put("final_total", payment.finalTotal)
        }
        return json
    }

    private fun shortenRoomType(rawRoomName: String, fallbackType: String): String {
        val source = rawRoomName.ifBlank { fallbackType }
            .substringBefore(" - ")
            .trim()
        if (source.isBlank()) return ""
        val lower = source.lowercase()
        return when {
            "villa" in lower -> "Villa"
            "suite" in lower -> "Suite"
            "deluxe" in lower -> "Deluxe"
            "standard" in lower -> "Standard"
            "superior" in lower -> "Superior"
            "premium" in lower -> "Premium"
            "executive" in lower -> "Executive"
            "family" in lower -> "Family"
            "studio" in lower -> "Studio"
            "quadruple" in lower -> "Quadruple"
            "triple" in lower -> "Triple"
            "double" in lower || "twin" in lower -> "Twin/Double"
            "king" in lower -> "King"
            "queen" in lower -> "Queen"
            else -> source
        }
    }

    private fun toRoomTypeKey(displayType: String): String = displayType.trim().lowercase()

    private fun normalizeDiscountType(type: String): String = when (type.trim().lowercase()) {
        "percentage", "percent" -> "percentage"
        "fixed", "fixed_amount", "amount" -> "fixed_amount"
        else -> type.trim().lowercase().ifBlank { "percentage" }
    }

    private fun normalizeRoomRequestStatus(status: String): String = when (status.trim().lowercase()) {
        "processing", "in_progress", "dang_xu_ly" -> "processing"
        "resolved", "done", "closed", "da_xu_ly" -> "resolved"
        else -> "new"
    }

    private fun isMissingReviewContextColumn(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return "booking_id" in message || "hotel_id" in message
    }

    private fun isSchemaMismatch(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return "PGRST204" in message ||
            "PGRST303" in message ||
            "42703" in message ||
            "column" in message ||
            "schema cache" in message
    }

    private fun String.errorSuffix(): String {
        if (isBlank()) return ""
        val compact = replace(Regex("\\s+"), " ").trim()
        return if (compact.isBlank()) "" else ": $compact"
    }
}
