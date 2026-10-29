// Test helper: an in-memory stand-in for Supabase (auth + PostgREST) and Razorpay, installed by
// replacing globalThis.fetch. Folders starting with "_" are not deployed.

export interface Due {
  id: string;
  society_id: string;
  flat_no: string;
  title: string;
  amount_paise: number;
  status: string;
  payment_link_id: string | null;
  payment_link_url: string | null;
  receipt_no?: number | null;
  payment_mode?: string | null;
  utr?: string | null;
  gateway_payment_id?: string | null;
}

export interface Link {
  id: string;
  short_url: string;
  status: string;
  amount: number;
  amount_paid: number;
  reference_id: string;
  notes: Record<string, string>;
  payments: { payment_id: string; amount: number; status: string }[] | null;
}

export class FakeWorld {
  users: Record<string, { id: string; phone?: string }> = {};
  societies: Record<string, { name: string; online_payments_enabled: boolean }> = {};
  memberships: { society_id: string; user_id: string; flat_no: string; status: string }[] = [];
  gateways: Record<string, { key_id: string; key_secret: string; webhook_secret: string }> = {};
  dues: Due[] = [];
  links: Record<string, Link> = {};
  events = new Set<string>();
  nextReceipt = 1;
  calls: string[] = [];
  createdLinkBodies: Record<string, unknown>[] = [];
  private realFetch = globalThis.fetch;

  install() {
    Deno.env.set("SUPABASE_URL", "https://proj.supabase.co");
    Deno.env.set("SUPABASE_ANON_KEY", "anon");
    Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", "service");
    globalThis.fetch = ((input: string | URL | Request, init?: RequestInit) =>
      Promise.resolve(this.route(String(input), init ?? {}))) as typeof fetch;
  }

  uninstall() {
    globalThis.fetch = this.realFetch;
  }

  /** Mirrors public.record_online_payment in the razorpay migration. */
  recordOnlinePayment(a: { p_due_id: string; p_link_id: string; p_payment_id: string; p_amount_paise: number; p_reference: string }) {
    const d = this.dues.find((x) => x.id === a.p_due_id);
    if (!d) return error(400, "Due not found");
    if (d.payment_link_id !== a.p_link_id) return error(400, "Payment link does not match this due");
    if (d.status === "paid") return Response.json(d.receipt_no);
    if (a.p_amount_paise < d.amount_paise) return error(400, "Paid amount is less than the due");
    Object.assign(d, {
      status: "paid",
      receipt_no: this.nextReceipt++,
      payment_mode: "online",
      utr: a.p_reference,
      gateway_payment_id: a.p_payment_id,
    });
    return Response.json(d.receipt_no);
  }

