// Notiz: online payment for a due, via the society's own Razorpay account.
//
// POST { "due_id": "...", "action": "create" | "check" }   (called by the app with the user's token)
//   create → returns { status: "created", url } — a Razorpay Payment Link to open — or { status: "paid" }
//   check  → if the link was paid but the webhook hasn't arrived yet, records it now; returns { status }
//
// Only a member of the due's flat can pay it. The society's keys are read with the service role
// and never leave the server.
//
// Deploy:  supabase functions deploy create-payment-link
// Needs:   SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_SERVICE_ROLE_KEY (provided by Supabase)

import { db, HttpError, json, q, requireUser, rpc, select, serve } from "../_shared/supabase.ts";
import {
  createPaymentLink,
  fetchPayment,
  fetchPaymentLink,
  type PaymentLink,
  paymentReference,
  type RazorpayKeys,
} from "../_shared/razorpay.ts";

interface DueRow {
  id: string;
  society_id: string;
  flat_no: string;
  title: string;
  amount_paise: number;
  status: "pending" | "submitted" | "paid";
  payment_link_id: string | null;
  payment_link_url: string | null;
  societies: { name: string; online_payments_enabled: boolean } | null;
}

const LINK_LIFETIME_SECONDS = 30 * 24 * 3600;

/** Records a paid link against the due (idempotent). */
async function settle(keys: RazorpayKeys, due: DueRow, link: PaymentLink): Promise<void> {
  const captured = link.payments?.find((p) => p.status === "captured") ?? link.payments?.[0];
  if (!captured) return;
  const payment = await fetchPayment(keys, captured.payment_id).catch(() => undefined);
  await rpc<number>("record_online_payment", {
    p_due_id: due.id,
    p_link_id: link.id,
    p_payment_id: captured.payment_id,
    p_amount_paise: link.amount_paid,
    p_reference: paymentReference(payment, captured.payment_id),
  });
}

export async function handle(req: Request): Promise<Response> {
  if (req.method !== "POST") throw new HttpError(405, "method_not_allowed");
  const user = await requireUser(req.headers.get("Authorization"));
  const body = await req.json().catch(() => ({})) as { due_id?: string; action?: string };
  const action = body.action === "check" ? "check" : "create";
  if (!body.due_id) throw new HttpError(400, "due_id_required");

  const [due] = await select<DueRow>(
    `dues?id=eq.${q(body.due_id)}&select=id,society_id,flat_no,title,amount_paise,status,payment_link_id,payment_link_url,societies(name,online_payments_enabled)`,
  );
  if (!due) throw new HttpError(404, "due_not_found");

  const [member] = await select<{ flat_no: string }>(
    `memberships?society_id=eq.${q(due.society_id)}&user_id=eq.${q(user.id)}&status=eq.approved&select=flat_no`,
  );
  if (!member || member.flat_no !== due.flat_no) throw new HttpError(403, "not_your_flat");

  if (due.status === "paid") return json({ status: "paid" });

  const [keys] = await select<RazorpayKeys>(
    `payment_gateways?society_id=eq.${q(due.society_id)}&select=key_id,key_secret`,
  );

  // Existing link: reuse it, or settle it if it was paid and the webhook is late
  if (due.payment_link_id && keys) {
    const link = await fetchPaymentLink(keys, due.payment_link_id);
    if (link.status === "paid") {
      await settle(keys, due, link);
      return json({ status: "paid" });
    }
    if (action === "create" && link.status === "created" && due.status === "pending") {
      return json({ status: "created", url: link.short_url });
    }
  }
  if (action === "check") return json({ status: due.status });

  if (!keys || !due.societies?.online_payments_enabled) throw new HttpError(400, "online_payments_not_enabled");
  if (due.status !== "pending") throw new HttpError(409, "already_reported");

  const contact = user.phone ? `+${user.phone.replace(/^\+/, "")}` : undefined;
  const link = await createPaymentLink(keys, {
    amountPaise: due.amount_paise,
    description: `${due.title} - Flat ${due.flat_no} - ${due.societies.name}`,
    // Unique per link (Razorpay rejects reused ids), traceable to the due
    referenceId: `${due.id.replace(/-/g, "").slice(0, 30)}-${Date.now().toString(36)}`,
    notes: { due_id: due.id, society_id: due.society_id, flat_no: due.flat_no },
    customer: contact && contact.length >= 8 && contact.length <= 14 ? { contact } : undefined,
    expireBy: Math.floor(Date.now() / 1000) + LINK_LIFETIME_SECONDS,
  });

  await db(`dues?id=eq.${q(due.id)}`, {
    method: "PATCH",
    body: JSON.stringify({ payment_link_id: link.id, payment_link_url: link.short_url }),
  });
  return json({ status: "created", url: link.short_url });
}

if (import.meta.main) Deno.serve(serve(handle));
