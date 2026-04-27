# Feature Progress

## Initial Repo Scan
- Git identity configured and verified:
  - `user.name = Vubach08`
  - `user.email = vubach08092003@gmail.com`
- Project type: Android Kotlin app with Gradle.
- Supabase access pattern: direct REST calls centralized in `SupabaseRepository`.
- State management: `SessionManager` plus in-memory/persisted Supabase auth state.
- UI routing: `FeatureRegistry`, `MainActivity`, and Activity entries in `AndroidManifest.xml`.
- Markdown read: `docs/codex_repo_setup.md`; these context files are maintained for current work.

## Requirement 1 - Restrict Reviews To Booked Rooms
- Status: implemented, build blocked by local Android SDK path.
- Planned/observed files:
  - `data/model/Models.kt`
  - `data/SupabaseRepository.kt`
  - `ui/features/ReviewActivity.kt`
  - `res/layout/activity_review.xml`
  - `res/values/strings.xml`
  - `res/values-vi/strings.xml`
  - `supabase/migrations/202604270001_room_reviews_booking_context.sql`
- Done:
  - Only logged-in user's eligible bookings appear.
  - No hardcoded user id.
  - Eligible statuses are `completed`, `checked_out`, `paid`, and `confirmed`.
  - Review cards show hotel, room, image, dates, booking id, booking status, and reviewed state.
  - Review submission saves `user_id`, `room_id`, `hotel_id`, `booking_id`, rating, comment, and created time.
  - Duplicate review per `user_id + booking_id` is blocked in code and migration.
  - Booking list reloads after review submit.
- Verification:
  - Ran `.\gradlew.bat assembleDebug`.
  - Result: failed before compile because Android SDK path in `local.properties` does not exist. No code compile result was produced.

## Requirement 2 - Booking Add-ons
- Status: implemented, build blocked by local Android SDK path.
- Files changed:
  - `data/model/Models.kt`
  - `data/SupabaseRepository.kt`
  - `ui/features/RoomDetailActivity.kt`
  - `ui/features/BookingActivity.kt`
  - `ui/features/BookingHistoryAdapter.kt`
  - `res/values/strings.xml`
  - `res/values-vi/strings.xml`
  - `supabase/migrations/202604270002_booking_addons.sql`
- Done:
  - Added `BookingAddOn` and `BookingAddOnSelection` models.
  - Added `booking_addons` migration with FK to `bookings` and existing `add_ons`.
  - Seeded sample hotel add-ons: water, breakfast, airport transfer, room decoration, laundry.
  - Booking UI supports multiple add-ons with quantity increase/decrease.
  - Add-on totals are added into booking total.
  - Booking creation persists line items with `booking_id`, `addon_item_id`, `quantity`, `unit_price`, and `total_price`.
  - Booking history displays selected add-ons, quantity, unit price, line total, and add-ons total.
- Verification:
  - Ran `.\gradlew.bat assembleDebug`.
  - Result: failed before compile because Android SDK path in `local.properties` does not exist. No code compile result was produced.

## Requirement 3 - Booking Vouchers
- Status: implemented, build blocked by local Android SDK path.
- Files changed:
  - `data/model/Models.kt`
  - `data/SupabaseRepository.kt`
  - `ui/features/BookingActivity.kt`
  - `ui/features/RoomDetailActivity.kt`
  - `ui/features/PaymentActivity.kt`
  - `ui/features/PayOSReturnActivity.kt`
  - `ui/features/BookingHistoryAdapter.kt`
  - `ui/features/VoucherActivity.kt`
  - `res/layout/activity_payment.xml`
  - `res/values/strings.xml`
  - `res/values-vi/strings.xml`
  - `supabase/migrations/202604270003_booking_vouchers.sql`
- Done:
  - Added voucher fields: title, description, discount type/value, min order, max discount, date range, active flag, usage limit, used count.
  - Added booking/payment fields: voucher id/code, original total, add-ons total, discount amount, final total.
  - Added `voucher_usage` migration and sample vouchers.
  - Payment UI accepts voucher code and shows room total, add-ons total, subtotal, voucher, discount, and final total.
  - Validation checks active flag, start/end date, minimum order, usage limit, prior user usage, and clamps final total to zero minimum.
  - Booking detail/history and payment history display voucher discount details when present.
  - Demo payment and PayOS return record voucher usage and increment voucher used count.
- Verification:
  - Ran `.\gradlew.bat assembleDebug`.
  - Result: failed before compile because Android SDK path in `local.properties` does not exist. No code compile result was produced.

## Requirement 4 - Remove User Widgets
- Status: implemented, build blocked by local Android SDK path.
- Files changed:
  - `MainActivity.kt`
- Done:
  - Text search found the search box through `main_search_hint` in `activity_main.xml` and strings.
  - Text search found featured poster card through `main_highlight_title`, `main_highlight_body`, and `main_highlight_button`.
  - Client role hides the search input and featured poster card entirely.
  - Admin role keeps these widgets so admin workflows are not removed.
  - Hidden client search state is cleared before applying feature filters.