  private route(url: string, init: RequestInit): Response {
    const method = init.method ?? "GET";
    const u = new URL(url);
    this.calls.push(`${method} ${u.hostname}${u.pathname}`);
    const headers = new Headers(init.headers);

    if (u.hostname === "proj.supabase.co") {
      if (u.pathname === "/auth/v1/user") {
        const token = headers.get("Authorization")?.replace("Bearer ", "") ?? "";
        const user = this.users[token];
        return user ? Response.json(user) : error(401, "bad token");
      }
      if (headers.get("Authorization") !== "Bearer service") return error(401, "service role required");
      const table = u.pathname.replace("/rest/v1/", "");
      const f = (k: string) => {
        const v = u.searchParams.get(k);
        return v?.startsWith("eq.") ? v.slice(3) : undefined;
      };
      const body = init.body ? JSON.parse(String(init.body)) : undefined;

      if (table === "rpc/record_online_payment") return this.recordOnlinePayment(body);
      if (table === "dues" && method === "GET") {
        const rows = this.dues.filter((d) =>
          (!f("id") || d.id === f("id")) &&
          (!f("payment_link_id") || d.payment_link_id === f("payment_link_id")) &&
          (!f("society_id") || d.society_id === f("society_id"))
        ).map((d) => ({ ...d, societies: this.societies[d.society_id] ?? null }));
        return Response.json(rows);
      }
      if (table === "dues" && method === "PATCH") {
        const d = this.dues.find((x) => x.id === f("id"));
        if (d) Object.assign(d, body);
        return new Response(null, { status: 204 });
      }
      if (table === "memberships") {
        return Response.json(this.memberships.filter((m) =>
          m.society_id === f("society_id") && m.user_id === f("user_id") && m.status === f("status")
        ));
      }
      if (table === "payment_gateways") {
        const g = this.gateways[f("society_id") ?? ""];
        return Response.json(g ? [g] : []);
      }
      if (table === "gateway_events" && method === "GET") {
        return Response.json(this.events.has(f("event_id") ?? "") ? [{ event_id: f("event_id") }] : []);
      }
      if (table === "gateway_events" && method === "POST") {
        this.events.add(body.event_id);
        return new Response(null, { status: 201 });
      }
      return error(500, `unhandled ${method} ${table}`);
    }

    if (u.hostname === "api.razorpay.com") {
      const auth = headers.get("Authorization") ?? "";
      const [keyId, keySecret] = atob(auth.replace("Basic ", "")).split(":");
      const ok = Object.values(this.gateways).some((g) => g.key_id === keyId && g.key_secret === keySecret);
      if (!ok) return error(401, "Authentication failed");
      if (u.pathname === "/v1/payment_links" && method === "POST") {
        const b = JSON.parse(String(init.body));
        this.createdLinkBodies.push(b);
        if (Object.values(this.links).some((l) => l.reference_id === b.reference_id)) {
          return error(400, "reference_id already exists");
        }
        const id = `plink_${Object.keys(this.links).length + 1}`;
        this.links[id] = {
          id, short_url: `https://rzp.io/rzp/${id}`, status: "created", amount: b.amount, amount_paid: 0,
          reference_id: b.reference_id, notes: b.notes, payments: null,
        };
        return Response.json(this.links[id]);
      }
      const linkMatch = u.pathname.match(/^\/v1\/payment_links\/(.+)$/);
      if (linkMatch && method === "GET") {
        const l = this.links[linkMatch[1]];
        return l ? Response.json(l) : error(404, "not found");
      }
      const payMatch = u.pathname.match(/^\/v1\/payments\/(.+)$/);
      if (payMatch) {
        return Response.json({ id: payMatch[1], amount: 0, status: "captured", method: "upi", acquirer_data: { rrn: "RRN-" + payMatch[1] } });
      }
    }
    return error(500, `unexpected ${method} ${url}`);
  }

  /** Simulates the resident paying on Razorpay's page. */
  pay(linkId: string, paymentId = "pay_1") {
    const l = this.links[linkId];
    l.status = "paid";
    l.amount_paid = l.amount;
    l.payments = [{ payment_id: paymentId, amount: l.amount, status: "captured" }];
  }
}

function error(status: number, message: string) {
  return new Response(JSON.stringify({ error: { description: message }, message }), { status });
}

export function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) throw new Error(msg);
}

export function assertEquals<T>(actual: T, expected: T, msg = "") {
  const a = JSON.stringify(actual), e = JSON.stringify(expected);
  if (a !== e) throw new Error(`${msg}\n  expected: ${e}\n  actual:   ${a}`);
}

export function seed(world: FakeWorld) {
  world.users["tok-ravi"] = { id: "u-ravi", phone: "919999900002" };
  world.users["tok-other"] = { id: "u-other" };
  world.societies["s1"] = { name: "Green Park", online_payments_enabled: true };
  world.memberships.push(
    { society_id: "s1", user_id: "u-ravi", flat_no: "B-202", status: "approved" },
    { society_id: "s1", user_id: "u-other", flat_no: "A-101", status: "approved" },
  );
  world.gateways["s1"] = { key_id: "rzp_test_ABC", key_secret: "sekret", webhook_secret: "whsec123" };
  world.dues.push({
    id: "11111111-2222-3333-4444-555555555555", society_id: "s1", flat_no: "B-202", title: "Nov maintenance",
    amount_paise: 250000, status: "pending", payment_link_id: null, payment_link_url: null,
  });
}
