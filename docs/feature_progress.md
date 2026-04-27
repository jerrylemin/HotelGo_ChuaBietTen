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
- Status: in progress from existing working tree changes, pending verification and commit.
- Planned/observed files:
  - `data/model/Models.kt`
  - `data/SupabaseRepository.kt`
  - `ui/features/ReviewActivity.kt`
  - `res/layout/activity_review.xml`
  - `res/values/strings.xml`
  - `res/values-vi/strings.xml`
  - `supabase/migrations/202604270001_room_reviews_booking_context.sql`
- Work to verify:
  - Only logged-in user's eligible bookings appear.
  - No hardcoded user id.
  - `reviews.booking_id` and duplicate protection exist.
  - Build succeeds.

## Requirement 2 - Booking Add-ons
- Status: not completed.
- Existing code has basic `add_on_items` listing and stores selected IDs in `bookings.add_ons`; this still needs proper booking add-on line items, quantity, totals, persistence, and detail display.

## Requirement 3 - Booking Vouchers
- Status: not completed.
- Existing voucher code is basic and uses `type/value/min_spend`; this still needs robust fields, validation, discount totals, persisted booking/payment discount fields, and usage tracking when supported.

## Requirement 4 - Remove User Widgets
- Status: not completed.
- Text search found user home search box in `activity_main.xml` using `main_search_hint`.
- Text search found featured poster card in `activity_main.xml` using `main_highlight_title/body/button`.

## Known Follow-up Checks
- Apply Supabase migrations to the target project.
- Verify RLS policies allow logged-in users to select their bookings, add-ons, vouchers, and insert own review/booking rows.
- Run `.\gradlew.bat assembleDebug` after each feature milestone.
- Run `.\gradlew.bat test` or `.\gradlew.bat build` for final verification.
