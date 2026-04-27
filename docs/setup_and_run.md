# Setup And Run

## Android
- Open the repository in Android Studio, or run from the repo root:
  - `.\gradlew.bat assembleDebug`
  - `.\gradlew.bat testDebugUnitTest`
- Keep machine-specific SDK settings in `local.properties`; do not commit it.

## Supabase
- The Android app should use only the Supabase URL and anon key from local configuration.
- Do not commit `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_ACCESS_TOKEN`, database passwords, `.env`, `supabase.env`, `local.properties`, or any other secret.
- Apply SQL migrations from `supabase/migrations` in order. For this session, apply:
  - `supabase/migrations/202604280002_voucher_poster_notification_alignment.sql`
- If PostgREST still reports stale schema after migration, reload the schema cache. The migration includes `notify pgrst, 'reload schema';`.

## Email Notifications
- The app creates in-app notifications.
- Do not put SMTP credentials or service-role keys in Android.
- Email delivery should be implemented later through a backend or Supabase Edge Function configured outside the app.
