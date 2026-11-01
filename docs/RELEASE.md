# Release setup — signing, crash reporting, privacy policy

## 1. Android release signing

**Create the upload key once** (keep the file and passwords somewhere safe — a password manager plus an offline backup. Losing it means asking Google to reset the upload key):

```bash
keytool -genkeypair -v -keystore notiz-upload.jks -alias notiz -keyalg RSA -keysize 2048 -validity 10000
```

Put `notiz-upload.jks` in the project root and create `keystore.properties` next to it:

```properties
storeFile=notiz-upload.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=notiz
keyPassword=YOUR_KEY_PASSWORD
```

Both files are in `.gitignore` — never commit them. (On CI, set `NOTIZ_KEYSTORE_FILE`, `NOTIZ_KEYSTORE_PASSWORD`, `NOTIZ_KEY_ALIAS`, `NOTIZ_KEY_PASSWORD` instead.)

**Every upload:** bump `notiz.versionCode` (and `notiz.versionName` if you like) in `gradle.properties`, then:

```bash
./gradlew :composeApp:bundleRelease
# → composeApp/build/outputs/bundle/release/composeApp-release.aab
```

Upload the `.aab` in Play Console. Use **Play App Signing** (the default): Google holds the app signing key, your keystore is only the upload key.

## 2. Crash reporting (Firebase Crashlytics)

Uses the same Firebase project as push notifications.

- **Android:** nothing to add — with `composeApp/google-services.json` present, the Crashlytics plugin and library are applied automatically. In the Firebase console open **Crashlytics** once and click **Enable**.
- **iOS:** run `xcodegen` again in `iosApp/` (FirebaseCrashlytics was added to `project.yml`, plus a build step that uploads dSYMs so stack traces are readable).
- **Check it works:** install a release build, make it crash once (e.g. temporarily `throw RuntimeException("Crashlytics test")` from a button), reopen the app, and the crash shows up in Firebase → Crashlytics within a few minutes.

## 3. Privacy policy and account deletion page

The policy is `docs/privacy.html`. It includes a **Delete your account** section (`#delete`) that Play asks for.

1. Fill in the placeholders: **[YOUR NAME OR BUSINESS NAME]** and **[YOUR SUPPORT EMAIL]** (use a support address you'll check, ideally not your personal one).
2. Publish it with GitHub Pages: GitHub → repo **Settings → Pages** → Source **Deploy from a branch** → Branch `main`, folder **/docs** → Save.
   The URL becomes `https://oamprakash.github.io/NotizApp/privacy.html` (already set as `AppConfig.PRIVACY_POLICY_URL`; Settings → Privacy policy opens it). GitHub Pages on a free plan needs the repo to be public; otherwise host the single HTML file anywhere (Netlify drop, Cloudflare Pages) and update `AppConfig.PRIVACY_POLICY_URL`.
3. Play Console:
   - **App content → Privacy policy:** the URL above.
   - **App content → Data deletion:** the same URL with `#delete`.
   - **Data safety** (matches the policy):

| Data type | Collected | Shared | Purpose | Optional |
| --- | --- | --- | --- | --- |
| Phone number | Yes | No | Account management | No |
| Name | Yes | No | App functionality, account management | No |
| Photos | Yes | No | App functionality | Yes |
| Other user-generated content (notices, complaints, chat) | Yes | No | App functionality | Yes |
| Purchase history (dues payments) | Yes | No | App functionality | No |
| Crash logs, diagnostics | Yes | No | Analytics (crash reporting) | No |
| Device or other IDs (push token) | Yes | No | App functionality | No |

   Answer "data is encrypted in transit: Yes" and "users can request deletion: Yes". Payment card/UPI details are handled by Razorpay, not Notiz.

This isn't legal advice — have the policy reviewed before a public launch.

## 4. iOS (TestFlight)

Bump `CFBundleVersion` in `iosApp/project.yml` for every upload, set `aps-environment: production`, run `xcodegen`, then Xcode → Product → Archive → Distribute. Add the privacy policy URL in App Store Connect → App Privacy.
