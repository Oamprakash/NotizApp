# Device test checklist

Two accounts in the same society: **A = president**, **R = resident** (section 10 adds a secretary, a treasurer and a guard). Best on two phones (e.g. Galaxy M02s + iPhone). Run every migration first, then a fresh install of the latest build. Tick each line; screenshot anything that looks off.

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
- [ ] A deletes R's message → "Message removed by the committee", quote in replies wiped.

## 7. Settings and members
- [ ] Edit name; language switch; Privacy policy opens.
- [ ] A: society details + UPI ID save → R gets "New UPI ID" push; new invite code.
- [ ] A: Members — invite card → WhatsApp; ⋯ menu: change role (sheet), change flat (sheet), mute, remove. R gets each push; R's app refreshes (new flat in header / back to join screen when removed).
- [ ] R: Leave society; Delete account (type DELETE) → back to login.

## 8. Tamil
- [ ] R's phone: every screen, sheet and dialog in Tamil, nothing cut off or overlapping.
- [ ] R's notifications arrive in Tamil; A's in English.

## 9. Robustness
- [ ] Airplane mode: friendly "no internet" messages, nothing crashes; back online → pull to refresh works.
- [ ] Send the app to background for 10+ minutes, come back: no endless spinner.
- [ ] Rotate / large font size (Settings → Display → Font size) on Android: screens still readable.
- [ ] Crashlytics: any crash shows in Firebase within minutes.

## 10. Roles
Add three more test accounts: **S** (secretary), **T** (treasurer) and **G** (security guard).
- [ ] G: join screen → **I'm security staff** switch hides the flat field → waiting screen says "as security staff". A gets a join push saying "(security staff)".
- [ ] A: Members → ⋯ → **Change role** sheet: make S Secretary, T Treasurer. Each gets "Your role: …" and their app changes in place.
- [ ] S: Home tray has Notice, Poll, Invite (no Raise bill); attention tiles for requests, join requests, reported messages. Can post a notice and a poll; no Raise bill button on Dues; sees only own flat's dues.
- [ ] T: Home shows the collection band; tray has Notice and Raise bill; Dues shows every flat, Mark paid and Paid in cash work. No poll button. Settings shows UPI fields but not society name.
- [ ] R: no committee tools anywhere; Members screen is "Neighbours" without the ⋯ menu.
- [ ] G: four tabs — **Gate**, **Residents**, Notices (no Polls), Help desk. Residents list: search by flat or name, call button opens the dialler. Raise a request from Help desk → S gets the push.
- [ ] A: Settings → **Roles & permissions**: give Treasurer *Create polls* → T reopens the app → Poll button appears. *Reset to defaults* takes it away again.
- [ ] S tries Members → ⋯ on T: no Change role, no Remove (only the president can).
- [ ] A makes S president, then changes own role to Resident (warning shown). A's committee tools disappear; S now sees Roles & permissions.

## 11. Gate log
Use **G** (guard) and **R** (resident, ideally Tamil) on two phones.
- [ ] G: Gate → New visitor → Delivery, type R's flat (suggestions appear), tap Swiggy, name → **Ask R's flat**. Card shows "Asked flat".
- [ ] R: push "Visitor at the gate" (Tamil) → tap → Gate screen with Allow / Deny. Also on Home at the top.
- [ ] R taps Allow → G gets "B-202 allowed ✅"; card says "Allowed by …" → **Let in** → moves to Inside now → **Out** → Earlier today.
- [ ] Repeat with Deny → "said no" → **Send away**.
- [ ] Unknown flat shows "No one lives in this flat on Notiz"; Ask button stays off.
- [ ] Let in now (cab) → R gets "has come in", no Allow / Deny.
- [ ] Ask flat and don't answer → G's screen still refreshes by itself when R answers later (within ~15 s even without the push).
- [ ] R: Gate → Invite a guest → name, Tomorrow → code → Share on WhatsApp. Today's pass appears under Expected today for G (no code shown).
- [ ] G: Guest code → wrong code / tomorrow's code refused with a clear message → today's code → "✓ … let them in"; R gets "Your guest has arrived". Using it again fails.
- [ ] R cancels a code → it disappears for G too.
- [ ] A (president): Home → Gate → **Gate desk** tab shows the whole society's log.

## 12. Gate photos
- [ ] G: New visitor → camera tile → camera opens → photo shows in the sheet; Retake and Remove work. Ask the flat.
- [ ] R: push says "Photo attached"; the Allow / Deny card shows the photo; tap it → full screen.
- [ ] G: the photo shows on the desk card, under Inside now and in Earlier today.
- [ ] Another resident (other flat) can't see the visitor or the photo.
- [ ] iPhone: first time asks for camera access; saying "Don't Allow" shows "Couldn't open the camera" and the visitor can still be logged.

## 13. Visitor alerts
Run `20261116000000_visitor_alerts.sql` and redeploy the `push` function first. Use R logged in on **two phones** if you can.
- [ ] R: Settings → Visitor alerts: all three checks green (fix any red one with its button), brand tip shown on Xiaomi / OnePlus / Vivo / Samsung. *Send a test alert*, lock the phone: it rings like a call; a second test within a minute says "Wait a minute".
- [ ] G logs a visitor for R's flat with R's app **closed**: both of R's phones ring repeatedly, with Allow / Deny buttons (Tamil labels on the Tamil phone).
- [ ] Tap **Allow** on one phone without opening the app: ringing stops on **both** phones, a quiet "Allowed. The gate has been told." appears, G gets "allowed ✅".
- [ ] Repeat with Deny. Repeat and let G tap *Let in anyway* or *Send away* instead: R's phones stop ringing.
- [ ] Airplane mode on R, tap Allow: "Couldn't send your answer…"; tapping it opens the app.
- [ ] Not answered: the alert disappears after 10 minutes.
- [ ] iPhone: the alert shows Allow / Deny on long-press, asks for Face ID, and comes through a Focus mode.
- [ ] Settings → Notifications: turn off *Chat messages*, have A post in chat: no push for R. A posts a notice with *Notices and polls* off: no push. A raises a bill: R still gets it.
- [ ] Turn off the "Visitors at the gate" channel in Android settings: the Gate screen shows "This phone may miss visitors".
