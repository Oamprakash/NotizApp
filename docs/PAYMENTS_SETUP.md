# Online payments (Razorpay) — setup

Residents tap **Pay online** on a due, pay on Razorpay's page (UPI, cards, net banking), and the due is marked paid with a numbered receipt automatically. No UTR typing, no treasurer verification.

Each society uses **its own Razorpay account**: money goes straight to the society's bank account and Notiz never holds funds. Societies that don't set this up keep the existing flow (UPI app + "Already paid" + admin verification).

## How it works

```
Resident taps "Pay online"
   └─▶ Edge Function create-payment-link  (checks the resident's flat, reads the society's keys)
          └─▶ Razorpay: create Payment Link ─▶ app opens the link ─▶ resident pays
Razorpay ─▶ webhook ─▶ Edge Function razorpay-webhook  (verifies signature with the society's secret)
          └─▶ record_online_payment ─▶ due = paid, mode "online", RRN as reference, receipt number
                                       └─▶ push: "Payment confirmed ✅"
Backup: when the resident returns to the app, it asks create-payment-link to "check" the link and
records the payment if the webhook hasn't arrived.
```

The key secret is stored in `payment_gateways`, which no app user can read; only the Edge Functions can (service role). Admins see the Key ID and webhook secret, never the key secret.

## 1. Developer: deploy (once)

1. Run `supabase/migrations/20261029200000_razorpay.sql` in the Supabase SQL Editor (after the earlier migrations).
2. Deploy the two functions from the repo root:

```bash
supabase functions deploy create-payment-link
supabase functions deploy razorpay-webhook --no-verify-jwt
```

`razorpay-webhook` must skip JWT checks because Razorpay calls it; it authenticates every call with the HMAC signature instead. No secrets need setting: each society's keys live in the database.

Or deploy them in the dashboard: **Edge Functions → Deploy a new function → Via Editor**. Paste each `index.ts`, plus the files from `supabase/functions/_shared/` they import. For `razorpay-webhook`, turn off "Enforce JWT verification".

## 2. Society: Razorpay account (the society's treasurer or secretary)

1. Sign up at [razorpay.com](https://razorpay.com) **in the society's name**. KYC usually needs the society registration certificate, the society's PAN, and a bank account in the society's name. Activation can take a few days.
2. While waiting, everything can be tried with **test keys** (`rzp_test_…`): payments are simulated and no money moves.

## 3. Society admin: connect it in Notiz

In the app: **Dues → Set up online payments**.

1. **Razorpay API keys**: Razorpay Dashboard → Account & Settings → API Keys → Generate key. Paste the **Key ID** and **Key Secret**, then tap **Save keys**.
2. **Webhook**: Razorpay Dashboard → Account & Settings → Webhooks → **Add new webhook**:
   - Webhook URL: copy it from the app (it ends with `?society=<id>`)
   - Secret: copy it from the app
   - Active events: tick **payment_link.paid**
   - Save
3. **Turn on** "Accept online payments". Residents now see **Pay online** on their dues.

Test with test keys first: pay a due using Razorpay's test UPI/card details. The due should turn **Paid**, with mode "Online (Razorpay)" and a receipt. Then save the live keys (`rzp_live_…`), add the same webhook in live mode, and you're done.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| "Razorpay rejected the API keys" | Key ID and Key Secret are from the same mode (test/live) and were copied completely. |
| Paid on Razorpay but still "Unpaid" | Reopen the Dues tab (the app re-checks the link). If it stays unpaid: Razorpay → Webhooks → the webhook's delivery log, and Supabase → Edge Functions → razorpay-webhook → Logs. A 401 means the webhook secret doesn't match. |
| "Online payments aren't turned on" | Step 3 switch is off, or keys were removed. |
| Wrong amount paid | Links are created for the exact due amount with partial payments off; a short payment is logged and not recorded. |

## Notes

- Razorpay deducts its fee per payment from the amount collected; check Razorpay's current pricing for UPI, cards and net banking.
- Payment links expire after 30 days; tapping **Pay online** again creates a new one.
- Refunds are done in the Razorpay Dashboard. Notiz doesn't reverse receipts automatically yet.
