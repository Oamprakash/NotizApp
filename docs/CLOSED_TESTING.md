# Closed testing on Google Play

Everything needed to get Notiz into a Google Play closed test and, after 14 days, apply for production. Do the steps in order; each has a box to tick.

New personal developer accounts must run a closed test with **at least 12 testers who stay opted in for 14 days in a row** before they can apply for production ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465)). Since 31 August 2026 new apps and updates must **target Android 16 (API 36)**; Notiz already does (`android-targetSdk = "36"`).

---

## 0. Things only you can fill in

- [ ] **Support email** (Play shows it publicly; use a separate address you'll check, e.g. a new Gmail).
  Put it in three places: `docs/privacy.html` (two `[YOUR SUPPORT EMAIL]` placeholders), `AppConfig.SUPPORT_EMAIL` (turns on Settings → *Send feedback*), and Play Console.
- [ ] **Publisher name** in `docs/privacy.html` (`[YOUR NAME OR BUSINESS NAME]`). Your own name is fine for a personal account.
- [ ] **Publish the privacy policy** (GitHub → repo Settings → Pages → branch `main`, folder `/docs`). Check that `https://oamprakash.github.io/NotizApp/privacy.html` opens. A private repo can't use free Pages; then drop `docs/privacy.html` on Netlify or Cloudflare Pages and update `AppConfig.PRIVACY_POLICY_URL`.

## 1. Backend ready for testers

- [ ] All migrations run on the Supabase project the app points to (`AppConfig.SUPABASE_URL`), in order, up to `20261115000000_gate_photos.sql`.
- [ ] Phone login uses a real SMS provider (Supabase → Authentication → Providers → Phone). Check the SMS daily limit covers ~20 testers logging in.
- [ ] Push works (`docs/PUSH_SETUP.md`): Firebase project, `google-services.json`, the `push` Edge Function and the webhook.
- [ ] Crashlytics enabled in Firebase (`docs/RELEASE.md` §2).
- [ ] Razorpay in **test mode** for the closed test (testers can try *Pay online* with test cards and no real money moves).

## 2. Signed build

- [ ] Upload key and `keystore.properties` set up (`docs/RELEASE.md` §1). Back up the `.jks` file and passwords.
- [ ] `gradle.properties`: `notiz.versionCode=1`, `notiz.versionName=0.9.0` (already set). Bump `versionCode` by 1 for every later upload.
- [ ] Build: `./gradlew :composeApp:bundleRelease` → `composeApp/build/outputs/bundle/release/composeApp-release.aab`.
- [ ] Install a release build on your phone once (`./gradlew :composeApp:installRelease`, or the APK from the bundle) and run through `docs/DEVICE_TEST.md` sections 1–2. R8 shrinking is off (`isMinifyEnabled = false`) so the release behaves like debug; turn it on later, after adding keep rules and testing.

## 3. Demo account for Google's reviewers

Reviewers can't receive your SMS, so give them a fixed test number with a ready-made society.

- [ ] Supabase → Authentication → Providers → Phone → **Test phone numbers**: add `919000000001=123456`.
- [ ] In the app: log in with **9000000001**, code **123456**, name **Play Reviewer**. Stop at "Join or register".
- [ ] Supabase SQL editor: run `supabase/seed/review_demo.sql`. It creates **Green Park Residency (Demo)** with neighbours, notices, this month's bill (one paid, one to verify, two unpaid), help-desk requests, a poll, chat, a pending join request and a Swiggy delivery waiting at the gate, and makes the reviewer the President. Run it again any time to reset.
- [ ] Reopen the app and check every tab has content. This is also the society to take screenshots in (step 5).

## 4. Create the app in Play Console

- [ ] **Create app** → App name `Notiz: Apartment Society App` · Default language **English (India) – en-IN** · **App** · **Free** · accept the declarations.

### App content (Policy → App content)

| Form | Answer |
| --- | --- |
| Privacy policy | `https://oamprakash.github.io/NotizApp/privacy.html` |
| App access | **All or some functionality is restricted** → add instructions: *Name:* Demo login · *Phone:* `9000000001` · *OTP:* `123456` · *Instructions:* "Tap Continue, enter 9000000001, then code 123456. The account is the President of a demo society with sample data, so every screen (Home, Notices, Dues, Help desk, Chat, Settings → Roles & permissions, Home → Gate) has content." |
| Ads | **No**, the app doesn't contain ads |
| Content rating | Category **All other app types** (it's a communication/utility app). Violence, sexuality, language, controlled substances, gambling: **No**. *Can users interact or exchange content?* **Yes** (society chat, notices). *Shares user's location?* **No**. *Allows purchases of digital goods?* **No**. Google works out the rating from these answers; expect a low age rating with a "Users interact" note. |
| Target audience | **18 and over** only. Not appealing to children. |
| News app | **No** |
| COVID-19 contact tracing | **No** |
| Data safety | See the table below |
| Government app | **No** |
| Financial features | Notiz doesn't hold money, lend or offer payment services; it opens the resident's UPI app or the society's own Razorpay checkout to pay maintenance. Read the list and pick **"My app doesn't provide any financial features"** unless a category clearly fits. |
| Health | **No** health features |
| Advertising ID | **No** (Notiz doesn't use Firebase Analytics or ads). Check the merged manifest has no `com.google.android.gms.permission.AD_ID` (Android Studio → AndroidManifest.xml → Merged Manifest); if a library adds it, answer Yes → Analytics, or remove it with `tools:node="remove"`. |
| Data deletion | Account deletion URL: `https://oamprakash.github.io/NotizApp/privacy.html#delete`. Users can delete their account in the app: **Yes**. Partial data deletion: **No**. |

### Data safety

*Does your app collect or share any of the required user data types?* **Yes**. *Is all data encrypted in transit?* **Yes**. *Do you provide a way to request deletion?* **Yes**. No data is **shared** with third parties (Supabase, Firebase and Razorpay are service providers acting for Notiz, which Play doesn't count as sharing).

| Data type | Collected | Purpose | Optional? |
| --- | --- | --- | --- |
| Personal info → Name | Yes | App functionality, Account management | Required |
| Personal info → Phone number | Yes (residents' login numbers; visitors' numbers if the guard types them) | Account management, App functionality | Required |
| Personal info → Other info (flat number, role) | Yes | App functionality | Required |
| Financial info → Purchase history (maintenance dues, UPI reference numbers, receipts) | Yes | App functionality | Required |
| Messages → Other in-app messages (society chat, notices, help-desk requests) | Yes | App functionality | Optional |
| Photos and videos → Photos (notice, complaint and visitor photos) | Yes | App functionality | Optional |
| App activity → Other user-generated content (polls, votes, gate log) | Yes | App functionality | Optional |
| App info and performance → Crash logs, Diagnostics | Yes | Analytics | Required |
| Device or other IDs (push token) | Yes | App functionality | Required |

Not collected: location, contacts, calendar, audio, files, web history, card or bank details, health.

## 5. Store listing (Grow → Store presence → Main store listing)

Graphics are in `art/store/`:

- [ ] **App icon** 512×512: `art/store/app-icon-512.png`
- [ ] **Feature graphic** 1024×500: `art/store/feature-graphic.png` (source: `feature-graphic.html`)
- [ ] **Phone screenshots** (2–8; 4+ recommended). Take them in the demo society. Play rejects screenshots whose long side is more than twice the short side, and the Galaxy M02s screen (720×1600) is just over that, so **crop each to 720×1440** (cut the status bar and a bit of the bottom) or take them on a Pixel emulator. Suggested set, in order:
  1. Home as President (collection band, attention tiles)
  2. Gate: the Swiggy Allow / Deny card (Home → Gate)
  3. Dues: this month's bill with *to verify*
  4. Notices with the pinned water-tank notice
  5. Help desk with the status tracker
  6. Chat with a reply
  7. Settings → Roles & permissions
  8. Home in Tamil (Settings → Language → தமிழ்)

**App name** (30 max): `Notiz: Apartment Society App`

**Short description** (80 max):
> Notices, maintenance dues, help desk, visitors and polls for your apartment.

**Full description** (4000 max):

> Notiz is one simple app for your apartment society: notices, maintenance dues, complaints, visitors at the gate, polls and a society chat, in English and Tamil.
>
> NOTICES THAT GET READ
> • Post notices with photos and pin the important ones
> • Residents get a notification; the committee sees how many have read it
>
> MAINTENANCE DUES WITHOUT THE CHASING
> • Raise a bill for every flat in one go
> • Residents pay with any UPI app or online, then send the UPI reference
> • The treasurer confirms, and a PDF receipt is ready to share
> • Export the month to Excel
>
> HELP DESK
> • Raise a request with photos: plumbing, electrical, lift, security and more
> • Track it from Raised to In progress to Fixed, with a notification at each step
>
> GATE AND VISITORS
> • Security staff log visitors with a photo; the flat taps Allow or Deny
> • Invite a guest with a one-time code; they walk straight in
> • A clear log of who came in and when
>
> POLLS AND CHAT
> • One vote per flat, results live
> • A society chat with replies, and tools to remove messages and mute members
>
> ROLES FOR EVERYONE
> • President, secretary, treasurer, residents and security staff each see what they need
> • The president decides what each role can do
>
> PRIVATE BY DESIGN
> • Only members of your society see its data; dues and help-desk requests stay between the flat and the committee
> • No ads. Your number isn't shown to other residents
>
> Getting started takes a minute: the committee registers the society and shares an invite code; residents join with their flat number.

Tamil translation (Store listing → Manage translations → add **Tamil – ta-IN**):

**Short description:**
> அறிவிப்பு, பராமரிப்புக் கட்டணம், உதவி மையம், வாயில் — ஒரே செயலியில்.

**Full description:**
> Notiz — உங்கள் அடுக்குமாடிக் குடியிருப்புச் சங்கத்துக்கான எளிய செயலி. அறிவிப்புகள், பராமரிப்புக் கட்டணம், புகார்கள், வாயில் பார்வையாளர்கள், வாக்கெடுப்புகள், சங்க அரட்டை — தமிழிலும் ஆங்கிலத்திலும்.
>
> • அறிவிப்புகள்: புகைப்படங்களுடன் வெளியிடுங்கள், முக்கியமானவற்றை மேலே பின் செய்யுங்கள்; யார் படித்தார்கள் என்று குழு பார்க்கலாம்.
> • பராமரிப்புக் கட்டணம்: எல்லா வீடுகளுக்கும் ஒரே முறையில் பில்; UPI அல்லது ஆன்லைனில் செலுத்தலாம்; பொருளாளர் உறுதிசெய்ததும் PDF ரசீது.
> • உதவி மையம்: புகைப்படங்களுடன் கோரிக்கை; ஒவ்வொரு நிலையிலும் அறிவிப்பு.
> • வாயில்: பாதுகாவலர் பார்வையாளரைப் புகைப்படத்துடன் பதிவு செய்வார்; வீட்டினர் அனுமதி / மறு என்று தொடலாம்; விருந்தினருக்கு ஒருமுறைக் குறியீடு.
> • வாக்கெடுப்பு மற்றும் அரட்டை: ஒரு வீட்டுக்கு ஒரு வாக்கு; பதில் அனுப்பும் வசதியுடன் சங்க அரட்டை.
> • பொறுப்புகள்: தலைவர், செயலாளர், பொருளாளர், குடியிருப்பாளர், பாதுகாவலர் — ஒவ்வொருவருக்கும் தேவையானது மட்டும்.
> • தனியுரிமை: உங்கள் சங்க உறுப்பினர்கள் மட்டுமே தரவைப் பார்க்க முடியும். விளம்பரங்கள் இல்லை.

- [ ] **App category:** Lifestyle (alternatives: House & Home, Communication). **Tags:** pick the closest to "Real estate / Home", "Communication".
- [ ] **Contact details:** the support email; website optional (the privacy page is fine).

## 6. The closed test track

- [ ] Testing → **Closed testing** → *Create track* (or use the default *Closed testing – Alpha*).
- [ ] **Countries:** India.
- [ ] **Testers:** create a **Google Group** (e.g. `notiz-testers@googlegroups.com`) and add the group, so people can join without you editing the list. Each tester must use the Google account that's signed in to Play on their phone.
- [ ] **Create release** → upload the `.aab` → release name `0.9.0 (1)` → release notes:

  ```
  <en-IN>
  First test build of Notiz. Please try: joining your society, notices, paying a due, raising a help-desk request, voting, chat, and the gate (visitor approval and guest codes). Settings → Send feedback goes straight to us.
  </en-IN>
  <ta-IN>
  Notiz-இன் முதல் சோதனைப் பதிப்பு. சங்கத்தில் சேருதல், அறிவிப்புகள், கட்டணம், உதவி மையம், வாக்கெடுப்பு, அரட்டை, வாயில் — அனைத்தையும் முயற்சி செய்து, Settings → கருத்து அனுப்பு மூலம் சொல்லுங்கள்.
  </ta-IN>
  ```

- [ ] Send for review. The first review usually takes a few days. Once it's live, copy the **opt-in link** (Testers tab) and send it with the Google Group link.
- [ ] Check the **Pre-launch report** (Release → Testing → Pre-launch report) after a few hours: crashes, accessibility and screenshots from Google's test devices. It will mostly stop at the login screen, which is fine.

## 7. Getting 12+ testers who stay 14 days

The clock counts **consecutive days with at least 12 testers opted in**; anyone who opts out and back in restarts their own 14 days. Aim for **18–20** so a few drop-outs don't matter.

- [ ] **Your pilot society first.** Real committee members and residents are the best testers and give the feedback Google asks about. Ask the committee to register the society and post the invite code in the society WhatsApp group with the opt-in link.
- [ ] **Family and friends** with Android phones, each joining the pilot society or a second test society (one person registers, others join).
- [ ] **Developer tester swaps** (communities where developers test each other's apps) to top up the numbers. Treat these as extra, not the core.
- [ ] Use **Day 1 / Day 5 / Day 10 nudges** (a WhatsApp message with one task each), so Google sees real use:
  - Day 1: install, join the society, set your language.
  - Day 3: pay this month's test due with the UPI reference "123456789012" (committee: mark it paid).
  - Day 5: raise a help-desk request with a photo; vote in the poll.
  - Day 8: chat and reply to a message; committee posts a notice.
  - Day 10: guard (or a volunteer with the Security role) logs a visitor for your flat; Allow or Deny.
  - Day 12: invite a guest with a code.
- [ ] Ship **at least one update** during the 14 days (fixes from feedback, `versionCode` 2). It shows the test led to changes.
- [ ] Keep a simple log of feedback and what you changed; you'll paste it into the production application.

## 8. Apply for production (after day 14)

Play Console → Dashboard → **Apply for production**. Draft answers (edit to match what really happened):

- **How did you recruit testers?** "Residents and committee members of our pilot apartment society in Chennai, plus friends and family who joined test societies. We shared the opt-in link and invite code in the society's group."
- **How engaged were they?** "Testers used the app over the 14 days: joined the society, paid test dues, raised help-desk requests with photos, voted in polls, chatted, and the security guard logged visitors that residents approved in the app."
- **Feedback and what you changed:** list the real items, e.g. "Added a confirmation before Allow/Deny at the gate; centred the guest-code field; fixed …".
- **Who is the app for / value:** "Apartment societies in India (starting with Chennai) that run notices, maintenance collection, complaints and visitor approvals over chat groups and paper. Notiz puts them in one app in English and Tamil."
- **Expected installs in year one:** an honest estimate, e.g. "1,000–5,000 (10–30 societies)".
- **Production readiness:** "Crash-free in testing (Crashlytics), privacy policy and in-app account deletion in place, demo account for review provided."

Google usually replies within 7 days. If they ask for more testing, keep the closed test running and apply again.

## 9. iPhone testers (optional, in parallel)

TestFlight doesn't have the 12-tester rule. See `docs/RELEASE.md` §4: set `aps-environment: production`, run `xcodegen`, archive and upload, then add testers by email (internal: up to 100 App Store Connect users; external: needs a short beta review). Use the same demo account in *Test Information → Sign-in required*.
