# Push notifications — setup

Notiz sends push notifications for:

| Event | Who gets it |
| --- | --- |
| New notice | All members except the author |
| New bill raised | That flat |
| Payment reported (UTR entered) | Admins |
| Payment confirmed / not received | That flat |
| Dues reminder: 2 days before, on the due date, 3 days overdue | That flat |
| New complaint | Admins |
| Complaint status changed | Resident who raised it |
| New poll | All members except the creator |
| Chat message | All members except the sender (grouped per society) |
| Join request | Admins |
| Request approved | The new member |

Tapping a notification opens the matching tab (join requests open Members).

## How it works

```
App ── register_device_token() ──▶ device_tokens
Triggers on notices/dues/… ─────▶ notification_outbox ── Database Webhook ──▶ Edge Function `push`
                                                                              │
                                              Firebase Cloud Messaging ◀──────┘
                                                 ├─▶ Android
                                                 └─▶ APNs ─▶ iOS
```

Recipients are decided by database triggers, so the app can't send or spoof notifications.
The app builds and runs without any of the setup below; push just stays off until it's done.

## 1. Database

Supabase → **SQL Editor** → paste `supabase/migrations/20261029000000_push_notifications.sql` → **Run**.

## 2. Firebase project

1. [console.firebase.google.com](https://console.firebase.google.com) → **Add project** → name it `Notiz` (Analytics optional).
2. **Add app → Android**: package name `com.notiz.app` → download `google-services.json` → put it in `composeApp/`.
3. **Add app → iOS**: bundle ID `com.notiz.app` → download `GoogleService-Info.plist` → put it in `iosApp/iosApp/`, then run `xcodegen` again in `iosApp/`.
4. **Project settings → Service accounts → Generate new private key**. Keep this JSON file private; it can send pushes to all your users. Never commit it.

Both Firebase config files are in `.gitignore`. Keep a copy somewhere safe.

## 3. Deploy the Edge Function

Install the [Supabase CLI](https://supabase.com/docs/guides/local-development/cli/getting-started), then from the repo root:

```bash
supabase login
supabase link --project-ref <project-ref>          # the part before .supabase.co in your URL

# Any long random string; the webhook must send the same value
openssl rand -hex 32                               # copy the output

supabase secrets set PUSH_WEBHOOK_SECRET=<that-random-string>
supabase secrets set FCM_SERVICE_ACCOUNT="$(cat path/to/service-account.json)"

supabase functions deploy push --no-verify-jwt
```

`--no-verify-jwt` is needed because the webhook authenticates with the secret header instead.

## 4. Database Webhook

Supabase → **Database → Webhooks → Create a new hook**:

| Field | Value |
| --- | --- |
| Name | `push_outbox` |
| Table | `notification_outbox` |
| Events | Insert |
| Type | Supabase Edge Functions |
| Edge Function | `push` (method POST, timeout 5000 ms) |
| HTTP Headers | add `x-webhook-secret` = the random string from step 3 |

## 5. Daily dues reminders

Supabase → **Integrations → Cron** → enable → **Create job**:

| Field | Value |
| --- | --- |
| Name | `dues-reminders` |
| Schedule | `30 3 * * *` (every day 03:30 UTC = 9:00 AM IST) |
| Type | SQL snippet |
| SQL | `select public.enqueue_due_reminders();` |

## 6. iOS: connect APNs (needs the paid Apple Developer Program)

1. [developer.apple.com](https://developer.apple.com/account) → **Certificates, IDs & Profiles → Keys → +** → tick **Apple Push Notifications service (APNs)** → download the `.p8` file and note the **Key ID**.
2. Firebase → **Project settings → Cloud Messaging → Apple app configuration** → upload the `.p8` with the Key ID and your **Team ID**.
3. In Xcode, select your Team under Signing & Capabilities. `project.yml` already adds the Push Notifications entitlement (`aps-environment: development`). Change it to `production` for TestFlight and App Store builds.

The Firebase iOS SDK 13 needs **Xcode 26 or newer**. Test on a real iPhone.

## 7. Test

1. Install on two phones and sign in as admin (A) and resident (B). Allow notifications when asked.
2. On A, post a notice. B should get "📢 <title>" within a few seconds. Tap it and Notices opens.
3. If nothing arrives:
   - **Table Editor → `device_tokens`**: B's token should be there. If it's missing, the Firebase config file isn't in the app, or notifications were denied.
   - **Table Editor → `notification_outbox`**: the new row should have `sent_at` and `sent_count` filled. If `sent_at` stays empty, check the webhook (step 4).
   - **Edge Functions → push → Logs**: shows FCM or OAuth errors, for example a wrong service account or missing APNs key.

## Before launch

- Set `aps-environment` to `production` for release builds.
- Remove the test phone numbers from Supabase Auth.
- Clean old outbox rows now and then, for example a monthly cron job:
  `delete from notification_outbox where sent_at < now() - interval '30 days';`
