# Codex Context

## Project Structure
- Android app: `app/`.
- Kotlin source: `app/src/main/java/com/example/hotelapp_test2/`.
- UI Activities: `MainActivity.kt`, `ui/auth/*`, `ui/features/*`.
- Home UI: `MainActivity.kt`; client users do not see the feature search input or featured poster card, while admin users still can.
- Shared UI base/helpers: `ui/BaseActivity.kt`, `ui/FeatureAdapter.kt`, `ui/GridSpacingItemDecoration.kt`, `ui/toast.kt`.
- Routing/feature entry points: `core/FeatureRegistry.kt` and `AndroidManifest.xml`.
- State/session: `data/SessionManager.kt`, `data/SupabaseRepository.kt` auth cache.
- Models/types: `data/model/Models.kt`.
- Supabase REST API client/repository: `data/SupabaseRepository.kt`.
- Seed/bootstrap: `data/SeedData.kt`, `scripts/*`, `HotelList/*/data/*.json`.
- Database migrations: `supabase/migrations/`.
- Android resources/layouts/strings: `app/src/main/res/`.

## Current Booking Flow
- Users browse hotels/rooms through `HotelSearchActivity`, `HotelDetailActivity`, `HotelRoomDetailActivity`, `RoomSearchActivity`, and `RoomDetailActivity`.
- Client role is required before booking screens continue.
- `RoomDetailActivity` can create a booking for a selected room. `BookingActivity` is a manual booking form using room code.
- Booking creation calls `SupabaseRepository.createBooking`, which writes a row to `bookings`.
- Booking with add-ons uses `SupabaseRepository.createBookingWithAddOns`, which writes `bookings` plus `booking_addons` line items.
- Booking creation now falls back to the base `bookings` columns when the target Supabase database has not applied voucher/add-on migrations yet, so placing a booking does not fail on missing optional columns.
- Add-ons are selected before confirmation/payment in `RoomDetailActivity` and `BookingActivity`; users can increase/decrease quantity and totals are included in the booking total.
- Booking history is shown in `BookingHistoryActivity` with `BookingHistoryAdapter`.
- Booking history loads `booking_addons` details and shows quantity, unit price, line total, and add-ons total.
- Booking history display now shortens long hotel/room identifiers, guest ids, add-on ids, dates, and money values so cards remain readable on mobile.
- Admin can confirm/cancel bookings from booking history. Client can cancel when the check-in date is at least 2 days away.
- Client booking cards now show:
  - A short booking code (BK-XXXXXX) instead of the raw UUID.
  - A coloured stay-status badge (Đã check-in / Đã check-out / Quá hạn) when applicable.

## Current Payment Flow
- `PaymentActivity` lists payments through `SupabaseRepository.listPayments`.
- Client payment screen accepts booking id plus optional voucher code, validates voucher, previews room total/add-ons/subtotal/discount/final total, and then creates a manual payment record.
- Payment records use the `payments` table through `SupabaseRepository.createPayment`.
- Existing booking total is read from `bookings.total`; voucher financial fields are stored on `bookings` and `payments` when present.
- Voucher usage is recorded in `voucher_usage` after successful payment and `vouchers.used_count` is incremented.

## Admin Check-in / Check-out Flow (NEW – 2026-04-28)
- **Screen**: `ui/features/CheckInOutActivity.kt`, layout: `res/layout/activity_check_in_out.xml`.
- **Adapter**: `ui/features/AdminBookingAdapter.kt`, card layout: `res/layout/item_admin_booking_card.xml`.
- **Filter chips**: All / Chờ nhận phòng / Đã check-in / Đã check-out / Quá hạn / Đã hủy.
- **Flow**:
  1. Screen auto-loads all relevant bookings via `SupabaseRepository.listAdminBookings()`.
  2. Bookings are shown as selectable cards with: short booking code (BK-XXXXXX), room name, guest name, dates, stay-status badge.
  3. Tapping a card highlights it and reveals the bottom action panel.
  4. Action panel shows: booking code, room name, guest, dates, status; overdue warning if applicable.
  5. Check-in button enabled only when `stay_status = pending_checkin`.
  6. Check-out button enabled only when `stay_status = checked_in` or `overdue`.
  7. Both buttons disabled for cancelled or already-checked-out bookings.
  8. On Check-in: calls `checkInBooking()` → updates `status + stay_status = checked_in`, writes `actual_check_in_at` + `checked_in_at`.
  9. On Check-out: calls `checkOutBooking()` → updates `status + stay_status = checked_out`, writes timestamps, also updates room status back to `available`.
  10. List refreshes immediately after each action.
