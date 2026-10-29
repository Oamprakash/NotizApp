// Run:  deno test --allow-env supabase/functions/create-payment-link/index_test.ts

import { handle } from "./index.ts";
import { serve } from "../_shared/supabase.ts";
import { assert, assertEquals, FakeWorld, seed } from "../_shared/testing.ts";

const DUE = "11111111-2222-3333-4444-555555555555";
const call = (token: string | null, body: unknown) =>
  serve(handle)(
    new Request("http://x/create-payment-link", {
      method: "POST",
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: JSON.stringify(body),
    }),
  );

function world() {
  const w = new FakeWorld();
  seed(w);
  w.install();
  return w;
}

Deno.test("creates a link with the society's keys, saves it on the due, and reuses it", async () => {
  const w = world();
  try {
    const res = await call("tok-ravi", { due_id: DUE });
    assertEquals(res.status, 200);
    const out = await res.json();
    assertEquals(out, { status: "created", url: "https://rzp.io/rzp/plink_1" });

    const sent = w.createdLinkBodies[0] as Record<string, any>;
    assertEquals(sent.amount, 250000);
    assertEquals(sent.currency, "INR");
    assertEquals(sent.accept_partial, false);
    assertEquals(sent.notes.due_id, DUE);
    assertEquals(sent.customer, { contact: "+919999900002" });
    assert(sent.reference_id.length <= 40, "reference_id ≤ 40 chars");
    assertEquals(w.dues[0].payment_link_id, "plink_1");

    // Tapping again reuses the same open link instead of creating a new one
    const again = await (await call("tok-ravi", { due_id: DUE })).json();
    assertEquals(again.url, "https://rzp.io/rzp/plink_1");
    assertEquals(Object.keys(w.links).length, 1);
  } finally {
    w.uninstall();
  }
});

Deno.test("only the due's own flat can pay", async () => {
  const w = world();
  try {
    const res = await call("tok-other", { due_id: DUE });
    assertEquals(res.status, 403);
    assertEquals((await res.json()).error, "not_your_flat");
    assertEquals((await call(null, { due_id: DUE })).status, 401);
    assertEquals((await call("bad", { due_id: DUE })).status, 401);
  } finally {
    w.uninstall();
  }
});

Deno.test("refuses when online payments are off or the payment was reported manually", async () => {
  const w = world();
  try {
    w.societies["s1"].online_payments_enabled = false;
    const off = await call("tok-ravi", { due_id: DUE });
    assertEquals(off.status, 400);
    assertEquals((await off.json()).error, "online_payments_not_enabled");

    w.societies["s1"].online_payments_enabled = true;
    w.dues[0].status = "submitted";
    const reported = await call("tok-ravi", { due_id: DUE });
    assertEquals(reported.status, 409);
    assertEquals((await reported.json()).error, "already_reported");
  } finally {
    w.uninstall();
  }
});

Deno.test("check: a paid link is recorded even if the webhook never arrives", async () => {
  const w = world();
  try {
    await call("tok-ravi", { due_id: DUE });
    assertEquals((await (await call("tok-ravi", { due_id: DUE, action: "check" })).json()).status, "pending");

    w.pay("plink_1", "pay_77");
    const out = await (await call("tok-ravi", { due_id: DUE, action: "check" })).json();
    assertEquals(out.status, "paid");
    assertEquals(w.dues[0].status, "paid");
    assertEquals(w.dues[0].payment_mode, "online");
    assertEquals(w.dues[0].utr, "RRN-pay_77", "UTR is the bank RRN");
    assertEquals(w.dues[0].receipt_no, 1);

    // Asking again doesn't issue another receipt
    await call("tok-ravi", { due_id: DUE, action: "check" });
    assertEquals(w.nextReceipt, 2);
  } finally {
    w.uninstall();
  }
});
