// Run:  deno test --allow-env supabase/functions/push/index_test.ts
// Exercises the whole handler with fetch() stubbed: database, Google OAuth and FCM.

import { buildMessage, createSignedJwt, handle, inList } from "./index.ts";

function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) throw new Error(msg);
}
function assertEquals<T>(actual: T, expected: T, msg = "") {
  const a = JSON.stringify(actual), e = JSON.stringify(expected);
  if (a !== e) throw new Error(`${msg}\n  expected: ${e}\n  actual:   ${a}`);
}

async function makeServiceAccount() {
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  ) as CryptoKeyPair;
  const pkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
  let bin = "";
  for (const b of pkcs8) bin += String.fromCharCode(b);
  const b64 = btoa(bin).match(/.{1,64}/g)!.join("\n");
  return {
    publicKey: pair.publicKey,
    sa: {
      project_id: "notiz-test",
      client_email: "push@notiz-test.iam.gserviceaccount.com",
      // Same shape as the downloaded JSON: literal newlines inside the PEM
      private_key: `-----BEGIN PRIVATE KEY-----\n${b64}\n-----END PRIVATE KEY-----\n`,
    },
  };
}

function b64urlDecode(s: string): Uint8Array<ArrayBuffer> {
  const b64 = s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4);
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}

Deno.test("JWT is RS256-signed with the service account key and has the FCM scope", async () => {
  const { sa, publicKey } = await makeServiceAccount();
  const jwt = await createSignedJwt(sa, 1_800_000_000);
  const [h, c, sig] = jwt.split(".");
  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5", publicKey, b64urlDecode(sig), new TextEncoder().encode(`${h}.${c}`),
  );
  assert(ok, "signature should verify");
  const claims = JSON.parse(new TextDecoder().decode(b64urlDecode(c)));
  assertEquals(claims.scope, "https://www.googleapis.com/auth/firebase.messaging");
  assertEquals(claims.iss, sa.client_email);
  assertEquals(claims.exp - claims.iat, 3600);
});

Deno.test("message carries title/body, string data, Android channel and chat grouping", () => {
  const m = buildMessage("tok", {
    title: "Ravi · B-202",
    body: "Hello",
    data: { type: "chat", society_id: "s1", count: 3 },
  }).message;
  assertEquals(m.notification, { title: "Ravi · B-202", body: "Hello" });
  assertEquals(m.data.count, "3", "data values must be strings");
  assertEquals(m.android.notification.channel_id, "notiz_general");
  assertEquals((m.android.notification as { tag?: string }).tag, "chat-s1");
  assertEquals((m.apns.payload.aps as { "thread-id"?: string })["thread-id"], "chat-s1");
});

Deno.test("in-list quoting survives FCM token characters", () => {
  assertEquals(decodeURIComponent(inList(["a:b_c-d", "x"])), `("a:b_c-d","x")`);
});

Deno.test("handler: rejects calls without the webhook secret", async () => {
  Deno.env.set("PUSH_WEBHOOK_SECRET", "s3cret");
  const res = await handle(new Request("http://x", { method: "POST", body: "{}" }));
  assertEquals(res.status, 401);
});

Deno.test("handler: sends to every token, deletes dead ones, marks the outbox row", async () => {
  const { sa } = await makeServiceAccount();
  Deno.env.set("PUSH_WEBHOOK_SECRET", "s3cret");
  Deno.env.set("FCM_SERVICE_ACCOUNT", JSON.stringify(sa));
  Deno.env.set("SUPABASE_URL", "https://proj.supabase.co");
  Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", "service-key");

  const calls: { method: string; url: string; body?: string; auth?: string }[] = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? "GET";
    const headers = new Headers(init?.headers);
    calls.push({ method, url, body: init?.body as string | undefined, auth: headers.get("Authorization") ?? undefined });

    if (url.startsWith("https://proj.supabase.co/rest/v1/device_tokens") && method === "GET") {
      return Response.json([{ token: "good-token" }, { token: "dead-token" }]);
    }
    if (url === "https://oauth2.googleapis.com/token") {
      return Response.json({ access_token: "ya29.fake", expires_in: 3600 });
    }
    if (url === "https://fcm.googleapis.com/v1/projects/notiz-test/messages:send") {
      const token = JSON.parse(init!.body as string).message.token;
      return token === "dead-token"
        ? new Response(JSON.stringify({ error: { status: "NOT_FOUND", details: [{ errorCode: "UNREGISTERED" }] } }), { status: 404 })
        : Response.json({ name: "projects/notiz-test/messages/1" });
    }
    if (url.startsWith("https://proj.supabase.co/rest/v1/")) return new Response(null, { status: 204 });
    throw new Error(`unexpected fetch ${method} ${url}`);
  }) as typeof fetch;

  try {
    const res = await handle(new Request("http://x", {
      method: "POST",
      headers: { "x-webhook-secret": "s3cret" },
      body: JSON.stringify({
        type: "INSERT",
        table: "notification_outbox",
        record: { id: 42, user_ids: ["u1", "u2"], title: "📢 Water cut", body: "Sunday", data: { type: "notice" }, sent_at: null },
      }),
    }));
    assertEquals(res.status, 200);
    assertEquals(await res.json(), { sent: 1, dead: 1, failed: 0 });

    const lookup = calls.find((c) => c.method === "GET" && c.url.includes("device_tokens"))!;
    assert(decodeURIComponent(lookup.url).includes(`user_id=in.("u1","u2")`), "looks up both recipients");
    assertEquals(lookup.auth, "Bearer service-key");

    const sends = calls.filter((c) => c.url.includes("fcm.googleapis.com"));
    assertEquals(sends.length, 2);
    assertEquals(sends[0].auth, "Bearer ya29.fake");

    const del = calls.find((c) => c.method === "DELETE")!;
    assert(decodeURIComponent(del.url).includes(`token=in.("dead-token")`), "deletes only the dead token");

    const mark = calls.find((c) => c.method === "PATCH")!;
    assert(mark.url.endsWith("notification_outbox?id=eq.42"), "marks the right outbox row");
    const marked = JSON.parse(mark.body!);
    assertEquals(marked.sent_count, 1);
    assertEquals(marked.error, null);
  } finally {
    globalThis.fetch = realFetch;
  }
});

Deno.test("handler: no tokens → marks sent with 0 and never calls Google", async () => {
  const { sa } = await makeServiceAccount();
  Deno.env.set("FCM_SERVICE_ACCOUNT", JSON.stringify(sa));
  const urls: string[] = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    urls.push(`${init?.method ?? "GET"} ${String(input)}`);
    if ((init?.method ?? "GET") === "GET") return Response.json([]);
    return new Response(null, { status: 204 });
  }) as typeof fetch;
  try {
    const res = await handle(new Request("http://x", {
      method: "POST",
      headers: { "x-webhook-secret": "s3cret" },
      body: JSON.stringify({ type: "INSERT", record: { id: 7, user_ids: ["u9"], title: "t", body: "b", data: {}, sent_at: null } }),
    }));
    assertEquals(await res.json(), { sent: 0 });
    assert(!urls.some((u) => u.includes("googleapis")), "no Google calls");
    assert(urls.some((u) => u.startsWith("PATCH") && u.endsWith("id=eq.7")), "outbox row marked");
  } finally {
    globalThis.fetch = realFetch;
  }
});
