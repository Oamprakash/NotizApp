// Run:  deno test --allow-env supabase/functions/razorpay-webhook/index_test.ts

import { handle } from "./index.ts";
import { serve } from "../_shared/supabase.ts";
import { webhookSignature } from "../_shared/razorpay.ts";
import { assertEquals, FakeWorld, seed } from "../_shared/testing.ts";

const DUE = "11111111-2222-3333-4444-555555555555";
const SOCIETY = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

function world() {
  const w = new FakeWorld();
  seed(w);
  // Use a UUID-shaped society id here, as the webhook URL requires
  w.societies[SOCIETY] = w.societies["s1"];
  w.gateways[SOCIETY] = w.gateways["s1"];
  w.dues[0].society_id = SOCIETY;
  w.dues[0].payment_link_id = "plink_9";
  w.links["plink_9"] = {
    id: "plink_9", short_url: "https://rzp.io/rzp/plink_9", status: "paid", amount: 250000, amount_paid: 250000,
    reference_id: "r", notes: {}, payments: [{ payment_id: "pay_Q1", amount: 250000, status: "captured" }],
  };
  w.install();
  return w;
}

function paidEvent(amountPaid = 250000, linkId = "plink_9") {
  return JSON.stringify({
    entity: "event",
    event: "payment_link.paid",
    payload: {
      payment_link: { entity: { id: linkId, amount: 250000, amount_paid: amountPaid, status: "paid" } },
      payment: { entity: { id: "pay_Q1", amount: amountPaid, status: "captured", method: "upi", acquirer_data: { rrn: "103608848276" } } },
    },
  });
}

async function deliver(raw: string, opts: { secret?: string; eventId?: string; society?: string } = {}) {
  const sig = await webhookSignature(opts.secret ?? "whsec123", raw);
  return serve(handle)(
    new Request(`http://x/razorpay-webhook?society=${opts.society ?? SOCIETY}`, {
      method: "POST",
      headers: { "x-razorpay-signature": sig, ...(opts.eventId ? { "x-razorpay-event-id": opts.eventId } : {}) },
      body: raw,
    }),
  );
}

Deno.test("signature matches the known HMAC-SHA256 test vector", async () => {
  // RFC 4231 test case 2
  assertEquals(
    await webhookSignature("Jefe", "what do ya want for nothing?"),
    "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
  );
});

Deno.test("valid payment_link.paid marks the due paid with the RRN and a receipt", async () => {
  const w = world();
  try {
    const res = await deliver(paidEvent(), { eventId: "evt_1" });
    assertEquals(res.status, 200);
    assertEquals(await res.json(), { ok: true, receipt_no: 1 });
    assertEquals(w.dues[0].status, "paid");
    assertEquals(w.dues[0].payment_mode, "online");
    assertEquals(w.dues[0].utr, "103608848276");
    assertEquals(w.dues[0].gateway_payment_id, "pay_Q1");
  } finally {
    w.uninstall();
  }
});

Deno.test("rejects a wrong signature and changes nothing", async () => {
  const w = world();
  try {
    const res = await deliver(paidEvent(), { secret: "not-the-secret" });
    assertEquals(res.status, 401);
    assertEquals(w.dues[0].status, "pending");
  } finally {
    w.uninstall();
  }
});

Deno.test("a repeated delivery is ignored", async () => {
  const w = world();
  try {
    await deliver(paidEvent(), { eventId: "evt_dup" });
    const second = await deliver(paidEvent(), { eventId: "evt_dup" });
    assertEquals(await second.json(), { ok: true, duplicate: true });
    assertEquals(w.nextReceipt, 2, "only one receipt issued");
  } finally {
    w.uninstall();
  }
});

Deno.test("short payment is acknowledged but not recorded", async () => {
  const w = world();
  try {
    const res = await deliver(paidEvent(100), { eventId: "evt_short" });
    assertEquals(res.status, 200);
    assertEquals((await res.json()).not_recorded?.includes("less than"), true);
    assertEquals(w.dues[0].status, "pending");
  } finally {
    w.uninstall();
  }
});

Deno.test("other events and unknown links are acknowledged", async () => {
  const w = world();
  try {
    const other = await deliver(JSON.stringify({ event: "payment_link.expired", payload: {} }));
    assertEquals(await other.json(), { ok: true, ignored: "payment_link.expired" });
    const unknown = await deliver(paidEvent(250000, "plink_unknown"));
    assertEquals((await unknown.json()).unknown_link, "plink_unknown");
  } finally {
    w.uninstall();
  }
});

Deno.test("society without saved keys → 404; malformed society id → 400", async () => {
  const w = world();
  try {
    assertEquals((await deliver(paidEvent(), { society: "11111111-1111-1111-1111-111111111111" })).status, 404);
    assertEquals((await deliver(paidEvent(), { society: "drop-table" })).status, 400);
  } finally {
    w.uninstall();
  }
});
