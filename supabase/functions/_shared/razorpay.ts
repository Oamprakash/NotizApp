// Minimal Razorpay client: Payment Links, payments, webhook signatures. No npm dependencies.

const API = "https://api.razorpay.com/v1";

export interface RazorpayKeys {
  key_id: string;
  key_secret: string;
}

export interface PaymentLink {
  id: string;
  short_url: string;
  status: "created" | "partially_paid" | "expired" | "cancelled" | "paid";
  amount: number;
  amount_paid: number;
  reference_id?: string;
  payments?: { payment_id: string; amount: number; status: string; method?: string }[] | null;
}

export interface Payment {
  id: string;
  amount: number;
  status: string;
  method?: string;
  vpa?: string;
  acquirer_data?: { rrn?: string; upi_transaction_id?: string; bank_transaction_id?: string };
}

function auth(keys: RazorpayKeys) {
  return `Basic ${btoa(`${keys.key_id}:${keys.key_secret}`)}`;
}

async function call<T>(keys: RazorpayKeys, path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API}${path}`, {
    ...init,
    headers: { Authorization: auth(keys), "Content-Type": "application/json", ...(init.headers ?? {}) },
  });
  const text = await res.text();
  if (!res.ok) {
    let message = text;
    try {
      message = JSON.parse(text)?.error?.description ?? text;
    } catch { /* not JSON */ }
    throw new RazorpayError(res.status, message);
  }
  return JSON.parse(text) as T;
}

export class RazorpayError extends Error {
  constructor(public status: number, message: string) {
    super(`Razorpay ${status}: ${message}`);
  }
}

export interface CreateLinkInput {
  amountPaise: number;
  description: string;
  referenceId: string;
  notes: Record<string, string>;
  customer?: { name?: string; contact?: string };
  expireBy: number; // unix seconds
}

export function createPaymentLink(keys: RazorpayKeys, input: CreateLinkInput): Promise<PaymentLink> {
  return call<PaymentLink>(keys, "/payment_links", {
    method: "POST",
    body: JSON.stringify({
      amount: input.amountPaise,
      currency: "INR",
      accept_partial: false,
      description: input.description.slice(0, 2048),
      reference_id: input.referenceId.slice(0, 40),
      customer: input.customer,
      notify: { sms: false, email: false }, // Notiz sends its own notifications
      reminder_enable: false,
      notes: input.notes,
      expire_by: input.expireBy,
    }),
  });
}

export function fetchPaymentLink(keys: RazorpayKeys, id: string): Promise<PaymentLink> {
  return call<PaymentLink>(keys, `/payment_links/${encodeURIComponent(id)}`);
}

export function fetchPayment(keys: RazorpayKeys, id: string): Promise<Payment> {
  return call<Payment>(keys, `/payments/${encodeURIComponent(id)}`);
}

/** The bank reference to print as "UTR": UPI RRN when available, else Razorpay's payment id. */
export function paymentReference(p: Payment | undefined, fallbackId: string): string {
  return p?.acquirer_data?.rrn || p?.acquirer_data?.upi_transaction_id || p?.acquirer_data?.bank_transaction_id ||
    p?.id || fallbackId;
}

/** HMAC-SHA256 of the raw body with the webhook secret, hex encoded (Razorpay's X-Razorpay-Signature). */
export async function webhookSignature(secret: string, rawBody: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const mac = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(rawBody)));
  return Array.from(mac, (b) => b.toString(16).padStart(2, "0")).join("");
}

/** Compares without leaking where the strings differ. */
export function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}
