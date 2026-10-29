// Notiz: Razorpay webhook — marks dues paid when a Payment Link is paid.
//
// Each society adds this webhook in its own Razorpay dashboard:
//   URL:    https://<project>.supabase.co/functions/v1/razorpay-webhook?society=<society id>
//   Secret: the webhook secret shown in Notiz → Dues → Online payments
//   Event:  payment_link.paid
//
// The signature (X-Razorpay-Signature = HMAC-SHA256 of the raw body) is checked with that
// society's secret before anything is trusted. Repeated deliveries are ignored using
// X-Razorpay-Event-Id.
//
// Deploy:  supabase functions deploy razorpay-webhook --no-verify-jwt

import { db, HttpError, json, q, rpc, select, serve } from "../_shared/supabase.ts";
import { fetchPayment, type Payment, paymentReference, timingSafeEqual, webhookSignature } from "../_shared/razorpay.ts";

interface Gateway {
  key_id: string;
  key_secret: string;
  webhook_secret: string;
}

interface LinkPaidEvent {
  event: string;
  payload?: {
    payment_link?: { entity?: { id: string; amount_paid: number; payments?: { payment_id: string }[] | null } };
    payment?: { entity?: Payment };
  };
}

/** Errors that a retry can't fix (wrong amount, unknown link): acknowledge so Razorpay stops retrying. */
const PERMANENT = ["does not match", "less than the due", "Due not found"];

export async function handle(req: Request): Promise<Response> {
  if (req.method !== "POST") throw new HttpError(405, "method_not_allowed");
  const societyId = new URL(req.url).searchParams.get("society");
  if (!societyId || !/^[0-9a-f-]{36}$/i.test(societyId)) throw new HttpError(400, "society_required");

  const raw = await req.text(); // the signature covers the exact bytes — don't re-serialise
  const [gateway] = await select<Gateway>(
    `payment_gateways?society_id=eq.${q(societyId)}&select=key_id,key_secret,webhook_secret`,
  );
  if (!gateway) throw new HttpError(404, "not_configured");

  const expected = await webhookSignature(gateway.webhook_secret, raw);
  if (!timingSafeEqual(expected, req.headers.get("x-razorpay-signature") ?? "")) {
    throw new HttpError(401, "bad_signature");
  }

  const eventId = req.headers.get("x-razorpay-event-id");
  if (eventId) {
    const seen = await select<{ event_id: string }>(`gateway_events?event_id=eq.${q(eventId)}&select=event_id`);
    if (seen.length > 0) return json({ ok: true, duplicate: true });
  }

  const event = JSON.parse(raw) as LinkPaidEvent;
  let result: Record<string, unknown> = { ok: true, ignored: event.event };

  if (event.event === "payment_link.paid") {
    const link = event.payload?.payment_link?.entity;
    const payment = event.payload?.payment?.entity;
    const paymentId = payment?.id ?? link?.payments?.[0]?.payment_id;
    if (!link || !paymentId) throw new HttpError(400, "bad_payload");

    const [due] = await select<{ id: string }>(
      `dues?payment_link_id=eq.${q(link.id)}&society_id=eq.${q(societyId)}&select=id`,
    );
    if (!due) {
      console.warn(`payment_link.paid for unknown link ${link.id} (society ${societyId})`);
      result = { ok: true, unknown_link: link.id };
    } else {
      // The webhook may omit acquirer data; fetch the payment for the bank reference (RRN).
      const full = payment?.acquirer_data ? payment : await fetchPayment(gateway, paymentId).catch(() => payment);
      try {
        const receipt = await rpc<number>("record_online_payment", {
          p_due_id: due.id,
          p_link_id: link.id,
          p_payment_id: paymentId,
          p_amount_paise: link.amount_paid,
          p_reference: paymentReference(full, paymentId),
        });
        result = { ok: true, receipt_no: receipt };
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        if (!PERMANENT.some((p) => msg.includes(p))) throw e; // temporary: let Razorpay retry
        console.error(`Not recorded: ${msg}`);
        result = { ok: true, not_recorded: msg };
      }
    }
  }

  if (eventId) {
    await db("gateway_events?on_conflict=event_id", {
      method: "POST",
      body: JSON.stringify({ event_id: eventId, society_id: societyId }),
      headers: { Prefer: "resolution=ignore-duplicates,return=minimal" },
    });
  }
  return json(result);
}

if (import.meta.main) Deno.serve(serve(handle));
