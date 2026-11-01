// Notiz: delete my account (required by Google Play for apps with sign-up).
//
// POST (with the user's token). Refuses with 409 "last_admin" if the user is the only admin of a
// society that has other members — they must make someone else admin first. Otherwise deletes
// the Supabase Auth user, which cascades to their profile, memberships, device tokens and read
// receipts. Notices, messages and complaints they wrote stay for the society, unlinked.
//
// Deploy:  supabase functions deploy delete-account

import { HttpError, json, requireUser, rpc, serve } from "../_shared/supabase.ts";

export async function handle(req: Request): Promise<Response> {
  if (req.method !== "POST") throw new HttpError(405, "method_not_allowed");
  const user = await requireUser(req.headers.get("Authorization"));

  const blocker = await rpc<string | null>("account_deletion_blocker", { p_user: user.id });
  if (blocker) throw new HttpError(409, "last_admin", blocker);

  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const res = await fetch(`${Deno.env.get("SUPABASE_URL")}/auth/v1/admin/users/${encodeURIComponent(user.id)}`, {
    method: "DELETE",
    headers: { apikey: key, Authorization: `Bearer ${key}` },
  });
  if (!res.ok) throw new Error(`Couldn't delete user: ${res.status} ${await res.text()}`);
  return json({ deleted: true });
}

if (import.meta.main) Deno.serve(serve(handle));
