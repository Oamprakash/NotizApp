// Notiz push sender — Supabase Edge Function (Deno).
//
// Triggered by a Database Webhook on INSERT into public.notification_outbox.
// Looks up the recipients' device tokens and sends each one a notification through
// Firebase Cloud Messaging (HTTP v1), which delivers to both Android and iOS (via APNs).
//
// Secrets (supabase secrets set …):
//   FCM_SERVICE_ACCOUNT   the Firebase service-account JSON (whole file contents)
//   PUSH_WEBHOOK_SECRET   any long random string; the webhook must send it as `x-webhook-secret`
// Provided automatically by Supabase: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY
//
// Deploy:  supabase functions deploy push --no-verify-jwt

interface OutboxRow {
  id: number;
  user_ids: string[];
  title: string;
  body: string;
  data: Record<string, unknown>;
  sent_at: string | null;
}

interface WebhookPayload {
  type: "INSERT" | "UPDATE" | "DELETE";
  table: string;
  record: OutboxRow | null;
}

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

const ANDROID_CHANNEL_ID = "notiz_general";
const SEND_CONCURRENCY = 25;

// ─────────────────────────── Database (PostgREST with the service role) ───────────────────────────

async function db(path: string, init: RequestInit = {}): Promise<Response> {
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const res = await fetch(`${Deno.env.get("SUPABASE_URL")}/rest/v1/${path}`, {
    ...init,
    headers: {
      apikey: key,
      Authorization: `Bearer ${key}`,
      "Content-Type": "application/json",
      Prefer: "return=minimal",
      ...(init.headers ?? {}),
    },
  });
  if (!res.ok) throw new Error(`Database ${init.method ?? "GET"} ${path.split("?")[0]} failed: ${res.status} ${await res.text()}`);
  return res;
}

/** PostgREST `in` filter value: in.("a","b") — values quoted so ':' and ',' are safe. */
export function inList(values: string[]): string {
  return encodeURIComponent(`(${values.map((v) => `"${v.replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`).join(",")})`);
}

async function tokensFor(userIds: string[]): Promise<string[]> {
  const res = await db(`device_tokens?select=token&user_id=in.${inList(userIds)}`);
  const rows = await res.json() as { token: string }[];
  return rows.map((r) => r.token);
}

async function deleteTokens(tokens: string[]): Promise<void> {
  await db(`device_tokens?token=in.${inList(tokens)}`, { method: "DELETE" });
}

async function markSent(id: number, sentCount: number, error: string | null): Promise<void> {
  await db(`notification_outbox?id=eq.${id}`, {
    method: "PATCH",
    body: JSON.stringify({ sent_at: new Date().toISOString(), sent_count: sentCount, error }),
  });
}

// ─────────────────────────── Google OAuth (service account → access token) ───────────────────────────

let cachedToken: { value: string; expiresAt: number } | null = null;

function base64UrlEncode(input: ArrayBuffer | Uint8Array | string): string {
  const bytes = typeof input === "string"
    ? new TextEncoder().encode(input)
    : input instanceof Uint8Array ? input : new Uint8Array(input);
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const base64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\\n/g, "")
    .replace(/\s+/g, "");
  const der = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  return await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

export async function createSignedJwt(sa: ServiceAccount, nowSeconds: number): Promise<string> {
  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: nowSeconds,
    exp: nowSeconds + 3600,
  };
  const unsigned = `${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(claims))}`;
  const key = await importPrivateKey(sa.private_key);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  return `${unsigned}.${base64UrlEncode(signature)}`;
}

async function getAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.expiresAt - 60 > now) return cachedToken.value;

  const jwt = await createSignedJwt(sa, now);
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!res.ok) throw new Error(`Google OAuth failed: ${res.status} ${await res.text()}`);
  const json = await res.json() as { access_token: string; expires_in: number };
  cachedToken = { value: json.access_token, expiresAt: now + json.expires_in };
  return json.access_token;
}

// ─────────────────────────── FCM ───────────────────────────

/** FCM data values must all be strings. */
function stringifyData(data: Record<string, unknown>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(data ?? {})) {
    if (v !== null && v !== undefined) out[k] = typeof v === "string" ? v : JSON.stringify(v);
  }
  return out;
}

export function buildMessage(token: string, row: Pick<OutboxRow, "title" | "body" | "data">) {
  const data = stringifyData(row.data);
  // Group chat pushes per society so a busy chat doesn't flood the tray.
  const tag = data.type === "chat" && data.society_id ? `chat-${data.society_id}` : undefined;
  return {
    message: {
      token,
      notification: { title: row.title, body: row.body },
      data: { ...data, title: row.title, body: row.body } as Record<string, string>,
      android: {
        priority: "HIGH",
        notification: {
          channel_id: ANDROID_CHANNEL_ID,
          ...(tag ? { tag } : {}),
        },
      },
      apns: {
        headers: { "apns-priority": "10" },
        payload: { aps: { sound: "default", ...(tag ? { "thread-id": tag } : {}) } },
      },
    },
  };
}

type SendResult = "ok" | "dead" | "error";

async function sendOne(
  accessToken: string,
  projectId: string,
  token: string,
  row: OutboxRow,
): Promise<SendResult> {
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
    body: JSON.stringify(buildMessage(token, row)),
  });
  if (res.ok) return "ok";
  const text = await res.text();
  // Uninstalled app / expired token → remove it so we stop trying.
  if (res.status === 404 || text.includes("UNREGISTERED") ||
      (res.status === 400 && text.includes("registration token"))) {
    return "dead";
  }
  console.error(`FCM ${res.status} for token …${token.slice(-6)}: ${text}`);
  return "error";
}

async function inBatches<T, R>(items: T[], size: number, fn: (item: T) => Promise<R>): Promise<R[]> {
  const results: R[] = [];
  for (let i = 0; i < items.length; i += size) {
    results.push(...await Promise.all(items.slice(i, i + size).map(fn)));
  }
  return results;
}

// ─────────────────────────── Handler ───────────────────────────

export async function handle(req: Request): Promise<Response> {
  const secret = Deno.env.get("PUSH_WEBHOOK_SECRET");
  if (!secret || req.headers.get("x-webhook-secret") !== secret) {
    return new Response("Unauthorized", { status: 401 });
  }

  const payload = await req.json() as WebhookPayload;
  const row = payload.record;
  if (payload.type !== "INSERT" || !row || row.sent_at) {
    return Response.json({ skipped: true });
  }

  const saJson = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!saJson) return new Response("FCM_SERVICE_ACCOUNT secret is not set", { status: 500 });
  const sa = JSON.parse(saJson) as ServiceAccount;

  const list = row.user_ids.length > 0 ? await tokensFor(row.user_ids) : [];
  if (list.length === 0) {
    await markSent(row.id, 0, null);
    return Response.json({ sent: 0 });
  }

  const accessToken = await getAccessToken(sa);
  const results = await inBatches(list, SEND_CONCURRENCY, (t) => sendOne(accessToken, sa.project_id, t, row));

  const dead = list.filter((_, i) => results[i] === "dead");
  if (dead.length > 0) await deleteTokens(dead);

  const sent = results.filter((r) => r === "ok").length;
  const failed = results.filter((r) => r === "error").length;
  await markSent(row.id, sent, failed > 0 ? `${failed} failed` : null);

  return Response.json({ sent, dead: dead.length, failed });
}

if (import.meta.main) {
  Deno.serve(async (req) => {
    try {
      return await handle(req);
    } catch (e) {
      console.error(e);
      return new Response(String(e instanceof Error ? e.message : e), { status: 500 });
    }
  });
}
