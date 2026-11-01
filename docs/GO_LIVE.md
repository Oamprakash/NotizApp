# Go-live checklist — first pilot society

Goal: one housing society using Notiz for real notices, dues and complaints for 90 days, with live payments and real OTP SMS. Tick each box; the order matters roughly top to bottom.

## 1. Backend (Supabase)

- [ ] All migrations run, in date order (`supabase/migrations/`): init, push notifications, receipts, razorpay, admin tools.
- [ ] Edge Functions deployed to the **Notiz** project: `push`, `create-payment-link`, `razorpay-webhook`, `delete-account` (`--no-verify-jwt` only where noted in their setup docs; `delete-account` deploys normally: `supabase functions deploy delete-account`).
- [ ] Push webhook (`notification_outbox` → `push`) and daily `dues-reminders` cron are set up ([PUSH_SETUP.md](PUSH_SETUP.md)).
- [ ] Upgrade the project to **Supabase Pro** so it never pauses on residents. Free projects pause after a week of inactivity.
- [ ] Turn on daily backups (included on Pro) and note how to restore.
- [ ] Clean up test data: delete test societies, dues and users from the Table Editor (or start a fresh production project).

## 2. Login (real OTP SMS)

- [ ] Choose an SMS provider in Supabase Auth → Phone: Twilio, Vonage, MessageBird or Textlocal (or MSG91 via a "Send SMS" hook).
- [ ] **DLT registration (India, required for SMS):** register your business as a Principal Entity on a DLT portal (Jio, Vodafone Idea, Airtel, BSNL), register a 6-letter sender ID (e.g. NOTIZA), and get an OTP template approved, e.g. `Your Notiz login code is {#var#}. Do not share it with anyone.` Configure the same template in your SMS provider. This can take a few days, so start early.
- [ ] Remove the **test phone numbers** from Supabase Auth (or set "valid until" to before launch).
- [ ] Turn on Auth rate limits for SMS (Auth → Rate Limits) to avoid OTP abuse costs.

## 3. Payments (the society's Razorpay)

- [ ] The society completes Razorpay KYC in the society's name: registration certificate, society PAN, bank account in the society's name, authorised signatory's ID.
- [ ] In Notiz (Dues → Online payments), the admin saves the **live** keys (`rzp_live_…`).
- [ ] In Razorpay **live mode**, add the webhook again (URL + secret from the app, event `payment_link.paid`). Test-mode and live-mode webhooks are separate.
- [ ] Do one real ₹1 test payment from a resident account: due becomes Paid, receipt shows "Online (Razorpay)", the push arrives.
- [ ] The society's UPI ID in Notiz is a current-account / merchant VPA (for residents who pay outside Razorpay).

## 4. Android release

- [ ] Create an upload keystore and keep it safe (losing it means you can't update the app):
      `keytool -genkeypair -v -keystore notiz-upload.jks -alias notiz -keyalg RSA -keysize 2048 -validity 10000`
- [ ] Add a release `signingConfig` that reads the keystore path and passwords from `local.properties` (never commit them), and bump `versionCode` for every upload.
- [ ] Build: `./gradlew :composeApp:bundleRelease` → `composeApp/build/outputs/bundle/release/*.aab`.
- [ ] Play Console: create the app (`com.notiz.app`), fill in the store listing basics, **Data safety** form (phone number, name, payment info handled by Razorpay) and a **privacy policy URL**.
- [ ] Upload to **Internal testing**, add the pilot residents' Google account emails (up to 100), and share the opt-in link.
- [ ] Before a public launch: a personal developer account needs **closed testing with 12+ testers for 14 days** first. Start that during the pilot.
- [ ] Turn on R8 (`isMinifyEnabled = true`) only after testing; add keep rules for kotlinx.serialization if needed.

For quick installs before Play Console is ready, the GitHub Actions build produces a debug APK on every push (Actions → latest run → Artifacts).

## 5. iOS release (if pilot residents use iPhones)

- [ ] Apple Developer Program (₹8,700/year approx.) enrolled.
- [ ] APNs key uploaded to Firebase ([PUSH_SETUP.md](PUSH_SETUP.md) §6), `GoogleService-Info.plist` in `iosApp/iosApp/`.
- [ ] `aps-environment` set to **production** in `iosApp/project.yml`, then `xcodegen`.
- [ ] Xcode → Product → Archive → upload; in App Store Connect add pilot residents as **TestFlight** testers (external testing needs a quick beta review).

## 6. Legal and trust

- [ ] Privacy policy page (what's collected: phone, name, flat number, payments metadata; where it's stored: Supabase, region; contact email; how to delete an account). Host it on a simple site or a public doc.
- [ ] Terms of use (Notiz isn't a party to payments; societies are responsible for their dues).
- [ ] Support contact: an email or WhatsApp number shown in the app listing.
- [ ] Account deletion: in the app under Settings → Delete my account. Play *also* asks for a web link where people can request deletion without the app: add a short "Delete your account" section to the privacy page (open Notiz → Settings → Delete my account, or email us from your registered number) and paste that URL into Play Console → App content → Data deletion. Say what's deleted (name, phone, memberships, device tokens) and what's kept (posts without your name; paid dues and receipts as society records).

## 7. Onboarding the pilot society (in person)

**Before the visit**
- [ ] Get the flat list (Excel or a photo of the register) and the committee members' phone numbers.
- [ ] Create the society in Notiz with the secretary's account; make the treasurer an admin.
- [ ] Print the invite code on an A4 notice for the notice board and lift.

**Weekend help desk (2–3 hours in the clubhouse)**
- [ ] Help residents install from the internal-testing link, log in, and join with the invite code + flat number.
- [ ] Admin approves on the spot; residents allow notifications when asked.
- [ ] Post the first notice live ("Notiz is now our official notice board") and show the read count growing.
- [ ] Raise the current month's maintenance bill in Notiz; show **Pay online** and the receipt.
- [ ] Switch elders to Tamil (தமிழ் button) where helpful.

**First 30 days**
- [ ] Committee posts notices only on Notiz (WhatsApp group: "see Notiz" links only).
- [ ] Track the three pilot numbers weekly: flats joined (target ≥ 70%), notices read (≥ 80%), dues paid on time vs. last month.
- [ ] 15-minute check-in with the secretary every week; collect complaints and feature requests.
- [ ] Day 30 review with the committee; day 90 decision on the paid plan.

## 8. Monitoring

- [ ] Supabase → Reports: database size, auth users, Edge Function errors — check weekly.
- [ ] Edge Function logs for `push`, `create-payment-link`, `razorpay-webhook` after each big announcement or billing day.
- [ ] Razorpay → Webhooks → delivery log: no failed deliveries.
- [ ] Add crash reporting before scaling beyond one society (e.g. Firebase Crashlytics or Sentry for KMP).