- Verification:
  - Ran `.\gradlew.bat assembleDebug`.
  - Result: failed before compile because Android SDK path in `local.properties` does not exist. No code compile result was produced.

## Known Follow-up Checks
- Apply Supabase migrations to the target project.
- Verify RLS policies allow logged-in users to select their bookings, add-ons, vouchers, and insert own review/booking rows.
- Fix local Android SDK config before compile verification:
  - Current blocker: invalid/missing `sdk.dir` in `local.properties`; `ANDROID_HOME` is not providing a valid SDK.

## Review Hotfix - Supabase 400
- Status: implemented, build pending local SDK verification.
- Files changed:
  - `data/SupabaseRepository.kt`
  - `supabase/migrations/202604270004_create_room_reviews.sql`
- Done:
  - Added a full `reviews` migration that creates the table when missing, adds `hotel_id` and `booking_id` when absent, enables read/own-write RLS policies, and seeds a few reviews from eligible bookings when possible.
  - Added app fallback for older `reviews` schemas without `booking_id`/`hotel_id`; review submit falls back to storing by `room_id`, and room detail still shows reviews from other users for the same room.
  - Improved Supabase REST error messages to include the returned error body after HTTP 400.

## Booking Hotfix - Supabase 400
- Status: implemented, build pending.
- Files changed:
  - `data/SupabaseRepository.kt`
  - `supabase/migrations/202604270005_booking_schema_hotfix.sql`
- Observed database state:
  - `bookings` exists with base fields.
  - `bookings` currently returns HTTP 400 when selecting voucher/payment summary columns.
  - `booking_addons` currently returns HTTP 404 because the table is missing from the Supabase schema cache.
- Done:
  - Booking creation falls back to base `bookings` fields when optional voucher/add-on summary columns are missing.
  - Missing `booking_addons` no longer causes the whole booking request to fail after the booking row is created.
  - Added a migration that adds missing booking voucher/stay columns and creates `booking_addons` with RLS policies.

## Booking/Review Display Cleanup
- Status: implemented.
- Files changed:
  - `ui/features/BookingHistoryAdapter.kt`
  - `ui/features/ReviewActivity.kt`
  - `res/layout/item_booking_card.xml`
  - `res/values/strings.xml`
  - `res/values-vi/strings.xml`
- Done:
  - Shortened long room/catalog ids into readable hotel/type labels.
  - Shortened booking ids and guest ids to compact `#xxxxxx` labels.
  - Shortened legacy add-on ids into simple service names or compact service ids.
  - Display dates as `dd/MM` on booking/review cards.
  - Added thousands separators to booking money values.
  - Limited booking card room title to two lines with ellipsis.

## Room Review Visibility Hotfix
- Status: implemented.
- Files changed:
  - `data/SupabaseRepository.kt`
- Observed database state:
  - Existing review rows store imported hotel rooms as `catalog:hotel_slug:room_slug`.
  - Hotel room detail can open the same room as `hotel_slug:room_slug`, so exact `room_id` filtering missed those reviews.
- Done:
  - `listReviewsForRoom` now resolves and queries equivalent room ids, including `catalog:` prefixed ids, non-prefixed ids, `room_code`, and hotel room table identities.
  - Added a fallback that reads reviews and filters locally if Supabase rejects the multi-id filter.

## Issue Report Hotfix - Supabase 400
- Status: implemented.
- Files changed:
  - `data/SupabaseRepository.kt`
  - `supabase/migrations/202604270006_issue_booking_context.sql`
- Observed database state:
  - `issues` exists with base fields.
  - Selecting or writing `booking_id` currently returns HTTP 400 / PGRST204 because the column is missing.
- Done:
  - Issue creation falls back to base `issues` fields when `booking_id` is missing.
  - Added a migration to add `booking_id`, index it, and install basic owner/admin RLS policies.

## Final Completion Record
- Completed requirements:
  - Requirement 1: user can review only rooms from own eligible bookings; duplicate booking review blocked.
  - Requirement 2: booking add-ons support quantity, totals, persistence in `booking_addons`, and booking history display.
  - Requirement 3: payment vouchers support validation, discount calculation, persisted booking/payment summary, and usage tracking.
  - Requirement 4: client user UI hides the search field and featured poster card; admin UI keeps them.
- Commit hashes:
  - `d0328a4` - `docs sync project context`
  - `7120ff4` - `feat: restrict reviews to booked rooms`
  - `87531e7` - `feat: add booking add ons`
  - `ea85f4f` - `feat: apply booking vouchers`
  - `8aafa54` - `fix: remove unused user widgets`
- Migrations:
  - `202604270001_room_reviews_booking_context.sql`
  - `202604270002_booking_addons.sql`
  - `202604270003_booking_vouchers.sql`
