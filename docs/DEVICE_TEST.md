# Device test checklist

Two accounts in the same society: **A = committee (admin)**, **R = resident**. Best on two phones (e.g. Galaxy M02s + iPhone). Run every migration first, then a fresh install of the latest build. Tick each line; screenshot anything that looks off.

## 0. Setup
- [ ] Both phones: notifications allowed when asked.
- [ ] R switches language to **தமிழ்** in Settings (A stays English) — used in section 8.

## 1. Login and onboarding
- [ ] Login: teal panel, white status-bar icons (Android **and** iPhone), தமிழ் switch works, +91 prefix, OTP screen, resend.
- [ ] New account: name screen → join with invite code + flat → "waiting for approval" screen.
- [ ] A approves → R gets "You're in! 🎉" push; tapping it opens the app straight into Home.
- [ ] Register a society (third test account): form, then lands on committee Home.

## 2. Home
- [ ] A: teal band with collection, tray (Notice, Raise bill, Poll, Invite) — each button goes to the right place.
- [ ] A: "Needs your attention" tiles — coloured when something waits, grey zeros otherwise; each opens the right screen.
- [ ] R: due card overlapping the band (days left / due today / overdue chip), Pay now and "Already paid?".
- [ ] R: shortcuts, latest notice with date block, poll — vote from Home, see the tick.
- [ ] Pull to refresh on Home. Status-bar icons turn dark again on other tabs.
- [ ] Bottom bar badges: unread notices, dues to verify (A) / unpaid (R), open requests (A).

## 3. Notices and polls
- [ ] A: New notice sheet — title, details, **Pin to top** switch, 3 photos → Post. R gets the push.
- [ ] R: notice shows unread dot; open it → detail card, photos open full screen; dot gone after.
- [ ] A: read-by bar updates; Pin / Unpin; Delete (photos disappear too).
- [ ] A: New poll sheet — add/remove options (2–6) → Create. R votes, changes vote; results bars.

## 4. Dues
- [ ] A: Raise bill sheet — quick name, ₹ amount, date chips (date in words below) → Raise. R gets "New bill" push.
- [ ] R: hero card → **Pay via UPI** opens UPI app; back → Report payment sheet → UTR → "Send for checking".
- [ ] A: "to verify" badge → Mark paid → R gets "Payment confirmed"; receipt PDF shares correctly.
- [ ] A: Paid in cash on another due; Cancel due; Manage bills → Edit amount (R gets "Bill updated" in Tamil) and Cancel bill.
- [ ] Online: Pay online (Razorpay test mode) → back in app shows paid.
- [ ] A: Export to Excel opens in Sheets/Excel.

## 5. Help desk
- [ ] R: Raise request sheet — category tiles, description, photos → Send. A gets the push.
- [ ] A: tracker Raised → In progress → Fixed; R gets each update. Open/Resolved filters.
- [ ] R can't see other flats' requests or their photos.

## 6. Chat
- [ ] Send messages both ways; avatars, colours, your bubbles teal.
- [ ] Swipe **right** on someone else's message / **left** on your own → reply bar → send; quote shows, tap jumps to original. Person replied to gets "… replied to you".
- [ ] Long-press menu: Reply, Delete (own), Report (A gets push), Mute (A only).
- [ ] Muted R sees "muted" note instead of the message box; A unmutes from Members.
- [ ] A deletes R's message → "Message removed by an admin", quote in replies wiped.

## 7. Settings and members
- [ ] Edit name; language switch; Privacy policy opens.
- [ ] A: society details + UPI ID save → R gets "New UPI ID" push; new invite code.
- [ ] A: Members — invite card → WhatsApp; ⋯ menu: make admin, change flat (sheet), mute, remove. R gets each push; R's app refreshes (new flat in header / back to join screen when removed).
- [ ] R: Leave society; Delete account (type DELETE) → back to login.

## 8. Tamil
- [ ] R's phone: every screen, sheet and dialog in Tamil, nothing cut off or overlapping.
- [ ] R's notifications arrive in Tamil; A's in English.

## 9. Robustness
- [ ] Airplane mode: friendly "no internet" messages, nothing crashes; back online → pull to refresh works.
- [ ] Send the app to background for 10+ minutes, come back: no endless spinner.
- [ ] Rotate / large font size (Settings → Display → Font size) on Android: screens still readable.
- [ ] Crashlytics: any crash shows in Firebase within minutes.