- **Stay status resolution**: `SupabaseRepository.resolveStayStatus(booking)` — handles overdue logic at UI layer.
- **Client sees**: Stay status badge on their booking history cards; no ability to change it.

## Format Helpers (SupabaseRepository)
- `shortBookingCode(id)` → `BK-XXXXXX` (first 6 chars of UUID, uppercased, no dashes).
- `displayRoomName(rawId, roomName)` → strips `catalog:hotel:` prefix, converts `snake_case` to Title Case.
- `resolveStayStatus(booking)` → returns one of: `pending_checkin | checked_in | checked_out | overdue | cancelled`.

## Stay Status Values
| Value | Meaning |
|---|---|
| `pending_checkin` | Booking confirmed/paid, waiting for guest arrival |
| `checked_in` | Guest has checked in |
| `checked_out` | Guest has checked out |
| `overdue` | Still checked_in but checkout date has passed (UI-computed, not stored) |
| `cancelled` | Booking was cancelled |

## Database Tables Seen In Code
- `users`: profile, role, phone, email.
- `rooms`: legacy room catalog.
- `hotels`: hotel catalog.
- `hotel_rooms`: imported hotel room catalog.
- `bookings`: user booking records. **New columns (2026-04-28)**:
  - `stay_status text default 'pending_checkin'` — tracks stay lifecycle.
  - `checked_in_at timestamptz` — when guest actually checked in.
  - `checked_out_at timestamptz` — when guest actually checked out.
  - `updated_at timestamptz` — last update time.
  - (existing) `actual_check_in_at`, `actual_check_out_at` also updated.
- `reviews`: room reviews.
- `issues`: issue reports.
- `add_on_items`: admin-managed add-on items.
- `add_ons`: actual table used by current admin add-on repository code.
- `booking_addons`: booking add-on line items with quantity, unit price, and total price.
- `vouchers`: voucher definitions.
- `voucher_usage`: voucher usage history per user/booking/payment.
- `posters`: recommendation/search posters.
- `notifications`: in-app notifications.
- `notification_settings`: notification preferences.
- `payments`: payment/refund history.

## Important Files By Concern
- Auth/user: `ui/auth/AuthActivity.kt`, `ui/auth/LoginFragment.kt`, `ui/auth/RegisterFragment.kt`, `data/SessionManager.kt`, `data/SupabaseRepository.kt`.
- Admin routing: `core/FeatureRegistry.kt`, `MainActivity.kt`.
- **Admin check-in/out**: `ui/features/CheckInOutActivity.kt`, `ui/features/AdminBookingAdapter.kt`, `res/layout/activity_check_in_out.xml`, `res/layout/item_admin_booking_card.xml`.
- Booking UI: `ui/features/RoomDetailActivity.kt`, `ui/features/BookingActivity.kt`, `ui/features/BookingHistoryActivity.kt`, `ui/features/BookingHistoryAdapter.kt`.
- Room UI: `ui/features/RoomSearchActivity.kt`, `RoomDetailActivity.kt`, `RoomCrudActivity.kt`, `HotelSearchActivity.kt`, `HotelDetailActivity.kt`, `HotelRoomDetailActivity.kt`.
- Review UI: `ui/features/ReviewActivity.kt`, `res/layout/activity_review.xml`.
- Add-on UI/admin: `ui/features/AddOnItemsActivity.kt`.
- Voucher UI/admin/client: `ui/features/VoucherActivity.kt`.
- Payment UI: `ui/features/PaymentActivity.kt`.
- Services/repository/API: `data/SupabaseRepository.kt`.
- Models/interfaces: `data/model/Models.kt`.
- Supabase schema/migrations: `supabase/migrations/*.sql`.
- Stay status migration: `supabase/migrations/20260428_add_stay_status.sql`.

