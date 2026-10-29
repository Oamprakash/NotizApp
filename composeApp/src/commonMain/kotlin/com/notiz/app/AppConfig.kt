package com.notiz.app

/**
 * Supabase project settings: Supabase dashboard → Project Settings → API.
 * The anon key is safe to ship in the app; Row Level Security protects the data.
 */
object AppConfig {
    const val SUPABASE_URL = "https://YOUR-PROJECT-REF.supabase.co"
    const val SUPABASE_ANON_KEY = "YOUR-ANON-KEY"

    /** Country code prefixed to the phone number on the login screen. */
    const val DEFAULT_COUNTRY_CODE = "+91"
}
