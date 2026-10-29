// Helpers for Edge Functions: database access with the service role (PostgREST) and
// resolving the signed-in user from their access token. No npm dependencies.

export class HttpError extends Error {
  constructor(public status: number, public code: string, message?: string) {
    super(message ?? code);
  }
}

function env(name: string): string {
  const v = Deno.env.get(name);
  if (!v) throw new Error(`${name} is not set`);
  return v;
}

/** Calls PostgREST with the service role (bypasses RLS — check permissions yourself). */
export async function db(path: string, init: RequestInit = {}): Promise<Response> {
  const key = env("SUPABASE_SERVICE_ROLE_KEY");
  const res = await fetch(`${env("SUPABASE_URL")}/rest/v1/${path}`, {
    ...init,
    headers: {
      apikey: key,
      Authorization: `Bearer ${key}`,
      "Content-Type": "application/json",
      Prefer: "return=minimal",
      ...(init.headers ?? {}),
    },
  });
  if (!res.ok) {
    throw new Error(`Database ${init.method ?? "GET"} ${path.split("?")[0]} failed: ${res.status} ${await res.text()}`);
  }
  return res;
}

/** GET rows as JSON. */
export async function select<T>(path: string): Promise<T[]> {
  return await (await db(path)).json() as T[];
}

/** Calls a database function and returns its JSON result. */
export async function rpc<T>(name: string, args: Record<string, unknown>): Promise<T> {
  const res = await db(`rpc/${name}`, { method: "POST", body: JSON.stringify(args), headers: { Prefer: "" } });
  return await res.json() as T;
}

export interface AuthUser {
  id: string;
  phone?: string;
}

/** The user behind an `Authorization: Bearer <access token>` header, or a 401. */
export async function requireUser(authorization: string | null): Promise<AuthUser> {
  if (!authorization?.startsWith("Bearer ")) throw new HttpError(401, "not_signed_in");
  const res = await fetch(`${env("SUPABASE_URL")}/auth/v1/user`, {
    headers: { apikey: env("SUPABASE_ANON_KEY"), Authorization: authorization },
  });
  if (!res.ok) throw new HttpError(401, "not_signed_in");
  return await res.json() as AuthUser;
}

/** `in.("a","b")` / `eq.x` values are URL-encoded so ':' ',' '"' are safe. */
export const q = (value: string) => encodeURIComponent(value);

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

/** Wraps a handler: HttpError → JSON error body; anything else → 500 (logged). */
export function serve(handler: (req: Request) => Promise<Response>) {
  return async (req: Request): Promise<Response> => {
    try {
      return await handler(req);
    } catch (e) {
      if (e instanceof HttpError) return json({ error: e.code, message: e.message }, e.status);
      console.error(e);
      return json({ error: "server_error", message: e instanceof Error ? e.message : String(e) }, 500);
    }
  };
}
