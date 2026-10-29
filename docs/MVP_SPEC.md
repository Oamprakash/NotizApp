# Notiz — MVP Spec (Housing Societies)

**Notiz** — one app for running a housing society: notices people actually see, dues collected on time, complaints tracked, polls, and a group chat.

## 1. Goal of the MVP

Win 10 housing societies in one city (Chennai) with a free 90-day pilot, and prove two numbers:

| Metric | Target by day 90 |
| --- | --- |
| Flats joined | ≥ 70% of flats |
| Notices read | ≥ 80% of joined flats |
| Dues paid on time | Higher than the society's current baseline |

Everything outside these outcomes is out of scope for v1.

## 2. Users and roles

| Role | Who | Can do |
| --- | --- | --- |
| Admin | Secretary, treasurer, committee | Register society, approve members, post notices, raise dues bills, verify payments, update complaints, create polls |
| Resident | Owner or tenant of a flat | Read notices, pay dues, raise complaints, vote (one vote per flat), chat |

One user belongs to one society in v1 (data model already allows more).

## 3. Features in v1

1. **Phone OTP login** (Supabase Auth) + profile name.
2. **Society onboarding**
   - Admin registers society → gets a 6-character invite code.
   - Resident joins with invite code + flat number → status *pending* → admin approves.
3. **Notices**: admin posts (title, body, pin). Residents see pinned first. Opening a notice records a read. Admin sees "Read by X of Y members".
4. **Dues**
   - Admin raises a bill (title, amount, due date) → one dues row per flat.
   - Resident taps **Pay via UPI** → UPI app opens with society VPA + amount → resident enters UTR → status *submitted*.
   - Admin verifies → *paid*. Admin sees collected vs total.
5. **Complaints**: resident raises (category + description). Admin moves Open → In progress → Resolved. Residents see their own; admin sees all.
6. **Polls**: admin creates question + options. One vote per flat (can change vote). Live results.
7. **Group chat**: one society-wide chat, realtime.
8. **Members (admin)**: approve/reject pending, see all flats, share invite code.

## 4. Out of scope for v1 (next phases)

| Phase | Feature |
| --- | --- |
| 2 | ✅ Push notifications (FCM/APNs) for notices, dues, reminders, complaints, polls, chat, approvals — see `docs/PUSH_SETUP.md` |
| 2 | Tamil + Hindi UI (Compose resources), voice notes |
| 2 | Payment gateway (Razorpay/Cashfree UPI) with auto-reconciliation via webhooks, replacing manual UTR entry |
| 2 | Receipts as PDF, dues export to Excel for the auditor |
| 3 | Multiple groups per society (blocks, committee-only), threads, media |
| 3 | SMS / web-link fallback for residents without the app |
| 3 | Visitor and staff management, amenity booking |
| 3 | Multi-society switcher, schools and businesses segments |

## 5. Screens

| # | Screen | Notes |
| --- | --- | --- |
| 1 | Login | Phone (+91) → OTP |
| 2 | Profile | Full name |
| 3 | Society setup | Tab: Join with code / Register society |
| 4 | Pending approval | Refresh, sign out |
| 5 | Home | Top bar (society, members, sign out) + bottom tabs |
| 5a | Notices tab | List, admin FAB to post |
| 5b | Dues tab | Resident: my dues + pay. Admin: all flats, verify, new bill |
| 5c | Complaints tab | List, raise, admin status chips |
| 5d | Polls tab | Vote, results bars, admin create |
| 5e | Chat tab | Realtime messages |
| 6 | Notice detail | Marks read; admin read count |
| 7 | Members | Invite code, pending approvals, member list |

## 6. Architecture

```
Kotlin Multiplatform (Android + iOS)
└── composeApp
    ├── commonMain   ← 100% of UI (Compose Multiplatform) + logic
    │   ├── data/    Supabase client, models, repositories
    │   ├── ui/      screens + ViewModels (androidx lifecycle KMP)
    │   └── util/    UPI deep link builder, formatting
    ├── androidMain  MainActivity, Ktor OkHttp engine
    └── iosMain      MainViewController, Ktor Darwin engine
iosApp/              SwiftUI host app (XcodeGen project.yml)
supabase/migrations/ Postgres schema, RLS policies, RPC functions
```

- **Backend:** Supabase (Postgres + Auth + Realtime). Row Level Security on every table; all multi-step writes (create society, join, raise dues, vote, payment state changes) are `security definer` RPCs that check the caller's role.
- **DI:** a small manual `AppContainer` (no DI framework for the MVP).
- **Navigation:** Navigation Compose (JetBrains multiplatform) with type-safe routes; auth gating via an `AppViewModel` state machine.
- **Money:** stored as paise (`bigint`) to avoid float errors.

## 7. Data model

| Table | Key columns |
| --- | --- |
| profiles | id (= auth user), full_name |
| societies | name, city, upi_vpa, upi_payee_name, invite_code |
| memberships | society_id, user_id, flat_no, role (admin/resident), status (pending/approved/rejected) |
| notices | society_id, author_id, title, body, pinned |
| notice_reads | notice_id, user_id |
| dues | society_id, flat_no, title, amount_paise, due_date, status (pending/submitted/paid), utr |
| complaints | society_id, raised_by, flat_no, category, description, status |
| polls / poll_options / poll_votes | one vote per (poll, flat) |
| messages | society_id, sender_id, sender_name, flat_no, body |

## 8. Sprint plan (solo, 2-week sprints)

| Sprint | Scope | Exit criteria |
| --- | --- | --- |
| 1 | Supabase project, migration, OTP login, profile, society create/join/approve | Two phones join the same society |
| 2 | Notices with read tracking, members screen | Admin sees read count |
| 3 | Dues: bills, UPI pay, UTR submit, verify, totals | One real UPI payment verified end to end |
| 4 | Complaints, polls, chat | All tabs working on Android + iOS |
| 5 | Polish, crash reporting, Play internal testing + TestFlight, pilot with 1 society | First society live |

## 9. Risks

| Risk | Mitigation |
| --- | --- |
| UPI apps decline intent payments to personal VPAs | Use the society's current-account / merchant VPA; move to a payment gateway in phase 2 |
| SMS OTP cost and DLT registration in India | Start with Supabase test numbers; use MSG91/Twilio with DLT templates before launch |
| Residents won't install another app | Admin-led onboarding help desk; phase-3 web/SMS fallback |
| iOS build friction | Compose Multiplatform shares 100% UI; only the host app is Swift |
