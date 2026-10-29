# Notiz — housing-society app (KMP MVP)

Android + iOS app for housing societies: notices with read tracking, UPI dues collection, complaints, polls (one vote per flat) and a realtime group chat. 100% shared Kotlin with Compose Multiplatform; backend on Supabase.

Scope, screens, data model and sprint plan: [`docs/MVP_SPEC.md`](docs/MVP_SPEC.md).

## Stack

| Layer | Choice |
| --- | --- |
| Language / UI | Kotlin 2.4.20, Compose Multiplatform 1.12.2, Material 3 |
| Navigation / state | JetBrains Navigation Compose 2.9.2, Lifecycle ViewModel 2.10.0 |
| Backend | Supabase (Postgres + RLS, Phone OTP auth, Realtime) via supabase-kt 3.8.0 |
| Networking | Ktor 3.5.1 (OkHttp on Android, Darwin on iOS) |
| Build | Gradle 8.14.3, AGP 8.13.2, JDK 17+ |

## Project layout

```
composeApp/src/
  commonMain/kotlin/com/notiz/app/
    App.kt                 root: theme + onboarding state machine
    AppConfig.kt           ← put your Supabase URL + anon key here
    AppContainer.kt        manual DI (repositories)
    data/                  Supabase client, models, repositories
    push/                  push notifications: token registration, tap routing
    ui/                    auth, onboarding, home tabs, notices, dues,
                           complaints, polls, chat, members, theme
    i18n/                  language switch (English / தமிழ்), translatable messages
    util/                  ₹ formatting, dates, UPI deep links
  commonMain/composeResources/
    values/strings.xml     English text
    values-ta/strings.xml  Tamil text (same keys)
  commonTest/              unit tests for util/
  androidMain/             MainActivity, manifest, FCM service, notification channel
  iosMain/                 MainViewController (called from Swift), iOS push bridge
iosApp/                    SwiftUI host app + AppDelegate for push (XcodeGen project.yml)
supabase/migrations/       schema, RLS policies, RPC functions, push triggers
supabase/functions/push/   Edge Function that sends notifications via FCM (+ tests)
docs/MVP_SPEC.md           MVP scope and plan
docs/PUSH_SETUP.md         Firebase / APNs / webhook / cron setup for push
```

## 1. Set up Supabase (10 minutes)

1. Create a project at [supabase.com](https://supabase.com) — pick the **Mumbai (ap-south-1)** region for Indian users.
2. **SQL Editor → New query** → run each file in `supabase/migrations/` in date order
   (`20261023000000_init.sql`, then `20261029000000_push_notifications.sql`).
   (Or with the CLI: `supabase link` then `supabase db push`.)
3. **Authentication → Sign In / Providers → Phone**: enable it.
   - For development, add **test phone numbers** with fixed OTPs (e.g. `919999900001` → `123456`). No SMS is sent and it costs nothing.
   - Before launch, connect an SMS provider (Twilio, MessageBird, Vonage or Textlocal; MSG91 via a Send SMS hook). Indian SMS needs DLT-registered templates.
4. **Project Settings → API**: copy the Project URL and the `anon` public key into
   `composeApp/src/commonMain/kotlin/com/notiz/app/AppConfig.kt`.
   The anon key is meant to ship in apps; Row Level Security protects the data.

## 2. Run on Android

Open the root folder in a recent Android Studio (2025.2 or newer), let Gradle sync, choose the `composeApp` run configuration and run on your phone or emulator.

```bash
./gradlew :composeApp:assembleDebug        # APK in composeApp/build/outputs/apk/debug/
./gradlew :composeApp:testDebugUnitTest    # unit tests
```

## 3. Run on iOS (needs a Mac with Xcode 16+)

```bash
brew install xcodegen
cd iosApp && xcodegen          # generates iosApp.xcodeproj from project.yml
open iosApp.xcodeproj
```

Pick a simulator and Run. The Xcode build phase calls Gradle to build the Kotlin framework (first build takes a few minutes). For a device, set your Team under Signing & Capabilities.

## 4. Try the full flow

1. Phone A: sign in → name → **Register society** (add a UPI ID) → you are admin.
2. Members (top-right) → **Share on WhatsApp** to send the invite code.
3. Phone B: sign in → **Join with code** + flat number → waits for approval.
4. Phone A: Members → **Approve**.
5. Post a notice (A) → open it (B) → A sees "Read by 1 of 2".
6. Dues → **Raise bill** (A) → **Pay via UPI** then enter the UTR (B) → **Mark paid** (A).
7. Raise a complaint (B) → change status (A). Create a poll (A) → vote (B). Chat both ways.

## Languages

The app is in **English and Tamil (தமிழ்)**. It follows the phone's language, and the **English / தமிழ்** button (login, setup and home screens) switches instantly. The choice is saved on the device.

To add or change text, edit both `composeResources/values/strings.xml` and `values-ta/strings.xml` with the same key, and use it in code as `stringResource(Res.string.key)`. Messages created in ViewModels use `uiText(Res.string.key)` so they're translated when shown. Adding Hindi later is one more folder: `values-hi/strings.xml`.

## Push notifications

Notices, bills, payment updates, dues reminders, complaints, polls, chat and join requests send push notifications to the right people. Setup takes about 20 minutes: a Firebase project, one Edge Function, a webhook and a daily cron job. See **[docs/PUSH_SETUP.md](docs/PUSH_SETUP.md)**. Without that setup the app still builds and runs, just with push off.

## Security model

- Every table has RLS. Residents see only their society, only their own flat's dues and their own complaints; outsiders and pending members see nothing but the society name.
- Privileged writes (create/join society, raise bills, report/verify payments, polls, votes, read receipts) are `security definer` RPCs that check the caller's role.
- Triggers fill sender name and flat number server-side, so they can't be spoofed.
- One vote per flat is enforced by a primary key on `(poll_id, flat_no)`.
- Push recipients are chosen by database triggers; the app can only register its own device token.

## What was verified while building this

- **SQL migration**: run against PostgreSQL 16 with Supabase-style roles, then tested end to end — create/join/approve, notices and read counts, dues → UTR → verify, complaint status, vote change, chat sender spoofing, and an outsider seeing zero rows. All passed.
- **supabase-kt calls**: checked against the supabase-kt 3.8.0 source.
- **Utilities**: the `util/` unit tests pass.
- **Push triggers**: every notification case (12 scenarios) checked on PostgreSQL 16 for the right recipients and text; outsiders can't read tokens or the outbox or call the internal functions.
- **Edge Function**: type-checked with Deno, 6 tests pass (JWT signing, FCM payload, sending, dead-token cleanup, outbox marking).
- **Not yet compiled**: the build environment had no access to Maven/Google repositories or Xcode, so the first Gradle sync happens on your machine. If a version clashes, these are the usual fixes:
  - `compose.material3` / `compose.runtime` deprecation warnings are expected with Compose Multiplatform 1.12; they don't fail the build.
  - If AGP complains about the KMP + `com.android.application` combination, stay on AGP 8.13.x (AGP 9 needs the new `androidApp` + `com.android.kotlin.multiplatform.library` structure).
  - Keep `ktor` in `libs.versions.toml` equal to the Ktor version supabase-kt uses.

## Known MVP limits (next steps)

- UPI payments are confirmed manually via UTR. Many UPI apps block intent payments with an amount to personal VPAs, so use the society's current-account/merchant VPA. Phase 2: Razorpay or Cashfree with webhook auto-confirmation.
- English only; Tamil/Hindi strings via Compose resources next.
- One society per user in the UI (the schema already supports more).