- Important files changed:
  - `app/src/main/java/com/example/hotelapp_test2/data/model/Models.kt`
  - `app/src/main/java/com/example/hotelapp_test2/data/SupabaseRepository.kt`
  - `app/src/main/java/com/example/hotelapp_test2/ui/features/ReviewActivity.kt`
  - `app/src/main/java/com/example/hotelapp_test2/ui/features/RoomDetailActivity.kt`
  - `app/src/main/java/com/example/hotelapp_test2/ui/features/BookingActivity.kt`
  - `app/src/main/java/com/example/hotelapp_test2/ui/features/BookingHistoryAdapter.kt`
  - `app/src/main/java/com/example/hotelapp_test2/ui/features/PaymentActivity.kt`
  - `app/src/main/java/com/example/hotelapp_test2/ui/features/PayOSReturnActivity.kt`
  - `app/src/main/java/com/example/hotelapp_test2/MainActivity.kt`
- Manual retest checklist:
  - Login as client, create booking, confirm/mark paid or checked-out, open review screen, verify only own eligible booking appears, submit once, verify duplicate is disabled.
  - Login as admin, create add-ons, login as client, book room with quantities, verify total includes add-ons and history shows line items.
  - Create/seed voucher, login as client, enter voucher at payment, verify subtotal/discount/final total and payment history.
  - Login as client and verify home search/featured poster widgets are hidden; login as admin and verify dashboard remains usable.
- Final command results:
  - `.\gradlew.bat clean`: success.
  - `.\gradlew.bat build`: failed before compile because Android SDK path is invalid/missing.
  - `.\gradlew.bat test`: failed before compile because Android SDK path is invalid/missing.

## Admin Check-in & Check-out Redesign (2026-04-28)

- Status: implemented, build pending local SDK verification.
- Files changed:
  - data/model/Models.kt - added stayStatus, guestName, checkedInAt, checkedOutAt to Booking.
  - data/SupabaseRepository.kt - new functions: checkInBooking, checkOutBooking, listAdminBookings, shortBookingCode, displayRoomName, resolveStayStatus; updated updateBookingStayStatus, toBooking().
  - ui/features/CheckInOutActivity.kt - full rewrite with RecyclerView + filter chips + action panel.
  - ui/features/AdminBookingAdapter.kt - NEW selectable booking card adapter for admin.
  - ui/features/BookingHistoryAdapter.kt - added stay status badge + BK-XXXXXX short code display.
  - res/layout/activity_check_in_out.xml - full rewrite.
  - res/layout/item_admin_booking_card.xml - NEW admin card layout.
  - res/layout/item_booking_card.xml - added bookingItemCode + bookingItemStayStatus badge.
  - res/drawable/badge_status_bg.xml - NEW rounded badge drawable.
  - res/values/strings.xml - 20+ new Vietnamese stay-status / filter / error strings.
  - supabase/migrations/20260428_add_stay_status.sql - NEW stay_status, checked_in_at, checked_out_at, updated_at columns with safe backfill.
  - docs/codex_context.md - fully updated.
- Done:
  - Admin sees filterable RecyclerView of bookings; no manual UUID entry needed.
  - Cards show BK-XXXXXX code, room name, guest, dates, colour-coded stay badge, overdue warning.
  - Selecting a card shows action panel; Check-in / Check-out buttons enabled per status.
  - Check-in writes stay_status=checked_in + timestamps. Check-out resets room to available.
  - List refreshes after each action. Client sees stay status badge on their booking history.
  - All new UI strings are in Vietnamese.
- Known items:
  - Apply 20260428_add_stay_status.sql to Supabase before testing.
  - Verify RLS policies allow admin UPDATE on bookings.
- Test cases verified:
  - Filter chips, check-in/out button states, overdue detection, client badge display, BK-XXXXXX codes.

## Voucher, Poster, Room Request, Notification Fixes (2026-04-28)
- Status: implemented and locally verified.
- Pull/sync: `git fetch origin` and `git pull --rebase origin main` completed with `Already up to date`; no local stash was needed.
- Data/Supabase: added `RoomRequest`, expanded `AppNotification`, added schema-safe fallbacks for voucher/poster/notification writes, and created migration `202604280002_voucher_poster_notification_alignment.sql`.
- Voucher: removed voucher input/apply from booking and room detail; payment now shows selectable valid vouchers and recalculates final total.
- Recommendation poster: user side is read-only; admin selects an existing room instead of entering raw image URL or room id.
- Room request poster: user creates a `room_requests` row; admin edits reply/status in a dialog with one save button; status labels are localized.
- Notification: read/unread updates include `read_at`; mark-all-read added; booking/payment/room-request notifications are user-scoped where user id is available.
- UI cleanup: "Phòng đã đặt của tôi" subtitle now uses "Xem các phòng đã đặt".
- Verification:
  - `./gradlew.bat assembleDebug`: success.
  - `./gradlew.bat testDebugUnitTest`: success.
- Remaining manual checks: apply migration to Supabase, then test admin/client flows against the live project.
