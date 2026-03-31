package com.wallet.manager.data.remote.supabase

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.android.Android

object SupabaseConfig {
    private const val SUPABASE_URL = "https://kfjntmhfctgsassynzte.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imtmam50bWhmY3Rnc2Fzc3luenRlIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQ5NjUwODksImV4cCI6MjA5MDU0MTA4OX0.uAbKdkC7hDsyb_-i-gClN9EDcfgnzdiQ6-dQuBESi9s"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
    }
}
