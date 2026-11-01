// Run:  deno test --allow-env supabase/functions/delete-account/index_test.ts

import { handle } from "./index.ts";
import { serve } from "../_shared/supabase.ts";
import { assertEquals } from "../_shared/testing.ts";

function fakeSupabase(blocker: string | null) {
  Deno.env.set("SUPABASE_URL", "https://proj.supabase.co");
  Deno.env.set("SUPABASE_ANON_KEY", "anon");
  Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", "service");
  const deleted: string[] = [];
  const real = globalThis.fetch;
  globalThis.fetch = ((input: string | URL | Request, init?: RequestInit) => {
    const url = new URL(String(input));
    const auth = new Headers(init?.headers).get("Authorization");
    if (url.pathname === "/auth/v1/user") {
      return Promise.resolve(auth === "Bearer tok-ravi" ? Response.json({ id: "u-ravi" }) : new Response("no", { status: 401 }));
    }
    if (url.pathname === "/rest/v1/rpc/account_deletion_blocker" && auth === "Bearer service") {
      return Promise.resolve(Response.json(blocker));
    }
    if (url.pathname.startsWith("/auth/v1/admin/users/") && init?.method === "DELETE" && auth === "Bearer service") {
      deleted.push(decodeURIComponent(url.pathname.split("/").pop()!));
      return Promise.resolve(Response.json({}));
    }
    return Promise.resolve(new Response(`unexpected ${init?.method} ${url}`, { status: 500 }));
  }) as typeof fetch;
  return { deleted, restore: () => (globalThis.fetch = real) };
}

const call = (token?: string) =>
  serve(handle)(new Request("http://x", { method: "POST", headers: token ? { Authorization: `Bearer ${token}` } : {} }));

Deno.test("deletes the signed-in user", async () => {
  const f = fakeSupabase(null);
  try {
    const res = await call("tok-ravi");
    assertEquals(res.status, 200);
    assertEquals(f.deleted, ["u-ravi"]);
  } finally {
    f.restore();
  }
});

Deno.test("refuses for the last admin of a society, naming it", async () => {
  const f = fakeSupabase("Green Park");
  try {
    const res = await call("tok-ravi");
    assertEquals(res.status, 409);
    assertEquals(await res.json(), { error: "last_admin", message: "Green Park" });
    assertEquals(f.deleted, []);
  } finally {
    f.restore();
  }
});

Deno.test("requires sign-in", async () => {
  const f = fakeSupabase(null);
  try {
    assertEquals((await call()).status, 401);
    assertEquals((await call("bad")).status, 401);
    assertEquals(f.deleted, []);
  } finally {
    f.restore();
  }
});
