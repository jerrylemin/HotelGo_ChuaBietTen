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
- Status: not completed.
- Text search found user home search box in `activity_main.xml` using `main_search_hint`.
- Text search found featured poster card in `activity_main.xml` using `main_highlight_title/body/button`.

## Known Follow-up Checks
- Apply Supabase migrations to the target project.
- Verify RLS policies allow logged-in users to select their bookings, add-ons, vouchers, and insert own review/booking rows.
- Run `.\gradlew.bat assembleDebug` after each feature milestone.
- Run `.\gradlew.bat test` or `.\gradlew.bat build` for final verification.
