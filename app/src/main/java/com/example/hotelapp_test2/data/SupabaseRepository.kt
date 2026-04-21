package com.example.hotelapp_test2.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.example.hotelapp_test2.BuildConfig
import com.example.hotelapp_test2.data.model.AddOnItem
import com.example.hotelapp_test2.data.model.AppNotification
import com.example.hotelapp_test2.data.model.Booking
import com.example.hotelapp_test2.data.model.HotelCatalogItem
import com.example.hotelapp_test2.data.model.HotelCatalogRoom
import com.example.hotelapp_test2.data.model.IssueReport
import com.example.hotelapp_test2.data.model.NotificationSettings
import com.example.hotelapp_test2.data.model.Payment
import com.example.hotelapp_test2.data.model.Poster
import com.example.hotelapp_test2.data.model.Review
import com.example.hotelapp_test2.data.model.Room
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
        val displayName: String = ""
    )

    private data class HttpResult(val code: Int, val body: String)
    private data class HotelLookup(
        val displayName: String = "",
        val area: String = "",
        val city: String = ""
    )

    private val mainHandler = Handler(Looper.getMainLooper())
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
            parseAndPersistAuth(response.body)
        }
    }

    fun signUpWithEmail(
        email: String,
        password: String,
        name: String,
        onSuccess: (SupabaseUser) -> Unit,
        onError: (Exception) -> Unit
    ) {
        runAsync(onSuccess, onError) {
            val payload = JSONObject()
                .put("email", email)
                .put("password", password)
                .put("data", JSONObject().put("full_name", name))
            val response = request(
                method = "POST",
                path = "/auth/v1/signup",
                jsonBody = payload.toString(),
                bearer = BuildConfig.SUPABASE_ANON_KEY
            )
            if (response.code !in 200..299) {
                throw IllegalStateException(authError(response.body, "Dang ky that bai"))
            }
            parseAndPersistAuth(response.body)
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
            parseAndPersistAuth(response.body)
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
        }
    }

    fun deleteRoomByCode(code: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { delete("rooms", mapOf("id" to "eq.$code")) }

    fun searchRooms(queryText: String, onSuccess: (List<Room>) -> Unit, onError: (Exception) -> Unit) {
        runAsync(onSuccess, onError) {
            val hotelLookup = loadHotelLookup()
            val rooms = select("rooms", mapOf("select" to "*", "limit" to "200")).toRoomList(hotelLookup)
            if (queryText.isBlank()) rooms else rooms.filter {
                it.area.contains(queryText, true) || it.city.contains(queryText, true)
            }
        }
    }

    fun filterRooms(type: String?, sortAscending: Boolean?, onSuccess: (List<Room>) -> Unit, onError: (Exception) -> Unit) {
        runAsync(onSuccess, onError) {
            val hotelLookup = loadHotelLookup()
            val q = linkedMapOf("select" to "*", "limit" to "50")
            if (!type.isNullOrBlank()) q["type"] = "eq.$type"
            if (sortAscending != null) q["order"] = if (sortAscending) "price.asc" else "price.desc"
            select("rooms", q).toRoomList(hotelLookup)
        }
    }

    fun searchHotels(queryText: String, onSuccess: (List<HotelCatalogItem>) -> Unit, onError: (Exception) -> Unit) {
        runAsync(onSuccess, onError) {
            val hotels = select("hotels", mapOf("select" to "*", "order" to "display_name.asc", "limit" to "100")).toHotelCatalogList()
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
        }
    }

    fun listHotelRooms(hotelId: String, onSuccess: (List<HotelCatalogRoom>) -> Unit, onError: (Exception) -> Unit) {
        if (hotelId.isBlank()) {
            onSuccess(emptyList())
            return
        }
        runAsync(onSuccess, onError) {
            select(
                "hotel_rooms",
                mapOf("select" to "*", "hotel_id" to "eq.$hotelId", "order" to "price.asc", "limit" to "200")
            ).toHotelCatalogRoomList()
        }
    }

    fun getHotelRoomById(roomId: String, onSuccess: (HotelCatalogRoom?) -> Unit, onError: (Exception) -> Unit) {
        if (roomId.isBlank()) {
            onSuccess(null)
            return
        }
        runAsync(onSuccess, onError) {
            select("hotel_rooms", mapOf("select" to "*", "id" to "eq.$roomId", "limit" to "1")).firstObjectOrNull()?.toHotelCatalogRoom()
        }
    }

    fun createBooking(booking: Booking, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { upsert("bookings", bookingToJson(booking.copy(id = booking.id.ifBlank { UUID.randomUUID().toString() }))) }

    fun listBookings(userId: String?, onSuccess: (List<Booking>) -> Unit, onError: (Exception) -> Unit) {
        runAsync(onSuccess, onError) {
            val q = linkedMapOf("select" to "*", "limit" to "50", "order" to "created_at.desc")
            if (!userId.isNullOrBlank()) q["user_id"] = "eq.$userId"
            select("bookings", q).toBookingList()
        }
    }

    fun updateBookingStatus(bookingId: String, status: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { patch("bookings", mapOf("id" to "eq.$bookingId"), JSONObject().put("status", status)) }

    fun createReview(review: Review, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { upsert("reviews", reviewToJson(review.copy(id = review.id.ifBlank { UUID.randomUUID().toString() }))) }

    fun listRecentReviews(limit: Long, onSuccess: (List<Review>) -> Unit, onError: (Exception) -> Unit) =
        runAsync(onSuccess, onError) { select("reviews", mapOf("select" to "*", "order" to "created_at.desc", "limit" to limit.toString())).toReviewList() }

    fun createIssue(issue: IssueReport, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { upsert("issues", issueToJson(issue.copy(id = issue.id.ifBlank { UUID.randomUUID().toString() }))) }

    fun createVoucher(voucher: Voucher, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { upsert("vouchers", voucherToJson(voucher.copy(id = voucher.id.ifBlank { voucher.code.ifBlank { UUID.randomUUID().toString() } }))) }

    fun listVouchers(onSuccess: (List<Voucher>) -> Unit, onError: (Exception) -> Unit) =
        runAsync(onSuccess, onError) { select("vouchers", mapOf("select" to "*", "order" to "code.asc")).toVoucherList() }

    fun getVoucherByCode(code: String, onSuccess: (Voucher?) -> Unit, onError: (Exception) -> Unit) =
        runAsync(onSuccess, onError) { select("vouchers", mapOf("select" to "*", "code" to "eq.$code", "limit" to "1")).firstObjectOrNull()?.toVoucher() }

    fun createPoster(poster: Poster, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { upsert("posters", posterToJson(poster.copy(id = poster.id.ifBlank { UUID.randomUUID().toString() }))) }

    fun listPosters(type: String, limit: Long, onSuccess: (List<Poster>) -> Unit, onError: (Exception) -> Unit) =
        runAsync(onSuccess, onError) { select("posters", mapOf("select" to "*", "type" to "eq.$type", "order" to "created_at.desc", "limit" to limit.toString())).toPosterList() }

    fun createAddOn(item: AddOnItem, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { upsert("add_ons", addOnToJson(item.copy(id = item.id.ifBlank { UUID.randomUUID().toString() }))) }

    fun listAddOns(onSuccess: (List<AddOnItem>) -> Unit, onError: (Exception) -> Unit) =
        runAsync(onSuccess, onError) { select("add_ons", mapOf("select" to "*", "order" to "name.asc")).toAddOnList() }

    fun createNotification(notification: AppNotification, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { upsert("notifications", notificationToJson(notification.copy(id = notification.id.ifBlank { UUID.randomUUID().toString() }))) }

    fun createPayment(payment: Payment, onSuccess: () -> Unit, onError: (Exception) -> Unit) =
        runAsyncUnit(onSuccess, onError) { upsert("payments", paymentToJson(payment.copy(id = payment.id.ifBlank { UUID.randomUUID().toString() }))) }

    fun fetchNotificationSettings(userId: String, onSuccess: (NotificationSettings) -> Unit, onError: (Exception) -> Unit) {
        runAsync(onSuccess, onError) {
            val row = select("users", mapOf("select" to "raw", "id" to "eq.$userId", "limit" to "1")).firstObjectOrNull()
            val raw = row?.optJSONObject("raw")
            NotificationSettings(
                checkIn = raw?.optBooleanCompat("notifCheckIn", true) ?: true,
                promo = raw?.optBooleanCompat("notifPromo", true) ?: true,
                roomStatus = raw?.optBooleanCompat("notifRoomStatus", true) ?: true
            )
        }
    }

    fun updateNotificationSettings(userId: String, settings: NotificationSettings, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        runAsyncUnit(onSuccess, onError) {
            val currentRaw = select("users", mapOf("select" to "raw", "id" to "eq.$userId", "limit" to "1"))
                .firstObjectOrNull()?.optJSONObject("raw") ?: JSONObject()
            currentRaw.put("notifCheckIn", settings.checkIn)
            currentRaw.put("notifPromo", settings.promo)
            currentRaw.put("notifRoomStatus", settings.roomStatus)
            patch("users", mapOf("id" to "eq.$userId"), JSONObject().put("raw", currentRaw))
        }
    }

    fun listenNotifications(role: String, onSuccess: (List<AppNotification>) -> Unit, onError: (Exception) -> Unit) {
        val normalized = if (normalizeRole(role) == "admin") "admin" else "client"
        runAsync(onSuccess, onError) {
            select(
                "notifications",
                mapOf("select" to "*", "target_role" to "in.(all,$normalized)", "order" to "created_at.desc", "limit" to "50")
            ).toNotificationList()
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
        if (response.code !in 200..299) throw IllegalStateException("Upsert $table failed: HTTP ${response.code}")
    }

    private fun patch(table: String, filters: Map<String, String>, payload: JSONObject) {
        val response = request("PATCH", "/rest/v1/$table", query = filters, jsonBody = payload.toString())
        if (response.code !in 200..299) throw IllegalStateException("Patch $table failed: HTTP ${response.code}")
    }

    private fun delete(table: String, filters: Map<String, String>) {
        val response = request("DELETE", "/rest/v1/$table", query = filters)
        if (response.code !in 200..299) throw IllegalStateException("Delete $table failed: HTTP ${response.code}")
    }

    private fun select(table: String, query: Map<String, String>): JSONArray {
        val response = request("GET", "/rest/v1/$table", query = query)
        if (response.code !in 200..299) throw IllegalStateException("Select $table failed: HTTP ${response.code}")
        return if (response.body.isBlank()) JSONArray() else JSONArray(response.body)
    }

    private fun parseAndPersistAuth(body: String): SupabaseUser {
        val root = JSONObject(body)
        val userObj = root.optJSONObject("user") ?: throw IllegalStateException("Khong nhan duoc user")
        val user = SupabaseUser(
            uid = userObj.optString("id"),
            email = userObj.optString("email"),
            displayName = userObj.optJSONObject("user_metadata")?.optString("full_name").orEmpty()
        )
        if (user.uid.isBlank()) throw IllegalStateException("User id tu Supabase bi trong")
        persistAuthState(user, root.optString("access_token"), root.optString("refresh_token"))
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
            o.optString("msg").ifBlank { o.optString("error_description").ifBlank { fallback } }
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

    private fun parseTimestampMillis(value: Any?): Long {
        return when (value) {
            is Number -> value.toLong()
            is String -> try { OffsetDateTime.parse(value).toInstant().toEpochMilli() } catch (_: Exception) { try { Instant.parse(value).toEpochMilli() } catch (_: Exception) { 0L } }
            else -> 0L
        }
    }

    private fun loadHotelLookup(): Map<String, HotelLookup> {
        val hotels = select("hotels", mapOf("select" to "id,display_name,area,city", "limit" to "500"))
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

    private fun JSONObject.toBooking(): Booking = Booking(
        id = optString("id"),
        userId = optString("user_id"),
        roomId = optString("room_id"),
        checkIn = optString("check_in"),
        checkOut = optString("check_out"),
        status = optString("status", "pending"),
        total = optDoubleCompat("total"),
        addOns = optStringList("add_ons"),
        createdAt = parseTimestampMillis(opt("created_at"))
    )

    private fun JSONObject.toReview(): Review = Review(
        id = optString("id"),
        roomId = optString("room_id"),
        userId = optString("user_id"),
        rating = optIntCompat("rating"),
        comment = optString("comment"),
        createdAt = parseTimestampMillis(opt("created_at"))
    )

    private fun JSONObject.toVoucher(): Voucher = Voucher(
        id = optString("id"),
        code = optString("code"),
        type = optString("type", "percent"),
        value = optDoubleCompat("value"),
        minSpend = optDoubleCompat("min_spend"),
        startAt = optString("start_at"),
        endAt = optString("end_at"),
        active = optBooleanCompat("active", true),
        usageLimit = optIntCompat("usage_limit")
    )

    private fun JSONObject.toPoster(): Poster = Poster(
        id = optString("id"),
        type = optString("type", "recommend"),
        title = optString("title"),
        content = optString("content"),
        imageUrl = optString("image_url"),
        role = normalizeRole(optString("role", "client")),
        createdAt = parseTimestampMillis(opt("created_at"))
    )

    private fun JSONObject.toAddOn(): AddOnItem = AddOnItem(
        id = optString("id"),
        name = optString("name"),
        price = optDoubleCompat("price"),
        category = optString("category", "snack"),
        active = optBooleanCompat("active", true)
    )

    private fun JSONObject.toNotification(): AppNotification = AppNotification(
        id = optString("id"),
        title = optString("title"),
        body = optString("body"),
        targetRole = optString("target_role", "all"),
        createdAt = parseTimestampMillis(opt("created_at"))
    )

    private fun JSONArray.toRoomList(hotelLookup: Map<String, HotelLookup> = emptyMap()): List<Room> =
        (0 until length()).mapNotNull { optJSONObject(it)?.toRoom(hotelLookup) }
    private fun JSONArray.toHotelCatalogList(): List<HotelCatalogItem> = (0 until length()).mapNotNull { optJSONObject(it)?.toHotelCatalogItem() }
    private fun JSONArray.toHotelCatalogRoomList(): List<HotelCatalogRoom> = (0 until length()).mapNotNull { optJSONObject(it)?.toHotelCatalogRoom() }
    private fun JSONArray.toBookingList(): List<Booking> = (0 until length()).mapNotNull { optJSONObject(it)?.toBooking() }
    private fun JSONArray.toReviewList(): List<Review> = (0 until length()).mapNotNull { optJSONObject(it)?.toReview() }
    private fun JSONArray.toVoucherList(): List<Voucher> = (0 until length()).mapNotNull { optJSONObject(it)?.toVoucher() }
    private fun JSONArray.toPosterList(): List<Poster> = (0 until length()).mapNotNull { optJSONObject(it)?.toPoster() }
    private fun JSONArray.toAddOnList(): List<AddOnItem> = (0 until length()).mapNotNull { optJSONObject(it)?.toAddOn() }
    private fun JSONArray.toNotificationList(): List<AppNotification> = (0 until length()).mapNotNull { optJSONObject(it)?.toNotification() }

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

    private fun bookingToJson(booking: Booking): JSONObject = JSONObject()
        .put("id", booking.id)
        .put("user_id", booking.userId)
        .put("room_id", booking.roomId)
        .put("check_in", normalizeDate(booking.checkIn))
        .put("check_out", normalizeDate(booking.checkOut))
        .put("status", booking.status)
        .put("total", booking.total)
        .put("add_ons", JSONArray(booking.addOns))
        .put("created_at", millisToIso(booking.createdAt))

    private fun reviewToJson(review: Review): JSONObject = JSONObject()
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
        .put("title", issue.title)
        .put("description", issue.description)
        .put("status", issue.status)
        .put("created_at", millisToIso(issue.createdAt))

    private fun voucherToJson(voucher: Voucher): JSONObject = JSONObject()
        .put("id", voucher.id)
        .put("code", voucher.code)
        .put("type", voucher.type)
        .put("value", voucher.value)
        .put("min_spend", voucher.minSpend)
        .put("start_at", normalizeDate(voucher.startAt))
        .put("end_at", normalizeDate(voucher.endAt))
        .put("active", voucher.active)
        .put("usage_limit", voucher.usageLimit)

    private fun posterToJson(poster: Poster): JSONObject = JSONObject()
        .put("id", poster.id)
        .put("type", poster.type)
        .put("title", poster.title)
        .put("content", poster.content)
        .put("image_url", poster.imageUrl)
        .put("role", normalizeRole(poster.role))
        .put("created_at", millisToIso(poster.createdAt))

    private fun addOnToJson(item: AddOnItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("name", item.name)
        .put("price", item.price)
        .put("category", item.category)
        .put("active", item.active)

    private fun notificationToJson(notification: AppNotification): JSONObject = JSONObject()
        .put("id", notification.id)
        .put("title", notification.title)
        .put("body", notification.body)
        .put("target_role", normalizeTargetRole(notification.targetRole))
        .put("created_at", millisToIso(notification.createdAt))

    private fun paymentToJson(payment: Payment): JSONObject = JSONObject()
        .put("id", payment.id)
        .put("booking_id", payment.bookingId)
        .put("user_id", payment.userId)
        .put("amount", payment.amount)
        .put("method", payment.method)
        .put("status", payment.status)
        .put("card_last4", payment.cardLast4)
        .put("created_at", millisToIso(payment.createdAt))

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
}