## How To Run
- Open the project in Android Studio or run Gradle from repo root.
- Build debug APK: `.\gradlew.bat assembleDebug`.
- Full build: `.\gradlew.bat build`.
- Unit tests: `.\gradlew.bat test`.
- Supabase values are read from `local.properties` into BuildConfig (SUPABASE_URL, SUPABASE_ANON_KEY).
- **Do NOT commit** `local.properties`, `.env`, or any secret keys.
- Current local blocker: `local.properties` contains an Android `sdk.dir` path that does not exist on this machine. Set a valid Android SDK path or `ANDROID_HOME` before build/test.

## Fast Manual Tests
- Auth: register/login, verify role resolution on home screen.
- Booking: open a room detail, choose dates/guests, create booking, verify it appears in booking history with BK-XXXXXX code.
- Check-in: log in as admin → Check-in & Check-out → see booking list → tap a booking → tap Check-in → verify status updates.
- Check-out: after check-in, tap Check-out → verify status updates, room goes back to available.
- Overdue: set a booking's checkout date in the past while stay_status=checked_in → verify the card shows '⚠ QUÁ HẠN TRẢ PHÒNG'.
- Client view: after admin checks in → client sees 'Đã check-in' badge on their booking card.
- Filter chips: switch between All / Chờ nhận / Đã check-in / etc. and verify list updates.

## Completion Summary
- Initial docs commit: `d0328a4 docs sync project context`.
- Reviews commit: `7120ff4 feat: restrict reviews to booked rooms`.
- Add-ons commit: `87531e7 feat: add booking add ons`.
- Vouchers commit: `ea85f4f feat: apply booking vouchers`.
- User widget cleanup commit: `8aafa54 fix: remove unused user widgets`.
- Payment flow redesign: decommissioned PayOS, implemented manual payment with booking selection.
- **Admin check-in/out redesign (2026-04-28)**:
  - `feat: add booking stay status flow` — Models.kt, SupabaseRepository.kt, migration.
  - `feat: improve admin checkin checkout management` — CheckInOutActivity, AdminBookingAdapter, layouts, drawables.
  - `feat: show stay status to clients` — BookingHistoryAdapter, item_booking_card.xml.
  - `docs: update checkin checkout flow context` — codex_context.md, feature_progress.md.
- Migrations created:
  - `supabase/migrations/202604270001_room_reviews_booking_context.sql`
  - `supabase/migrations/202604270002_booking_addons.sql`
  - `supabase/migrations/202604270003_booking_vouchers.sql`
  - `supabase/migrations/202604270004_create_room_reviews.sql`
  - `supabase/migrations/202604270005_booking_schema_hotfix.sql`
  - `supabase/migrations/202604270006_issue_booking_context.sql`
  - `supabase/migrations/20260427_add_payment_summary_fields.sql`
  - `supabase/migrations/20260428_add_stay_status.sql` ← **NEW**

## Voucher / Poster / Notification Alignment (2026-04-28)
- Voucher flow: booking screens no longer accept voucher codes. Vouchers are loaded and selected only in `PaymentActivity`, where the UI shows voucher details, conditions, discount, and final total.
- Admin voucher creation: `SupabaseRepository.createVoucher` writes canonical voucher columns first and falls back only for legacy schema mismatch. User voucher list reads active/valid vouchers.
- Recommendation posters: user screen is read-only and lists active admin recommendation posters. Admin chooses a room from the existing room list; room id and image are derived from selected room data.
- Room search requests: user requests are stored in `room_requests` with `user_id`, `user_email`, `request_text`, `budget`, `admin_reply`, `status`, `created_at`, `updated_at`. Admin edits reply/status in a dialog with a single save action.
- Notifications: notifications can be user-scoped, track `is_read` and `read_at`, and include type/related metadata. Android creates in-app notifications only; email sending is an intentional backend/Edge Function integration point.
- Supabase tables involved: `vouchers`, `voucher_usage`, `posters`, `room_requests`, `notifications`, `notification_settings`, `bookings`, `rooms`, `hotel_rooms`, `payments`.
- Migration added: `supabase/migrations/202604280002_voucher_poster_notification_alignment.sql`.
- Files changed: `Models.kt`, `SupabaseRepository.kt`, `BookingActivity.kt`, `RoomDetailActivity.kt`, `PaymentActivity.kt`, `RecommendationPosterActivity.kt`, `SearchPosterActivity.kt`, `NotificationsActivity.kt`, `BookingHistoryActivity.kt`, related layouts and string resources.
