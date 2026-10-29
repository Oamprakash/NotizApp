package com.notiz.app

/**
 * Supabase project settings: Supabase dashboard → Project Settings → API.
 * The anon key is safe to ship in the app; Row Level Security protects the data.
 */
object AppConfig {
    const val SUPABASE_URL = "https://vfsefnmowgsrhnkdciku.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZmc2Vmbm1vd2dzcmhua2RjaWt1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTMyNTQwMjUsImV4cCI6MjEwODgzMDAyNX0.m4tTAZBPkEDHALcUITHp1hMV6Gq2BtJy5U_EEN-i6J8"

    /** Country code prefixed to the phone number on the login screen. */
    const val DEFAULT_COUNTRY_CODE = "+91"
}
