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
- Payment gateway helper: `data/PayOSGateway.kt`.
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

## Current Payment Flow
- `PaymentActivity` lists payments through `SupabaseRepository.listPayments`.
- Client payment screen accepts booking id plus optional voucher code, validates voucher, previews room total/add-ons/subtotal/discount/final total, and then creates demo or PayOS payment.
- PayOS return is handled in `PayOSReturnActivity`.
- Payment records use the `payments` table through `SupabaseRepository.createPayment`.
- Existing booking total is read from `bookings.total`; voucher financial fields are stored on `bookings` and `payments` when present.
- Voucher usage is recorded in `voucher_usage` after successful demo/PayOS return and `vouchers.used_count` is incremented.

## Current Review Flow
- Review UI is `ReviewActivity` with layout `activity_review.xml`.
- Reviewable bookings for the logged-in user load through `SupabaseRepository.listReviewableBookings`.
- Eligible booking statuses are `completed`, `checked_out`, `paid`, and `confirmed`.
- Review creation uses `SupabaseRepository.createReviewAndRefreshRoom`, validates booking ownership/status/room, blocks duplicate user+booking reviews, inserts into `reviews`, and refreshes room rating count.
- Review creation now falls back to legacy `reviews` schemas that do not yet have `booking_id` or `hotel_id`, so users can still submit and other users can read room reviews by `room_id`.
- Booking cards display hotel, room, image, check-in/check-out, booking id, booking status, and reviewed/unreviewed state.
- Review booking cards shorten hotel/room names, dates, and booking ids to avoid long wrapped identifiers.
- Room detail review loading resolves equivalent room identifiers such as `hotel:room`, `catalog:hotel:room`, and `room_code`, so reviews written through one flow appear when another user opens the same room through another flow.
- `listReviewableBookings` maps rooms from both `rooms` and `hotel_rooms` so imported hotel catalog bookings can be reviewed.

## Database Tables Seen In Code
- `users`: profile, role, phone, email.
- `rooms`: legacy room catalog.
- `hotels`: hotel catalog.
- `hotel_rooms`: imported hotel room catalog.
- `bookings`: user booking records.
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
- Booking UI: `ui/features/RoomDetailActivity.kt`, `ui/features/BookingActivity.kt`, `ui/features/BookingHistoryActivity.kt`, `ui/features/BookingHistoryAdapter.kt`.
- Room UI: `ui/features/RoomSearchActivity.kt`, `RoomDetailActivity.kt`, `RoomCrudActivity.kt`, `HotelSearchActivity.kt`, `HotelDetailActivity.kt`, `HotelRoomDetailActivity.kt`.
- Review UI: `ui/features/ReviewActivity.kt`, `res/layout/activity_review.xml`.
- Add-on UI/admin: `ui/features/AddOnItemsActivity.kt`; booking selection currently appears in `RoomDetailActivity.kt` and `BookingActivity.kt`.
- Voucher UI/admin/client: `ui/features/VoucherActivity.kt`; booking voucher entry currently appears in `RoomDetailActivity.kt` and `BookingActivity.kt`.
- Payment UI: `ui/features/PaymentActivity.kt`, `ui/features/PayOSReturnActivity.kt`.
- Services/repository/API: `data/SupabaseRepository.kt`, `data/PayOSGateway.kt`.
- Models/interfaces: `data/model/Models.kt`.
- Supabase schema/migrations: `supabase/migrations/*.sql`.
- Add-on migration: `supabase/migrations/202604270002_booking_addons.sql`.
- Voucher migration: `supabase/migrations/202604270003_booking_vouchers.sql`.

## How To Run
- Open the project in Android Studio or run Gradle from repo root.
- Build debug APK: `.\gradlew.bat assembleDebug`.
- Full build: `.\gradlew.bat build`.
- Unit tests: `.\gradlew.bat test`.
- Supabase and PayOS values are read from `local.properties` into BuildConfig.
- Current local blocker: `local.properties` contains an Android `sdk.dir` path that does not exist on this machine. Set a valid Android SDK path or `ANDROID_HOME` before build/test.

## Fast Manual Tests
- Auth: register/login, verify role resolution on home screen.
- Booking: open a room detail, choose dates/guests, create booking, verify it appears in booking history.
- Payment: open payment screen, create a demo or PayOS payment for a booking, verify payment history.
- Review: create/confirm/complete a booking, open reviews, verify only owned eligible bookings appear, submit one review, verify duplicate is blocked.
- Add-ons: create add-on items as admin, book as client, verify selected add-ons affect total and appear in booking history/detail after implementation.
- Voucher: create voucher as admin/client screen, apply during booking/payment after implementation, verify discount math and persisted booking/payment fields.
- User home cleanup: login as client and verify `Search rooms, vouchers, guests...` and `Featured poster/Create poster` are absent; login as admin and verify admin dashboard still works.

## Completion Summary
- Initial docs commit: `d0328a4 docs sync project context`.
- Reviews commit: `7120ff4 feat: restrict reviews to booked rooms`.
- Add-ons commit: `87531e7 feat: add booking add ons`.
- Vouchers commit: `ea85f4f feat: apply booking vouchers`.
- User widget cleanup commit: `8aafa54 fix: remove unused user widgets`.
- Migrations created:
  - `supabase/migrations/202604270001_room_reviews_booking_context.sql`
  - `supabase/migrations/202604270002_booking_addons.sql`
  - `supabase/migrations/202604270003_booking_vouchers.sql`
  - `supabase/migrations/202604270004_create_room_reviews.sql`
- `supabase/migrations/202604270005_booking_schema_hotfix.sql`
- `supabase/migrations/202604270006_issue_booking_context.sql`
- Final verification commands:
  - `.\gradlew.bat clean`: success.
  - `.\gradlew.bat build`: failed before compile because Android SDK path is missing/invalid.
  - `.\gradlew.bat test`: failed before test compile because Android SDK path is missing/invalid.
